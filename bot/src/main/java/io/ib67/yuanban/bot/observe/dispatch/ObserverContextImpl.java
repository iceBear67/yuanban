package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.view.StateReader;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ObserverContext} 的实现。
 */
public final class ObserverContextImpl implements ObserverContext {
    private final ObservationHost host;
    private final ObservationDispatcher dispatcher;
    private final ObserverEntry entry;

    ObserverContextImpl(ObservationHost host, ObservationDispatcher dispatcher, ObserverEntry entry) {
        this.host = host;
        this.dispatcher = dispatcher;
        this.entry = entry;
    }

    /**
     * 内置 observer 的状态读取检查：observer 观察中、并且调用线程不是调度线程时抛出 {@link IllegalStateException}
     *
     * @param context observer 收到的上下文；还没有注册时为 null
     */
    public static void checkReadable(@Nullable ObserverContext context) {
        if (context instanceof ObserverContextImpl impl && impl.entry.isOpen() && !impl.host.onSchedulerThread()) {
            throw new IllegalStateException("the observer is still observing; read it through ObserverHandle.read or after it is closed");
        }
    }

    @Override
    public Bot bot() {
        return host.bot();
    }

    @Override
    public StateReader state() {
        return host.reader();
    }

    @Override
    public @Nullable WorldInfo world() {
        return host.world();
    }

    /**
     * 在 observer 自己的回调中调用时，回调返回后关闭；在调度线程上的其它时刻调用时立即关闭
     *
     * @throws IllegalStateException 在调度线程以外调用
     */
    @Override
    public void close() {
        if (!host.onSchedulerThread()) {
            throw new IllegalStateException("ObserverContext.close must be called on the scheduler thread");
        }
        if (entry.inCallback) {
            entry.closeRequested = true;
        } else {
            dispatcher.close(entry, CloseReason.BY_OBSERVER);
        }
    }
}
