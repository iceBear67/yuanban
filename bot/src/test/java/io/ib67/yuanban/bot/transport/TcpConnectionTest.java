package io.ib67.yuanban.bot.transport;

import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.registry.VanillaReference;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketReading;
import net.minestom.server.network.packet.PacketWriting;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPluginMessagePacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * TCP 连接：测试在回环地址上扮演服务端，按客户端发出的阶段解码数据包。
 */
class TcpConnectionTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;

    /**
     * 记录入站线程交出的数据包与关闭通知；注册表为原版注册表
     */
    private static final class RecordingSink implements InboundSink {
        final LinkedBlockingQueue<ServerPacket> packets = new LinkedBlockingQueue<>();
        final CompletableFuture<@Nullable Throwable> closed = new CompletableFuture<>();

        @Override
        public void accept(ServerPacket packet) {
            packets.add(packet);
        }

        @Override
        public Registries registries() {
            return VanillaReference.get();
        }

        @Override
        public void closed(@Nullable Throwable error) {
            closed.complete(error);
        }
    }

    /**
     * 服务端一侧：逐个读出客户端发出的数据包，阶段按 PacketVanilla.nextClientState 更新
     */
    private static final class ClientReader {
        private final SocketChannel channel;
        private final NetworkBuffer buffer = NetworkBuffer.staticBuffer(1 << 20, VanillaReference.get());
        private ConnectionState state = ConnectionState.HANDSHAKE;
        private boolean compressed;

        private ClientReader(SocketChannel channel) {
            this.channel = channel;
        }

        ClientPacket next() throws Exception {
            while (true) {
                if (buffer.readableBytes() > 0) {
                    var start = buffer.readIndex();
                    if (PacketReading.readClient(buffer, state, compressed) instanceof PacketReading.Result.Success<ClientPacket> success) {
                        var parsed = success.packets().getFirst();
                        state = parsed.nextState();
                        return parsed.packet();
                    }
                    buffer.readIndex(start);
                }
                buffer.compact();
                buffer.readChannel(channel);
            }
        }
    }

    private static ServerSocketChannel listen() throws IOException {
        return ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }

    private static int port(ServerSocketChannel listener) throws IOException {
        return ((InetSocketAddress) listener.getLocalAddress()).getPort();
    }

    private static ClientHandshakePacket handshake(int port) {
        return new ClientHandshakePacket(MinecraftServer.PROTOCOL_VERSION, "127.0.0.1", port, ClientHandshakePacket.Intent.LOGIN);
    }

    @Test
    void writeThreadTracksPhasesCompressionAndRegistries() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            try (var socket = listener.accept()) {
                var reader = new ClientReader(socket);
                var handshake = handshake(port(listener));
                var loginStart = new ClientLoginStartPacket("tcp", UUID.randomUUID());
                connection.send(List.of(handshake, loginStart)).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertEquals(handshake, reader.next());
                assertEquals(loginStart, reader.next());

                // 服务端启用压缩：入站线程读到 Set Compression 之后，写线程按这个阈值写出
                var compression = NetworkBuffer.resizableBuffer(64, VanillaReference.get());
                PacketWriting.writeFramedPacket(compression, ConnectionState.LOGIN, new SetCompressionPacket(64), 0);
                compression.writeChannel(socket);
                assertEquals(new SetCompressionPacket(64), sink.packets.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
                reader.compressed = true;

                // Login Acknowledged 与 Finish Configuration 改变写线程的阶段；带附魔的物品需要缓冲区上的注册表
                var sword = ItemStack.builder(Material.DIAMOND_SWORD)
                        .set(DataComponents.ENCHANTMENTS, new EnchantmentList(Map.of(Enchantment.SHARPNESS, 5)))
                        .build();
                var brand = new ClientPluginMessagePacket("minecraft:brand", new byte[200]);
                var creative = new ClientCreativeInventoryActionPacket((short) 36, sword);
                connection.send(List.of(new ClientLoginAcknowledgedPacket(), brand, new ClientFinishConfigurationPacket(), creative))
                        .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                assertInstanceOf(ClientLoginAcknowledgedPacket.class, reader.next());
                var readBrand = assertInstanceOf(ClientPluginMessagePacket.class, reader.next());
                assertEquals(200, readBrand.data().length);
                assertInstanceOf(ClientFinishConfigurationPacket.class, reader.next());
                assertEquals(creative, reader.next());
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
                var reader = new ClientReader(socket);
                var handshake = handshake(port(listener));
                var loginStart = new ClientLoginStartPacket("closing", UUID.randomUUID());
                var written = connection.send(List.of(handshake, loginStart));
                connection.close();
                // close() 之前交出的数据包照常写出，然后关闭；之后交出的数据包以异常结束
                assertTrue(connection.send(List.of(loginStart)).isCompletedExceptionally());
                assertEquals(handshake, reader.next());
                assertEquals(loginStart, reader.next());
                assertThrows(EOFException.class, reader::next);
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
            // 服务端不读取：写线程取出这一批之后阻塞在写入上
            var packets = new ArrayList<ClientPacket>();
            packets.add(handshake(port(listener)));
            packets.add(new ClientLoginStartPacket("stuck", UUID.randomUUID()));
            packets.add(new ClientLoginAcknowledgedPacket());
            for (int i = 0; i < 700; i++) {
                packets.add(new ClientPluginMessagePacket("test:fill", new byte[30_000]));
            }
            var written = connection.send(packets);
            Thread.sleep(200);
            assertFalse(written.isDone());
            // 服务端重置连接：已经取出、还没有写完的一批以异常结束
            socket.setOption(StandardSocketOptions.SO_LINGER, 0);
            socket.close();
            var failure = assertThrows(ExecutionException.class, () -> written.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            // 连接随之关闭（入站线程读到重置或者末尾，取决于操作系统先报告哪一个）
            sink.closed.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Test
    void closeDiscardsWhatTheSocketCannotTakeAndReleasesTheConnection() throws Exception {
        try (var listener = listen()) {
            var sink = new RecordingSink();
            var connection = new TcpTransport("127.0.0.1", port(listener)).open("tcp", sink);
            try (var socket = listener.accept()) {
                // 服务端不读取：写线程写满发送缓冲区之后等待套接字可写
                var packets = new ArrayList<ClientPacket>();
                packets.add(handshake(port(listener)));
                packets.add(new ClientLoginStartPacket("full", UUID.randomUUID()));
                packets.add(new ClientLoginAcknowledgedPacket());
                for (int i = 0; i < 700; i++) {
                    packets.add(new ClientPluginMessagePacket("test:fill", new byte[30_000]));
                }
                var written = connection.send(packets);
                var queued = connection.send(List.of(new ClientPluginMessagePacket("test:after", new byte[16])));
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
    void readTimeoutAppliesDuringLogin() throws Exception {
        try (var listener = listen(); var runtime = BotRuntime.create()) {
            var bot = runtime.connect(BotOptions.builder().username("silent")
                    .transport(new TcpTransport("127.0.0.1", port(listener)))
                    .readTimeout(Duration.ofMillis(300))
                    .build());
            try (var _ = listener.accept()) {
                // 服务端一直不发送数据：登录阶段不 tick，调度线程按读取超时的时间醒来并断开
                var thrown = assertThrows(BotDisconnectedException.class, () -> bot.joined().await(TIMEOUT));
                assertTrue(thrown.getMessage().contains("timed out"), thrown.getMessage());
                assertEquals(BotStatus.DISCONNECTED, bot.status());
            }
        }
    }
}
