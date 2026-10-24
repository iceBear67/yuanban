package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.BotTimeoutException;
import io.ib67.yuanban.bot.InventoryActions;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.ObservedAction;
import io.ib67.yuanban.bot.ObserverFailureException;
import io.ib67.yuanban.bot.decode.DecodeContext;
import io.ib67.yuanban.bot.decode.SectionPool;
import io.ib67.yuanban.bot.interaction.AttackTask;
import io.ib67.yuanban.bot.interaction.DigTask;
import io.ib67.yuanban.bot.interaction.HandAction;
import io.ib67.yuanban.bot.interaction.HandController;
import io.ib67.yuanban.bot.interaction.InteractTask;
import io.ib67.yuanban.bot.interaction.UseItemOnTask;
import io.ib67.yuanban.bot.interaction.UseItemTask;
import io.ib67.yuanban.bot.movement.JumpToAction;
import io.ib67.yuanban.bot.movement.MoveForwardAction;
import io.ib67.yuanban.bot.movement.MoveToAction;
import io.ib67.yuanban.bot.movement.MovementAction;
import io.ib67.yuanban.bot.movement.MovementController;
import io.ib67.yuanban.bot.movement.RotateAction;
import io.ib67.yuanban.bot.movement.Rotation;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observation.ActionCompleted;
import io.ib67.yuanban.bot.observe.Observation.HeldSlotChanged;
import io.ib67.yuanban.bot.observe.Observation.MovementSuspensionChanged;
import io.ib67.yuanban.bot.observe.Observation.TickingSuspensionChanged;
import io.ib67.yuanban.bot.observe.Observation.PlayerLoaded;
import io.ib67.yuanban.bot.observe.Observation.StateUpdated;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverHandle;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.observe.builtin.BlockBrokenObserver;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.observe.dispatch.ObservationDispatcher;
import io.ib67.yuanban.bot.observe.dispatch.ObservationHost;
import io.ib67.yuanban.bot.observe.dispatch.ObserverEntry;
import io.ib67.yuanban.bot.player.LocalPlayer;
import io.ib67.yuanban.bot.protocol.BundleBuffer;
import io.ib67.yuanban.bot.protocol.InboundPacket;
import io.ib67.yuanban.bot.protocol.ProtocolHost;
import io.ib67.yuanban.bot.protocol.ProtocolMachine;
import io.ib67.yuanban.bot.state.ActionHandle;
import io.ib67.yuanban.bot.state.BotState;
import io.ib67.yuanban.bot.state.Outbox;
import io.ib67.yuanban.bot.transport.ClientConnection;
import io.ib67.yuanban.bot.transport.InboundSink;
import io.ib67.yuanban.bot.transport.PacketDirection;
import io.ib67.yuanban.bot.transport.TcpConnection;
import io.ib67.yuanban.bot.view.StateReader;
import io.ib67.yuanban.bot.world.ClientWorld;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Point;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@link Bot} 的实现。模型、动作队列、协议状态只在调度线程上读写；其它线程只把请求放进队列。
 */
public final class BotInstance implements Bot, ObservationHost, ProtocolHost {
    private static final Component DISCONNECTED_BY_CLIENT = Component.text("disconnected by the client");
    private static final Component CONNECTION_CLOSED = Component.text("connection closed");
    private static final Component CLOSED_BEFORE_WRITTEN = Component.text("the connection closed before the packets were written");
    /**
     * wiki Chat Message：消息超过 256 个字符时服务端会踢出客户端
     */
    public static final int MAX_CHAT_LENGTH = 256;
    public static final int MAX_COMMAND_LENGTH = 32767;

    private final Scheduler scheduler;
    private final BotOptions options;
    private final ObservationDispatcher dispatcher;
    private final BotState state;
    private final ProtocolMachine protocol;
    private final MovementController movement;
    private final HandController hand;
    private final StateReaderImpl reader;
    private final ConcurrentLinkedQueue<Object> inbound = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> observerTasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> requests = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<OneShot> oneShots = new ArrayDeque<>();
    private final Set<ActionImpl> unfinished = ConcurrentHashMap.newKeySet();
    private final List<ActionImpl> joinedWaiters = new ArrayList<>();
    private final List<ActionImpl> loadedWaiters = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    /**
     * 在调度线程上调用 joined()、loaded() 时已经发生过：在下一轮调度中执行完毕
     */
    private final List<ActionImpl> succeedNextRound = new ArrayList<>();
    private final AtomicLong actionSequence = new AtomicLong();
    /**
     * {@code observeDuring} 调用 submit 期间为调用之前的动作编号：编号大于它的动作结束时总是产生 ActionCompleted。
     * observer 在 submit 返回之后才绑定到动作上，在 submit 中就已经结束的动作，它的 ActionCompleted 因此仍会在绑定之后投递，
     * observer 随之以 ACTION_COMPLETED 关闭。不在 submit 中时为 {@link Long#MAX_VALUE}
     */
    private long completionDuringSubmit = Long.MAX_VALUE;
    private final SecureRandom random = new SecureRandom();
    private volatile BotStatus status = BotStatus.LOGIN;
    private volatile @Nullable Component disconnectReason;
    private volatile @Nullable Throwable failure;
    private volatile boolean suspendRequested;
    private @Nullable ClientConnection connection;
    private BundleBuffer bundles = new BundleBuffer();
    private int generation;
    private long nextTickNanos;
    private boolean suspended;
    private boolean tickingSuspended;
    private boolean dirty;

    BotInstance(Scheduler scheduler, BotOptions options) {
        this.scheduler = scheduler;
        this.options = options;
        this.dispatcher = new ObservationDispatcher(this);
        this.state = new BotState(options, dispatcher,
                new LocalPlayer(ProtocolMachine.offlineUuid(options.username()), options.username()),
                scheduler::isSchedulerThread);
        this.movement = new MovementController(state);
        this.hand = new HandController(state, movement);
        this.protocol = new ProtocolMachine(this);
        this.reader = new StateReaderImpl(this);
    }

    /**
     * 在调用线程上打开第一条连接，握手在第一轮调度中发出
     */
    void open() {
        var target = ++generation;
        var opened = options.transport().open(options.username(), new Sink(target));
        submit(requests, () -> attach(target, opened, false));
    }

    // 内部组件

    public BotState state() {
        return state;
    }

    public MovementController movement() {
        return movement;
    }

    public HandController hand() {
        return hand;
    }

    public BotOptions options() {
        return options;
    }

    @Nullable WaitPump pump() {
        return scheduler.pump();
    }

    boolean movementSuspended() {
        return suspended;
    }

    boolean tickingSuspended() {
        return tickingSuspended;
    }

    @Override
    public long activeNanos() {
        return scheduler.activeNanos();
    }

    ActionImpl registerStep(int ticks) {
        return newAction("stepTicks(" + ticks + ")");
    }

    void activateStep(Action action, int ticks) {
        var stepAction = (ActionImpl) action;
        if (stepAction.isDone()) {
            return;
        }
        if (ended()) {
            stepAction.terminate(endException());
            return;
        }
        var step = new Step(stepAction, ticks);
        steps.add(step);
        stepAction.canceller(reason -> {
            if (steps.remove(step)) {
                stepAction.cancelled(reason);
            }
        });
        scheduler.wakeup();
    }

    void runtimeSuspensionChanged() {
        scheduler.wakeup();
    }

    @Override
    public boolean onSchedulerThread() {
        return scheduler.isSchedulerThread();
    }

    void checkNotSchedulerThread(String method) {
        if (onSchedulerThread()) {
            throw new IllegalStateException(method + " blocks and cannot be called on the scheduler thread");
        }
    }

    // ObservationHost

    @Override
    public Bot bot() {
        return this;
    }

    @Override
    public StateReader reader() {
        return reader;
    }

    @Override
    public @Nullable WorldInfo world() {
        return state.worldInfo();
    }

    // 协议状态机使用

    /**
     * 协议状态机改变连接状态：Login Finished、Finish Configuration、Start Configuration
     *
     * @throws IllegalStateException 不允许的转移（{@link #allowedTransition}）
     */
    public void status(BotStatus status) {
        transition(status);
    }

    /**
     * 改变连接状态，只允许 {@link #allowedTransition} 中的转移
     *
     * @throws IllegalStateException 不允许的转移
     */
    private void transition(BotStatus next) {
        if (!allowedTransition(status, next)) {
            throw new IllegalStateException("illegal status transition " + status + " -> " + next);
        }
        status = next;
    }

    /**
     * 允许的连接状态转移：Login Finished（LOGIN → CONFIGURATION）、Finish Configuration（CONFIGURATION → PLAY）、
     * Start Configuration（PLAY → CONFIGURATION）、Transfer（CONFIGURATION 或 PLAY → LOGIN）、
     * 连接结束或 bot 因异常结束（DISCONNECTED 以外的状态 → DISCONNECTED）。初始状态为 LOGIN
     */
    static boolean allowedTransition(BotStatus from, BotStatus to) {
        return switch (to) {
            case LOGIN -> from == BotStatus.CONFIGURATION || from == BotStatus.PLAY;
            case CONFIGURATION -> from == BotStatus.LOGIN || from == BotStatus.PLAY;
            case PLAY -> from == BotStatus.CONFIGURATION;
            case DISCONNECTED -> from != BotStatus.DISCONNECTED;
        };
    }

    /**
     * Login (play) 处理完成
     */
    public void markJoined() {
        var waiting = List.copyOf(joinedWaiters);
        joinedWaiters.clear();
        waiting.forEach(ActionImpl::succeed);
    }

    /**
     * 进入世界（Login (play) 与 Respawn，包括同一维度内保留世界的 Respawn）：第一次 tick 在一个间隔之后
     */
    public void enteredWorld() {
        nextTickNanos = 0;
    }

    /**
     * 发送了 Player Loaded
     */
    void markLoaded() {
        if (dispatcher.wants(PlayerLoaded.class)) {
            dispatcher.emit(new PlayerLoaded());
        }
        var waiting = List.copyOf(loadedWaiters);
        loadedWaiters.clear();
        waiting.forEach(ActionImpl::succeed);
    }

    /**
     * 服务端断开或本地断开：记录原因，bot 进入 DISCONNECTED；observer 以 DISCONNECTED 关闭，
     * 未结束的动作以 {@link BotDisconnectedException} 结束
     */
    public void disconnected(Component reason) {
        if (ended()) {
            return;
        }
        disconnectReason = reason;
        transition(BotStatus.DISCONNECTED);
        closeConnection();
        end(new BotDisconnectedException(reason));
    }

    /**
     * Transfer：关闭当前连接，用 TransportResolver 得到新 Transport，以握手意图 TRANSFER 重新登录，保留 CookieStore。
     * <p>
     * 原版客户端在关闭旧连接之前，已经把 Transfer 之前产生的数据包交给了网络层，关闭之后不再发送任何数据包：
     * Transfer 之前产生的数据包交给旧连接，然后关闭它（{@link ClientConnection#close()}：写到连接不能再立即接受数据为止）；
     * 离开世界引起的队列清空不发送清理数据包；
     * 新连接上的第一个数据包是 Handshake。
     */
    public void transfer(String host, int port) {
        var target = ++generation;
        var old = Objects.requireNonNull(connection, "transfer without a connection");
        handOver(old, state.outbox().drain());
        closeConnection();
        transition(BotStatus.LOGIN);
        protocol.leaveWorld();
        var transport = options.transportResolver().resolve(host, port);
        Thread.ofVirtual().name("yuanban-bot-transfer").start(() -> {
            try {
                var opened = transport.open(options.username(), new Sink(target));
                submit(requests, () -> attach(target, opened, true));
            } catch (Throwable t) {
                submit(requests, () -> {
                    if (target == generation && !ended()) {
                        fail(t);
                    }
                });
            }
        });
    }

    private void attach(int target, ClientConnection opened, boolean transfer) {
        if (target != generation || ended()) {
            opened.close();
            return;
        }
        connection = opened;
        bundles = new BundleBuffer();
        protocol.startLogin(opened.serverAddress(), opened.serverPort(), transfer);
    }

    private void closeConnection() {
        var current = connection;
        connection = null;
        if (current != null) {
            current.close();
        }
    }

    // 调度

    /**
     * 一轮调度
     *
     * @return 下一次需要调度这个 bot 的时间（{@link System#nanoTime()}）：tick 到期或者 TCP 连接的读取超时到期，
     * 取较早的一个；两者都没有时为 {@link Long#MAX_VALUE}
     */
    long round(long now) {
        var alreadyHappened = List.copyOf(succeedNextRound);
        succeedNextRound.clear();
        try {
            runAll(observerTasks);
            dispatcher.flush();
            drainInbound();
            alreadyHappened.forEach(ActionImpl::succeed);
            runAll(requests);
            dispatcher.flush();
            if (!ended()) {
                updateSuspension(now);
                checkReadTimeout(now);
            }
            var tickDue = !ended() && tickDue(now);
            if (!ended() && status == BotStatus.PLAY && state.loadTracker().loaded() && (!tickingSuspended || tickDue)) {
                runOneShots();
            }
            if (tickDue) {
                BotTicker.tick(this);
                completeSteps();
                dirty = true;
            }
            if (!ended()) {
                flushOutbox();
            }
            if (dirty) {
                dirty = false;
                if (dispatcher.wants(StateUpdated.class)) {
                    dispatcher.emit(new StateUpdated());
                }
                dispatcher.flush();
            }
        } catch (Throwable t) {
            fail(t);
        }
        if (ended()) {
            return Long.MAX_VALUE;
        }
        return Math.min(nextTickNanos == 0 ? Long.MAX_VALUE : nextTickNanos, readDeadline());
    }

    private void runAll(ConcurrentLinkedQueue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }

    private void drainInbound() {
        Object item;
        while ((item = inbound.poll()) != null) {
            switch (item) {
                case Received(int target, InboundPacket packet) -> {
                    if (target != generation || ended()) {
                        continue;
                    }
                    checkInbound(packet.packet());
                    var batch = bundles.accept(packet);
                    if (batch == null) {
                        continue;
                    }
                    for (var each : batch) {
                        protocol.handle(each);
                        // Transfer 之后同一个 bundle 中剩余的数据包属于旧连接，与旧连接之后的数据包一样被忽略
                        if (ended() || target != generation) {
                            break;
                        }
                    }
                    dirty = true;
                    dispatcher.flush();
                }
                case Closed(int target, Throwable error) -> {
                    if (target != generation || ended()) {
                        continue;
                    }
                    if (error != null) {
                        fail(error);
                    } else {
                        disconnected(CONNECTION_CLOSED);
                    }
                }
                default -> throw new IllegalStateException("unknown inbound item " + item);
            }
        }
    }

    /**
     * 当前连接交来的数据包必须在这条连接的握手发出之后到达，并且属于当前状态对应的协议阶段。TCP 连接只在收到 Login Start
     * 之后收到数据，并且按当前阶段解码，总是满足；直接交出数据包对象的 Transport（进程内连接）不满足时，按 12 结束 bot。
     * 在到达时检查：改变阶段的数据包不在 bundle 中，按到达顺序应用，所以此刻的状态就是 TCP 连接解码这个数据包时的阶段
     *
     * @throws IllegalStateException 握手还没有发出（{@link #attach} 还没有执行），或者数据包不属于当前状态对应的协议阶段
     */
    private void checkInbound(MinecraftPacket packet) {
        if (connection == null) {
            throw new IllegalStateException("received " + packet.getClass().getSimpleName()
                    + " before the handshake was sent on this connection");
        }
        if (!PacketDirection.clientboundIn(protocolState(status), packet.getClass())) {
            throw new IllegalStateException("received " + packet.getClass().getSimpleName() + " in " + status);
        }
    }

    /**
     * 连接状态对应的协议阶段
     *
     * @throws IllegalStateException 连接已经结束
     */
    private static ProtocolState protocolState(BotStatus status) {
        return switch (status) {
            case LOGIN -> ProtocolState.LOGIN;
            case CONFIGURATION -> ProtocolState.CONFIGURATION;
            case PLAY -> ProtocolState.GAME;
            case DISCONNECTED -> throw new IllegalStateException("the connection has ended");
        };
    }

    /**
     * 任意一级处于暂停状态，该 bot 的移动就暂停；实际状态改变时产生 MovementSuspensionChanged，
     * 它是 {@link StateReader#movementSuspended()} 读到的状态，所以本轮同样产生 StateUpdated
     */
    private void updateSuspension(long now) {
        var movement = suspendRequested || scheduler.suspended();
        if (movement != suspended) {
            suspended = movement;
            dirty = true;
            if (dispatcher.wants(MovementSuspensionChanged.class)) {
                dispatcher.emit(new MovementSuspensionChanged(movement));
            }
            dispatcher.flush();
        }
        var ticking = scheduler.tickingSuspended();
        if (ticking != tickingSuspended) {
            tickingSuspended = ticking;
            nextTickNanos = ticking ? 0 : now + tickPeriod();
            dirty = true;
            if (dispatcher.wants(TickingSuspensionChanged.class)) {
                dispatcher.emit(new TickingSuspensionChanged(ticking));
            }
            dispatcher.flush();
        }
    }

    /**
     * TCP 连接：距离上次读到数据超过 readTimeout 时关闭连接
     */
    private void checkReadTimeout(long now) {
        var timeout = options.readTimeout();
        if (timeout.isZero() || !(connection instanceof TcpConnection tcp)) {
            return;
        }
        if (now - tcp.lastReadNanos() > timeout.toNanos()) {
            disconnected(Component.text("timed out after " + timeout + " without data from the server"));
        }
    }

    /**
     * TCP 连接的读取超时到期的时间；没有 TCP 连接或者不检测时为 {@link Long#MAX_VALUE}。
     * 登录与配置阶段不 tick，调度线程按这个时间醒来检查超时
     */
    private long readDeadline() {
        var timeout = options.readTimeout();
        if (timeout.isZero() || !(connection instanceof TcpConnection tcp)) {
            return Long.MAX_VALUE;
        }
        return tcp.lastReadNanos() + timeout.toNanos() + 1;
    }

    /**
     * tick 周期为 {@code 1000 / min(tickRate, 20)} 毫秒；落后超过 5 个 tick 时丢弃积压，不追赶
     */
    private boolean tickDue(long now) {
        var world = state.worldOrNull();
        if (status != BotStatus.PLAY || world == null) {
            nextTickNanos = 0;
            return false;
        }
        var period = periodOf(world);
        if (tickingSuspended && !steps.isEmpty()) {
            nextTickNanos = now;
            return true;
        }
        if (tickingSuspended) {
            // 暂停期间没有步进时不需要再被调度，nextTickNanos 为 0 使 round() 返回 Long.MAX_VALUE，
            // 调度线程可以一直 park 到恢复或者下一次步进（这两者都会唤醒调度线程）
            nextTickNanos = 0;
            return false;
        }
        if (nextTickNanos == 0) {
            nextTickNanos = now + period;
            return false;
        }
        if (now - nextTickNanos > 5 * period) {
            nextTickNanos = now;
        }
        if (now < nextTickNanos) {
            return false;
        }
        nextTickNanos += period;
        return true;
    }

    private long tickPeriod() {
        var world = state.worldOrNull();
        if (world == null) {
            return 0;
        }
        return periodOf(world);
    }

    /**
     * 世界当前的 tick 周期：{@code 1000 / min(tickRate, 20)} 毫秒
     */
    private static long periodOf(ClientWorld world) {
        var rate = Math.min(world.tickState().tickRate(), 20f);
        if (!(rate > 0)) {
            throw new IllegalStateException("invalid tick rate " + rate);
        }
        return (long) (1_000_000_000L / rate);
    }

    private void completeSteps() {
        for (var iterator = steps.iterator(); iterator.hasNext();) {
            var step = iterator.next();
            if (--step.remaining == 0) {
                iterator.remove();
                step.action.canceller(null);
                step.action.succeed();
            }
        }
    }

    private void runOneShots() {
        OneShot shot;
        while ((shot = oneShots.poll()) != null) {
            shot.action().canceller(null);
            shot.body().accept(shot.action());
            dirty = true;
        }
        dispatcher.flush();
    }

    /**
     * 把本轮产生的待发送数据包一次交给连接，然后投递 PacketSent；之后完成在写出时执行完毕的动作
     */
    private void flushOutbox() {
        var outbox = state.outbox();
        if (outbox.isEmpty()) {
            return;
        }
        var current = connection;
        if (current == null) {
            // Transfer 之前产生的数据包已经交给旧连接，新连接建立之前不产生数据包，新连接上的第一个数据包是 Handshake
            throw new IllegalStateException("packets were produced without a connection: " + outbox.packets());
        }
        var batch = outbox.drain();
        var written = send(current, batch);
        dispatcher.flush();
        completeWhenWritten(current, batch, written);
        dispatcher.flush();
    }

    /**
     * 把一批数据包交给连接，产生 PacketSent，并安排在写出时执行完毕的动作；不投递观察记录
     */
    private void handOver(ClientConnection target, Outbox.Batch batch) {
        completeWhenWritten(target, batch, send(target, batch));
    }

    private CompletableFuture<Void> send(ClientConnection target, Outbox.Batch batch) {
        if (batch.packets().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        var written = target.send(batch.packets());
        for (var packet : batch.packets()) {
            dispatcher.emitSent(packet);
        }
        return written;
    }

    /**
     * 数据包写出时，这一批中写出即执行完毕的动作执行完毕。写出失败时：
     * Transfer 关闭的旧连接上的动作以 {@link BotDisconnectedException} 结束；
     * 当前连接写出失败时连接随之关闭，它的关闭通知结束整个 bot
     */
    private void completeWhenWritten(ClientConnection target, Outbox.Batch batch, CompletableFuture<Void> written) {
        if (batch.completeOnWrite().isEmpty()) {
            return;
        }
        if (written.isDone() && !written.isCompletedExceptionally()) {
            batch.completeOnWrite().forEach(ActionHandle::succeed);
            return;
        }
        written.whenComplete((_, error) -> submit(requests, () -> {
            if (error == null) {
                batch.completeOnWrite().forEach(ActionHandle::succeed);
            } else if (target != connection) {
                var exception = new BotDisconnectedException(CLOSED_BEFORE_WRITTEN);
                batch.completeOnWrite().forEach(action -> action.terminate(exception));
            }
        }));
    }

    /**
     * 调度线程上的异常结束这个 bot：关闭连接，未结束的动作以 {@link BotFailureException} 结束，
     * 仍在观察的 observer 以 DISCONNECTED 关闭，之后的调用立即抛出 {@link BotFailureException}
     */
    void fail(Throwable t) {
        if (failure != null) {
            return;
        }
        failure = t;
        var wasEnded = status == BotStatus.DISCONNECTED;
        if (!wasEnded) {
            transition(BotStatus.DISCONNECTED);
        }
        closeConnection();
        if (!wasEnded) {
            end(new BotFailureException(t));
        }
    }

    /**
     * 先关闭全部 observer，再结束未结束的动作。某个 observer 的 {@code onClose} 抛出异常时只有它以 FAILED 关闭，
     * 异常记在它的句柄上（{@link io.ib67.yuanban.bot.observe.ObserverHandle#failure()}），结束动作的异常不变
     */
    private void end(RuntimeException exception) {
        oneShots.clear();
        nextTickNanos = 0;
        dispatcher.closeAll(CloseReason.DISCONNECTED);
        for (var action : List.copyOf(unfinished)) {
            action.terminate(exception);
        }
    }

    boolean ended() {
        return status == BotStatus.DISCONNECTED;
    }

    RuntimeException endException() {
        var cause = failure;
        if (cause != null) {
            return new BotFailureException(cause);
        }
        return new BotDisconnectedException(Objects.requireNonNullElse(disconnectReason, CONNECTION_CLOSED));
    }

    /**
     * 提交动作或注册 observer 之前：bot 已经结束时抛出异常
     */
    private void ensureUsable() {
        if (ended()) {
            throw endException();
        }
    }

    void execute(Runnable task) {
        if (onSchedulerThread()) {
            task.run();
        } else {
            submit(requests, task);
        }
    }

    /**
     * 把任务交给调度线程。调度线程已经退出时（包括在它最后一轮之后才入队），在调用线程上执行仍在队列中的任务
     */
    private void submit(ConcurrentLinkedQueue<Runnable> queue, Runnable task) {
        queue.add(task);
        scheduler.wakeup();
        if (!scheduler.running()) {
            stopped();
        }
    }

    /**
     * 调度线程退出之后：断开还没有结束的 bot（在 runtime 关闭期间加入的 bot），执行仍在队列中的任务，
     * 让等待这些任务的线程得到结果、让之后才打开的连接被关闭。由调度线程在退出时调用，
     * 也由在它退出之后提交任务的线程调用
     */
    synchronized void stopped() {
        try {
            if (!ended()) {
                disconnected(DISCONNECTED_BY_CLIENT);
            }
            runAll(observerTasks);
            runAll(requests);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * 查询：在其它线程调用时作为查询任务交给调度线程并等待结果；在调度线程上调用时直接执行
     */
    <T> T query(Supplier<T> query) {
        var cause = failure;
        if (cause != null) {
            throw new BotFailureException(cause);
        }
        if (onSchedulerThread() || !scheduler.running()) {
            return query.get();
        }
        var result = new CompletableFuture<T>();
        submit(requests, () -> {
            try {
                result.complete(query.get());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return Waits.join(result);
    }

    // 动作

    ActionImpl newAction(String name) {
        var action = new ActionImpl(this, name, actionSequence.incrementAndGet());
        unfinished.add(action);
        return action;
    }

    void actionEnded(ActionImpl action, @Nullable Throwable error) {
        unfinished.remove(action);
        if (action.sequence() > completionDuringSubmit || dispatcher.wantsCompletion(action)) {
            dispatcher.emit(new ActionCompleted(action, error));
        }
    }

    void cancel(ActionImpl action, String reason) {
        execute(() -> action.requestCancel(reason));
    }

    private Action movementAction(String name, Function<ActionImpl, MovementAction> factory) {
        ensureUsable();
        var action = newAction(name);
        execute(() -> {
            if (ended()) {
                action.terminate(endException());
            } else {
                movement.enqueue(factory.apply(action));
            }
        });
        return action;
    }

    private Action handAction(String name, Function<ActionImpl, HandAction> factory) {
        ensureUsable();
        var action = newAction(name);
        execute(() -> {
            if (ended()) {
                action.terminate(endException());
            } else {
                hand.enqueue(factory.apply(action));
            }
        });
        return action;
    }

    /**
     * 一次性请求：按提交顺序在 bot 加载之后的下一轮调度中执行，产生的数据包本轮发出，数据包交给连接时执行完毕
     *
     * @param body 在调度线程上把数据包写入 Outbox
     */
    public Action oneShot(String name, Consumer<ActionImpl> body) {
        ensureUsable();
        var action = newAction(name);
        execute(() -> {
            if (ended()) {
                action.terminate(endException());
                return;
            }
            var shot = new OneShot(action, current -> {
                body.accept(current);
                state.outbox().completeWhenWritten(current);
            });
            oneShots.addLast(shot);
            action.canceller(reason -> {
                oneShots.remove(shot);
                action.cancelled(reason);
            });
        });
        return action;
    }

    /**
     * 等待 Login (play) 或 Player Loaded。已经发生过时在下一轮调度中执行完毕：在其它线程调用时请求就在下一轮处理；
     * 在调度线程上调用时推迟到下一轮，{@code observeDuring} 因此可以在动作结束之前把 observer 绑定到它上面
     */
    private Action waitFor(String name, BooleanSupplier done, List<ActionImpl> waiters) {
        ensureUsable();
        var action = newAction(name);
        var calledOnSchedulerThread = onSchedulerThread();
        execute(() -> {
            if (ended()) {
                action.terminate(endException());
            } else if (done.getAsBoolean()) {
                if (calledOnSchedulerThread) {
                    succeedNextRound.add(action);
                    scheduler.wakeup();
                } else {
                    action.succeed();
                }
            } else {
                waiters.add(action);
                action.canceller(reason -> {
                    waiters.remove(action);
                    action.cancelled(reason);
                });
            }
        });
        return action;
    }

    // Bot

    @Override
    public String username() {
        return options.username();
    }

    @Override
    public BotStatus status() {
        return status;
    }

    @Override
    public @Nullable Component disconnectReason() {
        return disconnectReason;
    }

    @Override
    public @Nullable Throwable failure() {
        return failure;
    }

    @Override
    public <T> T inspect(Function<StateReader, T> reader) {
        return query(() -> reader.apply(this.reader));
    }

    /**
     * bot 已经结束时抛出 {@link BotDisconnectedException} 或 {@link BotFailureException}；
     * 注册时 observer 的回调抛出异常时抛出 {@link ObserverFailureException}
     */
    @Override
    public <O extends Observer> ObserverHandle<O> observe(O observer) {
        ensureUsable();
        if (onSchedulerThread()) {
            return new ObserverHandleImpl<>(this, registerNow(observer), observer);
        }
        var result = new CompletableFuture<ObserverHandle<O>>();
        submit(observerTasks, () -> {
            if (ended()) {
                result.completeExceptionally(endException());
                return;
            }
            try {
                result.complete(new ObserverHandleImpl<>(this, dispatcher.register(observer), observer));
            } catch (ObserverFailureException e) {
                // subscription() 或 onStart 抛出异常：这个 observer 以 FAILED 关闭，observe 抛出 ObserverFailureException
                result.completeExceptionally(e);
            } catch (Throwable t) {
                // 其它异常结束 bot（由调度循环的处理入口完成），observe 抛出 BotFailureException
                result.completeExceptionally(new BotFailureException(t));
                throw t;
            }
        });
        return Waits.join(result);
    }

    /**
     * 在调度线程上立即注册；{@code subscription()} 与 {@code onStart} 抛出异常时调用方收到 {@link ObserverFailureException}，
     * 其它异常结束 bot，调用方收到 {@link BotFailureException}
     */
    private ObserverEntry registerNow(Observer observer) {
        if (ended()) {
            throw endException();
        }
        try {
            return dispatcher.register(observer);
        } catch (ObserverFailureException e) {
            throw e;
        } catch (Throwable t) {
            fail(t);
            throw new BotFailureException(t);
        }
    }

    @Override
    public <O extends Observer> ObservedAction<O> observeDuring(O observer, Function<Bot, Action> submit) {
        ensureUsable();
        if (onSchedulerThread()) {
            return registerDuring(observer, submit);
        }
        var result = new CompletableFuture<ObservedAction<O>>();
        submit(observerTasks, () -> {
            if (ended()) {
                result.completeExceptionally(endException());
                return;
            }
            try {
                result.complete(registerDuring(observer, submit));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return Waits.join(result);
    }

    /**
     * 在调度线程上的同一个任务中依次完成：注册 observer、调用 submit 提交一个动作、把 observer 绑定到这个动作上
     */
    private <O extends Observer> ObservedAction<O> registerDuring(O observer, Function<Bot, Action> submit) {
        var entry = registerNow(observer);
        var handle = new ObserverHandleImpl<>(this, entry, observer);
        var before = actionSequence.get();
        var outer = completionDuringSubmit;
        completionDuringSubmit = Math.min(outer, before);
        Action action;
        try {
            action = submit.apply(this);
        } catch (Throwable t) {
            fail(t);
            throw new BotFailureException(t);
        } finally {
            completionDuringSubmit = outer;
        }
        if (!(action instanceof ActionImpl impl) || impl.bot() != this || impl.sequence() <= before) {
            handle.close();
            throw new IllegalArgumentException("observeDuring submit must return an action submitted by this bot during the call, got " + action);
        }
        dispatcher.bind(entry, action);
        return new ObservedActionImpl<>(action, handle);
    }

    @Override
    public ObservedAction<BlockBrokenObserver> breakBlock(Point pos) {
        return observeDuring(Observers.blockBroken(pos), bot -> bot.attemptBreakBlock(pos));
    }

    @Override
    public ObservedAction<BlockBrokenObserver> breakBlock(Point pos, Direction face) {
        return observeDuring(Observers.blockBroken(pos), bot -> bot.attemptBreakBlock(pos, face));
    }

    @Override
    public void await(Predicate<StateReader> condition, Duration timeout) {
        checkNotSchedulerThread("Bot.await");
        var handle = observe(Observers.condition(condition));
        try {
            handle.awaitClose(timeout);
        } catch (BotTimeoutException e) {
            handle.close();
            throw e;
        }
        if (!handle.observer().met()) {
            throw endException();
        }
    }

    private static final class Step {
        private final ActionImpl action;
        private int remaining;

        private Step(ActionImpl action, int remaining) {
            this.action = action;
            this.remaining = remaining;
        }
    }

    /**
     * bot 在世界中（Login (play) 处理完成之后、离开世界之前）时已经发生过；不在任何世界中时等待下一次 Login (play)
     */
    @Override
    public Action joined() {
        return waitFor("joined", () -> state.worldOrNull() != null, joinedWaiters);
    }

    /**
     * 当前世界已经加载（自上次 Login (play) 或 Respawn 以来发送过 Player Loaded）时已经发生过；
     * 否则等待下一次加载，包括离开世界之后下一个世界的加载
     */
    @Override
    public Action loaded() {
        return waitFor("loaded", () -> state.loadTracker().loaded(), loadedWaiters);
    }

    @Override
    public Action moveTo(Point target) {
        return moveTo(target, MoveOptions.DEFAULT);
    }

    @Override
    public Action moveTo(Point target, MoveOptions options) {
        return movementAction("moveTo " + target, action -> new MoveToAction(action, target, options));
    }

    @Override
    public Action moveForward(double blocks) {
        return moveForward(blocks, MoveOptions.DEFAULT);
    }

    @Override
    public Action moveForward(double blocks, MoveOptions options) {
        var direction = options.direction();
        if (direction != null && direction.x() == 0 && direction.z() == 0) {
            throw new IllegalArgumentException("moveForward direction has no horizontal component: " + direction);
        }
        return movementAction("moveForward " + blocks, action -> new MoveForwardAction(action, blocks, options));
    }

    @Override
    public Action jumpTo(Point target) {
        return jumpTo(target, MoveOptions.DEFAULT);
    }

    @Override
    public Action jumpTo(Point target, MoveOptions options) {
        return movementAction("jumpTo " + target, action -> new JumpToAction(action, target, options));
    }

    @Override
    public Action rotate(float yaw, float pitch) {
        // NaN 与任何数比较都为 false，所以按「在范围之内」判断
        if (!(pitch >= -90 && pitch <= 90)) {
            throw new IllegalArgumentException("pitch must be within [-90, 90], got " + pitch);
        }
        var rotation = new Rotation(yaw, pitch);
        return movementAction("rotate " + yaw + ", " + pitch, action -> new RotateAction(action, _ -> rotation));
    }

    @Override
    public Action lookAt(Point target) {
        return movementAction("lookAt " + target, action -> new RotateAction(action,
                controller -> Rotation.lookAt(controller.player().eyePosition(), target)));
    }

    @Override
    public Action lookAt(int entityId) {
        return movementAction("lookAt entity " + entityId, action -> new RotateAction(action, controller -> {
            var entity = controller.world().entities().get(entityId);
            return entity == null ? null : Rotation.lookAt(controller.player().eyePosition(), entity.snapshot().center());
        }));
    }

    @Override
    public Action attemptBreakBlock(Point pos) {
        return handAction("attemptBreakBlock " + pos, action -> new DigTask(action, pos, null));
    }

    @Override
    public Action attemptBreakBlock(Point pos, Direction face) {
        return handAction("attemptBreakBlock " + pos + " " + face, action -> new DigTask(action, pos, face));
    }

    @Override
    public Action placeBlock(Point against, Direction face, Hand hand) {
        var cursor = UseItemOnTask.faceCenter(face);
        return handAction("placeBlock " + against + " " + face, action -> new UseItemOnTask(action, against, face, hand, cursor));
    }

    @Override
    public Action useItemOn(Point pos, Direction face, Hand hand, Point cursor) {
        return handAction("useItemOn " + pos + " " + face, action -> new UseItemOnTask(action, pos, face, hand, cursor));
    }

    @Override
    public Action useItem(Hand hand) {
        return handAction("useItem " + hand, action -> new UseItemTask(action, hand, 0));
    }

    @Override
    public Action useItem(Hand hand, int holdTicks) {
        if (holdTicks <= 0) {
            throw new IllegalArgumentException("holdTicks must be positive, got " + holdTicks);
        }
        return handAction("useItem " + hand + " for " + holdTicks + " ticks", action -> new UseItemTask(action, hand, holdTicks));
    }

    @Override
    public Action attack(int entityId) {
        return handAction("attack " + entityId, action -> new AttackTask(action, entityId));
    }

    @Override
    public Action interact(int entityId, Hand hand) {
        return handAction("interact " + entityId, action -> new InteractTask(action, entityId, hand));
    }

    @Override
    public Action chat(String message) {
        if (message.length() > MAX_CHAT_LENGTH) {
            throw new IllegalArgumentException("chat message exceeds " + MAX_CHAT_LENGTH + " characters");
        }
        return oneShot("chat", _ -> {
            var update = state.chat().lastSeen().update();
            state.outbox().send(new ServerboundChatPacket(message, System.currentTimeMillis(), random.nextLong(), null,
                    update.messageCount(), update.acknowledged(), update.checksum()));
        });
    }

    @Override
    public Action command(String command) {
        if (command.length() > MAX_COMMAND_LENGTH) {
            throw new IllegalArgumentException("command exceeds " + MAX_COMMAND_LENGTH + " characters");
        }
        return oneShot("command", _ -> state.outbox().send(new ServerboundChatCommandPacket(command)));
    }

    @Override
    public Action selectHotbar(int slot) {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("hotbar slot must be within [0, 8], got " + slot);
        }
        return oneShot("selectHotbar " + slot, _ -> {
            state.outbox().send(new ServerboundSetCarriedItemPacket(slot));
            hand.carriedItemSent(slot);
            var player = state.player();
            var old = player.heldSlot();
            player.heldSlot(slot);
            if (old != slot && dispatcher.wants(HeldSlotChanged.class)) {
                dispatcher.emit(new HeldSlotChanged(old, slot));
            }
        });
    }

    @Override
    public Action swapHands() {
        return oneShot("swapHands", _ -> state.outbox().send(new ServerboundPlayerActionPacket(
                PlayerAction.SWAP_HANDS, Vector3i.ZERO, Direction.DOWN, 0)));
    }

    @Override
    public Action drop(boolean wholeStack) {
        return oneShot("drop", _ -> state.outbox().send(new ServerboundPlayerActionPacket(
                wholeStack ? PlayerAction.DROP_ITEM_STACK : PlayerAction.DROP_ITEM, Vector3i.ZERO, Direction.DOWN, 0)));
    }

    @Override
    public Action respawn() {
        return oneShot("respawn", _ -> state.outbox().send(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN)));
    }

    @Override
    public InventoryActions inventory() {
        return new InventoryActionsImpl(this);
    }

    @Override
    public Action send(MinecraftPacket packet) {
        PacketDirection.requireServerbound(packet.getClass());
        return oneShot("send " + packet.getClass().getSimpleName(), _ -> state.outbox().send(packet));
    }

    @Override
    public void suspendMovement() {
        suspendRequested = true;
        scheduler.wakeup();
    }

    @Override
    public void resumeMovement() {
        suspendRequested = false;
        scheduler.wakeup();
    }

    @Override
    public void disconnect() {
        execute(() -> disconnected(DISCONNECTED_BY_CLIENT));
    }

    void observerTask(Runnable task) {
        if (onSchedulerThread()) {
            task.run();
        } else {
            submit(observerTasks, task);
        }
    }

    ObservationDispatcher dispatcher() {
        return dispatcher;
    }

    @Override
    public String toString() {
        return "Bot[" + options.username() + "]";
    }

    /**
     * 一条连接的入站入口：做解码上下文的处理，然后放进 bot 的入站队列。调用不会并发，按到达顺序：
     * TCP 连接在 Netty 事件循环上调用；其它连接实现必须串行调用（{@link InboundSink}）
     */
    private final class Sink implements InboundSink {
        private final int target;
        private final DecodeContext decode = new DecodeContext(SectionPool.SHARED);

        private Sink(int target) {
            this.target = target;
        }

        @Override
        public void accept(MinecraftPacket packet) {
            inbound.add(new Received(target, new InboundPacket(packet, decode.process(packet))));
            scheduler.wakeup();
        }

        @Override
        public void closed(@Nullable Throwable error) {
            inbound.add(new Closed(target, error));
            scheduler.wakeup();
        }
    }

    private record Received(int target, InboundPacket packet) {
    }

    private record Closed(int target, @Nullable Throwable error) {
    }

    private record OneShot(ActionImpl action, Consumer<ActionImpl> body) {
    }
}
