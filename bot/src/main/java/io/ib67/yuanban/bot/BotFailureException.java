package io.ib67.yuanban.bot;

/**
 * bot 因程序错误结束。原因是在调度线程、入站线程或写线程上抛出的异常。
 */
public final class BotFailureException extends RuntimeException {
    public BotFailureException(Throwable cause) {
        super(cause);
    }
}
