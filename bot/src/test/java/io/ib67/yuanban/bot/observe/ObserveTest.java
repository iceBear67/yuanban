package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDelimiterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserveTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;

    private static void withBot(BiConsumer<TestPeer, Bot> test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "observer", new Pos(0.5, 0, 0.5));
            test.accept(peer, bot);
        }
    }

    /**
     * 记录收到的观察记录与回调顺序
     */
    private static class Recorder implements Observer {
        private final Subscription subscription;
        final List<String> events = new ArrayList<>();
        @Nullable CloseReason closed;

        Recorder(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public Subscription subscription() {
            return subscription;
        }

        @Override
        public void onStart(ObserverContext context) {
            events.add("start");
        }

        @Override
        public void onObservation(Observation observation, ObserverContext context) {
            events.add(describe(observation));
        }

        @Override
        public void onClose(CloseReason reason, ObserverContext context) {
            closed = reason;
            events.add("close " + reason);
        }
    }

    private static ClientboundSystemChatPacket chat(String text) {
        return new ClientboundSystemChatPacket(Component.text(text), false);
    }

    private static ClientboundBlockUpdatePacket blockUpdate(int x, int y, int z, Block block) {
        return new ClientboundBlockUpdatePacket(new BlockChangeEntry(Vector3i.from(x, y, z), block.stateId()));
    }

    /**
     * Bundle Delimiter 没有内容，用空缓冲区解码得到
     */
    private static ClientboundDelimiterPacket delimiter() {
        return new ClientboundDelimiterPacket(Unpooled.EMPTY_BUFFER);
    }

    private static String describe(Observation observation) {
        return switch (observation) {
            case PacketReceived(var packet) -> "received " + packet.getClass().getSimpleName();
            case BlockChanged changed -> "block " + changed.pos().blockX() + " " + changed.now().name();
            case ChatReceived chat -> "chat " + chat.line().plainText();
            case ActionCompleted completed -> "completed " + (completed.error() == null);
            default -> observation.getClass().getSimpleName();
        };
    }

    @Test
    void onlyObservationsAfterRegistrationAreDelivered() {
        withBot((peer, bot) -> {
            peer.push(chat("before"));
            var handle = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            peer.push(chat("after"));
            peer.barrier();
            handle.close();
            assertEquals(List.of("start", "chat after", "close BY_HANDLE"), handle.observer().events);
        });
    }

    @Test
    void modelRecordsComeBeforeTheirPacketAndBundlesAreAppliedFirst() {
        withBot((peer, bot) -> {
            var seenInCallback = new ArrayList<Block>();
            var handle = bot.observe(new Recorder(Subscription.of(BlockChanged.class).received(ClientboundBlockUpdatePacket.class)) {
                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    super.onObservation(observation, context);
                    if (observation instanceof BlockChanged) {
                        // 回调中读到的模型包含整个 bundle 的结果
                        seenInCallback.add(context.state().block(2, -1, 0));
                    }
                }
            });
            peer.push(delimiter(),
                    blockUpdate(1, -1, 0, Block.GOLD_BLOCK),
                    blockUpdate(2, -1, 0, Block.DIAMOND_BLOCK),
                    delimiter());
            peer.barrier();
            handle.close();
            assertEquals(List.of("start",
                    "block 1 minecraft:gold_block", "received ClientboundBlockUpdatePacket",
                    "block 2 minecraft:diamond_block", "received ClientboundBlockUpdatePacket",
                    "close BY_HANDLE"), handle.observer().events);
            assertEquals(List.of(Block.DIAMOND_BLOCK, Block.DIAMOND_BLOCK), seenInCallback);
        });
    }

    @Test
    void observersRegisteredInACallbackStartAfterItReturns() {
        withBot((_, bot) -> {
            var inCallback = bot.moveForward(1000);
            var later = bot.moveForward(1000);
            var recorder = new Recorder(Subscription.of(ActionCompleted.class));
            var registered = new CompletableFuture<ObserverHandle<Recorder>>();
            bot.observe(Observers.listener(BotTicked.class, _ -> {
                if (!registered.isDone()) {
                    var handle = bot.observe(recorder);
                    // 在调度线程上取消立即处理：同一个回调中、注册之后产生的 ActionCompleted 不投递给它
                    inCallback.cancel();
                    registered.complete(handle);
                }
            }));
            var handle = assertTimeoutPreemptively(TIMEOUT, () -> registered.get());
            assertFalse(inCallback.await(TIMEOUT));
            // 回调返回之后产生的记录照常投递
            later.cancel();
            assertFalse(later.await(TIMEOUT));
            handle.close();
            assertEquals(List.of("start", "completed false", "close BY_HANDLE"), recorder.events);
        });
    }

    @Test
    void onlySubscribedTypesAreDelivered() {
        withBot((peer, bot) -> {
            var handle = bot.observe(new Recorder(Subscription.NONE.received(ClientboundSystemChatPacket.class)));
            peer.push(blockUpdate(1, -1, 0, Block.GOLD_BLOCK));
            peer.push(chat("x"));
            peer.barrier();
            handle.close();
            assertEquals(List.of("start", "received ClientboundSystemChatPacket", "close BY_HANDLE"), handle.observer().events);
        });
    }

    @Test
    void subscriptionRejectsPacketObservationTypes() {
        assertThrows(IllegalArgumentException.class, () -> Subscription.of(PacketReceived.class));
        assertThrows(IllegalArgumentException.class, () -> Subscription.of(PacketSent.class));
    }

    @Test
    void subscriptionRejectsPacketClassesOfTheWrongDirection() {
        // 入站只接受服务端发往客户端的数据包类，出站只接受客户端发往服务端的数据包类
        assertThrows(IllegalArgumentException.class, () -> Subscription.NONE.received(ServerboundKeepAlivePacket.class));
        assertThrows(IllegalArgumentException.class, () -> Subscription.NONE.sent(ClientboundKeepAlivePacket.class));
        assertThrows(IllegalArgumentException.class,
                () -> new Subscription(Set.of(), Set.of(ServerboundChatPacket.class), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Subscription(Set.of(), Set.of(), Set.of(ClientboundSystemChatPacket.class)));
        // MinecraftPacket.class 表示这一方向的全部数据包，两个方向都接受
        var all = Subscription.NONE.received(MinecraftPacket.class).sent(MinecraftPacket.class);
        assertEquals(Set.of(MinecraftPacket.class), all.received());
        assertEquals(Set.of(MinecraftPacket.class), all.sent());
    }

    @Test
    void closeReasons() {
        withBot((peer, bot) -> {
            // BY_OBSERVER：回调返回之后关闭
            var self = bot.observe(new Recorder(Subscription.of(ChatReceived.class)) {
                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    super.onObservation(observation, context);
                    context.close();
                    events.add("after close request");
                }
            });
            peer.push(chat("a"), chat("b"));
            assertEquals(CloseReason.BY_OBSERVER, self.awaitClose(TIMEOUT));
            assertEquals(List.of("start", "chat a", "after close request", "close BY_OBSERVER"), self.observer().events);

            // BY_HANDLE：返回时 onClose 已经执行
            var byHandle = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            byHandle.close();
            assertEquals(CloseReason.BY_HANDLE, byHandle.observer().closed);
            assertFalse(byHandle.isOpen());

            // DISCONNECTED：连接结束时全部关闭
            var disconnected = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            peer.close(null);
            assertEquals(CloseReason.DISCONNECTED, disconnected.awaitClose(TIMEOUT));
            assertEquals(CloseReason.DISCONNECTED, disconnected.observer().closed);
            assertThrows(BotDisconnectedException.class,
                    () -> bot.observe(new Recorder(Subscription.of(ChatReceived.class))));
        });
    }

    @Test
    void failingCallbackEndsTheBotWithoutOnClose() {
        withBot((peer, bot) -> {
            var other = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            var failing = bot.observe(new Recorder(Subscription.of(ChatReceived.class)) {
                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    throw new IllegalStateException("broken observer");
                }
            });
            peer.push(chat("boom"));
            assertEquals(CloseReason.FAILED, failing.awaitClose(TIMEOUT));
            assertNull(failing.observer().closed);
            assertEquals(CloseReason.DISCONNECTED, other.awaitClose(TIMEOUT));
            assertEquals("broken observer", bot.failure().getMessage());
            assertThrows(BotFailureException.class, () -> bot.observe(new Recorder(Subscription.of(ChatReceived.class))));
        });
    }

    private static Recorder brokenOnClose() {
        return new Recorder(Subscription.of(ChatReceived.class)) {
            @Override
            public void onClose(CloseReason reason, ObserverContext context) {
                throw new IllegalStateException("broken onClose");
            }
        };
    }

    @Test
    void aThrowingOnCloseFailsHandleCloseOnOtherThreads() {
        withBot((_, bot) -> {
            var handle = bot.observe(brokenOnClose());
            // 在其它线程（测试线程）调用：onClose 抛出的异常结束 bot，close() 与在调度线程上调用时一样抛出 BotFailureException
            var thrown = assertThrows(BotFailureException.class, handle::close);
            assertEquals("broken onClose", thrown.getCause().getMessage());
            assertEquals(CloseReason.FAILED, handle.closeReason());
            assertEquals("broken onClose", bot.failure().getMessage());
        });
    }

    @Test
    void aThrowingOnCloseWhenTheConnectionEndsFailsTheBot() {
        withBot((peer, bot) -> {
            var moving = bot.moveForward(1000);
            bot.observe(brokenOnClose());
            var other = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            peer.push(new ClientboundDisconnectPacket(Component.text("bye")));
            // 其余 observer 照常关闭；onClose 的异常记录为失败原因，未结束的动作与之后的调用都得到 BotFailureException
            assertEquals(CloseReason.DISCONNECTED, other.awaitClose(TIMEOUT));
            var failure = assertThrows(BotFailureException.class, () -> moving.await(TIMEOUT));
            assertEquals("broken onClose", failure.getCause().getMessage());
            assertEquals("broken onClose", assertThrows(BotFailureException.class, () -> bot.rotate(0, 0)).getCause().getMessage());
            assertEquals("broken onClose", bot.failure().getMessage());
            // 断开原因仍然保留
            assertEquals(Component.text("bye"), bot.disconnectReason());
            assertEquals(BotStatus.DISCONNECTED, bot.status());
        });
    }

    @Test
    void aThrowingSubscriptionEndsTheBotAndObserveThrows() {
        withBot((_, bot) -> {
            var broken = new Recorder(Subscription.NONE) {
                @Override
                public Subscription subscription() {
                    throw new IllegalStateException("broken subscription");
                }
            };
            // 在其它线程调用：注册失败时 observe 抛出 BotFailureException，不会一直等待
            var thrown = assertTimeoutPreemptively(TIMEOUT, () -> assertThrows(BotFailureException.class, () -> bot.observe(broken)));
            assertEquals("broken subscription", thrown.getCause().getMessage());
            assertEquals("broken subscription", bot.failure().getMessage());
        });
    }

    private static Recorder brokenSubscription() {
        return new Recorder(Subscription.NONE) {
            @Override
            public Subscription subscription() {
                throw new IllegalStateException("broken subscription");
            }
        };
    }

    @Test
    void aThrowingSubscriptionEndsTheBotOnTheSchedulerThreadAndInObserveDuring() {
        // 在调度线程上（另一个 observer 的回调中）注册：observe 抛出 BotFailureException，bot 结束
        withBot((_, bot) -> {
            var thrown = new CompletableFuture<Throwable>();
            bot.observe(Observers.listener(BotTicked.class, _ -> {
                if (!thrown.isDone()) {
                    try {
                        bot.observe(brokenSubscription());
                        thrown.complete(null);
                    } catch (Throwable t) {
                        thrown.complete(t);
                    }
                }
            }));
            var failure = assertInstanceOf(BotFailureException.class, assertTimeoutPreemptively(TIMEOUT, () -> thrown.get()));
            assertEquals("broken subscription", failure.getCause().getMessage());
            assertEquals("broken subscription", bot.failure().getMessage());
        });
        // observeDuring：注册失败时同样结束 bot，不提交动作
        withBot((_, bot) -> {
            var thrown = assertThrows(BotFailureException.class, () -> bot.observeDuring(brokenSubscription(), b -> b.rotate(0, 0)));
            assertEquals("broken subscription", thrown.getCause().getMessage());
            assertEquals("broken subscription", bot.failure().getMessage());
        });
    }

    @Test
    void observeDuringLoadedAfterLoadingClosesWithTheAction() {
        withBot((_, bot) -> {
            // loaded() 已经发生过：在调度线程上调用时推迟到下一轮执行完毕，observer 在这之前已经绑定到动作上
            var observed = bot.observeDuring(new Recorder(Subscription.of(ActionCompleted.class)), Bot::loaded);
            var recorder = observed.await(TIMEOUT);
            assertEquals(CloseReason.ACTION_COMPLETED, observed.handle().closeReason());
            assertEquals(List.of("start", "completed true", "close ACTION_COMPLETED"), recorder.events);
            assertTrue(observed.action().await(TIMEOUT));
        });
    }

    private static Action cancelledRotation(Bot bot) {
        var action = bot.rotate(0, 0);
        // submit 在调度线程上执行，取消立即处理：submit 返回时动作已经结束
        action.cancel();
        assertTrue(action.future().isDone());
        return action;
    }

    @Test
    void observeDuringClosesWhenTheActionEndsInsideSubmit() {
        withBot((_, bot) -> {
            // 没有任何 observer 订阅 ActionCompleted：submit 中结束的动作仍然产生它，observer 在绑定之后以 ACTION_COMPLETED 关闭
            var unsubscribed = bot.observeDuring(new Recorder(Subscription.of(ChatReceived.class)), ObserveTest::cancelledRotation);
            assertEquals(List.of("start", "close ACTION_COMPLETED"), unsubscribed.await(TIMEOUT).events);
            assertEquals(CloseReason.ACTION_COMPLETED, unsubscribed.handle().closeReason());
            assertFalse(unsubscribed.action().await(TIMEOUT));

            var subscribed = bot.observeDuring(new Recorder(Subscription.of(ActionCompleted.class)), ObserveTest::cancelledRotation);
            assertEquals(List.of("start", "completed false", "close ACTION_COMPLETED"), subscribed.await(TIMEOUT).events);
        });
    }

    @Test
    void anObserverCanOnlyBeRegisteredOnce() {
        withBot((_, bot) -> {
            var recorder = new Recorder(Subscription.of(ChatReceived.class));
            bot.observe(recorder).close();
            assertThrows(IllegalStateException.class, () -> bot.observe(recorder));
        });
    }

    @Test
    void observeOnAnEndedBotDoesNotRegisterTheObserver() throws Exception {
        try (var runtime = BotRuntime.create()) {
            var peerA = new TestPeer();
            var peerB = new TestPeer();
            var peerC = new TestPeer();
            var ended = TestServer.joinedBot(runtime, peerA, "ended", new Pos(0.5, 0, 0.5));
            var racing = TestServer.joinedBot(runtime, peerB, "racing", new Pos(0.5, 0, 0.5));
            var alive = TestServer.joinedBot(runtime, peerC, "alive", new Pos(0.5, 0, 0.5));

            // bot 已经断开：observe 抛出异常，observer 不算注册过
            var watch = ended.observe(new Recorder(Subscription.NONE));
            peerA.close(null);
            assertEquals(CloseReason.DISCONNECTED, watch.awaitClose(TIMEOUT));
            var first = new Recorder(Subscription.of(ChatReceived.class));
            assertThrows(BotDisconnectedException.class, () -> ended.observe(first));

            // 注册请求交给调度线程之后、处理之前 bot 才断开：同样不算注册过
            var second = new Recorder(Subscription.of(ChatReceived.class));
            var observing = new CompletableFuture<Throwable>();
            var started = new AtomicBoolean();
            racing.observe(Observers.listener(BotTicked.class, _ -> {
                if (!started.compareAndSet(false, true)) {
                    return;
                }
                var thread = Thread.ofPlatform().start(() -> {
                    try {
                        racing.observe(second);
                        observing.complete(null);
                    } catch (Throwable t) {
                        observing.complete(t);
                    }
                });
                // 等它把注册请求交给调度线程、开始等待结果，然后在调度线程上断开
                while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TERMINATED) {
                    Thread.onSpinWait();
                }
                racing.disconnect();
            }));
            assertInstanceOf(BotDisconnectedException.class, observing.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));

            alive.observe(first).close();
            alive.observe(second).close();
            assertEquals(CloseReason.BY_HANDLE, first.closed);
            assertEquals(CloseReason.BY_HANDLE, second.closed);
        }
    }

    @Test
    void builtinStateCannotBeReadFromOtherThreadsWhileObserving() {
        withBot((peer, bot) -> {
            var handle = bot.observe(Observers.chatMessages());
            peer.push(chat("one"));
            peer.barrier();
            assertThrows(IllegalStateException.class, () -> handle.observer().lines());
            int count = handle.read(observer -> observer.lines().size());
            assertEquals(1, count);
            handle.close();
            assertEquals("one", handle.observer().lines().getFirst().plainText());
        });
    }

    @Test
    void observeDuringClosesAfterTheActionCompleted() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "during", _ -> {
            });
            TestServer.join(peer, new Pos(0.5, 0, 0.5), GameMode.CREATIVE, (_, _, _) -> {
            });
            var recorder = new Recorder(Subscription.of(BlockChanged.class, ActionCompleted.class)
                    .received(ClientboundBlockChangedAckPacket.class));
            var mark = peer.mark();
            var observed = bot.observeDuring(recorder, b -> b.attemptBreakBlock(new Vec(1, -1, 0)));
            peer.awaitSent(mark, ServerboundPlayerActionPacket.class, TIMEOUT);
            // 确认与方块更新在同一个 bundle 中、排在确认之前：观察范围包含方块更新，不包含确认之后的数据包
            peer.push(delimiter(),
                    blockUpdate(1, -1, 0, Block.AIR),
                    new ClientboundBlockChangedAckPacket(1),
                    blockUpdate(5, -1, 0, Block.AIR),
                    delimiter());
            var result = observed.await(TIMEOUT);
            assertEquals(CloseReason.ACTION_COMPLETED, observed.handle().closeReason());
            assertEquals(List.of("start", "block 1 minecraft:air", "completed true", "close ACTION_COMPLETED"), result.events);
            assertEquals(Block.AIR, bot.view().block(5, -1, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> bot.observeDuring(new Recorder(Subscription.of(ChatReceived.class)), _ -> null));
            var outside = bot.rotate(0, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> bot.observeDuring(new Recorder(Subscription.of(ChatReceived.class)), _ -> outside));
        }
    }

    @Test
    void worldBoundObserverSwitchesWorlds() {
        withBot((peer, bot) -> {
            var events = new ArrayList<String>();
            var observer = new WorldBoundObserver<List<String>>() {
                @Override
                protected Subscription worldSubscription() {
                    return Subscription.of(ChatReceived.class);
                }

                @Override
                protected List<String> openWorld(WorldInfo world, ObserverContext context) {
                    events.add("open " + world.epoch());
                    return new ArrayList<>();
                }

                @Override
                protected void observe(List<String> world, Observation observation, ObserverContext context) {
                    world.add(((ChatReceived) observation).line().plainText());
                }

                @Override
                protected void closeWorld(List<String> world, WorldClosed closed, ObserverContext context) {
                    events.add("close " + world + " " + closed);
                }
            };
            var handle = bot.observe(observer);
            peer.push(chat("first"));
            peer.push(new ClientboundRespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).getCommonPlayerSpawnInfo(), false, false));
            peer.push(chat("second"));
            peer.barrier();
            handle.close();
            var worlds = observer.worlds();
            assertEquals(2, worlds.size());
            assertEquals(List.of("first"), worlds.get(0).state());
            assertEquals(List.of("second"), worlds.get(1).state());
            assertTrue(worlds.get(0).closed() instanceof WorldClosed.Left left && left.next().epoch() == 2);
            assertEquals(new WorldClosed.ObserverClosed(CloseReason.BY_HANDLE), worlds.get(1).closed());
            assertEquals(List.of("open 1", "close [first] Left[next=" + worlds.get(1).world() + "]", "open 2",
                    "close [second] ObserverClosed[reason=BY_HANDLE]"), events);
        });
    }

    @Test
    void worldBoundObserverDropsRecordsBeforeTheFirstWorld() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "early", _ -> {
            });
            var observer = new WorldBoundObserver<List<MinecraftPacket>>() {
                @Override
                protected Subscription worldSubscription() {
                    return Subscription.NONE.received(ClientboundKeepAlivePacket.class);
                }

                @Override
                protected List<MinecraftPacket> openWorld(WorldInfo world, ObserverContext context) {
                    return new ArrayList<>();
                }

                @Override
                protected void observe(List<MinecraftPacket> world, Observation observation, ObserverContext context) {
                    world.add(((PacketReceived) observation).packet());
                }
            };
            var handle = bot.observe(observer);
            // 配置阶段的 Keep Alive 发生在进入第一个世界之前，被丢弃
            peer.barrier();
            assertNull(handle.read(WorldBoundObserver::currentWorld));
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.barrier();
            handle.close();
            assertEquals(1, observer.worlds().size());
            assertEquals(1, observer.currentWorld().size());
        }
    }
}
