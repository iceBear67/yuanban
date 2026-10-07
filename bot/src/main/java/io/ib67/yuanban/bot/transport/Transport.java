package io.ib67.yuanban.bot.transport;

/**
 * 建立到服务端的连接。
 */
public interface Transport {
    /**
     * 打开连接。inbound 接收入站线程解码出的数据包与连接关闭通知。
     */
    ClientConnection open(String username, InboundSink inbound);
}
