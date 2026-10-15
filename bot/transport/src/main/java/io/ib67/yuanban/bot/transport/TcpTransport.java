package io.ib67.yuanban.bot.transport;

import java.io.UncheckedIOException;

/**
 * 通过 TCP 连接服务端。不支持加密：收到 Encryption Request 时连接以 {@link UnsupportedOperationException} 结束。
 */
public final class TcpTransport implements Transport {
    private final String host;
    private final int port;

    public TcpTransport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 阻塞地建立连接，开启 {@code TCP_NODELAY}（原版客户端与服务端都关闭 Nagle 算法）。不做 SRV 解析
     *
     * @throws UncheckedIOException 连接失败
     */
    @Override
    public TcpConnection open(String username, InboundSink inbound) {
        return TcpConnection.open(host, port, inbound);
    }
}
