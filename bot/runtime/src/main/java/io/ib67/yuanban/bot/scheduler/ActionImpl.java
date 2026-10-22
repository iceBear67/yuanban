package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.state.ActionHandle;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@link Action} 的实现。内部 future 只在调度线程上完成。
 */
public final class ActionImpl implements Action, ActionHandle {
    private final BotInstance bot;
    private final String name;
    private final long sequence;
    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private @Nullable Consumer<String> canceller;

    ActionImpl(BotInstance bot, String name, long sequence) {
        this.bot = bot;
        this.name = name;
        this.sequence = sequence;
    }

    /**
     * 同一个 bot 中按提交顺序递增的编号
     */
    long sequence() {
        return sequence;
    }

    @Override
    public boolean await(Duration timeout) {
        bot.checkNotSchedulerThread("Action.await");
        try {
            Waits.await(future, timeout, bot.pump(), bot::activeNanos, "action " + name);
            return true;
        } catch (ActionCancelledException e) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> future() {
        return future.copy();
    }

    @Override
    public void cancel() {
        cancel(ActionCancelledException.BY_USER);
    }

    @Override
    public void cancel(String reason) {
        if (future.isDone()) {
            return;
        }
        bot.cancel(this, reason);
    }

    public BotInstance bot() {
        return bot;
    }

    /**
     * 内部 future：异常完成时直接以结束的异常完成，不包装
     */
    CompletableFuture<Void> internalFuture() {
        return future;
    }

    public boolean isDone() {
        return future.isDone();
    }

    /**
     * 设置取消时的清理：由持有这个动作的队列在调度线程上完成清理并调用 {@link #cancelled}
     */
    public void canceller(@Nullable Consumer<String> canceller) {
        this.canceller = canceller;
    }

    /**
     * 调度线程：请求取消
     */
    public void requestCancel(String reason) {
        if (future.isDone()) {
            return;
        }
        if (canceller != null) {
            canceller.accept(reason);
        } else {
            cancelled(reason);
        }
    }

    /**
     * 调度线程：执行完毕
     */
    public void succeed() {
        if (future.complete(null)) {
            bot.actionEnded(this, null);
        }
    }

    /**
     * 调度线程：以取消结束
     */
    public void cancelled(String reason) {
        var exception = new ActionCancelledException(reason);
        if (future.completeExceptionally(exception)) {
            bot.actionEnded(this, exception);
        }
    }

    /**
     * 调度线程：连接结束或 bot 因异常结束
     */
    public void terminate(RuntimeException exception) {
        if (future.completeExceptionally(exception)) {
            bot.actionEnded(this, exception);
        }
    }

    @Override
    public String toString() {
        return "Action[" + name + "]";
    }
}
