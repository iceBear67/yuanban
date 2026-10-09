package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.BotTimeoutException;
import io.ib67.yuanban.bot.RuntimeOptions;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.ActionCompleted;
import io.ib67.yuanban.bot.observe.Observation.BotTicked;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.transport.Transport;
import io.ib67.yuanban.bot.view.StateReader;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundTransferPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;

    @Test
    void oneShotRequestsWaitForPlayerLoaded() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "loader", _ -> {
            });
            var chat = bot.chat("hello");
            peer.barrier();
            assertTrue(peer.sentSince(0, ServerboundChatPacket.class).isEmpty());
            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            assertTrue(chat.await(TIMEOUT));
            var sent = peer.sent();
            var loaded = indexOf(sent, ServerboundPlayerLoadedPacket.class);
            var message = indexOf(sent, ServerboundChatPacket.class);
            assertTrue(message > loaded);
            assertEquals("hello", ((ServerboundChatPacket) sent.get(message)).getMessage());
        }
    }

    @Test
    void withoutLevelChunksLoadStartTheBotNeverLoads() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "unloaded", _ -> {
            });
            var spawn = new Pos(0.5, 0, 0.5);
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.push(new ClientboundPlayerPositionPacket(1, spawn.x(), spawn.y(), spawn.z(), 0, 0, 0, 0, 0));
            peer.push(TestServer.flatChunk().packet(0, 0, TestServer.biomeCount()));
            var chat = bot.chat("hello");
            // 原版客户端没有按 tick 计算的超时：收到 Game Event 13 之前一直不发送 Player Loaded，不执行请求
            var mark = peer.mark();
            var deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (peer.sentSince(mark, ServerboundClientTickEndPacket.class).size() < 70) {
                assertTrue(System.nanoTime() < deadline, "timed out waiting for ticks");
                Thread.onSpinWait();
            }
            assertTrue(peer.sentSince(0, ServerboundPlayerLoadedPacket.class).isEmpty());
            assertTrue(peer.sentSince(0, ServerboundChatPacket.class).isEmpty());
            assertFalse(bot.view().loaded());
            assertFalse(chat.future().isDone());

            peer.push(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));
            assertTrue(chat.await(TIMEOUT));
            var sent = peer.sent();
            assertTrue(indexOf(sent, ServerboundChatPacket.class) > indexOf(sent, ServerboundPlayerLoadedPacket.class));
        }
    }

    @Test
    void suspensionChangesUpdateTheStateWithoutTicks() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "paused", _ -> {
            });
            // 配置完成之后、Login (play) 之前不 tick，也没有数据包：暂停状态的改变本身产生 StateUpdated，condition 随之检查
            var suspended = bot.observe(Observers.condition(StateReader::movementSuspended));
            assertTrue(suspended.isOpen());
            bot.suspendMovement();
            assertEquals(CloseReason.BY_OBSERVER, suspended.awaitClose(TIMEOUT));
            assertTrue(suspended.observer().met());
        }
    }

    @Test
    void waitingThreadsShareThePump() throws Exception {
        var active = new AtomicInteger();
        var overlapped = new AtomicBoolean();
        var starts = new CopyOnWriteArrayList<Long>();
        Runnable pump = () -> {
            starts.add(System.nanoTime());
            if (active.incrementAndGet() > 1) {
                overlapped.set(true);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            active.decrementAndGet();
        };
        try (var runtime = BotRuntime.create(RuntimeOptions.builder().waitPump(pump).build())) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "pumped", new Pos(0.5, 0, 0.5));
            starts.clear();
            // 两个线程同时等待一秒：推进函数不会同时执行，整体仍然每 50 毫秒最多一次
            var waiters = new ArrayList<CompletableFuture<Void>>();
            for (int i = 0; i < 2; i++) {
                waiters.add(CompletableFuture.runAsync(() -> assertThrows(BotTimeoutException.class,
                        () -> bot.await(_ -> false, Duration.ofSeconds(1)))));
            }
            for (var waiter : waiters) {
                waiter.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }
            assertFalse(overlapped.get());
            var recorded = List.copyOf(starts);
            assertTrue(recorded.size() <= 22, recorded::toString);
            for (int i = 1; i < recorded.size(); i++) {
                assertTrue(recorded.get(i) - recorded.get(i - 1) >= TimeUnit.MILLISECONDS.toNanos(49), recorded::toString);
            }
        }
    }

    @Test
    void movementAndHandQueuesRunIndependently() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "queues", new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            var first = bot.rotate(10, 0);
            var second = bot.rotate(20, 0);
            var use = bot.useItem(Hand.MAIN_HAND);
            var useItem = peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT);
            assertEquals(1, useItem.getSequence());
            assertTrue(first.await(TIMEOUT));
            assertTrue(second.await(TIMEOUT));
            // 动作在 tick 中执行完毕，这个 tick 的数据包在同一轮的最后才交给连接：屏障的应答在之后的一轮发出，那时它们都已经记录
            peer.barrier();
            // 移动队列先进先出、一次运行一个：两次朝向在不同的 tick 中设置。
            // 朝向改变的 tick 同时到了每 20 tick 发送一次位置的时候，朝向由 Set Player Position and Rotation 发出
            var rotations = new ArrayList<Float>();
            var ticksBetween = 0;
            for (var packet : peer.sentSince(mark)) {
                if (packet instanceof ServerboundMovePlayerRotPacket rotation) {
                    rotations.add(rotation.getYaw());
                } else if (packet instanceof ServerboundMovePlayerPosRotPacket both) {
                    rotations.add(both.getYaw());
                }
                if (packet instanceof ServerboundClientTickEndPacket && rotations.size() == 1) {
                    ticksBetween++;
                }
            }
            assertEquals(List.of(10f, 20f), rotations);
            assertTrue(ticksBetween >= 1);
            // 手部动作在收到确认时执行完毕，不受移动队列影响
            assertFalse(use.future().isDone());
            peer.push(new ClientboundBlockChangedAckPacket(1));
            assertTrue(use.await(TIMEOUT));
        }
    }

    @Test
    void cancellationOutcomes() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "cancels", new Pos(0.5, 0, 0.5));
            var running = bot.moveForward(1000);
            var queued = bot.rotate(45, 0);
            queued.cancel();
            assertFalse(queued.await(TIMEOUT));
            var cause = assertThrows(CompletionException.class, () -> queued.future().join()).getCause();
            assertInstanceOf(ActionCancelledException.class, cause);
            assertEquals(ActionCancelledException.BY_USER, cause.getMessage());
            // 排队中的动作被取消之后，执行中的动作照常运行
            assertFalse(running.future().isDone());
            running.cancel();
            assertFalse(running.await(TIMEOUT));
            // future() 返回副本，对它调用 complete 不影响动作
            var action = bot.rotate(1, 0);
            action.future().complete(null);
            assertTrue(action.await(TIMEOUT));
        }
    }

    @Test
    void cancellingARunningDigSendsCancelledDigging() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "digger", new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(new Vec(1, -1, 0));
            var started = peer.awaitSent(mark, ServerboundPlayerActionPacket.class,
                    packet -> packet.getAction() == PlayerAction.START_DIGGING, TIMEOUT);
            assertEquals(1, started.getSequence());
            dig.cancel();
            assertFalse(dig.await(TIMEOUT));
            var cancelled = peer.awaitSent(mark, ServerboundPlayerActionPacket.class,
                    packet -> packet.getAction() == PlayerAction.CANCEL_DIGGING, TIMEOUT);
            assertEquals(Direction.DOWN, cancelled.getFace());
            assertEquals(0, cancelled.getSequence());
        }
    }

    @Test
    void cancelOnTheSchedulerThreadTakesEffectImmediately() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "immediate", new Pos(0.5, 0, 0.5));
            var events = new CopyOnWriteArrayList<String>();
            var action = bot.moveForward(1000);
            bot.observe(Observers.listener(BotTicked.class, _ -> {
                if (events.isEmpty()) {
                    action.cancel();
                    // 在调度线程上取消：返回时动作已经结束
                    events.add("done " + action.future().isDone());
                }
            }));
            assertFalse(action.await(TIMEOUT));
            peer.barrier();
            assertEquals(List.of("done true"), events);
        }
    }

    @Test
    void worldRebuildCancelsMovementAndHandQueues() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "rebuild", new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            var moving = bot.moveForward(1000);
            var using = bot.useItem(Hand.MAIN_HAND);
            // 请求在调度的第 3 步入队，晚于同一轮中的入站数据包：等两个动作都开始执行之后再重建世界
            peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT);
            peer.awaitSent(mark, ServerboundPlayerInputPacket.class, ServerboundPlayerInputPacket::isForward, TIMEOUT);
            var spawnInfo = TestServer.joinPacket(GameMode.SURVIVAL).getCommonPlayerSpawnInfo();
            peer.push(new ClientboundRespawnPacket(spawnInfo, false, false));
            assertFalse(moving.await(TIMEOUT));
            assertFalse(using.await(TIMEOUT));
            assertEquals(ActionCancelledException.WORLD_REBUILT,
                    assertThrows(CompletionException.class, () -> moving.future().join()).getCause().getMessage());
            assertEquals(io.ib67.yuanban.bot.observe.WorldEntryCause.RESPAWN, bot.view().world().cause());
            assertEquals(2, bot.view().world().epoch());
        }
    }

    @Test
    void playerDeathCancelsMovementAndHandQueues() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "mortal", new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            var moving = bot.moveForward(1000);
            var using = bot.useItem(Hand.MAIN_HAND);
            peer.awaitSent(mark, ServerboundPlayerInputPacket.class, ServerboundPlayerInputPacket::isForward, TIMEOUT);
            peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT);
            peer.push(new ClientboundSetHealthPacket(0, 20, 5));
            assertFalse(moving.await(TIMEOUT));
            assertFalse(using.await(TIMEOUT));
            assertEquals(ActionCancelledException.PLAYER_DIED,
                    assertThrows(CompletionException.class, () -> moving.future().join()).getCause().getMessage());
            assertEquals(ActionCancelledException.PLAYER_DIED,
                    assertThrows(CompletionException.class, () -> using.future().join()).getCause().getMessage());
        }
    }

    @Test
    void requestsBeforeTheFirstWorldWaitForIt() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "early", _ -> {
            });
            // 不在任何世界中：查询本地玩家与方块抛出 IllegalStateException
            assertThrows(IllegalStateException.class, () -> bot.view().player());
            assertThrows(IllegalStateException.class, () -> bot.view().block(0, 0, 0));
            var rotate = bot.rotate(30, 0);
            var use = bot.useItem(Hand.MAIN_HAND);
            peer.barrier();
            assertTrue(peer.sentSince(0, ServerboundUseItemPacket.class).isEmpty());
            // 第一次 Login (play) 不取消它们，加载之后执行
            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            assertTrue(rotate.await(TIMEOUT));
            var useItem = peer.awaitSent(0, ServerboundUseItemPacket.class, TIMEOUT);
            var sent = peer.sent();
            assertTrue(sent.indexOf(useItem) > indexOf(sent, ServerboundPlayerLoadedPacket.class));
            peer.push(new ClientboundBlockChangedAckPacket(useItem.getSequence()));
            assertTrue(use.await(TIMEOUT));
        }
    }

    @Test
    void aTransferConnectionOpenedAfterCloseIsClosed() throws Exception {
        var release = new CountDownLatch(1);
        var second = new TestPeer();
        Transport slow = (username, inbound) -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return second.open(username, inbound);
        };
        var runtime = BotRuntime.create();
        var peer = new TestPeer();
        var bot = TestServer.configuredBot(runtime, peer, "late", options -> options.transportResolver((_, _) -> slow));
        TestServer.join(peer, new Pos(0.5, 0, 0.5));
        peer.push(new ClientboundTransferPacket("other.invalid", 25565));
        // Transfer 关闭旧连接之后，Transfer 线程在打开新连接时阻塞
        var transferred = System.nanoTime() + TIMEOUT.toNanos();
        while (!peer.closedByBot()) {
            assertTrue(System.nanoTime() < transferred, "the transfer did not start");
            Thread.sleep(10);
        }
        runtime.close();
        assertEquals(BotStatus.DISCONNECTED, bot.status());
        // 调度线程已经退出之后 Transfer 线程才打开连接：这条连接被关闭
        release.countDown();
        var deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!second.closedByBot()) {
            assertTrue(System.nanoTime() < deadline, "the late connection was not closed");
            Thread.sleep(10);
        }
    }

    @Test
    void connectingWhileTheRuntimeClosesDisconnectsTheBot() throws Exception {
        var opening = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var peer = new TestPeer();
        Transport slow = (username, inbound) -> {
            opening.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return peer.open(username, inbound);
        };
        var runtime = BotRuntime.create();
        var connecting = new CompletableFuture<Throwable>();
        Thread.ofPlatform().start(() -> {
            try {
                runtime.connect(BotOptions.builder().username("racing").transport(slow).build());
                connecting.complete(null);
            } catch (Throwable t) {
                connecting.complete(t);
            }
        });
        opening.await();
        runtime.close();
        release.countDown();
        assertInstanceOf(IllegalStateException.class, connecting.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        assertTrue(peer.closedByBot());
    }

    @Test
    void queriesSubmittedDuringTheLastRoundComplete() throws Exception {
        var runtime = BotRuntime.create();
        var peerA = new TestPeer();
        var peerB = new TestPeer();
        var a = TestServer.joinedBot(runtime, peerA, "queried", new Pos(0.5, 0, 0.5));
        var b = TestServer.joinedBot(runtime, peerB, "closing", new Pos(0.5, 0, 0.5));
        var result = new CompletableFuture<Long>();
        b.observe(new Observer() {
            @Override
            public Subscription subscription() {
                return Subscription.NONE;
            }

            @Override
            public void onObservation(Observation observation, ObserverContext context) {
            }

            @Override
            public void onClose(CloseReason reason, ObserverContext context) {
                // 在关闭时（调度线程的最后一轮，a 已经处理过）从其它线程查询 a，等它的任务入队之后再返回
                var querying = Thread.ofPlatform().start(() -> {
                    try {
                        result.complete(a.view().ticks());
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
                var deadline = System.nanoTime() + TIMEOUT.toNanos();
                while (querying.getState() != Thread.State.WAITING && querying.getState() != Thread.State.TERMINATED
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
            }
        });
        runtime.close();
        assertTrue(result.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS) > 0);
    }

    @Test
    void blockingCallsOnTheSchedulerThreadThrow() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "blocking", new Pos(0.5, 0, 0.5));
            var thrown = new AtomicReference<Throwable>();
            var action = bot.rotate(5, 5);
            var handle = bot.observe(Observers.listener(ActionCompleted.class, _ -> {
                try {
                    action.await(Duration.ofSeconds(1));
                } catch (Throwable t) {
                    thrown.compareAndSet(null, t);
                }
            }));
            assertTrue(action.await(TIMEOUT));
            peer.barrier();
            assertInstanceOf(IllegalStateException.class, thrown.get());
            handle.close();
            assertEquals(BotStatus.PLAY, bot.status());
        }
    }

    @Test
    void onlyTheProtocolStatusTransitionsAreAllowed() {
        // Login Finished、Finish Configuration、Start Configuration、Transfer、连接结束或 bot 因异常结束
        var allowed = Set.of(
                List.of(BotStatus.LOGIN, BotStatus.CONFIGURATION),
                List.of(BotStatus.CONFIGURATION, BotStatus.PLAY),
                List.of(BotStatus.PLAY, BotStatus.CONFIGURATION),
                List.of(BotStatus.CONFIGURATION, BotStatus.LOGIN),
                List.of(BotStatus.PLAY, BotStatus.LOGIN),
                List.of(BotStatus.LOGIN, BotStatus.DISCONNECTED),
                List.of(BotStatus.CONFIGURATION, BotStatus.DISCONNECTED),
                List.of(BotStatus.PLAY, BotStatus.DISCONNECTED));
        for (var from : BotStatus.values()) {
            for (var to : BotStatus.values()) {
                assertEquals(allowed.contains(List.of(from, to)), BotInstance.allowedTransition(from, to), from + " -> " + to);
            }
        }
    }

    @Test
    void oneFailingBotDoesNotStopOthers() {
        try (var runtime = BotRuntime.create()) {
            var peerA = new TestPeer();
            var peerB = new TestPeer();
            var a = TestServer.joinedBot(runtime, peerA, "failing", new Pos(0.5, 0, 0.5));
            var b = TestServer.joinedBot(runtime, peerB, "healthy", new Pos(0.5, 0, 0.5));
            var pending = a.moveForward(1000);
            var other = a.observe(Observers.chatMessages());
            // 窗口 0 没有槽位 46：原版 AbstractContainerMenu.getSlot 越界抛出异常，bot 以这个异常结束
            peerA.push(new ClientboundContainerSetSlotPacket(0, 1, 46, null));
            var failure = assertThrows(BotFailureException.class, () -> pending.await(TIMEOUT));
            assertInstanceOf(IndexOutOfBoundsException.class, failure.getCause());
            assertEquals(BotStatus.DISCONNECTED, a.status());
            assertEquals(io.ib67.yuanban.bot.observe.CloseReason.DISCONNECTED, other.closeReason());
            assertThrows(BotFailureException.class, () -> a.rotate(0, 0));
            assertThrows(BotFailureException.class, () -> a.view().player());
            peerB.barrier();
            assertTrue(b.rotate(3, 3).await(TIMEOUT));
            assertNotNull(b.view().player());
        }
    }

    @Test
    void modelEntryPointsRejectOtherThreads() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = (BotInstance) TestServer.joinedBot(runtime, peer, "confined", new Pos(0.5, 0, 0.5));
            assertThrows(IllegalStateException.class, () -> bot.movement().cancelAll("test"));
            assertThrows(IllegalStateException.class, () -> bot.hand().tick());
            // 在调度线程上执行的查询可以访问内部组件
            assertEquals(0, (int) bot.inspect(_ -> {
                bot.hand().acknowledge(0);
                return 0;
            }));
        }
    }

    @Test
    void connectRequiresAnOpenRuntime() {
        var runtime = BotRuntime.create();
        runtime.close();
        assertThrows(IllegalStateException.class, () -> runtime.connect(BotOptions.builder()
                .username("late").transport(new TestPeer()).build()));
    }

    private static int indexOf(List<?> packets, Class<?> type) {
        for (int i = 0; i < packets.size(); i++) {
            if (type.isInstance(packets.get(i))) {
                return i;
            }
        }
        throw new AssertionError(type.getSimpleName() + " not sent");
    }
}
