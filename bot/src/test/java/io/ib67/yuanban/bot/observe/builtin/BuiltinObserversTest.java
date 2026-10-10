package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observation.BlockChanged;
import io.ib67.yuanban.bot.observe.Observation.PacketReceived;
import io.ib67.yuanban.bot.observe.Observation.PacketSent;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.WorldClosed;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundBlockChangedAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinObserversTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;
    private static final Particle FLAME = new Particle(ParticleType.FLAME, null);

    private static void withBot(BiConsumer<TestPeer, Bot> test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "builtin", new Pos(0.5, 0, 0.5));
            test.accept(peer, bot);
        }
    }

    private static ClientboundLevelParticlesPacket particle(double x, double y, double z) {
        return new ClientboundLevelParticlesPacket(FLAME, false, false, x, y, z, 0.1f, 0.2f, 0.3f, 0.5f, 4);
    }

    private static ClientboundAddEntityPacket zombie(int id, double x, double z) {
        return new ClientboundAddEntityPacket(id, UUID.randomUUID(), EntityType.ZOMBIE, x, 0, z, 0, 0, 0);
    }

    private static ClientboundEntityPositionSyncPacket positionSync(int id, double x, double y, double z) {
        return new ClientboundEntityPositionSyncPacket(id, Vector3d.from(x, y, z), Vector3d.ZERO, 0, 0, true);
    }

    private static ClientboundBlockUpdatePacket blockUpdate(Vec pos, Block block) {
        return new ClientboundBlockUpdatePacket(new BlockChangeEntry(Vector3i.from(pos.blockX(), pos.blockY(), pos.blockZ()),
                block.stateId()));
    }

    private static ClientboundSystemChatPacket chat(Component message) {
        return new ClientboundSystemChatPacket(message, false);
    }

    @Test
    void particlesWithinRegionAndLimit() {
        withBot((peer, bot) -> {
            var handle = bot.observe(Observers.particlesWithin(Region.around(new Vec(10, 0, 10), 3)).limit(1));
            peer.push(particle(10, 0, 10), particle(20, 0, 20), particle(11, 1, 9));
            peer.barrier();
            handle.close();
            var observer = handle.observer();
            assertEquals(1, observer.particles().size());
            assertTrue(observer.truncated());
            var record = observer.particles().getFirst();
            assertEquals(FLAME, record.particle());
            assertEquals(new Vec(10, 0, 10), record.center());
            assertEquals(4, record.count());
            assertEquals(2, observer.worlds().getFirst().state().count());
        });
    }

    @Test
    void limitIsRejectedAfterRegistration() {
        withBot((_, bot) -> {
            // 注册之后 observer 的字段由调度线程读写，limit 只能在注册之前调用
            var region = Region.around(Vec.ZERO, 1);
            var particles = Observers.particlesWithin(region);
            var sounds = Observers.sounds(region);
            var blocks = Observers.blockChanges(region);
            var chat = Observers.chatMessages().limit(1);
            var actionBars = Observers.actionBars();
            var damage = Observers.damageTaken();
            var packets = Observers.packets(ClientboundSystemChatPacket.class);
            var sent = Observers.sentPackets(ServerboundChatPacket.class);
            var observations = Observers.observations(BlockChanged.class);
            for (Observer observer : List.<Observer>of(particles, sounds, blocks, chat, actionBars, damage, packets, sent, observations)) {
                bot.observe(observer);
            }
            assertThrows(IllegalStateException.class, () -> particles.limit(1));
            assertThrows(IllegalStateException.class, () -> sounds.limit(1));
            assertThrows(IllegalStateException.class, () -> blocks.limit(1));
            assertThrows(IllegalStateException.class, () -> chat.limit(2));
            assertThrows(IllegalStateException.class, () -> actionBars.limit(1));
            assertThrows(IllegalStateException.class, () -> damage.limit(1));
            assertThrows(IllegalStateException.class, () -> packets.limit(1));
            assertThrows(IllegalStateException.class, () -> sent.limit(1));
            assertThrows(IllegalStateException.class, () -> observations.limit(1));
        });
    }

    @Test
    void packetObservationTypesAreRejectedWhenConstructed() {
        // 订阅在调用工厂方法时构造：数据包的观察记录类型在调用线程上失败，不会在注册时结束 bot
        assertThrows(IllegalArgumentException.class, () -> Observers.first(PacketReceived.class, _ -> true));
        assertThrows(IllegalArgumentException.class, () -> Observers.observations(PacketSent.class));
        assertThrows(IllegalArgumentException.class, () -> Observers.listener(PacketReceived.class, _ -> {
        }));
        // 数据包类的方向不对时同样在调用线程上失败
        assertThrows(IllegalArgumentException.class, () -> Observers.packets(ServerboundKeepAlivePacket.class));
        assertThrows(IllegalArgumentException.class, () -> Observers.firstPacket(ServerboundKeepAlivePacket.class, _ -> true));
        assertThrows(IllegalArgumentException.class, () -> Observers.packetListener(ServerboundKeepAlivePacket.class, _ -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> Observers.sentPackets(ClientboundKeepAlivePacket.class));
    }

    @Test
    void entitySoundsUseTheEntityPosition() {
        withBot((peer, bot) -> {
            var handle = bot.observe(Observers.sounds(Region.around(new Vec(2, 0, 2), 1)));
            peer.push(zombie(7, 2, 2), zombie(8, 30, 30));
            peer.push(new ClientboundSoundEntityPacket(BuiltinSound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 7, 1, 1, 0),
                    new ClientboundSoundEntityPacket(BuiltinSound.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 8, 1, 1, 0));
            peer.barrier();
            handle.close();
            assertEquals(List.of(7), handle.observer().sounds().stream().map(SoundRecord::entityId).toList());
        });
    }

    @Test
    void movementOfTracksEntitiesAndRestartsInNewWorlds() {
        withBot((peer, bot) -> {
            peer.push(zombie(10, 0, 0));
            peer.barrier();
            var tracker = bot.observe(Observers.movementOf(entity -> entity.type() == EntityType.ZOMBIE));
            var closing = bot.observe(Observers.movementOf(entity -> entity.type() == EntityType.ZOMBIE,
                    MovementCriteria.create().atLeast(2).towards(new Vec(10, 0, 0), 30).entities(2)));
            peer.push(zombie(11, 0, 5), zombie(12, 0, 8));
            peer.push(positionSync(10, 3, 0, 0));
            // 方向不满足：向 -x 移动
            peer.push(positionSync(12, -3, 0, 8));
            peer.push(new ClientboundRemoveEntitiesPacket(new int[]{10}));
            peer.barrier();
            assertTrue(closing.isOpen());
            peer.push(positionSync(11, 3, 0, 4));
            assertEquals(CloseReason.BY_OBSERVER, closing.awaitClose(TIMEOUT));
            assertTrue(closing.observer().met());
            assertEquals(List.of(10, 11), closing.observer().metBy().stream().map(MovementTrack::entityId).toList());

            var tracks = tracker.read(MovementObserver::tracks);
            assertEquals(3, tracks.size());
            var first = tracks.getFirst();
            assertTrue(first.removed());
            assertEquals(new Vec(3, 0, 0), first.displacement());
            assertThrows(IllegalStateException.class, () -> tracker.read(MovementObserver::met));

            // 进入新世界时重新开始跟踪（重生到下界，世界重新创建，旧世界的实体不再存在）
            peer.push(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0));
            peer.push(zombie(20, 1, 1));
            peer.barrier();
            tracker.close();
            assertEquals(List.of(20), tracker.observer().tracks().stream().map(MovementTrack::entityId).toList());
            assertInstanceOf(WorldClosed.Left.class, tracker.observer().worlds().getFirst().closed());
        });
    }

    @Test
    void movementCriteriaNeedACondition() {
        assertThrows(IllegalArgumentException.class,
                () -> Observers.movementOf(_ -> true, MovementCriteria.create().entities(2)));
    }

    @Test
    void blockAtChecksTheCurrentBlockOnStart() {
        withBot((peer, bot) -> {
            var alreadyStone = bot.observe(Observers.blockAt(new Vec(0, -1, 0), block -> block.compare(Block.STONE)));
            assertEquals(CloseReason.BY_OBSERVER, alreadyStone.awaitClose(TIMEOUT));
            assertTrue(alreadyStone.observer().met());

            var waiting = bot.observe(Observers.blockAt(new Vec(1, -1, 0), block -> block.compare(Block.GOLD_BLOCK)));
            peer.push(blockUpdate(new Vec(1, -1, 0), Block.DIRT), blockUpdate(new Vec(1, -1, 0), Block.GOLD_BLOCK));
            assertEquals(CloseReason.BY_OBSERVER, waiting.awaitClose(TIMEOUT));
            assertEquals(2, waiting.observer().history().size());
            assertEquals(Block.GOLD_BLOCK, waiting.observer().current());
        });
    }

    @Test
    void blockBrokenClosesOnWorldChange() {
        withBot((peer, bot) -> {
            var handle = bot.observe(Observers.blockBroken(new Vec(0, -1, 0)));
            peer.push(blockUpdate(new Vec(0, -1, 0), Block.STONE));
            peer.push(new ClientboundRespawnPacket(TestServer.joinPacket(GameMode.SURVIVAL).getCommonPlayerSpawnInfo(), false, false));
            assertEquals(CloseReason.BY_OBSERVER, handle.awaitClose(TIMEOUT));
            assertFalse(handle.observer().broken());
        });
    }

    @Test
    void distanceObserversCancelTheAction() {
        withBot((_, bot) -> {
            var flag = new Vec(5.5, 0, 0.5);
            assertTrue(bot.lookAt(flag.add(0, 1.62F, 0)).await(TIMEOUT));
            var forward = bot.moveForward(20);
            var near = bot.observe(Observers.distanceWithIn(2, flag, forward));
            assertEquals(CloseReason.BY_OBSERVER, near.awaitClose(TIMEOUT));
            assertTrue(near.observer().met());
            assertFalse(forward.await(TIMEOUT));
            assertTrue(bot.view().player().position().distance(flag) <= 2.5);

            // 动作先结束：met 为 false
            var shortMove = bot.moveForward(0.5);
            var far = bot.observe(Observers.distanceOutOf(100, flag, shortMove));
            assertTrue(shortMove.await(TIMEOUT));
            assertEquals(CloseReason.BY_OBSERVER, far.awaitClose(TIMEOUT));
            assertFalse(far.observer().met());
        });
    }

    @Test
    void observersRegisteredOutsideAnyWorldObserveTheNextWorld() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "early", _ -> {
            });
            // 进入第一个世界之前：动作等待进入世界并加载之后执行，observer 观察下一个世界
            var dirt = new Vec(1, -1, 0);
            var broken = bot.breakBlock(dirt);
            var forward = bot.moveForward(10, MoveOptions.builder().direction(new Vec(1, 0, 0)).build());
            var near = bot.observe(Observers.distanceWithIn(2, new Vec(3.5, 0, 0.5), forward));
            assertTrue(near.isOpen());
            assertNull(broken.handle().read(BlockBrokenObserver::original));

            var mark = peer.mark();
            TestServer.join(peer, new Pos(0.5, 0, 0.5), GameMode.SURVIVAL, (x, z, chunk) -> {
                if (x == 0 && z == 0) {
                    chunk.set(1, -1, 0, Block.DIRT);
                }
            });
            // 传送到出生位置时距离为 3，之后向前移动进入 2 格以内时取消动作
            assertEquals(CloseReason.BY_OBSERVER, near.awaitClose(TIMEOUT));
            assertTrue(near.observer().met());
            assertFalse(forward.await(TIMEOUT));

            var finished = peer.awaitSent(mark, ServerboundPlayerActionPacket.class,
                    packet -> packet.getAction() == PlayerAction.FINISH_DIGGING, TIMEOUT);
            peer.push(blockUpdate(dirt, Block.AIR), new ClientboundBlockChangedAckPacket(finished.getSequence()));
            var result = broken.await(TIMEOUT);
            assertTrue(result.broken());
            assertEquals(Block.DIRT, result.original());
        }
    }

    @Test
    void distanceObserverRejectsActionsOfOtherBots() {
        try (var runtime = BotRuntime.create()) {
            var peerA = new TestPeer();
            var peerB = new TestPeer();
            var a = TestServer.joinedBot(runtime, peerA, "first", new Pos(0.5, 0, 0.5));
            var b = TestServer.joinedBot(runtime, peerB, "second", new Pos(0.5, 0, 0.5));
            var action = a.rotate(1, 1);
            assertThrows(IllegalArgumentException.class, () -> b.observe(Observers.distanceWithIn(1, Vec.ZERO, action)));
        }
    }

    @Test
    void conditionFirstChatAndDamage() {
        withBot((peer, bot) -> {
            var start = bot.view().ticks();
            bot.await(state -> state.ticks() >= start + 3, TIMEOUT);

            var hello = bot.observe(Observers.firstChat(line -> line.plainText().contains("hello")));
            var damage = bot.observe(Observers.damageTaken());
            peer.push(chat(Component.text("nothing")), chat(Component.text("say hello")));
            assertEquals(CloseReason.BY_OBSERVER, hello.awaitClose(TIMEOUT));
            assertEquals("say hello", hello.observer().matched().orElseThrow().plainText());

            peer.push(new ClientboundSetHealthPacket(15, 20, 5), new ClientboundSetHealthPacket(18, 20, 5));
            peer.barrier();
            damage.close();
            assertEquals(1, damage.observer().damages().size());
            var drop = assertInstanceOf(DamageRecord.HealthDrop.class, damage.observer().damages().getFirst());
            assertEquals(15, drop.change().health());
        });
    }

    @Test
    void firstTranslatedChatMatchesTheKeyInChildrenAndArguments() {
        withBot((peer, bot) -> {
            var joined = bot.observe(Observers.firstTranslatedChat("multiplayer.player.joined"));
            var nested = bot.observe(Observers.firstTranslatedChat("death.attack.player"));
            // 翻译组件不翻译，plainText 中没有它们的内容；不含这个翻译键的消息不匹配
            var other = Component.translatable("multiplayer.player.left", Component.text("bob"));
            var join = Component.text("> ").append(Component.translatable("multiplayer.player.joined", Component.text("alice")));
            var death = Component.translatable("chat.type.announcement", Component.text("server"),
                    Component.translatable("death.attack.player", Component.text("alice"), Component.text("bob")));
            peer.push(chat(other), chat(join), chat(death));
            assertEquals(CloseReason.BY_OBSERVER, joined.awaitClose(TIMEOUT));
            var line = joined.observer().matched().orElseThrow();
            assertEquals(join, line.message());
            assertEquals("> ", line.plainText());
            assertEquals(CloseReason.BY_OBSERVER, nested.awaitClose(TIMEOUT));
            assertEquals(death, nested.observer().matched().orElseThrow().message());
        });
    }
}
