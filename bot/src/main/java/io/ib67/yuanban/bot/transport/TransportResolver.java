package io.ib67.yuanban.bot.transport;

/**
 * 处理 Transfer 时为新地址选择 Transport。
 */
@FunctionalInterface
public interface TransportResolver {
    /**
     * 默认实现：总是使用 TCP 连接
     */
    TransportResolver TCP = TcpTransport::new;

    Transport resolve(String host, int port);
}
