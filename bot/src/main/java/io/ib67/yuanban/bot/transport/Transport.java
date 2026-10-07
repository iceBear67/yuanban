package io.ib67.yuanban.bot.transport;

/**
 * 建立到服务端的连接。
 */
public interface Transport {
    /**
     * 打开连接。inbound 接收连接交出的数据包与连接关闭通知（TCP 连接由入站线程解码后交出，
     * 对象级连接在发送数据包的服务端线程上交出）。
     */
    ClientConnection open(String username, InboundSink inbound);
}
