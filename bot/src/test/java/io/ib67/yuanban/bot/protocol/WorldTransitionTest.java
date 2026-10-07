package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.ChunkBuilder;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.BotTicked;
import io.ib67.yuanban.bot.observe.EntityRemoved;
import io.ib67.yuanban.bot.observe.EntitySpawned;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.StateUpdated;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldEntered;
import io.ib67.yuanban.bot.observe.WorldEntryCause;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientKeepAlivePacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.play.ClientChatMessagePacket;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import net.minestom.server.network.packet.client.play.ClientConfigurationAckPacket;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket;
import net.minestom.server.network.packet.client.play.ClientInputPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerLoadedPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.client.play.ClientTickEndPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.network.packet.server.common.KeepAlivePacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.common.TransferPacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BundlePacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityAttributesPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.OpenWindowPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.SetTickStatePacket;
import net.minestom.server.network.packet.server.play.SetTimePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.StartConfigurationPacket;
import net.minestom.server.registry.Registries;
import net.minestom.server.world.clock.WorldClock;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离开与进入世界：Transfer、重新配置、Respawn、重复的 Login (play)、传送与乘坐状态。
 */
class WorldTransitionTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;
    private static final Pos SPAWN = new Pos(0.5, 0, 0.5);
    private static final Vec DIRT = new Vec(1, -1, 0);

    @FunctionalInterface
    private interface BotTest {
        void run(TestPeer peer, Bot bot) throws Exception;
    }

    private static void withBot(Consumer<BotOptions.BotOptionsBuilder> options, BotTest test) throws Exception {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "traveller", options);
            join(peer);
            // 加载之后第二次模拟才着地，挖掘的进度从着地开始按正常速度计算
            bot.await(state -> state.player().onGround(), TIMEOUT);
            test.run(peer, bot);
        }
    }

    private static void join(TestPeer peer) {
        TestServer.join(peer, SPAWN, GameMode.SURVIVAL, (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(1, -1, 0, Block.DIRT);
            }
        });
    }

    private static boolean cancelledDigging(ClientPacket packet) {
        return packet instanceof ClientPlayerActionPacket action && action.status() == ClientPlayerActionPacket.Status.CANCELLED_DIGGING;
    }

    private static String cancelReason(io.ib67.yuanban.bot.Action action) {
        return assertThrows(CompletionException.class, () -> action.future().join()).getCause().getMessage();
    }

    @Test
    void transferHandsPendingPacketsToTheOldConnectionAndStartsWithHandshake() throws Exception {
        var second = new TestPeer();
        withBot(options -> options.transportResolver((_, _) -> second), (peer, bot) -> {
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(DIRT);
            peer.awaitSent(mark, ClientPlayerActionPacket.class,
                    packet -> packet.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING, TIMEOUT);
            // 同一个 bundle：Keep Alive 的应答在 Transfer 之前写入 Outbox；Transfer 之后的 Ping 属于旧连接，被忽略
            peer.push(new BundlePacket(), new KeepAlivePacket(77), new TransferPacket("other.invalid", 25566),
                    new PingPacket(78), new BundlePacket());
            second.awaitSent(0, ClientHandshakePacket.class, TIMEOUT);
            assertInstanceOf(ClientHandshakePacket.class, second.sent().getFirst());
            assertTrue(peer.closedByBot());
            var old = peer.sentSince(mark);
            assertTrue(old.stream().anyMatch(packet -> packet instanceof ClientKeepAlivePacket keepAlive && keepAlive.id() == 77), old::toString);
            assertTrue(old.stream().noneMatch(ClientPongPacket.class::isInstance));
            assertTrue(second.sent().stream().noneMatch(packet -> packet instanceof ClientPongPacket || packet instanceof ClientKeepAlivePacket));
            // Transfer 引起的队列清空不发送清理数据包
            assertTrue(old.stream().noneMatch(WorldTransitionTest::cancelledDigging), old::toString);
            assertFalse(dig.await(TIMEOUT));
            assertEquals(ActionCancelledException.WORLD_REBUILT, cancelReason(dig));
            assertNull(bot.view().world());
        });
    }

    @Test
    void transferEndsActionsWhosePacketsWereNotWritten() throws Exception {
        var second = new TestPeer();
        withBot(options -> options.transportResolver((_, _) -> second), (peer, bot) -> {
            var held = peer.holdWrites();
            var mark = peer.mark();
            var chat = bot.chat("lost in transfer");
            peer.awaitSent(mark, ClientChatMessagePacket.class, TIMEOUT);
            peer.push(new TransferPacket("other.invalid", 25566));
            second.awaitSent(0, ClientHandshakePacket.class, TIMEOUT);
            assertFalse(chat.future().isDone());
            // 旧连接在写出之前关闭：写出即执行完毕的动作以 BotDisconnectedException 结束，bot 继续在新连接上登录
            held.completeExceptionally(new ClosedChannelException());
            assertThrows(BotDisconnectedException.class, () -> chat.await(TIMEOUT));
            TestServer.configure(second, "traveller");
            join(second);
            assertEquals(WorldEntryCause.TRANSFER, bot.view().world().cause());
        });
    }

    @Test
    void reconfigurationLeavesTheWorldUntilTheNextLogin() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            peer.push(new SetSlotPacket(0, 1, (short) 36, ItemStack.of(Material.DIAMOND)));
            assertTrue(bot.selectHotbar(2).await(TIMEOUT));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(DIRT);
            peer.awaitSent(mark, ClientPlayerActionPacket.class,
                    packet -> packet.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING, TIMEOUT);
            peer.push(new StartConfigurationPacket());
            var ack = peer.awaitSent(mark, ClientConfigurationAckPacket.class, TIMEOUT);
            // 离开世界：动作以世界重建取消，不发送 Cancelled digging；配置阶段再取消它不做任何事
            dig.cancel();
            assertFalse(dig.await(TIMEOUT));
            assertEquals(ActionCancelledException.WORLD_REBUILT, cancelReason(dig));
            assertNull(bot.view().world());
            assertThrows(IllegalStateException.class, () -> bot.view().player());

            // 重新配置期间提交的请求等待进入下一个世界；在配置阶段取消手部动作不发送数据包
            var digAgain = bot.attemptBreakBlock(DIRT);
            var chat = bot.chat("after reconfiguration");
            var rotate = bot.rotate(45, 0);
            var cancelled = bot.useItem(PlayerHand.MAIN);
            cancelled.cancel();
            assertFalse(cancelled.await(TIMEOUT));
            Thread.sleep(200);
            peer.barrier();
            var sent = peer.sentSince(mark);
            var afterAck = sent.subList(sent.indexOf(ack) + 1, sent.size());
            assertTrue(afterAck.stream().allMatch(ClientKeepAlivePacket.class::isInstance), afterAck::toString);
            assertTrue(sent.stream().noneMatch(WorldTransitionTest::cancelledDigging), sent::toString);

            var configurationMark = peer.mark();
            peer.push(TestServer.registryData(false));
            peer.push(Registries.tagsPacket(TestServer.vanilla()));
            peer.push(new FinishConfigurationPacket());
            peer.awaitSent(configurationMark, ClientFinishConfigurationPacket.class, TIMEOUT);
            // Finish Configuration 之后、Login (play) 之前：仍不在任何世界中，不 tick，一次性请求不执行
            Thread.sleep(200);
            peer.barrier();
            assertTrue(peer.sentSince(configurationMark, ClientTickEndPacket.class).isEmpty());
            assertTrue(peer.sentSince(configurationMark, ClientChatMessagePacket.class).isEmpty());
            assertNull(bot.view().world());

            var joinMark = peer.mark();
            join(peer);
            var world = bot.view().world();
            assertEquals(WorldEntryCause.RECONFIGURATION, world.cause());
            assertEquals(2, world.epoch());
            assertTrue(rotate.await(TIMEOUT));
            assertTrue(chat.await(TIMEOUT));
            // 新的世界与本地玩家：方块交互序号从 1 开始，快捷栏槽位与物品栏重新开始
            var started = peer.awaitSent(joinMark, ClientPlayerActionPacket.class,
                    packet -> packet.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING, TIMEOUT);
            assertEquals(1, started.sequence());
            assertEquals(0, bot.view().player().heldSlot());
            assertEquals(ItemStack.AIR, bot.view().inventory().slots().get(36));
            assertFalse(digAgain.future().isDone());
            assertTrue(peer.sentSince(joinMark, ClientHeldItemChangePacket.class).isEmpty());
        });
    }

    @Test
    void clocksBelongToTheConnectionAndFollowTheWorldAge() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var instance = (BotInstance) bot;
            // 冻结世界：时钟只由 Update Time 推进
            peer.push(new SetTickStatePacket(20, true));
            peer.push(new SetTimePacket(1000, Map.of(WorldClock.OVERWORLD, new SetTimePacket.ClockState(100, 0.25f, 0.5f))));
            peer.barrier();
            assertEquals(Map.of(WorldClock.OVERWORLD, 100.25), bot.inspect(_ -> instance.state().clocks().clocks()));
            // 原版 ClientClockManager.handleUpdates：先按 World Age 的差值推进全部时钟，再替换列出的时钟，
            // 没有列出的时钟前进「差值 × 速率」
            peer.push(new SetTimePacket(1100, Map.of(WorldClock.THE_END, new SetTimePacket.ClockState(7, 0f, 1f))));
            peer.barrier();
            assertEquals(Map.of(WorldClock.OVERWORLD, 150.25, WorldClock.THE_END, 7.0),
                    bot.inspect(_ -> instance.state().clocks().clocks()));
            // 时钟属于连接，不属于世界：换维度的 Respawn 之后仍然存在（新的世界没有冻结，时钟按它的 World Age 推进）
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0));
            peer.barrier();
            assertEquals(Set.of(WorldClock.OVERWORLD, WorldClock.THE_END),
                    bot.inspect(_ -> instance.state().clocks().clocks().keySet()));
            // 重新配置之后进入游戏阶段（原版新建 ClientPacketListener）：时钟重新开始
            var mark = peer.mark();
            peer.push(new StartConfigurationPacket());
            peer.awaitSent(mark, ClientConfigurationAckPacket.class, TIMEOUT);
            peer.push(TestServer.registryData(false));
            peer.push(Registries.tagsPacket(TestServer.vanilla()));
            peer.push(new FinishConfigurationPacket());
            peer.awaitSent(mark, ClientFinishConfigurationPacket.class, TIMEOUT);
            assertEquals(Map.of(), bot.inspect(_ -> instance.state().clocks().clocks()));
        });
    }

    @Test
    void joinedAndLoadedWaitForTheNextWorldAfterLeaving() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            assertTrue(bot.joined().await(TIMEOUT));
            assertTrue(bot.loaded().await(TIMEOUT));
            peer.push(new StartConfigurationPacket());
            peer.barrier();
            // 重新配置期间不在任何世界中，也没有加载：等待下一个世界的 Login (play) 与 Player Loaded
            var joined = bot.joined();
            var loaded = bot.loaded();
            var configurationMark = peer.mark();
            peer.push(TestServer.registryData(false));
            peer.push(Registries.tagsPacket(TestServer.vanilla()));
            peer.push(new FinishConfigurationPacket());
            peer.awaitSent(configurationMark, ClientFinishConfigurationPacket.class, TIMEOUT);
            Thread.sleep(200);
            peer.barrier();
            assertFalse(joined.future().isDone());
            assertFalse(loaded.future().isDone());

            var mark = peer.mark();
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            assertTrue(joined.await(TIMEOUT));
            peer.barrier();
            assertFalse(loaded.future().isDone());
            peer.push(new PlayerPositionAndLookPacket(8, SPAWN, Vec.ZERO, 0, 0, 0));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    peer.push(TestServer.flatChunk().packet(x, z, TestServer.biomeCount()));
                }
            }
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0));
            assertTrue(loaded.await(TIMEOUT));
            peer.awaitSent(mark, ClientPlayerLoadedPacket.class, TIMEOUT);

            // Respawn 之后、加载之前：loaded() 等待这次重生之后的加载，joined() 照常执行完毕
            peer.push(new RespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).playerSpawnInfo(), (byte) 0));
            peer.barrier();
            var reloaded = bot.loaded();
            assertTrue(bot.joined().await(TIMEOUT));
            Thread.sleep(200);
            peer.barrier();
            assertFalse(reloaded.future().isDone());
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0));
            assertTrue(reloaded.await(TIMEOUT));
        });
    }

    @Test
    void respawnKeepsTheWorldInTheSameDimensionAndCreatesANewPlayer() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var speed = new EntityAttributesPacket.Property(Attribute.MOVEMENT_SPEED, 0.2,
                    List.of(new AttributeModifier("minecraft:test", 0.5, AttributeOperation.ADD_VALUE)));
            peer.push(new EntityAttributesPacket(TestServer.ENTITY_ID, List.of(speed)));
            peer.push(new OpenWindowPacket(5, InventoryType.CHEST_1_ROW.ordinal(), Component.text("chest")));
            assertTrue(bot.selectHotbar(3).await(TIMEOUT));
            assertTrue(bot.rotate(30, 10).await(TIMEOUT));
            var mark = peer.mark();
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0));
            peer.push(new RespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).playerSpawnInfo(), (byte) 0));
            peer.barrier();
            // 打开着窗口：先发送带窗口编号的 Close Container
            assertEquals(5, peer.awaitSent(mark, ClientCloseWindowPacket.class, TIMEOUT).windowId());
            // 新玩家的快捷栏槽位为 0：下一 tick 发送 Set Held Item(0)
            assertEquals(0, peer.awaitSent(mark, ClientHeldItemChangePacket.class, TIMEOUT).slot());
            var player = bot.view().player();
            // 没有 0x01：保留基础值，移除修饰符；没有 0x02：偏航角 -180（Minestom 的 Pos 把它规范为方向相同的 180），俯仰角 0，速度为零
            assertEquals(0.2, player.attributes().get(Attribute.MOVEMENT_SPEED), 1e-9);
            assertEquals(180, Math.abs(player.position().yaw()));
            assertEquals(0, player.position().pitch());
            assertEquals(Vec.ZERO, player.velocity());
            assertEquals(0, player.heldSlot());
            assertNull(bot.view().openWindow());

            // 同一维度内重生保留区块；换维度重新创建世界
            join(peer);
            assertEquals(Block.DIRT, bot.view().block(DIRT));
            peer.push(new EntityAttributesPacket(TestServer.ENTITY_ID, List.of(speed)));
            assertTrue(bot.rotate(30, 10).await(TIMEOUT));
            peer.push(new RespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).playerSpawnInfo(), (byte) 3));
            peer.barrier();
            assertEquals(Block.DIRT, bot.view().block(DIRT));
            player = bot.view().player();
            // 0x01：保留修饰符；0x02：保留朝向
            assertEquals(0.7, player.attributes().get(Attribute.MOVEMENT_SPEED), 1e-9);
            assertEquals(30, player.position().yaw());
            assertEquals(10, player.position().pitch());
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 3));
            peer.barrier();
            assertThrows(ChunkNotLoadedException.class, () -> bot.view().block(1, 5, 0));
        });
    }

    @Test
    void theFirstTickAfterRespawnComesOneIntervalLater() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            // 只在调度线程上写入，关闭之后读取
            var events = new ArrayList<String>();
            var stalled = new CountDownLatch(1);
            var armed = new AtomicBoolean(true);
            var handle = bot.observe(new Observer() {
                @Override
                public Subscription subscription() {
                    return Subscription.of(BotTicked.class, WorldEntered.class, StateUpdated.class);
                }

                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    switch (observation) {
                        case BotTicked _ -> {
                            events.add("tick");
                            if (armed.compareAndSet(true, false)) {
                                // 调度线程在这个 tick 之后停住三个间隔：处理 Respawn 的那一轮开始时，下一次 tick 已经过期
                                stalled.countDown();
                                try {
                                    Thread.sleep(150);
                                } catch (InterruptedException e) {
                                    throw new AssertionError(e);
                                }
                            }
                        }
                        case WorldEntered _ -> events.add("entered");
                        case StateUpdated _ -> events.add("updated");
                        default -> throw new AssertionError(observation);
                    }
                }
            });
            assertTrue(stalled.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            // 同一维度内的 Respawn 保留世界，同样是进入世界
            peer.push(new RespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).playerSpawnInfo(), (byte) 0));
            peer.barrier();
            peer.awaitSent(peer.mark(), ClientTickEndPacket.class, TIMEOUT);
            handle.close();
            // 处理 Respawn 的那一轮以 StateUpdated 结束，其中没有 tick
            var entered = events.indexOf("entered");
            assertTrue(entered > 0, events::toString);
            assertEquals("updated", events.get(entered + 1), events::toString);
        });
    }

    @Test
    void respawnRestartsTheBlockSequenceOnlyWithANewWorld() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var loadStart = new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0);
            var mark = peer.mark();
            var first = bot.useItem(PlayerHand.MAIN);
            assertEquals(1, peer.awaitSent(mark, ClientUseItemPacket.class, TIMEOUT).sequence());
            peer.push(new AcknowledgeBlockChangePacket(1));
            assertTrue(first.await(TIMEOUT));

            // 同一维度内重生保留世界：方块交互序号属于世界，继续递增
            peer.push(new RespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).playerSpawnInfo(), (byte) 0), loadStart);
            peer.barrier();
            mark = peer.mark();
            var second = bot.useItem(PlayerHand.MAIN);
            assertEquals(2, peer.awaitSent(mark, ClientUseItemPacket.class, TIMEOUT).sequence());
            peer.push(new AcknowledgeBlockChangePacket(2));
            assertTrue(second.await(TIMEOUT));

            // 换维度重新创建世界：序号从 1 开始
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0),
                    new ChunkBuilder(0, 256).packet(0, 0, TestServer.biomeCount()), loadStart);
            peer.barrier();
            mark = peer.mark();
            var third = bot.useItem(PlayerHand.MAIN);
            assertEquals(1, peer.awaitSent(mark, ClientUseItemPacket.class, TIMEOUT).sequence());
            peer.push(new AcknowledgeBlockChangePacket(1));
            assertTrue(third.await(TIMEOUT));
        });
    }

    @Test
    void respawnRestartsTheSentInputAndSprintingUnlessDataIsKept() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var sprint = MoveOptions.builder().mode(MovementMode.SPRINT).direction(new Vec(1, 0, 0)).build();
            var spawnInfo = TestServer.joinPacket(GameMode.SURVIVAL).playerSpawnInfo();
            var mark = peer.mark();
            var first = bot.moveForward(1000, sprint);
            peer.awaitSent(mark, ClientInputPacket.class, input -> input.forward() && input.sprint(), TIMEOUT);
            peer.awaitSent(mark, ClientEntityActionPacket.class, WorldTransitionTest::startSprinting, TIMEOUT);

            // 没有 Data kept 0x02：上次发送的 Player Input 为空、疾跑为 false，同样的移动再次发送两者。
            // 动作在加载之前提交，加载之后的第一次移动步骤就执行它
            peer.push(new RespawnPacket(spawnInfo, (byte) 0));
            peer.barrier();
            assertFalse(first.await(TIMEOUT));
            mark = peer.mark();
            var second = bot.moveForward(1000, sprint);
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0));
            peer.awaitSent(mark, ClientInputPacket.class, input -> input.forward() && input.sprint(), TIMEOUT);
            peer.awaitSent(mark, ClientEntityActionPacket.class, WorldTransitionTest::startSprinting, TIMEOUT);

            // Data kept 0x02：保留上次发送的 Player Input 与疾跑状态，同样的移动不再发送
            peer.push(new RespawnPacket(spawnInfo, (byte) 0x02));
            peer.barrier();
            assertFalse(second.await(TIMEOUT));
            var keptMark = peer.mark();
            var third = bot.moveForward(1000, sprint);
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0));
            bot.await(state -> state.player().position().x() > 1, TIMEOUT);
            assertTrue(peer.sentSince(keptMark, ClientInputPacket.class).isEmpty(), () -> peer.sentSince(keptMark).toString());
            assertTrue(peer.sentSince(keptMark, ClientEntityActionPacket.class).isEmpty(), () -> peer.sentSince(keptMark).toString());
            third.cancel();
        });
    }

    private static boolean startSprinting(ClientEntityActionPacket packet) {
        return packet.action() == ClientEntityActionPacket.Action.START_SPRINTING;
    }

    @Test
    void repeatedLoginKeepsThePlayerAndDiscardsTheHandQueueSilently() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            peer.push(new SetSlotPacket(0, 1, (short) 36, ItemStack.of(Material.DIAMOND)));
            peer.push(new EntityAttributesPacket(TestServer.ENTITY_ID, List.of(
                    new EntityAttributesPacket.Property(Attribute.MAX_HEALTH, 30, List.of()))));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(DIRT);
            peer.awaitSent(mark, ClientPlayerActionPacket.class,
                    packet -> packet.status() == ClientPlayerActionPacket.Status.STARTED_DIGGING, TIMEOUT);
            // 服务端不经过重新配置再次发送 Login (play)：沿用当前玩家，只做 resetPos
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.barrier();
            assertFalse(dig.await(TIMEOUT));
            assertTrue(peer.sentSince(mark).stream().noneMatch(WorldTransitionTest::cancelledDigging));
            var player = bot.view().player();
            assertEquals(ItemStack.of(Material.DIAMOND), bot.view().inventory().slots().get(36));
            assertEquals(30, player.health());
            assertEquals(Vec.ZERO, player.velocity());
        });
    }

    @Test
    void teleportRotatesVelocityLikeTheVanillaClient() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            bot.suspendMovement();
            bot.await(state -> state.movementSuspended(), TIMEOUT);
            peer.push(new EntityVelocityPacket(TestServer.ENTITY_ID, new Vec(0, 0, 1)));
            var mark = peer.mark();
            // 俯仰角从 0 变成 90，带 ROTATE_DELTA：原版先把速度绕 x 轴旋转 -90 度，(0, 0, 1) 变成 (0, -1, 0)
            peer.push(new PlayerPositionAndLookPacket(3, Vec.ZERO, Vec.ZERO, 0, 90,
                    RelativeFlags.COORD | RelativeFlags.YAW | RelativeFlags.DELTA_COORD | RelativeFlags.ROTATE_DELTA));
            peer.barrier();
            var velocity = bot.view().player().velocity();
            assertEquals(0, velocity.x(), 1e-6);
            assertEquals(-1, velocity.y(), 1e-6);
            assertEquals(0, velocity.z(), 1e-6);
            // 带 DELTA_X 的分量加上数据包的值，其余取数据包的值
            peer.push(new PlayerPositionAndLookPacket(4, Vec.ZERO, new Vec(0.5, 0.25, 0), 0, 0,
                    RelativeFlags.COORD | RelativeFlags.VIEW | RelativeFlags.DELTA_X));
            peer.barrier();
            assertEquals(new Vec(0.5, 0.25, 0), bot.view().player().velocity());
            // 传送的应答不计入移动包的上一次发送记录，着地标记不变：下一 tick 照常为改变的朝向发送移动包，着地为 true
            // （这个 tick 同时到了每 20 tick 发送一次位置的时候，朝向由 Set Player Position and Rotation 发出）
            var sent = peer.sentSince(mark);
            // 两次传送的应答：各自紧跟在 Confirm Teleportation 之后
            var replies = new ArrayList<ClientPacket>();
            for (int i = 0; i + 1 < sent.size(); i++) {
                if (sent.get(i) instanceof ClientTeleportConfirmPacket) {
                    replies.add(sent.get(i + 1));
                }
            }
            assertEquals(2, replies.size());
            var reply = assertInstanceOf(ClientPlayerPositionAndRotationPacket.class, replies.getFirst());
            assertFalse(reply.onGround());
            var next = peer.awaitSent(mark + sent.indexOf(reply) + 1, ClientPacket.class,
                    packet -> (packet instanceof ClientPlayerRotationPacket || packet instanceof ClientPlayerPositionAndRotationPacket)
                            && replies.stream().noneMatch(answer -> answer == packet), TIMEOUT);
            switch (next) {
                case ClientPlayerRotationPacket rotation -> {
                    assertEquals(90, rotation.pitch());
                    assertTrue(rotation.onGround());
                }
                case ClientPlayerPositionAndRotationPacket both -> {
                    assertEquals(90, both.position().pitch());
                    assertTrue(both.onGround());
                }
                default -> throw new AssertionError(next);
            }
            assertTrue(bot.view().player().onGround());
        });
    }

    @Test
    void ridingStateIsClearedWhenTheVehicleIsRemovedOrOnLogin() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            peer.push(new SpawnEntityPacket(400, UUID.randomUUID(), EntityType.OAK_BOAT, new Pos(2.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new SetPassengersPacket(400, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            var riding = bot.view().player().position();
            // 骑乘时传送不改变位置与朝向，照常回应
            var mark = peer.mark();
            peer.push(new PlayerPositionAndLookPacket(5, new Vec(8.5, 0, 8.5), Vec.ZERO, 45, 0, 0));
            peer.barrier();
            assertEquals(riding.x(), bot.view().player().position().x(), 1e-9);
            assertEquals(1, peer.sentSince(mark, ClientTeleportConfirmPacket.class).size());
            // 移除载具：停止乘坐，之后的传送照常生效
            peer.push(new DestroyEntitiesPacket(List.of(400)));
            peer.push(new PlayerPositionAndLookPacket(6, new Vec(8.5, 0, 8.5), Vec.ZERO, 45, 0, 0));
            peer.barrier();
            assertEquals(8.5, bot.view().player().position().x(), 1e-9);

            peer.push(new SpawnEntityPacket(401, UUID.randomUUID(), EntityType.OAK_BOAT, new Pos(2.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new SetPassengersPacket(401, List.of(TestServer.ENTITY_ID)));
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.push(new PlayerPositionAndLookPacket(7, new Vec(4.5, 0, 4.5), Vec.ZERO, 0, 0, 0));
            peer.barrier();
            assertEquals(4.5, bot.view().player().position().x(), 1e-9);
        });
    }

    @Test
    void removingAnEntityDetachesItsRidersAndRemembersThePlayersVehicle() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var instance = (BotInstance) bot;
            // 本地玩家乘坐矿车，矿车乘坐船
            peer.push(new SpawnEntityPacket(410, UUID.randomUUID(), EntityType.OAK_BOAT, new Pos(2.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new SpawnEntityPacket(411, UUID.randomUUID(), EntityType.MINECART, new Pos(2.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new SetPassengersPacket(410, List.of(411)),
                    new SetPassengersPacket(411, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            // 移除下面的船：矿车离开船（原版 Entity.setRemoved），本地玩家仍然乘坐矿车，之后的 tick 照常执行
            peer.push(new DestroyEntitiesPacket(List.of(410)));
            peer.barrier();
            assertNull(bot.view().entity(411).vehicle());
            var mark = peer.mark();
            peer.awaitSent(mark, ClientPlayerRotationPacket.class, TIMEOUT);
            assertEquals(Integer.valueOf(411), bot.inspect(_ -> instance.state().player().vehicle()));

            // 移除本地玩家乘坐的矿车：停止乘坐；之后对这个编号的 Synchronize Vehicle Position 应用到本地玩家，
            // 只发送 Set Player Position and Rotation（原版 handleTeleportEntity）
            peer.push(new DestroyEntitiesPacket(List.of(411)));
            peer.barrier();
            assertNull(bot.inspect(_ -> instance.state().player().vehicle()));
            mark = peer.mark();
            peer.push(new EntityTeleportPacket(411, new Pos(6.5, 0, 0.5, 90, 0), Vec.ZERO, 0, false));
            peer.barrier();
            assertEquals(6.5, bot.view().player().position().x(), 1e-9);
            assertEquals(90, bot.view().player().position().yaw());
            var reply = peer.awaitSent(mark, ClientPlayerPositionAndRotationPacket.class, packet -> packet.position().x() == 6.5, TIMEOUT);
            assertFalse(reply.onGround());
            assertTrue(peer.sentSince(mark, ClientTeleportConfirmPacket.class).isEmpty());

            // 这个编号的实体重新生成之后，数据包应用到实体
            peer.push(new SpawnEntityPacket(411, UUID.randomUUID(), EntityType.MINECART, new Pos(1.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new EntityTeleportPacket(411, new Pos(8.5, 0, 0.5), Vec.ZERO, 0, true));
            peer.barrier();
            assertEquals(8.5, bot.view().entity(411).position().x(), 1e-9);
            assertEquals(6.5, bot.view().player().position().x(), 1e-9);
        });
    }

    @Test
    void spawningAnExistingIdRemovesTheOldEntityFirst() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var instance = (BotInstance) bot;
            // 本地玩家乘坐船 420，船乘坐矿车 421
            peer.push(new SpawnEntityPacket(421, UUID.randomUUID(), EntityType.MINECART, new Pos(2.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new SpawnEntityPacket(420, UUID.randomUUID(), EntityType.OAK_BOAT, new Pos(2.5, 0, 0.5), 0, 0, Vec.ZERO),
                    new SetPassengersPacket(421, List.of(420)),
                    new SetPassengersPacket(420, List.of(TestServer.ENTITY_ID)));
            peer.barrier();
            var changes = new CopyOnWriteArrayList<Observation>();
            var handle = bot.observe(new Observer() {
                @Override
                public Subscription subscription() {
                    return Subscription.of(EntityRemoved.class, EntitySpawned.class);
                }

                @Override
                public void onObservation(Observation observation, ObserverContext context) {
                    changes.add(observation);
                }
            });
            // 同一编号再次生成（原版 ClientLevel.addEntity）：先移除旧的船，它离开矿车，本地玩家停止乘坐
            peer.push(new SpawnEntityPacket(420, UUID.randomUUID(), EntityType.PIG, new Pos(4.5, 0, 0.5), 0, 0, Vec.ZERO));
            peer.barrier();
            handle.close();
            assertEquals(2, changes.size(), changes::toString);
            assertEquals(EntityType.OAK_BOAT, assertInstanceOf(EntityRemoved.class, changes.get(0)).last().type());
            assertEquals(EntityType.PIG, assertInstanceOf(EntitySpawned.class, changes.get(1)).entity().type());
            assertNull(bot.inspect(_ -> instance.state().player().vehicle()));
            assertTrue(bot.view().entity(421).passengers().isEmpty());
            assertNull(bot.view().entity(420).vehicle());

            // 只有 Remove Entities 记下本地玩家乘坐过的载具：移除新的实体之后，这个编号的 Synchronize Vehicle Position
            // 不应用到本地玩家
            peer.push(new DestroyEntitiesPacket(List.of(420)), new EntityTeleportPacket(420, new Pos(9.5, 0, 0.5), Vec.ZERO, 0, false));
            peer.barrier();
            assertNotEquals(9.5, bot.view().player().position().x(), 1e-9);
        });
    }
}
