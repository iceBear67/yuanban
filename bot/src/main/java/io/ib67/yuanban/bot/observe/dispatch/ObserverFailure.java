package io.ib67.yuanban.bot.observe.dispatch;

/**
 * observer 的回调抛出了异常。这个 observer 已经以 FAILED 关闭，异常结束整个 bot。
 */
public final class ObserverFailure extends RuntimeException {
    ObserverFailure(Throwable cause) {
        super("observer callback failed", cause);
    }
}
