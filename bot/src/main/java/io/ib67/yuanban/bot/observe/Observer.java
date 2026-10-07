package io.ib67.yuanban.bot.observe;

/**
 * 由用户构造、交给 {@link io.ib67.yuanban.bot.Bot#observe} 的观察者。
 * 全部回调在调度线程上执行；回调阻塞会让同一 {@link io.ib67.yuanban.bot.BotRuntime} 中的全部 bot 停住。
 */
public interface Observer {
    /**
     * 订阅的观察记录类型；注册时读取一次，之后不再变化
     */
    Subscription subscription();

    /**
     * 注册生效时调用一次，可以通过 {@code context.state()} 读取此刻的模型
     */
    default void onStart(ObserverContext context) {
    }

    /**
     * 收到一条订阅的观察记录，据此更新自己的状态
     */
    void onObservation(Observation observation, ObserverContext context);

    /**
     * 关闭时调用一次；因回调抛出异常而关闭时不调用
     */
    default void onClose(CloseReason reason, ObserverContext context) {
    }
}
