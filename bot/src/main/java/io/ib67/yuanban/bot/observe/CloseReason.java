package io.ib67.yuanban.bot.observe;

/**
 * observer 关闭的原因。
 */
public enum CloseReason {
    /**
     * 用户调用了 {@link ObserverHandle#close()}
     */
    BY_HANDLE,
    /**
     * observer 在回调中调用了 {@link ObserverContext#close()}
     */
    BY_OBSERVER,
    /**
     * 通过 {@link io.ib67.yuanban.bot.Bot#observeDuring} 注册的 observer，它对应的动作结束
     */
    ACTION_COMPLETED,
    /**
     * bot 的连接结束
     */
    DISCONNECTED,
    /**
     * observer 的回调抛出了异常
     */
    FAILED
}
