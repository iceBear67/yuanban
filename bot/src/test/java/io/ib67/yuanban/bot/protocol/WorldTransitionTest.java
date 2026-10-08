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
import io.ib67.yuanban.bot.data.ItemData;
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
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeModifier;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.ModifierOperation;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundTransferPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDelimiterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
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
    private static final Key OVERWORLD_CLOCK = Key.key("overworld");
    private static final Key THE_END_CLOCK = Key.key("the_end");

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

    private static boolean cancelledDigging(MinecraftPacket packet) {
        return packet instanceof ServerboundPlayerActionPacket action && action.getAction() == PlayerAction.CANCEL_DIGGING;
    }

    private static boolean startedDigging(ServerboundPlayerActionPacket packet) {
        return packet.getAction() == PlayerAction.START_DIGGING;
    }

    private static String cancelReason(io.ib67.yuanban.bot.Action action) {
        return assertThrows(CompletionException.class, () -> action.future().join()).getCause().getMessage();
    }

    /**
     * Bundle Delimiter 没有内容，从空的数据构造
     */
    private static ClientboundDelimiterPacket delimiter() {
        return new ClientboundDelimiterPacket(Unpooled.EMPTY_BUFFER);
    }

    private static ClientboundGameEventPacket chunksLoadStart() {
        return new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null);
    }

    private static ClientboundRespawnPacket overworldRespawn(boolean keepMetadata, boolean keepAttributeModifiers) {
        return new ClientboundRespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).getCommonPlayerSpawnInfo(),
                keepMetadata, keepAttributeModifiers);
    }

    private static ClientboundPlayerPositionPacket teleport(int id, double x, double y, double z, float yaw, float pitch,
                                                           PositionElement... relatives) {
        return new ClientboundPlayerPositionPacket(id, x, y, z, 0, 0, 0, yaw, pitch, relatives);
    }

    private static ClientboundAddEntityPacket spawn(int id, EntityType type, double x, double y, double z) {
        return new ClientboundAddEntityPacket(id, UUID.randomUUID(), type, x, y, z, 0, 0, 0);
    }

    private static ClientboundTeleportEntityPacket teleportEntity(int id, double x, double y, double z, float yaw,
                                                                 boolean onGround) {
        return new ClientboundTeleportEntityPacket(id, Vector3d.from(x, y, z), Vector3d.ZERO, yaw, 0, List.of(), onGround);
    }

    private static ItemStack diamond() {
        return new ItemStack(ItemData.id(Key.key("diamond")));
    }

    private static int clockId(Key clock) {
        return TestServer.registryId(SyncedRegistries.WORLD_CLOCK, clock);
    }

    @Test
    void transferHandsPendingPacketsToTheOldConnectionAndStartsWithHandshake() throws Exception {
        var second = new TestPeer();
        withBot(options -> options.transportResolver((_, _) -> second), (peer, bot) -> {
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(DIRT);
            peer.awaitSent(mark, ServerboundPlayerActionPacket.class, WorldTransitionTest::startedDigging, TIMEOUT);
            // 同一个 bundle：Keep Alive 的应答在 Transfer 之前写入 Outbox；Transfer 之后的 Ping 属于旧连接，被忽略
            peer.push(delimiter(), new ClientboundKeepAlivePacket(77), new ClientboundTransferPacket("other.invalid", 25566),
                    new ClientboundPingPacket(78), delimiter());
            second.awaitSent(0, ClientIntentionPacket.class, TIMEOUT);
            assertInstanceOf(ClientIntentionPacket.class, second.sent().getFirst());
            assertTrue(peer.closedByBot());
            var old = peer.sentSince(mark);
            assertTrue(old.stream().anyMatch(packet -> packet instanceof ServerboundKeepAlivePacket keepAlive
                    && keepAlive.getPingId() == 77), old::toString);
            assertTrue(old.stream().noneMatch(ServerboundPongPacket.class::isInstance));
            assertTrue(second.sent().stream().noneMatch(packet -> packet instanceof ServerboundPongPacket
                    || packet instanceof ServerboundKeepAlivePacket));
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
            peer.awaitSent(mark, ServerboundChatPacket.class, TIMEOUT);
            peer.push(new ClientboundTransferPacket("other.invalid", 25566));
            second.awaitSent(0, ClientIntentionPacket.class, TIMEOUT);
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
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, 36, diamond()));
            assertTrue(bot.selectHotbar(2).await(TIMEOUT));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(DIRT);
            peer.awaitSent(mark, ServerboundPlayerActionPacket.class, WorldTransitionTest::startedDigging, TIMEOUT);
            peer.push(new ClientboundStartConfigurationPacket());
            var ack = peer.awaitSent(mark, ServerboundConfigurationAcknowledgedPacket.class, TIMEOUT);
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
            var cancelled = bot.useItem(Hand.MAIN_HAND);
            cancelled.cancel();
            assertFalse(cancelled.await(TIMEOUT));
            Thread.sleep(200);
            peer.barrier();
            var sent = peer.sentSince(mark);
            var afterAck = sent.subList(sent.indexOf(ack) + 1, sent.size());
            assertTrue(afterAck.stream().allMatch(ServerboundKeepAlivePacket.class::isInstance), afterAck::toString);
            assertTrue(sent.stream().noneMatch(WorldTransitionTest::cancelledDigging), sent::toString);

            var configurationMark = peer.mark();
            peer.push(TestServer.registryData(false));
            peer.push(TestServer.tags());
            peer.push(new ClientboundFinishConfigurationPacket());
            peer.awaitSent(configurationMark, ServerboundFinishConfigurationPacket.class, TIMEOUT);
            // Finish Configuration 之后、Login (play) 之前：仍不在任何世界中，不 tick，一次性请求不执行
            Thread.sleep(200);
            peer.barrier();
            assertTrue(peer.sentSince(configurationMark, ServerboundClientTickEndPacket.class).isEmpty());
            assertTrue(peer.sentSince(configurationMark, ServerboundChatPacket.class).isEmpty());
            assertNull(bot.view().world());

            var joinMark = peer.mark();
            join(peer);
            var world = bot.view().world();
            assertEquals(WorldEntryCause.RECONFIGURATION, world.cause());
            assertEquals(2, world.epoch());
            assertTrue(rotate.await(TIMEOUT));
            assertTrue(chat.await(TIMEOUT));
            // 新的世界与本地玩家：方块交互序号从 1 开始，快捷栏槽位与物品栏重新开始
            var started = peer.awaitSent(joinMark, ServerboundPlayerActionPacket.class, WorldTransitionTest::startedDigging, TIMEOUT);
            assertEquals(1, started.getSequence());
            assertEquals(0, bot.view().player().heldSlot());
            assertNull(bot.view().inventory().slots().get(36));
            assertFalse(digAgain.future().isDone());
            assertTrue(peer.sentSince(joinMark, ServerboundSetCarriedItemPacket.class).isEmpty());
        });
    }

    @Test
    void clocksBelongToTheConnectionAndFollowTheWorldAge() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var instance = (BotInstance) bot;
            // 冻结世界：时钟只由 Update Time 推进
            peer.push(new ClientboundTickingStatePacket(20, true));
            peer.push(new ClientboundSetTimePacket(1000, Map.of(clockId(OVERWORLD_CLOCK), new ClockNetworkState(100, 0.25f, 0.5f))));
            peer.barrier();
            assertEquals(Map.of(OVERWORLD_CLOCK, 100.25), bot.inspect(_ -> instance.state().clocks().clocks()));
            // 原版 ClientClockManager.handleUpdates：先按 World Age 的差值推进全部时钟，再替换列出的时钟，
            // 没有列出的时钟前进「差值 × 速率」
            peer.push(new ClientboundSetTimePacket(1100, Map.of(clockId(THE_END_CLOCK), new ClockNetworkState(7, 0f, 1f))));
            peer.barrier();
            assertEquals(Map.of(OVERWORLD_CLOCK, 150.25, THE_END_CLOCK, 7.0),
                    bot.inspect(_ -> instance.state().clocks().clocks()));
            // 时钟属于连接，不属于世界：换维度的 Respawn 之后仍然存在（新的世界没有冻结，时钟按它的 World Age 推进）
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0));
            peer.barrier();
            assertEquals(Set.of(OVERWORLD_CLOCK, THE_END_CLOCK),
                    bot.inspect(_ -> instance.state().clocks().clocks().keySet()));
            // 重新配置之后进入游戏阶段（原版新建 ClientPacketListener）：时钟重新开始
            var mark = peer.mark();
            peer.push(new ClientboundStartConfigurationPacket());
            peer.awaitSent(mark, ServerboundConfigurationAcknowledgedPacket.class, TIMEOUT);
            peer.push(TestServer.registryData(false));
            peer.push(TestServer.tags());
            peer.push(new ClientboundFinishConfigurationPacket());
            peer.awaitSent(mark, ServerboundFinishConfigurationPacket.class, TIMEOUT);
            assertEquals(Map.of(), bot.inspect(_ -> instance.state().clocks().clocks()));
        });
    }

    @Test
    void joinedAndLoadedWaitForTheNextWorldAfterLeaving() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            assertTrue(bot.joined().await(TIMEOUT));
            assertTrue(bot.loaded().await(TIMEOUT));
            peer.push(new ClientboundStartConfigurationPacket());
            peer.barrier();
            // 重新配置期间不在任何世界中，也没有加载：等待下一个世界的 Login (play) 与 Player Loaded
            var joined = bot.joined();
            var loaded = bot.loaded();
            var configurationMark = peer.mark();
            peer.push(TestServer.registryData(false));
            peer.push(TestServer.tags());
            peer.push(new ClientboundFinishConfigurationPacket());
            peer.awaitSent(configurationMark, ServerboundFinishConfigurationPacket.class, TIMEOUT);
            Thread.sleep(200);
            peer.barrier();
            assertFalse(joined.future().isDone());
            assertFalse(loaded.future().isDone());

            var mark = peer.mark();
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            assertTrue(joined.await(TIMEOUT));
            peer.barrier();
            assertFalse(loaded.future().isDone());
            peer.push(teleport(8, SPAWN.x(), SPAWN.y(), SPAWN.z(), 0, 0));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    peer.push(TestServer.flatChunk().packet(x, z, TestServer.biomeCount()));
                }
            }
            peer.push(chunksLoadStart());
            assertTrue(loaded.await(TIMEOUT));
            peer.awaitSent(mark, ServerboundPlayerLoadedPacket.class, TIMEOUT);

            // Respawn 之后、加载之前：loaded() 等待这次重生之后的加载，joined() 照常执行完毕
            peer.push(overworldRespawn(false, false));
            peer.barrier();
            var reloaded = bot.loaded();
            assertTrue(bot.joined().await(TIMEOUT));
            Thread.sleep(200);
            peer.barrier();
            assertFalse(reloaded.future().isDone());
            peer.push(chunksLoadStart());
            assertTrue(reloaded.await(TIMEOUT));
        });
    }

    @Test
    void respawnKeepsTheWorldInTheSameDimensionAndCreatesANewPlayer() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var speed = new Attribute(AttributeType.Builtin.MOVEMENT_SPEED, 0.2,
                    List.of(new AttributeModifier(Key.key("test"), 0.5, ModifierOperation.ADD)));
            peer.push(new ClientboundUpdateAttributesPacket(TestServer.ENTITY_ID, List.of(speed)));
            peer.push(new ClientboundOpenScreenPacket(5, ContainerType.GENERIC_9X1, Component.text("chest")));
            assertTrue(bot.selectHotbar(3).await(TIMEOUT));
            assertTrue(bot.rotate(30, 10).await(TIMEOUT));
            var mark = peer.mark();
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0));
            peer.push(overworldRespawn(false, false));
            peer.barrier();
            // 打开着窗口：先发送带窗口编号的 Close Container
            assertEquals(5, peer.awaitSent(mark, ServerboundContainerClosePacket.class, TIMEOUT).getContainerId());
            // 新玩家的快捷栏槽位为 0：下一 tick 发送 Set Held Item(0)
            assertEquals(0, peer.awaitSent(mark, ServerboundSetCarriedItemPacket.class, TIMEOUT).getSlot());
            var player = bot.view().player();
            // 没有 0x01：保留基础值，移除修饰符；没有 0x02：偏航角 -180（Minestom 的 Pos 把它规范为方向相同的 180），俯仰角 0，速度为零
            assertEquals(0.2, player.attributes().get(AttributeType.Builtin.MOVEMENT_SPEED), 1e-9);
            assertEquals(180, Math.abs(player.position().yaw()));
            assertEquals(0, player.position().pitch());
            assertEquals(Vec.ZERO, player.velocity());
            assertEquals(0, player.heldSlot());
            assertNull(bot.view().openWindow());

            // 同一维度内重生保留区块；换维度重新创建世界
            join(peer);
            assertEquals(Block.DIRT, bot.view().block(DIRT));
            peer.push(new ClientboundUpdateAttributesPacket(TestServer.ENTITY_ID, List.of(speed)));
            assertTrue(bot.rotate(30, 10).await(TIMEOUT));
            peer.push(overworldRespawn(true, true));
            peer.barrier();
            assertEquals(Block.DIRT, bot.view().block(DIRT));
            player = bot.view().player();
            // 0x01：保留修饰符；0x02：保留朝向
            assertEquals(0.7, player.attributes().get(AttributeType.Builtin.MOVEMENT_SPEED), 1e-9);
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
            peer.push(overworldRespawn(false, false));
            peer.barrier();
            peer.awaitSent(peer.mark(), ServerboundClientTickEndPacket.class, TIMEOUT);
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
            var mark = peer.mark();
            var first = bot.useItem(Hand.MAIN_HAND);
            assertEquals(1, peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT).getSequence());
            peer.push(new ClientboundBlockChangedAckPacket(1));
            assertTrue(first.await(TIMEOUT));

            // 同一维度内重生保留世界：方块交互序号属于世界，继续递增
            peer.push(overworldRespawn(false, false), chunksLoadStart());
            peer.barrier();
            mark = peer.mark();
            var second = bot.useItem(Hand.MAIN_HAND);
            assertEquals(2, peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT).getSequence());
            peer.push(new ClientboundBlockChangedAckPacket(2));
            assertTrue(second.await(TIMEOUT));

            // 换维度重新创建世界：序号从 1 开始
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0),
                    new ChunkBuilder(0, 256).packet(0, 0, TestServer.biomeCount()), chunksLoadStart());
            peer.barrier();
            mark = peer.mark();
            var third = bot.useItem(Hand.MAIN_HAND);
            assertEquals(1, peer.awaitSent(mark, ServerboundUseItemPacket.class, TIMEOUT).getSequence());
            peer.push(new ClientboundBlockChangedAckPacket(1));
            assertTrue(third.await(TIMEOUT));
        });
    }

    @Test
    void respawnRestartsTheSentInputAndSprintingUnlessDataIsKept() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            var sprint = MoveOptions.builder().mode(MovementMode.SPRINT).direction(new Vec(1, 0, 0)).build();
            var mark = peer.mark();
            var first = bot.moveForward(1000, sprint);
            peer.awaitSent(mark, ServerboundPlayerInputPacket.class, input -> input.isForward() && input.isSprint(), TIMEOUT);
            peer.awaitSent(mark, ServerboundPlayerCommandPacket.class, WorldTransitionTest::startSprinting, TIMEOUT);

            // 没有 Data kept 0x02：上次发送的 Player Input 为空、疾跑为 false，同样的移动再次发送两者。
            // 动作在加载之前提交，加载之后的第一次移动步骤就执行它
            peer.push(overworldRespawn(false, false));
            peer.barrier();
            assertFalse(first.await(TIMEOUT));
            mark = peer.mark();
            var second = bot.moveForward(1000, sprint);
            peer.push(chunksLoadStart());
            peer.awaitSent(mark, ServerboundPlayerInputPacket.class, input -> input.isForward() && input.isSprint(), TIMEOUT);
            peer.awaitSent(mark, ServerboundPlayerCommandPacket.class, WorldTransitionTest::startSprinting, TIMEOUT);

            // Data kept 0x02：保留上次发送的 Player Input 与疾跑状态，同样的移动不再发送
            peer.push(overworldRespawn(true, false));
            peer.barrier();
            assertFalse(second.await(TIMEOUT));
            var keptMark = peer.mark();
            var third = bot.moveForward(1000, sprint);
            peer.push(chunksLoadStart());
            bot.await(state -> state.player().position().x() > 1, TIMEOUT);
            assertTrue(peer.sentSince(keptMark, ServerboundPlayerInputPacket.class).isEmpty(),
                    () -> peer.sentSince(keptMark).toString());
            assertTrue(peer.sentSince(keptMark, ServerboundPlayerCommandPacket.class).isEmpty(),
                    () -> peer.sentSince(keptMark).toString());
            third.cancel();
        });
    }

    private static boolean startSprinting(ServerboundPlayerCommandPacket packet) {
        return packet.getState() == PlayerState.START_SPRINTING;
    }

    @Test
    void repeatedLoginKeepsThePlayerAndDiscardsTheHandQueueSilently() throws Exception {
        withBot(_ -> {
        }, (peer, bot) -> {
            peer.push(new ClientboundContainerSetSlotPacket(0, 1, 36, diamond()));
            peer.push(new ClientboundUpdateAttributesPacket(TestServer.ENTITY_ID, List.of(
                    new Attribute(AttributeType.Builtin.MAX_HEALTH, 30, List.of()))));
            var mark = peer.mark();
            var dig = bot.attemptBreakBlock(DIRT);
            peer.awaitSent(mark, ServerboundPlayerActionPacket.class, WorldTransitionTest::startedDigging, TIMEOUT);
            // 服务端不经过重新配置再次发送 Login (play)：沿用当前玩家，只做 resetPos
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.barrier();
            assertFalse(dig.await(TIMEOUT));
            assertTrue(peer.sentSince(mark).stream().noneMatch(WorldTransitionTest::cancelledDigging));
            var player = bot.view().player();
            assertEquals(diamond(), bot.view().inventory().slots().get(36));
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
            peer.push(new ClientboundSetEntityMotionPacket(TestServer.ENTITY_ID, Vector3d.from(0, 0, 1)));
            var mark = peer.mark();
            // 俯仰角从 0 变成 90，带 ROTATE_DELTA：原版先把速度绕 x 轴旋转 -90 度，(0, 0, 1) 变成 (0, -1, 0)
            peer.push(teleport(3, 0, 0, 0, 0, 90, PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT,
                    PositionElement.DELTA_X, PositionElement.DELTA_Y, PositionElement.DELTA_Z, PositionElement.ROTATE_DELTA));
            peer.barrier();
            var velocity = bot.view().player().velocity();
            assertEquals(0, velocity.x(), 1e-6);
            assertEquals(-1, velocity.y(), 1e-6);
            assertEquals(0, velocity.z(), 1e-6);
            // 带 DELTA_X 的分量加上数据包的值，其余取数据包的值
            peer.push(new ClientboundPlayerPositionPacket(4, 0, 0, 0, 0.5, 0.25, 0, 0, 0, PositionElement.X, PositionElement.Y,
                    PositionElement.Z, PositionElement.Y_ROT, PositionElement.X_ROT, PositionElement.DELTA_X));
            peer.barrier();
            assertEquals(new Vec(0.5, 0.25, 0), bot.view().player().velocity());
            // 传送的应答不计入移动包的上一次发送记录，着地标记不变：下一 tick 照常为改变的朝向发送移动包，着地为 true
            // （这个 tick 同时到了每 20 tick 发送一次位置的时候，朝向由 Set Player Position and Rotation 发出）
            var sent = peer.sentSince(mark);
            // 两次传送的应答：各自紧跟在 Confirm Teleportation 之后
            var replies = new ArrayList<MinecraftPacket>();
            for (int i = 0; i + 1 < sent.size(); i++) {
                if (sent.get(i) instanceof ServerboundAcceptTeleportationPacket) {
                    replies.add(sent.get(i + 1));
                }
            }
            assertEquals(2, replies.size());
            var reply = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, replies.getFirst());
            assertFalse(reply.isOnGround());
            var next = peer.awaitSent(mark + sent.indexOf(reply) + 1, MinecraftPacket.class,
                    packet -> (packet instanceof ServerboundMovePlayerRotPacket || packet instanceof ServerboundMovePlayerPosRotPacket)
                            && replies.stream().noneMatch(answer -> answer == packet), TIMEOUT);
            switch (next) {
                case ServerboundMovePlayerRotPacket rotation -> {
                    assertEquals(90, rotation.getPitch());
                    assertTrue(rotation.isOnGround());
                }
                case ServerboundMovePlayerPosRotPacket both -> {
                    assertEquals(90, both.getPitch());
                    assertTrue(both.isOnGround());
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
            peer.push(spawn(400, EntityType.OAK_BOAT, 2.5, 0, 0.5),
                    new ClientboundSetPassengersPacket(400, new int[]{TestServer.ENTITY_ID}));
            peer.barrier();
            var riding = bot.view().player().position();
            // 骑乘时传送不改变位置与朝向，照常回应
            var mark = peer.mark();
            peer.push(teleport(5, 8.5, 0, 8.5, 45, 0));
            peer.barrier();
            assertEquals(riding.x(), bot.view().player().position().x(), 1e-9);
            assertEquals(1, peer.sentSince(mark, ServerboundAcceptTeleportationPacket.class).size());
            // 移除载具：停止乘坐，之后的传送照常生效
            peer.push(new ClientboundRemoveEntitiesPacket(new int[]{400}));
            peer.push(teleport(6, 8.5, 0, 8.5, 45, 0));
            peer.barrier();
            assertEquals(8.5, bot.view().player().position().x(), 1e-9);

            peer.push(spawn(401, EntityType.OAK_BOAT, 2.5, 0, 0.5),
                    new ClientboundSetPassengersPacket(401, new int[]{TestServer.ENTITY_ID}));
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.push(teleport(7, 4.5, 0, 4.5, 0, 0));
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
            peer.push(spawn(410, EntityType.OAK_BOAT, 2.5, 0, 0.5),
                    spawn(411, EntityType.MINECART, 2.5, 0, 0.5),
                    new ClientboundSetPassengersPacket(410, new int[]{411}),
                    new ClientboundSetPassengersPacket(411, new int[]{TestServer.ENTITY_ID}));
            peer.barrier();
            // 移除下面的船：矿车离开船（原版 Entity.setRemoved），本地玩家仍然乘坐矿车，之后的 tick 照常执行
            peer.push(new ClientboundRemoveEntitiesPacket(new int[]{410}));
            peer.barrier();
            assertNull(bot.view().entity(411).vehicle());
            var mark = peer.mark();
            peer.awaitSent(mark, ServerboundMovePlayerRotPacket.class, TIMEOUT);
            assertEquals(Integer.valueOf(411), bot.inspect(_ -> instance.state().player().vehicle()));

            // 移除本地玩家乘坐的矿车：停止乘坐；之后对这个编号的 Synchronize Vehicle Position 应用到本地玩家，
            // 只发送 Set Player Position and Rotation（原版 handleTeleportEntity）
            peer.push(new ClientboundRemoveEntitiesPacket(new int[]{411}));
            peer.barrier();
            assertNull(bot.inspect(_ -> instance.state().player().vehicle()));
            mark = peer.mark();
            peer.push(teleportEntity(411, 6.5, 0, 0.5, 90, false));
            peer.barrier();
            assertEquals(6.5, bot.view().player().position().x(), 1e-9);
            assertEquals(90, bot.view().player().position().yaw());
            var reply = peer.awaitSent(mark, ServerboundMovePlayerPosRotPacket.class, packet -> packet.getX() == 6.5, TIMEOUT);
            assertFalse(reply.isOnGround());
            assertTrue(peer.sentSince(mark, ServerboundAcceptTeleportationPacket.class).isEmpty());

            // 这个编号的实体重新生成之后，数据包应用到实体
            peer.push(spawn(411, EntityType.MINECART, 1.5, 0, 0.5), teleportEntity(411, 8.5, 0, 0.5, 0, true));
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
            peer.push(spawn(421, EntityType.MINECART, 2.5, 0, 0.5),
                    spawn(420, EntityType.OAK_BOAT, 2.5, 0, 0.5),
                    new ClientboundSetPassengersPacket(421, new int[]{420}),
                    new ClientboundSetPassengersPacket(420, new int[]{TestServer.ENTITY_ID}));
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
            peer.push(spawn(420, EntityType.PIG, 4.5, 0, 0.5));
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
            peer.push(new ClientboundRemoveEntitiesPacket(new int[]{420}), teleportEntity(420, 9.5, 0, 0.5, 0, false));
            peer.barrier();
            assertNotEquals(9.5, bot.view().player().position().x(), 1e-9);
        });
    }
}
