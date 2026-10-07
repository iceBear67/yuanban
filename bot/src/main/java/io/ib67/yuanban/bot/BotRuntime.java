package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.scheduler.Scheduler;

import java.util.List;

/**
 * 一个调度线程，以及由它调度的全部 bot 的生命周期。
 */
public final class BotRuntime implements AutoCloseable {
    private final Scheduler scheduler;

    private BotRuntime(RuntimeOptions options) {
        this.scheduler = new Scheduler(options);
    }

    public static BotRuntime create(RuntimeOptions options) {
        return new BotRuntime(options);
    }

    public static BotRuntime create() {
        return create(RuntimeOptions.DEFAULT);
    }

    /**
     * 在调用线程上打开连接，返回 bot；握手在下一轮调度中发出
     *
     * @throws IllegalStateException runtime 已经关闭，或者在打开连接期间开始关闭（这时打开的连接被关闭）
     */
    public Bot connect(BotOptions options) {
        return scheduler.connect(options);
    }

    /**
     * 按创建顺序排列的全部 bot
     */
    public List<Bot> bots() {
        return List.copyOf(scheduler.bots());
    }

    /**
     * 暂停全部 bot 的移动。是请求，在下一轮调度中生效
     */
    public void suspendMovement() {
        scheduler.suspended(true);
    }

    public void resumeMovement() {
        scheduler.suspended(false);
    }

    /**
     * 断开全部 bot（未结束的动作以 {@link BotDisconnectedException} 结束，observer 以 DISCONNECTED 关闭），
     * 等调度线程退出后返回
     */
    @Override
    public void close() {
        scheduler.close();
    }
}
