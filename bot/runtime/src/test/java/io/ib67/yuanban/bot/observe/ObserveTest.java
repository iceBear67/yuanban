package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.ObserverFailureException;
import io.ib67.yuanban.bot.testing.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.TestViews;
import io.ib67.yuanban.bot.observe.Observation.ActionCompleted;
import io.ib67.yuanban.bot.observe.Observation.BlockChanged;
import io.ib67.yuanban.bot.observe.Observation.BotTicked;
import io.ib67.yuanban.bot.observe.Observation.ChatReceived;
import io.ib67.yuanban.bot.observe.Observation.PacketReceived;
import io.ib67.yuanban.bot.observe.Observation.PacketSent;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.observe.WorldBoundObserver.WorldClosed;
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
import java.util.concurrent.atomic.AtomicInteger;
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
            // 等 before 应用之后再注册：一轮中先处理注册，后处理入站数据包，交出与应用之间注册的 observer 会收到它
            peer.barrier();
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
    void failingCallbackClosesOnlyThatObserverWithoutOnClose() {
        withBot((peer, bot) -> {
            var failing = bot.observe(new Recorder(Subscription.of(ChatReceived.class)) {
                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    throw new IllegalStateException("broken observer");
                }
            });
            var other = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            peer.push(chat("boom"));
            // 只有抛出异常的 observer 以 FAILED 关闭，不调用它的 onClose；等待它的一方得到 ObserverFailureException
            var thrown = assertThrows(ObserverFailureException.class, () -> failing.awaitClose(TIMEOUT));
            assertEquals("broken observer", thrown.getCause().getMessage());
            assertEquals(CloseReason.FAILED, failing.closeReason());
            assertEquals("broken observer", failing.failure().getMessage());
            assertNull(failing.observer().closed);
            // 已经关闭，close() 不做任何事
            failing.close();
            // 同一条记录照常交给之后的 observer，bot 继续运行
            peer.push(chat("again"));
            peer.barrier();
            other.close();
            assertEquals(List.of("start", "chat boom", "chat again", "close BY_HANDLE"), other.observer().events);
            assertNull(other.failure());
            assertNull(bot.failure());
            assertEquals(BotStatus.PLAY, bot.status());
        });
    }

    @Test
    void aThrowingConditionFailsOnlyTheWait() {
        withBot((_, bot) -> {
            // 注册时先检查一次条件：这时抛出异常，Bot.await 抛出 ObserverFailureException
            var atStart = assertThrows(ObserverFailureException.class, () -> bot.await(_ -> {
                throw new IllegalStateException("broken at start");
            }, TIMEOUT));
            assertEquals("broken at start", atStart.getCause().getMessage());
            // 之后在 StateUpdated 时检查：抛出异常同样如此
            var checks = new AtomicInteger();
            var later = assertThrows(ObserverFailureException.class, () -> bot.await(_ -> {
                if (checks.incrementAndGet() > 1) {
                    throw new IllegalStateException("broken later");
                }
                return false;
            }, TIMEOUT));
            assertEquals("broken later", later.getCause().getMessage());
            assertNull(bot.failure());
            assertTrue(bot.rotate(0, 0).await(TIMEOUT));
        });
    }

    @Test
    void observedActionAwaitThrowsAsSoonAsTheObserverFails() {
        withBot((_, bot) -> {
            var observed = bot.observeDuring(new Recorder(Subscription.of(BotTicked.class)) {
                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    throw new IllegalStateException("broken during the action");
                }
            }, b -> b.moveForward(1000));
            // 不等动作结束，也不取消动作
            var thrown = assertThrows(ObserverFailureException.class, () -> observed.await(TIMEOUT));
            assertEquals("broken during the action", thrown.getCause().getMessage());
            assertEquals(CloseReason.FAILED, observed.handle().closeReason());
            assertFalse(observed.action().future().isDone());
            observed.action().cancel();
            assertFalse(observed.action().await(TIMEOUT));
            assertNull(bot.failure());
        });
    }

    @Test
    void aCallbackThrowingAfterItsObserverWasClosedEndsTheBot() {
        withBot((peer, bot) -> {
            var self = new CompletableFuture<ObserverHandle<?>>();
            var handle = bot.observe(new Recorder(Subscription.of(ChatReceived.class)) {
                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    // 在调度线程上调用 handle.close() 立即关闭这个 observer，之后回调又抛出异常
                    self.join().close();
                    throw new IllegalStateException("thrown after close");
                }
            });
            self.complete(handle);
            peer.push(chat("boom"));
            // observer 已经以 BY_HANDLE 关闭，异常无法再随关闭交给持有者，结束 bot
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TIMEOUT));
            assertEquals("thrown after close", bot.failure().getMessage());
            assertEquals(CloseReason.BY_HANDLE, handle.closeReason());
            assertNull(handle.failure());
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
            // 在其它线程（测试线程）调用：onClose 抛出异常时 observer 以 FAILED 关闭，close() 抛出 ObserverFailureException，bot 不受影响
            var thrown = assertThrows(ObserverFailureException.class, handle::close);
            assertEquals("broken onClose", thrown.getCause().getMessage());
            assertEquals(CloseReason.FAILED, handle.closeReason());
            assertEquals("broken onClose", handle.failure().getMessage());
            // 已经关闭，再次关闭不做任何事
            handle.close();
            assertNull(bot.failure());
            assertTrue(bot.rotate(0, 0).await(TIMEOUT));
        });
    }

    @Test
    void aThrowingOnCloseWhenTheConnectionEndsFailsOnlyThatObserver() {
        withBot((peer, bot) -> {
            var moving = bot.moveForward(1000);
            var broken = bot.observe(brokenOnClose());
            var other = bot.observe(new Recorder(Subscription.of(ChatReceived.class)));
            peer.push(new ClientboundDisconnectPacket(Component.text("bye")));
            // 其余 observer 照常关闭；onClose 抛出异常的 observer 以 FAILED 关闭，异常记在它的句柄上
            assertEquals(CloseReason.DISCONNECTED, other.awaitClose(TIMEOUT));
            var thrown = assertThrows(ObserverFailureException.class, () -> broken.awaitClose(TIMEOUT));
            assertEquals("broken onClose", thrown.getCause().getMessage());
            // bot 按断开结束：未结束的动作与之后的调用都得到 BotDisconnectedException
            assertEquals("bye", assertThrows(BotDisconnectedException.class, () -> moving.await(TIMEOUT)).getMessage());
            assertThrows(BotDisconnectedException.class, () -> bot.rotate(0, 0));
            assertNull(bot.failure());
            assertEquals(Component.text("bye"), bot.disconnectReason());
            assertEquals(BotStatus.DISCONNECTED, bot.status());
        });
    }

    @Test
    void aThrowingSubscriptionOrOnStartFailsObserveWithoutEndingTheBot() {
        withBot((_, bot) -> {
            var broken = brokenSubscription();
            // 在其它线程调用：注册失败时 observe 抛出 ObserverFailureException，不会一直等待
            var thrown = assertTimeoutPreemptively(TIMEOUT, () -> assertThrows(ObserverFailureException.class, () -> bot.observe(broken)));
            assertEquals("broken subscription", thrown.getCause().getMessage());
            // 同一个实例可以再次注册：每次注册都会再次调用 subscription()，因此同样失败
            assertEquals("broken subscription",
                    assertThrows(ObserverFailureException.class, () -> bot.observe(broken)).getCause().getMessage());
            var brokenStart = new Recorder(Subscription.of(ChatReceived.class)) {
                @Override
                public void onStart(ObserverContext context) {
                    throw new IllegalStateException("broken onStart");
                }
            };
            assertEquals("broken onStart", assertThrows(ObserverFailureException.class, () -> bot.observe(brokenStart)).getCause().getMessage());
            assertNull(bot.failure());
            assertTrue(bot.rotate(0, 0).await(TIMEOUT));
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
    void aThrowingSubscriptionFailsObserveOnTheSchedulerThreadAndInObserveDuring() {
        // 在调度线程上（另一个 observer 的回调中）注册：observe 抛出 ObserverFailureException，bot 不受影响
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
            var failure = assertInstanceOf(ObserverFailureException.class, assertTimeoutPreemptively(TIMEOUT, () -> thrown.get()));
            assertEquals("broken subscription", failure.getCause().getMessage());
            assertNull(bot.failure());
        });
        // observeDuring：注册失败时同样抛出 ObserverFailureException，不调用 submit
        withBot((_, bot) -> {
            var submitted = new AtomicBoolean();
            var thrown = assertThrows(ObserverFailureException.class, () -> bot.observeDuring(brokenSubscription(), b -> {
                submitted.set(true);
                return b.rotate(0, 0);
            }));
            assertEquals("broken subscription", thrown.getCause().getMessage());
            assertFalse(submitted.get());
            assertNull(bot.failure());
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
    void anObserverInstanceCanBeRegisteredMoreThanOnce() {
        withBot((peer, bot) -> {
            // 同一个实例交给同一个 bot 两次、交给不同的 bot：每次注册各自投递观察记录（docs/bot/observation.md）
            var recorder = new Recorder(Subscription.of(ChatReceived.class));
            var first = bot.observe(recorder);
            var second = bot.observe(recorder);
            peer.push(chat("hello"));
            peer.barrier();
            first.close();
            second.close();
            assertEquals(2, recorder.events.stream().filter("chat hello"::equals).count());
            assertEquals(CloseReason.BY_HANDLE, first.closeReason());
            assertEquals(CloseReason.BY_HANDLE, second.closeReason());
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

            // bot 已经断开：observe 抛出异常
            var watch = ended.observe(new Recorder(Subscription.NONE));
            peerA.close(null);
            assertEquals(CloseReason.DISCONNECTED, watch.awaitClose(TIMEOUT));
            var first = new Recorder(Subscription.of(ChatReceived.class));
            assertThrows(BotDisconnectedException.class, () -> ended.observe(first));

            // 注册请求交给调度线程之后、处理之前 bot 才断开：同样抛出异常
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
            assertThrows(IllegalStateException.class, () -> handle.observer().items());
            int count = handle.read(observer -> observer.items().size());
            assertEquals(1, count);
            handle.close();
            assertEquals("one", handle.observer().items().getFirst().plainText());
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
            assertEquals(Block.AIR, TestViews.of(bot).block(5, -1, 0));
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
