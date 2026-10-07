package io.ib67.yuanban.bot.transport.embedded;

import io.ib67.yuanban.bot.transport.InboundSink;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.property.ServerProperties;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对象级连接在服务端一侧的 {@link PlayerConnection}。
 * 与 {@code PlayerSocketConnection} 相同，{@link #sendPacket} 只把数据包放进队列；bot 一侧的入站线程（相当于 socket 连接的写线程）
 * 逐个取出，按 {@code PlayerSocketConnection.writePacketSync} 的顺序处理后交给 {@link InboundSink}。
 */
public final class EmbeddedPlayerConnection extends PlayerConnection {
    private static final Object CLOSED = new Object();

    private final int sequence;
    private final InboundSink sink;
    private final EmbeddedTransport.ProcessConnections processConnections;
    private final LinkedBlockingQueue<Object> toBot = new LinkedBlockingQueue<>();
    private final ServerSideInbound serverInbound = new ServerSideInbound(this);
    private final ListenerHandle<PlayerPacketOutEvent> outgoing = EventDispatcher.getHandle(PlayerPacketOutEvent.class);
    private final AtomicBoolean disconnected = new AtomicBoolean();
    /**
     * 创建玩家与服务端进程停止互斥，见 {@link #createPlayer()} 与 {@link #serverStopped()}
     */
    private final Object lifecycle = new Object();
    private boolean serverStopped;
    private final AtomicReference<@Nullable CompletableFuture<List<SelectKnownPacksPacket.Entry>>> knownPacks = new AtomicReference<>();
    private volatile @Nullable String serverAddress;
    private volatile int serverPort;
    private volatile int protocolVersion;
    private volatile @Nullable GameProfile loginProfile;

    EmbeddedPlayerConnection(int sequence, InboundSink sink, EmbeddedTransport.ProcessConnections processConnections) {
        this.sequence = sequence;
        this.sink = sink;
        this.processConnections = processConnections;
    }

    void start() {
        Thread.ofVirtual().name("yuanban-bot-embedded-inbound-" + sequence).start(this::deliverLoop);
        serverInbound.start(sequence);
    }

    /**
     * bot 发出的数据包，交给服务端入站线程
     */
    void receive(List<ClientPacket> packets) {
        serverInbound.enqueue(packets);
    }

    /**
     * bot 一侧关闭连接（{@code ClientConnection.close()}）：服务端入站线程处理完已经收到的数据包之后调用 {@link #disconnect()}，
     * 与 socket 连接中服务端的读取线程读到连接结束时相同。{@code PlayerDisconnectEvent} 等服务端监听器因此在服务端入站线程上执行，
     * 不在 bot 的调度线程上执行
     */
    void closeByBot() {
        serverInbound.close();
    }

    /**
     * 记录 bot 在 Handshake 中发送的值
     */
    void handshake(String serverAddress, int serverPort, int protocolVersion) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.protocolVersion = protocolVersion;
    }

    /**
     * 服务端入站线程收到 LOGIN 阶段的 Login Acknowledged 时，用 Login Success 中的档案调用 {@code ConnectionManager.createPlayer}。
     * 与 {@link #serverStopped()} 互斥：服务端进程已经停止时不创建玩家，关闭连接
     *
     * @return 创建的玩家；服务端进程已经停止时为 null
     */
    @Nullable Player createPlayer() {
        var profile = Objects.requireNonNull(loginProfile, "login success has not been sent");
        synchronized (lifecycle) {
            if (!serverStopped) {
                return MinecraftServer.getConnectionManager().createPlayer(this, profile);
            }
        }
        disconnect();
        return null;
    }

    /**
     * 服务端进程停止（进程的关闭任务，在 {@code ConnectionManager.shutdown} 踢出玩家之前执行）。
     * 已经创建玩家的连接由 {@code ConnectionManager.shutdown} 踢出；还没有创建玩家的连接 Minestom 不会关闭，在这里关闭，bot 随之断开
     */
    void serverStopped() {
        synchronized (lifecycle) {
            serverStopped = true;
            if (getPlayer() != null) {
                return;
            }
        }
        disconnect();
    }

    @Override
    public void sendPacket(SendablePacket packet) {
        if (disconnected.get()) {
            return;
        }
        toBot.add(packet);
    }

    // FIXME(Minestom M19): PlayerConnection.requestKnownPacks 先发送请求，再给不是 volatile 的字段赋值；回应在赋值之前到达时，
    //  receiveKnownPacksResponse 读到 null，回应被丢弃，配置等到 KNOWN_PACKS_RESPONSE_TIMEOUT 之后断开连接。
    //  这里先记下等待回应的 future，再发送请求，见 MINESTOM-BUG-REPORT.md
    @Override
    public CompletableFuture<List<SelectKnownPacksPacket.Entry>> requestKnownPacks(List<SelectKnownPacksPacket.Entry> serverPacks) {
        var future = new CompletableFuture<List<SelectKnownPacksPacket.Entry>>();
        if (!knownPacks.compareAndSet(null, future)) {
            throw new IllegalStateException("Known packs already pending");
        }
        sendPacket(new SelectKnownPacksPacket(serverPacks));
        return future;
    }

    @Override
    public void receiveKnownPacksResponse(List<SelectKnownPacksPacket.Entry> clientPacks) {
        var future = knownPacks.getAndSet(null);
        if (future != null) {
            future.complete(clientPacks);
        }
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), sequence & 0xFFFF);
    }

    @Override
    public @Nullable String getServerAddress() {
        return serverAddress;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public int getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public void disconnect() {
        if (!disconnected.compareAndSet(false, true)) {
            return;
        }
        super.disconnect();
        processConnections.remove(this);
        serverInbound.stop();
        toBot.add(CLOSED);
    }

    private void deliverLoop() {
        try {
            while (true) {
                var item = toBot.take();
                if (item == CLOSED) {
                    sink.closed(null);
                    return;
                }
                var packet = write((SendablePacket) item);
                if (packet != null) {
                    sink.accept(packet);
                }
            }
        } catch (Throwable t) {
            if (disconnected.compareAndSet(false, true)) {
                super.disconnect();
                processConnections.remove(this);
                serverInbound.stop();
            }
            sink.closed(t);
        }
    }

    /**
     * 照搬 {@code PlayerSocketConnection.writePacketSync} 的处理顺序，在 bot 一侧的入站线程上执行（相当于 socket 连接的写线程）：
     * {@code PlayerPacketOutEvent} 监听器中发给这个连接的数据包排在触发它的数据包之后，监听器执行时不持有任何连接的锁。
     * 这里抛出的异常与 socket 连接的写线程相同：交给 {@code ExceptionManager}，然后关闭连接
     *
     * @return 交给 bot 的数据包；被监听器取消或者出错时为 null
     */
    private @Nullable ServerPacket write(SendablePacket packet) {
        try {
            var state = getServerState();
            var serverPacket = SendablePacket.extractServerPacket(state, packet);
            if (serverPacket == null) {
                throw new IllegalStateException("buffered packets are not supported by the embedded connection: " + packet);
            }
            var player = getPlayer();
            if (player != null) {
                if (outgoing.hasListener()) {
                    var event = new PlayerPacketOutEvent(player, serverPacket);
                    outgoing.call(event);
                    if (event.isCancelled()) {
                        return null;
                    }
                }
                // 与 PlayerSocketConnection.writePacketSync 相同：只翻译本身是 ComponentHolding 的数据包，
                // 从 CachedPacket、FramedPacket 中取出的数据包不翻译
                if (ServerProperties.AUTOMATIC_COMPONENT_TRANSLATION.get() && packet instanceof ServerPacket.ComponentHolding holding) {
                    var locale = Objects.requireNonNullElseGet(player.getLocale(), MinestomAdventure::getDefaultLocale);
                    serverPacket = holding.copyWithOperator(component -> MinestomAdventure.COMPONENT_TRANSLATOR.apply(component, locale));
                }
            }
            // 与 PlayerSocketConnection.writePacketSync 相同：只对交来的 ServerPacket 本身更新阶段，
            // 从 CachedPacket、FramedPacket 中取出的数据包不更新
            if (packet instanceof ServerPacket) {
                var nextState = PacketVanilla.nextServerState(serverPacket, state);
                if (nextState != state) {
                    setServerState(nextState);
                }
            }
            if (serverPacket instanceof LoginSuccessPacket(GameProfile profile, var _)) {
                loginProfile = profile;
            }
            return serverPacket;
        } catch (Throwable t) {
            MinecraftServer.getExceptionManager().handleException(t);
            disconnect();
            return null;
        }
    }
}
