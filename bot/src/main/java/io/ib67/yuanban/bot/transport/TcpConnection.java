package io.ib67.yuanban.bot.transport;

import io.netty.channel.ChannelFuture;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.compression.CompressionConfig;
import org.geysermc.mcprotocollib.network.compression.ZlibCompression;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginCompressionPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一条 TCP 连接，使用 MCProtocolLib 的 Netty 管线完成分帧、压缩与编解码。MCProtocolLib 的默认监听器不启用，
 * 协议阶段由本类按数据包切换，登录与配置的应答由 bot 的协议状态机产生。
 * <ul>
 *     <li>入站：数据包在 Netty 事件循环上按到达顺序交给 {@link InboundSink}。收到 Set Compression 时立即启用压缩，
 *     因为同一次读取中紧跟的数据包已经是压缩格式；收到 Login Success、Finish Configuration、Start Configuration
 *     时立即切换入站阶段。</li>
 *     <li>出站：调度线程交来的一批数据包在事件循环上的一个任务中依次写入（编码在写入时按当前的出站阶段完成，
 *     改变阶段的数据包写入之后立即切换），最后一次写出到套接字（wiki FAQ 建议同一 tick 内的数据包一起发送）。</li>
 *     <li>关闭：按原版客户端的行为关闭 Netty 通道（原版 {@code Connection.disconnect}）。之前交出的数据包已经在通道中，
 *     还没有写进套接字的部分被 Netty 丢弃，写出它们的 future 以异常完成；关闭从不等待对端读取。</li>
 * </ul>
 * {@link MinecraftProtocol} 的阶段只在事件循环上读写。
 */
public final class TcpConnection implements ClientConnection {
    private final String host;
    private final int port;
    private final MinecraftProtocol protocol;
    private final InboundSink sink;
    private final LastReadHandler lastRead = new LastReadHandler();
    private final BotClientSession session;
    private final CompletableFuture<Void> active = new CompletableFuture<>();
    private final AtomicBoolean closedNotified = new AtomicBoolean();
    private volatile boolean closing;
    /**
     * 第一个写出失败的异常：写出失败时本类关闭通道，关闭通知带有这个异常
     */
    private volatile @Nullable Throwable writeFailure;

    private TcpConnection(String host, int port, InboundSink sink) {
        this.host = host;
        this.port = port;
        this.sink = sink;
        this.protocol = new MinecraftProtocol(MinecraftCodec.CODEC);
        protocol.setUseDefaultListeners(false);
        this.session = new BotClientSession(new InetSocketAddress(host, port), protocol, lastRead);
        session.setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, false);
        session.addListener(new Listener());
    }

    /**
     * 在调用线程上等待连接建立
     *
     * @throws UncheckedIOException 连接失败
     */
    static TcpConnection open(String host, int port, InboundSink sink) {
        var connection = new TcpConnection(host, port, sink);
        connection.session.connect(false);
        try {
            connection.active.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connection.close();
            throw new IllegalStateException("interrupted while connecting to " + host + ":" + port, e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof IOException io) {
                throw new UncheckedIOException("failed to connect to " + host + ":" + port, io);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("failed to connect to " + host + ":" + port, cause);
        }
        return connection;
    }

    @Override
    public String serverAddress() {
        return host;
    }

    @Override
    public int serverPort() {
        return port;
    }

    /**
     * @return 全部写进套接字之后完成；没有写进套接字之前连接关闭时以异常完成
     */
    @Override
    public CompletableFuture<Void> send(List<MinecraftPacket> packets) {
        if (closing) {
            return CompletableFuture.failedFuture(new ClosedChannelException());
        }
        var future = new CompletableFuture<Void>();
        var batch = List.copyOf(packets);
        var channel = session.getChannel();
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                future.completeExceptionally(new ClosedChannelException());
                return;
            }
            if (batch.isEmpty()) {
                future.complete(null);
                return;
            }
            var remaining = new AtomicInteger(batch.size());
            for (var packet : batch) {
                channel.write(packet).addListener((ChannelFuture written) -> {
                    if (!written.isSuccess()) {
                        // 先以这个异常结束这一批：关闭通道时，同一批中还没有写出的数据包以 ClosedChannelException 失败
                        future.completeExceptionally(written.cause());
                        writeFailed(written.cause());
                    } else if (remaining.decrementAndGet() == 0) {
                        future.complete(null);
                    }
                });
                switchStates(packet);
            }
            channel.flush();
        });
        return future;
    }

    /**
     * 关闭连接：已经交给通道的数据包中还没有写进套接字的部分被丢弃
     */
    @Override
    public void close() {
        closing = true;
        var channel = session.getChannel();
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 最后一次从套接字读到数据的时间（{@link System#nanoTime()}）
     */
    public long lastReadNanos() {
        return lastRead.lastReadNanos();
    }

    /**
     * 写出失败（编码失败或者套接字错误）：没有在关闭时关闭通道，关闭通知带有这个异常
     */
    private void writeFailed(Throwable cause) {
        if (closing) {
            return;
        }
        if (writeFailure == null) {
            writeFailure = cause;
        }
        session.getChannel().close();
    }

    /**
     * 发出改变阶段的数据包之后切换出站阶段；握手之后入站阶段同时切换，服务端从这时开始按登录阶段回应
     */
    private void switchStates(MinecraftPacket packet) {
        switch (packet) {
            case ClientIntentionPacket intention -> {
                var state = intention.getIntent() == HandshakeIntent.STATUS ? ProtocolState.STATUS : ProtocolState.LOGIN;
                protocol.setOutboundState(state);
                protocol.setInboundState(state);
            }
            case ServerboundLoginAcknowledgedPacket _ -> protocol.setOutboundState(ProtocolState.CONFIGURATION);
            case ServerboundFinishConfigurationPacket _ -> protocol.setOutboundState(ProtocolState.GAME);
            case ServerboundConfigurationAcknowledgedPacket _ -> protocol.setOutboundState(ProtocolState.CONFIGURATION);
            default -> {
            }
        }
    }

    private final class Listener extends SessionAdapter {
        @Override
        public void connected(ConnectedEvent event) {
            active.complete(null);
        }

        @Override
        public void packetReceived(Session session, Packet packet) {
            if (packet instanceof ClientboundLoginCompressionPacket compression) {
                var threshold = compression.getThreshold();
                session.setCompression(threshold >= 0 ? new CompressionConfig(threshold, new ZlibCompression(), false) : null);
            }
            sink.accept((MinecraftPacket) packet);
            var next = switch (packet) {
                case ClientboundLoginFinishedPacket _, ClientboundStartConfigurationPacket _ -> ProtocolState.CONFIGURATION;
                case ClientboundFinishConfigurationPacket _ -> ProtocolState.GAME;
                default -> null;
            };
            if (next != null) {
                session.switchInboundState(() -> protocol.setInboundState(next));
            }
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            if (!active.isDone()) {
                active.completeExceptionally(Objects.requireNonNullElseGet(event.getCause(),
                        () -> new IOException("the connection to " + host + ":" + port + " was closed before it was established")));
                return;
            }
            if (!closedNotified.compareAndSet(false, true)) {
                return;
            }
            var cause = event.getCause() != null ? event.getCause() : writeFailure;
            sink.closed(closing ? null : cause);
        }
    }
}
