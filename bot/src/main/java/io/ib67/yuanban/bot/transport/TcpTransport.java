package io.ib67.yuanban.bot.transport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;

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
     * 阻塞地建立连接，开启 {@code TCP_NODELAY}（原版客户端与服务端都关闭 Nagle 算法）。
     * 设置选项或建立连接失败时，已经打开的通道随之关闭
     *
     * @throws UncheckedIOException 连接失败
     */
    @Override
    public TcpConnection open(String username, InboundSink inbound) {
        AsynchronousSocketChannel channel;
        try {
            channel = AsynchronousSocketChannel.open();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open a channel to " + host + ":" + port, e);
        }
        try {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.connect(new InetSocketAddress(host, port)).get();
        } catch (IOException e) {
            throw closing(channel, new UncheckedIOException("failed to connect to " + host + ":" + port, e));
        } catch (ExecutionException e) {
            throw closing(channel, e.getCause() instanceof IOException io
                    ? new UncheckedIOException("failed to connect to " + host + ":" + port, io)
                    : new IllegalStateException("failed to connect to " + host + ":" + port, e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw closing(channel, new IllegalStateException("interrupted while connecting to " + host + ":" + port, e));
        } catch (RuntimeException e) {
            throw closing(channel, e);
        } catch (Error e) {
            throw closing(channel, e);
        }
        return TcpConnection.start(host, port, channel, inbound);
    }

    /**
     * 关闭建立失败的通道；关闭时的异常附加到 failure 上
     */
    private static <T extends Throwable> T closing(AsynchronousSocketChannel channel, T failure) {
        try {
            channel.close();
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
        return failure;
    }
}
