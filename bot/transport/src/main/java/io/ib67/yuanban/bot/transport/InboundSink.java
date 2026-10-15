package io.ib67.yuanban.bot.transport;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.jetbrains.annotations.Nullable;

/**
 * 连接把数据包交给 bot 的入口。调用不会并发，按到达顺序：TCP 连接在 Netty 事件循环上调用，其它实现必须串行调用。
 */
public interface InboundSink {
    /**
     * 按到达顺序交出一个来自服务端的数据包
     */
    void accept(MinecraftPacket packet);

    /**
     * 连接关闭，只调用一次。
     *
     * @param error 连接上的异常（解码失败、写出失败、{@link #accept} 抛出的异常、套接字错误）；
     *              正常关闭（对端关闭或本地调用 close）时为 null
     */
    void closed(@Nullable Throwable error);
}
