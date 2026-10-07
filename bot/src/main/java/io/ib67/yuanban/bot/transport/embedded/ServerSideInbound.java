package io.ib67.yuanban.bot.transport.embedded;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientCookieResponsePacket;
import net.minestom.server.network.packet.client.common.ClientKeepAlivePacket;
import net.minestom.server.network.packet.client.common.ClientPingRequestPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientEncryptionResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginPluginResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
import net.minestom.server.network.packet.client.status.StatusRequestPacket;
import net.minestom.server.property.ServerProperties;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 对象级连接的服务端入站线程：逐个取出 bot 发出的数据包，照搬 {@code PlayerSocketConnection.processPackets} 的分派规则。
 * 连接阶段由 {@code PacketListenerManager.processClientPacket}（立即处理的数据包）或玩家的数据包队列（在服务端 tick 中处理）更新。
 * bot 关闭连接时，这个线程处理完之前收到的数据包之后断开连接（相当于 socket 连接的读取线程读到连接结束）。
 */
final class ServerSideInbound {
    /**
     * {@code PlayerSocketConnection.IMMEDIATE_PROCESS_PACKETS} 的副本（私有常量）
     */
    static final Set<Class<? extends ClientPacket>> IMMEDIATE_PROCESS_PACKETS = Set.of(
            ClientHandshakePacket.class,
            ClientCookieResponsePacket.class,
            StatusRequestPacket.class,
            ClientPingRequestPacket.class,
            ClientKeepAlivePacket.class,
            ClientLoginStartPacket.class,
            ClientEncryptionResponsePacket.class,
            ClientLoginPluginResponsePacket.class,
            ClientSelectKnownPacksPacket.class,
            ClientLoginAcknowledgedPacket.class,
            ClientFinishConfigurationPacket.class
    );
    /**
     * {@code LoginListener.ERROR_DURING_LOGIN}
     */
    private static final Component ERROR_DURING_LOGIN = Component.text("Error during login!", NamedTextColor.RED);
    private static final Object STOP = new Object();
    /**
     * bot 关闭了连接：之前收到的数据包处理完之后断开，相当于 socket 连接的读取线程读到连接结束
     */
    private static final Object CLOSE = new Object();

    private final EmbeddedPlayerConnection connection;
    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();

    ServerSideInbound(EmbeddedPlayerConnection connection) {
        this.connection = connection;
    }

    void start(int sequence) {
        Thread.ofVirtual().name("yuanban-bot-embedded-server-inbound-" + sequence).start(this::loop);
    }

    void enqueue(List<ClientPacket> packets) {
        queue.addAll(packets);
    }

    void stop() {
        queue.add(STOP);
    }

    /**
     * bot 关闭连接：排在已经收到的数据包之后，在这个线程上断开连接
     */
    void close() {
        queue.add(CLOSE);
    }

    private void loop() {
        try {
            while (true) {
                var item = queue.take();
                if (item == STOP) {
                    return;
                }
                if (item == CLOSE) {
                    connection.disconnect();
                    return;
                }
                process((ClientPacket) item);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void process(ClientPacket packet) {
        var startingState = connection.getClientState();
        try {
            // 照搬 readClientPacket：创造模式以外的玩家发来的创造模式物品栏操作直接丢弃
            if (packet instanceof ClientCreativeInventoryActionPacket) {
                var player = connection.getPlayer();
                if (player == null || player.getGameMode() != GameMode.CREATIVE) {
                    return;
                }
            }
            if (packet instanceof ClientHandshakePacket handshake) {
                connection.handshake(handshake.serverAddress(), handshake.serverPort(), handshake.protocolVersion());
            }
            if (packet instanceof ClientLoginAcknowledgedPacket && startingState == ConnectionState.LOGIN) {
                // LoginListener.loginAckListener 只接受 PlayerSocketConnection，这里照搬它的逻辑。
                // 其它阶段没有 Login Acknowledged 的监听器，交给 processClientPacket：它与 Minestom 相同，
                // 只按 nextClientState 更新阶段并记一条警告
                acknowledgeLogin();
                return;
            }
            if (IMMEDIATE_PROCESS_PACKETS.contains(packet.getClass())) {
                MinecraftServer.getPacketListenerManager().processClientPacket(packet, connection);
            } else {
                Objects.requireNonNull(connection.getPlayer(), "no player for queued packet " + packet).addPacketToQueue(packet);
            }
        } catch (Throwable e) {
            if (startingState.ordinal() > ServerProperties.SUPPRESS_MISUSED_PACKET_ERROR_LEVEL.get()) {
                MinecraftServer.getExceptionManager().handleException(e);
            }
            if (ServerProperties.REJECT_MISUSED_PACKET.get()) {
                connection.disconnect();
            }
        }
    }

    private void acknowledgeLogin() {
        connection.setClientState(ConnectionState.CONFIGURATION);
        try {
            var player = connection.createPlayer();
            if (player == null) {
                // 服务端进程已经停止，连接已经关闭
                return;
            }
            // 与 LoginListener.executeConfig 相同：配置过程会等待客户端回应，所以放到新的虚拟线程中
            Thread.startVirtualThread(() -> {
                try {
                    MinecraftServer.getConnectionManager().doConfiguration(player, true);
                } catch (Throwable t) {
                    MinecraftServer.getExceptionManager().handleException(t);
                    player.kick(ERROR_DURING_LOGIN);
                }
            });
        } catch (Throwable t) {
            MinecraftServer.getExceptionManager().handleException(t);
            connection.kick(ERROR_DURING_LOGIN);
        }
    }
}
