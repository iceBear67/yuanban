package io.ib67.yuanban.bot;

/**
 * observer 的回调抛出了异常。这个 observer 以 {@link io.ib67.yuanban.bot.observe.CloseReason#FAILED} 关闭，
 * bot 不受影响；原因为回调抛出的异常。
 */
public final class ObserverFailureException extends RuntimeException {
    public ObserverFailureException(Throwable cause) {
        super(cause);
    }
}
