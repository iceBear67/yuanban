package io.ib67.yuanban.bot.transport;

import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

/**
 * 连接的入站线程把数据包交给 bot 的入口。{@link #registries()} 以外的方法只由同一个入站线程调用。
 */
public interface InboundSink {
    /**
     * 按到达顺序交出一个数据包。
     * 调用返回之后，{@link #registries()} 反映这个数据包带来的注册表变化。
     */
    void accept(ServerPacket packet);

    /**
     * TCP 连接编解码数据包时设置到 {@code NetworkBuffer} 上的注册表：
     * 收到 Finish Configuration 之后为按服务端编号构建的注册表，在此之前为原版注册表。
     * 由入站线程与写线程调用
     */
    Registries registries();

    /**
     * 连接关闭，只调用一次。
     *
     * @param error 入站线程或写线程上的异常；正常关闭（对端关闭或本地调用 close）时为 null
     */
    void closed(@Nullable Throwable error);
}
