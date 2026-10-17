package io.ib67.yuanban.bot.transport;

import io.ib67.yuanban.bot.data.ItemData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.compression.ZlibCompression;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginCompressionPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP 连接：测试在回环地址上扮演服务端，用 MCProtocolLib 的 {@link MinecraftCodec} 与 {@link MinecraftTypes} 读写帧。
 */
class TcpConnectionTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * 记录连接交出的数据包与关闭通知
     */
    private static final class RecordingSink implements InboundSink {
        final LinkedBlockingQueue<MinecraftPacket> packets = new LinkedBlockingQueue<>();
        final CompletableFuture<@Nullable Throwable> closed = new CompletableFuture<>();

        @Override
        public void accept(MinecraftPacket packet) {
            packets.add(packet);
        }

        @Override
        public void closed(@Nullable Throwable error) {
            closed.complete(error);
        }
    }

    /**
     * 服务端一侧：逐个读出客户端发出的数据包，按读出的数据包切换阶段；按服务端的阶段写出数据包
     */
    private static final class ServerSide {
        private final SocketChannel channel;
        private final ByteBuf pending = Unpooled.buffer();
        private final ZlibCompression zlib = new ZlibCompression();
        private ProtocolState readState = ProtocolState.HANDSHAKE;
        private int threshold = -1;

        private ServerSide(SocketChannel channel) {
            this.channel = channel;
        }

        MinecraftPacket next() throws Exception {
            // 帧的长度：先读够 VarInt 的全部字节
            for (int i = 0; ; i++) {
                ensure(i + 1);
                if ((pending.getByte(pending.readerIndex() + i) & 0x80) == 0) {
                    break;
                }
            }
            var length = MinecraftTypes.readVarInt(pending);
            ensure(length);
            var frame = pending.readSlice(length);
            var body = frame;
            if (threshold >= 0) {
                var dataLength = MinecraftTypes.readVarInt(frame);
                if (dataLength != 0) {
                    body = Unpooled.buffer(dataLength);
                    zlib.inflate(frame, body, dataLength);
                }
            }
            var id = MinecraftTypes.readVarInt(body);
            var packet = (MinecraftPacket) MinecraftCodec.CODEC.getCodec(readState).createServerboundPacket(id, body);
            assertFalse(body.isReadable(), "packet " + packet + " was not fully read");
            pending.discardReadBytes();
            readState = switch (packet) {
                case ClientIntentionPacket _ -> ProtocolState.LOGIN;
                case ServerboundLoginAcknowledgedPacket _, ServerboundConfigurationAcknowledgedPacket _ -> ProtocolState.CONFIGURATION;
                case ServerboundFinishConfigurationPacket _ -> ProtocolState.GAME;
                default -> readState;
            };
            return packet;
        }

        private void ensure(int bytes) throws IOException {
            var buffer = ByteBuffer.allocate(1 << 16);
            while (pending.readableBytes() < bytes) {
                if (channel.read(buffer.clear()) < 0) {
                    throw new EOFException();
                }
                pending.writeBytes(buffer.flip());
            }
        }

        /**
         * 按服务端的阶段与当前的压缩阈值编码一帧，写入 out
         */
        void frame(ByteBuf out, ProtocolState state, MinecraftPacket packet) {
            var body = Unpooled.buffer();
            MinecraftTypes.writeVarInt(body, MinecraftCodec.CODEC.getCodec(state).getClientboundId(packet));
            packet.serialize(body);
            var frame = Unpooled.buffer();
            if (threshold < 0) {
                frame.writeBytes(body);
            } else if (body.readableBytes() >= threshold) {
                MinecraftTypes.writeVarInt(frame, body.readableBytes());
                zlib.deflate(body, frame);
            } else {
                MinecraftTypes.writeVarInt(frame, 0);
                frame.writeBytes(body);
            }
            MinecraftTypes.writeVarInt(out, frame.readableBytes());
            out.writeBytes(frame);
        }

        /**
         * 一次写入全部字节
         */
        void write(ByteBuf out) throws IOException {
            var buffer = out.nioBuffer();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    private static ServerSocketChannel listen() throws IOException {
        return ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }

    private static int port(ServerSocketChannel listener) throws IOException {
        return ((InetSocketAddress) listener.getLocalAddress()).getPort();
    }

    private static ClientIntentionPacket handshake(int port) {
        return new ClientIntentionPacket(MinecraftCodec.CODEC.getProtocolVersion(), "127.0.0.1", port, HandshakeIntent.LOGIN);
    }

    private static List<MinecraftPacket> fill(int port, String username) {
        var packets = new ArrayList<MinecraftPacket>();
        packets.add(handshake(port));
        packets.add(new ServerboundHelloPacket(username, UUID.randomUUID()));
        packets.add(new ServerboundLoginAcknowledgedPacket());
        for (int i = 0; i < 700; i++) {
            packets.add(new ServerboundCustomPayloadPacket(Key.key("test:fill"), new byte[30_000]));
        }
        return packets;
    }

    @Test
    void phasesAndCompressionFollowThePacketsOnBothSides() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            try (var socket = listener.accept()) {
                var server = new ServerSide(socket);
                var handshake = handshake(port(listener));
                var hello = new ServerboundHelloPacket("tcp", UUID.randomUUID());
                connection.send(List.of(handshake, hello)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertEquals(handshake, server.next());
                assertEquals(hello, server.next());

                // Set Compression 与紧跟的压缩数据包在同一次写入中到达：入站在 Set Compression 之后立即按阈值解码，
                // Login Finished 之后立即按配置阶段解码
                var profile = new GameProfile(UUID.randomUUID(), "compressed_name");
                profile.setProperties(List.of(new GameProfile.Property("textures", "x".repeat(200))));
                var finished = new ClientboundLoginFinishedPacket(profile, new UUID(0, 0));
                var keepAlive = new ClientboundKeepAlivePacket(7);
                var out = Unpooled.buffer();
                server.frame(out, ProtocolState.LOGIN, new ClientboundLoginCompressionPacket(64));
                server.threshold = 64;
                server.frame(out, ProtocolState.LOGIN, finished);
                server.frame(out, ProtocolState.CONFIGURATION, keepAlive);
                server.write(out);
                assertEquals(new ClientboundLoginCompressionPacket(64), sink.packets.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                var readFinished = assertInstanceOf(ClientboundLoginFinishedPacket.class,
                        sink.packets.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                assertEquals(finished, readFinished);
                assertEquals("x".repeat(200), readFinished.getProfile().getProperties().getFirst().getValue());
                assertEquals(keepAlive, sink.packets.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS));

                // Login Acknowledged 与 Finish Configuration 改变出站阶段；超过阈值的数据包压缩写出
                var brand = new ServerboundCustomPayloadPacket(Key.key("brand"), new byte[200]);
                var creative = new ServerboundSetCreativeModeSlotPacket((short) 36,
                        new ItemStack(ItemData.id(Key.key("diamond_sword")), 1, null));
                connection.send(List.of(new ServerboundLoginAcknowledgedPacket(), brand,
                        ServerboundFinishConfigurationPacket.INSTANCE, creative)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertInstanceOf(ServerboundLoginAcknowledgedPacket.class, server.next());
                var readBrand = assertInstanceOf(ServerboundCustomPayloadPacket.class, server.next());
                assertEquals(200, readBrand.getData().length);
                assertInstanceOf(ServerboundFinishConfigurationPacket.class, server.next());
                assertEquals(creative, server.next());
                assertFalse(sink.closed.isDone());
            }
            connection.close();
        }
    }

    @Test
    void closeWritesBatchesHandedOverBeforeIt() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            try (var socket = listener.accept()) {
                var server = new ServerSide(socket);
                var handshake = handshake(port(listener));
                var hello = new ServerboundHelloPacket("closing", UUID.randomUUID());
                var written = connection.send(List.of(handshake, hello));
                connection.close();
                // close() 之前交出的数据包照常写出，然后关闭；之后交出的数据包以异常结束
                assertTrue(connection.send(List.of(hello)).isCompletedExceptionally());
                assertEquals(handshake, server.next());
                assertEquals(hello, server.next());
                assertThrows(EOFException.class, server::next);
                written.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertNull(sink.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void aBatchBeingWrittenEndsWhenTheConnectionBreaks() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            var socket = listener.accept();
            // 服务端不读取：套接字的发送缓冲区写满之后，这一批剩下的部分等待套接字可写
            var written = connection.send(fill(port(listener), "stuck"));
            Thread.sleep(200);
            assertFalse(written.isDone());
            // 服务端重置连接：还没有写完的一批以异常结束
            socket.setOption(StandardSocketOptions.SO_LINGER, 0);
            socket.close();
            var failure = assertThrows(ExecutionException.class, () -> written.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            // 连接随之关闭（读到重置或者写出失败，取决于操作系统先报告哪一个）
            sink.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Test
    void closeDiscardsWhatTheSocketCannotTakeAndReleasesTheConnection() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            try (var socket = listener.accept()) {
                // 服务端不读取：套接字的发送缓冲区写满之后，剩下的部分等待套接字可写
                var written = connection.send(fill(port(listener), "full"));
                var queued = connection.send(List.of(new ServerboundCustomPayloadPacket(Key.key("test:after"), new byte[16])));
                Thread.sleep(200);
                assertFalse(written.isDone());
                // 原版关闭 Netty 通道时丢弃没有写进套接字的数据：close() 不等待对端读取，连接随即关闭
                connection.close();
                assertNull(sink.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                assertInstanceOf(IOException.class, assertThrows(ExecutionException.class,
                        () -> written.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).getCause());
                assertThrows(ExecutionException.class, () -> queued.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                // 已经交给套接字的部分照常到达对端，然后是连接的末尾
                var buffer = ByteBuffer.allocate(1 << 16);
                long received = 0;
                int read;
                while ((read = socket.read(buffer.clear())) >= 0) {
                    received += read;
                }
                assertTrue(received > 0 && received < 700L * 30_000, String.valueOf(received));
            }
        }
    }

    @Test
    void encodingFailureClosesTheConnection() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            try (var socket = listener.accept()) {
                var server = new ServerSide(socket);
                var handshake = handshake(port(listener));
                // 登录阶段没有 Chat Command：编码失败，连接以这个异常关闭
                var written = connection.send(List.of(handshake, new ServerboundChatCommandPacket("help")));
                var failure = assertThrows(ExecutionException.class, () -> written.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                assertInstanceOf(EncoderException.class, failure.getCause());
                assertInstanceOf(EncoderException.class, sink.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                // 一批数据包写完之后才 flush：同一批中排在前面、还没有写进套接字的 Handshake 随连接关闭被丢弃
                assertThrows(EOFException.class, server::next);
                // 之后交出的数据包不再写出
                assertInstanceOf(ClosedChannelException.class, assertThrows(ExecutionException.class,
                        () -> connection.send(List.of(handshake)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).getCause());
            }
        }
    }

    @Test
    void connectionFailureThrows() throws Exception {
        int port;
        try (var listener = listen()) {
            port = port(listener);
        }
        var sink = new RecordingSink();
        assertThrows(UncheckedIOException.class, () -> new TcpTransport("127.0.0.1", port).open("tcp", sink));
        assertFalse(sink.closed.isDone());
    }
}
