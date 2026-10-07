package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.dispatch.ObserverContextImpl;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import org.jetbrains.annotations.Nullable;

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

    /**
     * {@code limit} 只能在注册之前调用：注册之后 observer 的字段由调度线程读写，其它线程修改它们会形成数据竞争。
     * 按世界的内置 observer 同样使用这个检查
     *
     * @throws IllegalStateException observer 已经注册过
     */
    static void checkNotRegistered(Observer observer) {
        if (BotInstance.registered(observer)) {
            throw new IllegalStateException("limit must be set before the observer " + observer + " is registered");
        }
    }
}
