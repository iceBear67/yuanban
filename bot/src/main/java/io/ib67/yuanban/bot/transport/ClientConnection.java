package io.ib67.yuanban.bot.transport;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 一条已经打开的连接。
 */
public interface ClientConnection {
    /**
     * 握手包中的服务端地址
     */
    String serverAddress();

    /**
     * 握手包中的服务端端口
     */
    int serverPort();

    /**
     * 只由调度线程调用；本轮的数据包一次交出，保持顺序，全部是发往服务端的数据包。
     *
     * @return 数据包交给连接之后完成（TCP 连接在写进套接字之后）；连接在写出之前关闭时以异常完成
     */
    CompletableFuture<Void> send(List<MinecraftPacket> packets);

    /**
     * 关闭连接，不等待对端读取：已经交出的数据包按顺序写出，写到连接不能再立即接受数据为止，没有写出的部分被丢弃
     * （原版客户端关闭 Netty 通道的行为），然后关闭。之后 {@link InboundSink#closed} 会被调用一次。
     */
    void close();
}
