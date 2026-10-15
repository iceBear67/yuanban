package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.dispatch.ObserverContextImpl;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 不按世界切换状态的内置 observer 的基类：记录收到的上下文，状态读取方法在观察中被调度线程以外的线程调用时抛出异常。
 */
abstract class BuiltinObserver implements Observer {
    private @Nullable ObserverContext context;

    @Override
    public final void onStart(ObserverContext context) {
        this.context = context;
        started(context);
    }

    /**
     * 注册生效时调用
     */
    void started(ObserverContext context) {
    }

    /**
     * 观察中被调度线程以外的线程调用时抛出 {@link IllegalStateException}
     */
    final void checkReadable() {
        ObserverContextImpl.checkReadable(context);
    }
}
