package io.ib67.yuanban.bot;

/**
 * 阻塞等待超过了给定的时限。
 */
public final class BotTimeoutException extends RuntimeException {
    public BotTimeoutException(String message) {
        super(message);
    }
}
