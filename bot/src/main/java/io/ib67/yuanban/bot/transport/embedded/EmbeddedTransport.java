package io.ib67.yuanban.bot.transport.embedded;

import io.ib67.yuanban.bot.transport.ClientConnection;
import io.ib67.yuanban.bot.transport.InboundSink;
import io.ib67.yuanban.bot.transport.Transport;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerProcess;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.property.ServerProperties;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对象级进程内连接：与同一 JVM 中的 Minestom 服务端直接传递数据包对象，不经过编码。
 * 服务端是否推进由测试负责。
 */
public final class EmbeddedTransport implements Transport {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /**
     * 每个服务端进程中还没有关闭的对象级连接；服务端进程被回收之后条目随之消失
     */
    private static final Map<ServerProcess, ProcessConnections> PROCESSES = Collections.synchronizedMap(new WeakHashMap<>());

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
        var connections = PROCESSES.computeIfAbsent(process, ProcessConnections::watch);
        var connection = new EmbeddedPlayerConnection(SEQUENCE.incrementAndGet(), inbound, connections);
        connections.add(connection);
        connection.start();
        return new Client(connection);
    }

    /**
     * 一个服务端进程中还没有关闭的对象级连接。服务端进程停止时，它的关闭任务（{@code SchedulerManager.buildShutdownTask}，
     * 在 {@code ConnectionManager.shutdown} 踢出玩家之前执行）对每个连接调用 {@link EmbeddedPlayerConnection#serverStopped()}；
     * 之后才加入的连接同样处理
     */
    static final class ProcessConnections {
        private final Set<EmbeddedPlayerConnection> connections = new HashSet<>();
        private boolean stopped;

        private static ProcessConnections watch(ServerProcess process) {
            var connections = new ProcessConnections();
            process.scheduler().buildShutdownTask(connections::stop);
            return connections;
        }

        private void add(EmbeddedPlayerConnection connection) {
            synchronized (this) {
                if (!stopped) {
                    connections.add(connection);
                    return;
                }
            }
            connection.serverStopped();
        }

        synchronized void remove(EmbeddedPlayerConnection connection) {
            connections.remove(connection);
        }

        private void stop() {
            List<EmbeddedPlayerConnection> open;
            synchronized (this) {
                stopped = true;
                open = List.copyOf(connections);
            }
            open.forEach(EmbeddedPlayerConnection::serverStopped);
        }
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
            connection.receive(packets);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            connection.closeByBot();
        }
    }
}
