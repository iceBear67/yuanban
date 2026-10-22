package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.BotTimeoutException;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * 阻塞等待：设置了推进函数时，到了推进的时间就在调用线程上执行一次（{@link WaitPump}：所有等待的线程共用，
 * 任何时刻最多一个线程执行，两次执行的开始时间至少相隔 50 毫秒），其余时间检查完成状态。
 */
final class Waits {
    /**
     * 其它线程正在推进、下一次推进的时间还没有更新时，至少等待这么久再检查
     */
    static final long MIN_WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private Waits() {
    }

    /**
     * @return future 的结果
     * @throws RuntimeException    future 异常完成时的异常
     * @throws BotTimeoutException 超时
     */
    static <T> T await(CompletableFuture<T> future, Duration timeout, @Nullable WaitPump pump,
                        LongSupplier clock, String what) {
        var deadline = clock.getAsLong() + timeout.toNanos();
        while (true) {
            var nextPump = 0L;
            if (!future.isDone() && pump != null) {
                nextPump = pump.pumpIfDue();
            }
            var now = clock.getAsLong();
            var remaining = deadline - now;
            try {
                if (future.isDone()) {
                    return future.get();
                }
                if (remaining <= 0) {
                    throw new BotTimeoutException("timed out after " + timeout + " waiting for " + what);
                }
                var wait = pump == null
                        ? remaining
                        : Math.min(remaining, Math.max(nextPump - System.nanoTime(), MIN_WAIT_NANOS));
                return future.get(wait, TimeUnit.NANOSECONDS);
            } catch (TimeoutException _) {
                // 继续推进并等待
            } catch (ExecutionException e) {
                throw rethrow(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for " + what, e);
            }
        }
    }

    /**
     * 不执行推进函数，等待调度线程完成一个任务
     */
    static <T> T join(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw rethrow(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private static RuntimeException rethrow(Throwable cause) {
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }
}
