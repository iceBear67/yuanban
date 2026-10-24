package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.RuntimeOptions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 每个 {@code BotRuntime} 一个调度线程，统一调度全部 bot。每轮按 bot 的创建顺序处理每个 bot，
 * 然后等待到最早的 tick 到期、有新的入站数据包或请求，或者被关闭。
 */
public final class Scheduler {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final RuntimeOptions options;
    private final @Nullable WaitPump pump;
    private final CopyOnWriteArrayList<BotInstance> bots = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> runtimeRequests = new ConcurrentLinkedQueue<>();
    private final Object activeClockLock = new Object();
    private final Thread thread;
    private volatile boolean closing;
    private volatile boolean running = true;
    private volatile boolean suspended;
    private volatile boolean tickingSuspended;
    // 只在 activeClockLock 的保护下读写，不需要 volatile
    private long pausedSince;
    private long pausedNanos;

    public Scheduler(RuntimeOptions options) {
        this.options = options;
        var waitPump = options.waitPump();
        this.pump = waitPump == null ? null : new WaitPump(waitPump);
        this.thread = Thread.ofPlatform()
                .name("yuanban-bot-scheduler-" + SEQUENCE.incrementAndGet())
                .daemon(true)
                .unstarted(this::loop);
        thread.start();
    }

    /**
     * 在调用线程上打开连接，然后加入调度
     *
     * @throws IllegalStateException runtime 已经关闭，或者在打开连接期间开始关闭；后一种情况下这个 bot 被断开
     */
    public BotInstance connect(BotOptions botOptions) {
        if (closing) {
            throw new IllegalStateException("the runtime is closed");
        }
        var bot = new BotInstance(this, botOptions);
        bot.open();
        bots.add(bot);
        wakeup();
        if (closing) {
            // 加入列表时调度线程还在运行：它退出时断开这个 bot；已经退出时在调用线程上断开
            if (!running) {
                bot.stopped();
            }
            throw new IllegalStateException("the runtime was closed while connecting");
        }
        return bot;
    }

    public List<BotInstance> bots() {
        return List.copyOf(bots);
    }

    public void wakeup() {
        LockSupport.unpark(thread);
    }

    public boolean isSchedulerThread() {
        return Thread.currentThread() == thread;
    }

    /**
     * 调度线程是否仍在运行；关闭之后模型不再变化，查询直接在调用线程上执行
     */
    boolean running() {
        return running;
    }

    /**
     * 等待时推进服务端；没有设置推进函数时为 null
     */
    @Nullable WaitPump pump() {
        return pump;
    }

    boolean suspended() {
        return suspended;
    }

    public void suspended(boolean suspended) {
        this.suspended = suspended;
        wakeup();
    }

    public void suspendTicking() {
        request(() -> setTickingSuspended(true));
    }

    public void resumeTicking() {
        request(() -> setTickingSuspended(false));
    }

    public List<Action> stepTicks(int ticks) {
        if (closing) {
            throw new IllegalStateException("the runtime is closed");
        }
        // 已经结束的 bot 不需要步进动作：bots 只增不减，断开的 bot 仍在列表中
        var actions = bots().stream().filter(bot -> !bot.ended()).<Action>map(bot -> bot.registerStep(ticks)).toList();
        request(() -> actions.forEach(action -> actionBot(action).activateStep(action, ticks)));
        return actions;
    }

    private static BotInstance actionBot(Action action) {
        return ((ActionImpl) action).bot();
    }

    private void request(Runnable task) {
        if (isSchedulerThread()) {
            task.run();
            return;
        }
        runtimeRequests.add(task);
        wakeup();
    }

    private void setTickingSuspended(boolean value) {
        synchronized (activeClockLock) {
            if (tickingSuspended == value) {
                return;
            }
            var now = System.nanoTime();
            if (value) {
                pausedSince = now;
            } else {
                pausedNanos += now - pausedSince;
                pausedSince = 0;
            }
            tickingSuspended = value;
        }
        for (var bot : bots) bot.runtimeSuspensionChanged();
    }

    /** 真实时间减去暂停 tick 的累计时间。 */
    public long activeNanos() {
        synchronized (activeClockLock) {
            var now = System.nanoTime();
            var paused = pausedNanos;
            if (tickingSuspended) {
                paused += now - pausedSince;
            }
            return now - paused;
        }
    }

    boolean tickingSuspended() {
        return tickingSuspended;
    }

    /**
     * 断开全部 bot，等调度线程处理完最后一轮后退出
     *
     * @throws IllegalStateException 在调度线程上调用
     */
    public void close() {
        if (isSchedulerThread()) {
            throw new IllegalStateException("BotRuntime.close blocks and cannot be called on the scheduler thread");
        }
        if (closing) {
            return;
        }
        for (var bot : bots) {
            bot.disconnect();
        }
        closing = true;
        wakeup();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while closing the runtime", e);
        }
    }

    private void loop() {
        try {
            while (true) {
                var stopping = closing;
                runRuntimeRequests();
                var next = Long.MAX_VALUE;
                for (var bot : bots) {
                    next = Math.min(next, bot.round(System.nanoTime()));
                }
                if (stopping) {
                    return;
                }
                if (next == Long.MAX_VALUE) {
                    LockSupport.park(this);
                } else {
                    var delay = next - System.nanoTime();
                    if (delay > 0) {
                        LockSupport.parkNanos(this, delay);
                    }
                }
            }
        } finally {
            running = false;
            // 最后一轮之后才入队的任务与在关闭期间加入的 bot：在这里处理，之后由提交任务的线程处理
            for (var bot : bots) {
                bot.stopped();
            }
        }
    }

    private void runRuntimeRequests() {
        Runnable request;
        while ((request = runtimeRequests.poll()) != null) request.run();
    }
}
