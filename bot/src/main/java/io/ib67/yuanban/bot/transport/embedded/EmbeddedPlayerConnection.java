package io.ib67.yuanban.bot.transport.embedded;

import io.ib67.yuanban.bot.transport.InboundSink;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.play.ClientCreativeInventoryActionPacket;
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
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对象级连接在服务端一侧的 {@link PlayerConnection}。连接没有常驻线程：
 * <ul>
 *     <li>服务端发出的数据包：{@link #sendPacket} 在服务端调用它的线程上按 {@code PlayerSocketConnection.writePacketSync} 的顺序处理，
 *     然后持有连接的锁交给 {@link InboundSink}；</li>
 *     <li>bot 发出的数据包：{@link #receive} 在 bot 的调度线程上照搬 {@code PlayerSocketConnection.processPackets} 的分派，
 *     还没有玩家时立即交给服务端的监听器，有玩家之后放进玩家的数据包队列，在服务端 tick 中处理。</li>
 * </ul>
 * 用完就退出的虚拟线程只有三种：创建玩家、配置、bot 关闭连接时的断开。
 */
public final class EmbeddedPlayerConnection extends PlayerConnection {
    /**
     * {@code LoginListener.ERROR_DURING_LOGIN}
     */
    private static final Component ERROR_DURING_LOGIN = Component.text("Error during login!", NamedTextColor.RED);

    private final int sequence;
    private final InboundSink sink;
    private final ListenerHandle<PlayerPacketOutEvent> outgoing = EventDispatcher.getHandle(PlayerPacketOutEvent.class);
    /**
     * 保护交给 bot 的一步（{@link InboundSink#accept}、{@link InboundSink#closed}）与 {@link #closed}：
     * 两者的调用因此不会并发，关闭只通知一次。持有它时不调用任何服务端代码，也不等待任何东西
     */
    private final Object lock = new Object();
    /**
     * bot 已经收到关闭通知，之后服务端发来的数据包都丢弃。只在持有 {@link #lock} 时写入
     */
    private volatile boolean closed;
    /**
     * 当前线程正在为这个连接触发 {@code PlayerPacketOutEvent} 时，为这次触发的暂存列表：
     * 监听器在同一线程上发给这个连接的数据包放进这里，排在触发它的数据包之后处理
     */
    private final ThreadLocal<@Nullable List<SendablePacket>> firing = new ThreadLocal<>();
    private final AtomicBoolean disconnected = new AtomicBoolean();
    private final AtomicReference<@Nullable CompletableFuture<List<SelectKnownPacksPacket.Entry>>> knownPacks = new AtomicReference<>();
    private volatile @Nullable GameProfile loginProfile;

    EmbeddedPlayerConnection(int sequence, InboundSink sink) {
        this.sequence = sequence;
        this.sink = sink;
    }

    /**
     * bot 发出的数据包，在调度线程上按顺序处理（{@link #process}）
     *
     * @return 处理完之后完成；连接已经关闭时以 {@link ClosedChannelException} 完成，与 TCP 连接相同
     */
    CompletableFuture<Void> receive(List<ClientPacket> packets) {
        if (closed) {
            return CompletableFuture.failedFuture(new ClosedChannelException());
        }
        packets.forEach(this::process);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * bot 一侧关闭连接（{@code ClientConnection.close()}）：通知 bot，之后服务端发来的数据包都丢弃；
     * 然后在虚拟线程上调用 {@link #disconnect()}，服务端代码不在 bot 的调度线程上执行。配置阶段 {@code disconnect()}
     * 在调用的线程上触发 {@code PlayerDisconnectEvent}，监听器等待手动推进的服务端、而推进服务端的测试线程正在等待 bot 时，
     * 两边会互相等待；游戏阶段它安排下一 tick 移除玩家，事件在 tick 线程上触发。
     * bot 已经收到关闭通知时什么都不做：{@code disconnect()} 已经或者将要由关闭连接的一方调用
     */
    void closeByBot() {
        if (notifyClosed()) {
            Thread.ofVirtual().name("yuanban-bot-embedded-disconnect-" + sequence).start(this::disconnect);
        }
    }

    /**
     * 在服务端调用它的线程上处理：照搬 {@code PlayerSocketConnection.writePacketSync} 的处理顺序（{@link #prepare}），
     * 然后持有连接的锁交给 bot（{@link #deliver}）。{@code PlayerPacketOutEvent} 监听器在同一线程上发给这个连接的数据包
     * 暂存起来，在触发它的数据包之后依次处理，与 socket 连接的写线程相同
     */
    @Override
    public void sendPacket(SendablePacket packet) {
        if (closed) {
            return;
        }
        var pending = firing.get();
        if (pending != null) {
            pending.add(packet);
            return;
        }
        var deferred = new ArrayList<SendablePacket>(0);
        var serverPacket = prepare(packet, deferred);
        if (serverPacket != null) {
            deliver(serverPacket);
        }
        deferred.forEach(this::sendPacket);
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

    /**
     * 服务端断开连接（踢出、超时、{@code ConnectionManager.shutdown}），以及 bot 关闭连接之后的断开。
     * {@code super.disconnect()} 从 {@code ConnectionManager} 移除玩家（服务端阶段不是游戏阶段时，它在调用的线程上触发
     * {@code PlayerDisconnectEvent}），然后通知 bot。踢出时的 Disconnect 数据包已经由 {@link #sendPacket} 交给 bot，排在关闭通知之前
     */
    @Override
    public void disconnect() {
        if (!disconnected.compareAndSet(false, true)) {
            return;
        }
        super.disconnect();
        notifyClosed();
    }

    /**
     * 照搬 {@code PlayerSocketConnection.processPackets} 与 {@code readClientPacket}。
     * 还没有玩家时（握手与 LOGIN 阶段）交给 {@code processClientPacket} 立即处理，与 socket 连接中这个阶段的数据包相同；
     * 有玩家之后放进玩家的数据包队列，在服务端 tick 中处理（配置阶段由 {@code ConnectionManager.tick} 处理）。
     * 连接阶段由 {@code processClientPacket} 在调用监听器之前更新
     */
    private void process(ClientPacket packet) {
        var startingState = getClientState();
        var player = getPlayer();
        // 照搬 readClientPacket：创造模式以外的玩家发来的创造模式物品栏操作直接丢弃
        if (packet instanceof ClientCreativeInventoryActionPacket
                && (player == null || player.getGameMode() != GameMode.CREATIVE)) {
            return;
        }
        if (packet instanceof ClientLoginAcknowledgedPacket && startingState == ConnectionState.LOGIN) {
            // LoginListener.loginAckListener 只接受 PlayerSocketConnection，这里照搬它的逻辑。
            // 其它阶段没有 Login Acknowledged 的监听器，照常处理：processClientPacket 与 Minestom 相同，
            // 只按 nextClientState 更新阶段并记一条警告
            acknowledgeLogin();
            return;
        }
        try {
            if (player == null) {
                MinecraftServer.getPacketListenerManager().processClientPacket(packet, this);
            } else {
                player.addPacketToQueue(packet);
            }
        } catch (Throwable e) {
            if (startingState.ordinal() > ServerProperties.SUPPRESS_MISUSED_PACKET_ERROR_LEVEL.get()) {
                MinecraftServer.getExceptionManager().handleException(e);
            }
            if (ServerProperties.REJECT_MISUSED_PACKET.get()) {
                disconnect();
            }
        }
    }

    /**
     * LOGIN 阶段的 Login Acknowledged：照搬 {@code processClientPacket} 的阶段更新与 {@code loginAckListener}。
     * {@code createPlayer} 断言在虚拟线程上调用，所以在虚拟线程上创建玩家，调度线程等待这个线程结束：
     * 处理下一个数据包时玩家已经存在，与 socket 连接在读取线程上直接创建玩家的顺序相同。
     * 配置与 {@code LoginListener.executeConfig} 相同，在新的虚拟线程上执行（配置过程会等待 bot 的回应）
     */
    private void acknowledgeLogin() {
        setClientState(ConnectionState.CONFIGURATION);
        var creating = new FutureTask<>(this::createPlayer);
        var thread = Thread.ofVirtual().name("yuanban-bot-embedded-login-" + sequence).start(creating);
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while creating the player", e);
        }
        if (creating.state() == Future.State.FAILED) {
            // 与 loginAckListener 相同
            MinecraftServer.getExceptionManager().handleException(creating.exceptionNow());
            kick(ERROR_DURING_LOGIN);
            return;
        }
        var player = creating.resultNow();
        Thread.ofVirtual().name("yuanban-bot-embedded-config-" + sequence).start(() -> {
            try {
                MinecraftServer.getConnectionManager().doConfiguration(player, true);
            } catch (Throwable t) {
                MinecraftServer.getExceptionManager().handleException(t);
                player.kick(ERROR_DURING_LOGIN);
            }
        });
    }

    /**
     * 用 Login Success 中的档案调用 {@code ConnectionManager.createPlayer}，在创建玩家的虚拟线程上执行
     */
    private Player createPlayer() {
        var profile = Objects.requireNonNull(loginProfile, "login success has not been sent");
        return MinecraftServer.getConnectionManager().createPlayer(this, profile);
    }

    /**
     * 照搬 {@code PlayerSocketConnection.writePacketSync} 的处理顺序。
     * 这里抛出的异常与 socket 连接的写线程相同：交给 {@code ExceptionManager}，然后关闭连接
     *
     * @param deferred 收集 {@code PlayerPacketOutEvent} 监听器在当前线程上发给这个连接的数据包
     * @return 交给 bot 的数据包；被监听器取消或者出错时为 null
     */
    private @Nullable ServerPacket prepare(SendablePacket packet, List<SendablePacket> deferred) {
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
                    firing.set(deferred);
                    try {
                        outgoing.call(event);
                    } finally {
                        firing.remove();
                    }
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

    /**
     * 持有连接的锁交给 bot（解码上下文与区段解码在这里执行）。
     * bot 一侧的异常以这个异常结束 bot，然后在锁外关闭连接，不交给 {@code ExceptionManager}
     */
    private void deliver(ServerPacket packet) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            try {
                sink.accept(packet);
                return;
            } catch (Throwable t) {
                closed = true;
                sink.closed(t);
            }
        }
        disconnect();
    }

    /**
     * 通知 bot 连接关闭，只通知一次
     *
     * @return 这次调用通知了 bot
     */
    private boolean notifyClosed() {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            closed = true;
            sink.closed(null);
            return true;
        }
    }
}
