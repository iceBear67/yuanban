package io.ib67.yuanban.bot;

/**
 * bot 因程序错误结束。原因是在调度线程上抛出的异常，或者连接交出数据包时抛出的异常
 * （TCP 连接在入站线程与写线程上，对象级连接在发送数据包的服务端线程上）。
 */
public final class BotFailureException extends RuntimeException {
    public BotFailureException(Throwable cause) {
        super(cause);
    }
}
