package io.ib67.yuanban.bot.transport;

import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

/**
 * 连接把数据包交给 bot 的入口。{@link #registries()} 以外的方法的调用不会并发，按到达顺序：
 * TCP 连接在入站线程上调用；对象级连接在发送数据包的服务端线程上调用，由连接的锁保证不并发。
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
     * 由 TCP 连接的入站线程与写线程调用
     */
    Registries registries();

    /**
     * 连接关闭，只调用一次。
     *
     * @param error TCP 连接为入站线程或写线程上的异常，对象级连接为 {@link #accept} 抛出的异常；
     *              正常关闭（对端关闭或本地调用 close）时为 null
     */
    void closed(@Nullable Throwable error);
}
