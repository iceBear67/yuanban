package io.ib67.yuanban.bot.transport.embedded;

import io.ib67.yuanban.bot.transport.ClientConnection;
import io.ib67.yuanban.bot.transport.InboundSink;
import io.ib67.yuanban.bot.transport.Transport;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.property.ServerProperties;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对象级进程内连接：与同一 JVM 中的 Minestom 服务端直接传递数据包对象，不经过编码。
 * 服务端是否推进由测试负责。
 */
public final class EmbeddedTransport implements Transport {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private EmbeddedTransport() {
    }

    public static EmbeddedTransport create() {
        return new EmbeddedTransport();
    }

    /**
     * @throws IllegalStateException 当前 JVM 中没有 Minestom 服务端进程、服务端不是离线模式，
     *                               或者 {@code minestom.viewable-packet} 不为 false
     */
    @Override
    public ClientConnection open(String username, InboundSink inbound) {
        var process = MinecraftServer.process();
        if (process == null) {
            throw new IllegalStateException("no Minestom server process in this JVM");
        }
        if (!(process.auth() instanceof Auth.Offline)) {
            throw new IllegalStateException("the embedded connection requires an offline mode server, got " + process.auth());
        }
        if (ServerProperties.VIEWABLE_PACKET.get()) {
            // 为 true 时 PacketViewableUtils 把发给观察者的数据包合并成 BufferedPacket，只写给 PlayerSocketConnection
            throw new IllegalStateException("the embedded connection requires minestom.viewable-packet=false");
        }
        return new Client(new EmbeddedPlayerConnection(SEQUENCE.incrementAndGet(), inbound));
    }

    private record Client(EmbeddedPlayerConnection connection) implements ClientConnection {
        @Override
        public String serverAddress() {
            return "localhost";
        }

        @Override
        public int serverPort() {
            return 25565;
        }

        @Override
        public CompletableFuture<Void> send(List<ClientPacket> packets) {
            return connection.receive(packets);
        }

        @Override
        public void close() {
            connection.closeByBot();
        }
    }
}
