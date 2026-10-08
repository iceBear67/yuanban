package io.ib67.yuanban.bot.transport;

/**
 * 建立到服务端的连接。本库提供 {@link TcpTransport}，进程内连接等其它方式由使用者实现。
 */
public interface Transport {
    /**
     * 打开连接。inbound 接收连接交出的数据包与连接关闭通知（TCP 连接在 Netty 事件循环上交出）。
     */
    ClientConnection open(String username, InboundSink inbound);
}
