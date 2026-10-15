package io.ib67.yuanban.bot.scheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 等待时推进服务端（{@code RuntimeOptions.waitPump}）：同一个 {@code BotRuntime} 中所有阻塞等待的线程共用一个。
 * 推进函数在任何时刻最多由一个线程执行，两次执行的开始时间至少相隔 50 毫秒；到了时间时，由正在等待的线程中先检查到的一个执行，
 * 其余线程只检查完成状态。
 */
final class WaitPump {
    static final long INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private final Runnable pump;
    private final ReentrantLock running = new ReentrantLock();
    /**
     * 下一次推进的时间（{@link System#nanoTime()}），在执行推进函数之前更新
     */
    private volatile long nextNanos = System.nanoTime();

    WaitPump(Runnable pump) {
        this.pump = pump;
    }

    /**
     * 到了推进的时间并且没有其它线程正在推进时，在调用线程上执行一次推进函数；推进函数抛出的异常传给调用线程
     *
     * @return 下一次推进的时间（{@link System#nanoTime()}）
     */
    long pumpIfDue() {
        if (System.nanoTime() - nextNanos >= 0 && running.tryLock()) {
            try {
                var start = System.nanoTime();
                if (start - nextNanos >= 0) {
                    nextNanos = start + INTERVAL_NANOS;
                    pump.run();
                }
            } finally {
                running.unlock();
            }
        }
        return nextNanos;
    }
}
