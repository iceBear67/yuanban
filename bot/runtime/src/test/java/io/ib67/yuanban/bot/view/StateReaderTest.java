package io.ib67.yuanban.bot.view;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.testing.ChunkBuilder;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import io.ib67.yuanban.bot.testing.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.TestViews;
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.KnownPack;
import org.geysermc.mcprotocollib.protocol.data.game.ServerLink;
import org.geysermc.mcprotocollib.protocol.data.game.ServerLinkType;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkBiomeData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.data.game.setting.Difficulty;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundServerLinksPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundUpdateEnabledFeaturesPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundChangeDifficultyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCooldownPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStepPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheRadiusPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetSimulationDistancePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import static io.ib67.yuanban.bot.TestServer.TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询接口读出的连接信息、世界状态、生物群系，以及本地玩家的物品冷却与元数据。
 */
class StateReaderTest {
    private static final Key OVERWORLD_CLOCK = Key.key("overworld");
    private static final int PORTAL_TELEPORT_BOUNDARY = 29999984;

    private static void withBot(BiConsumer<TestPeer, Bot> test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "reader", new Pos(0.5, 0, 0.5));
            test.accept(peer, bot);
        }
    }

    private static void waitTicks(TestPeer peer, int ticks) {
        var mark = peer.mark();
        var deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (peer.sentSince(mark, ServerboundClientTickEndPacket.class).size() < ticks) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + ticks + " ticks");
            }
            Thread.onSpinWait();
        }
    }

    private static ClientboundGameEventPacket gameEvent(GameEvent event, float value) {
        var buffer = Unpooled.buffer();
        buffer.writeByte(event.ordinal());
        buffer.writeFloat(value);
        var packet = new ClientboundGameEventPacket(buffer);
        buffer.release();
        return packet;
    }

    private static byte[] string(String value) {
        var buffer = Unpooled.buffer();
        MinecraftTypes.writeString(buffer, value);
        var bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        buffer.release();
        return bytes;
    }

    @Test
    void connectionInformationComesFromTheConfigurationPackets() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = runtime.connect(BotOptions.builder().username("reader").transport(peer).build());
            assertNull(TestViews.of(bot).serverBrand());
            assertEquals(List.of(), TestViews.of(bot).serverLinks());
            assertEquals(List.of(), TestViews.of(bot).featureFlags());
            var links = List.of(new ServerLink(ServerLinkType.WEBSITE, null, "https://example.com"));
            var features = List.of(Key.key("vanilla"), Key.key("trade_rebalance"));
            var mark = peer.mark();
            peer.push(new ClientboundLoginFinishedPacket(TestServer.profile("reader"), new UUID(0, 0)));
            peer.push(new ClientboundCustomPayloadPacket(Key.key("brand"), string("test-server")));
            peer.push(new ClientboundServerLinksPacket(links));
            peer.push(new ClientboundUpdateEnabledFeaturesPacket(features.toArray(Key[]::new)));
            peer.push(new ClientboundSelectKnownPacks(List.of(
                    new KnownPack(Key.MINECRAFT_NAMESPACE, "core", MinecraftCodec.CODEC.getMinecraftVersion()))));
            peer.push(TestServer.registryData(false));
            peer.push(TestServer.tags());
            peer.push(new ClientboundFinishConfigurationPacket());
            peer.awaitSent(mark, ServerboundFinishConfigurationPacket.class, TIMEOUT);
            assertEquals("test-server", TestViews.of(bot).serverBrand());
            assertEquals(links, TestViews.of(bot).serverLinks());
            assertEquals(features, TestViews.of(bot).featureFlags());
            // 进入游戏阶段时时钟重新开始，Update Time 之前没有时钟
            assertEquals(Map.of(), TestViews.of(bot).clocks());
            // 完成配置之后、Login (play) 之前不在任何世界中
            assertThrows(IllegalStateException.class, () -> TestViews.of(bot).worldState());
            assertThrows(IllegalStateException.class, () -> TestViews.of(bot).biome(0, 0, 0));
            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            assertEquals("test-server", TestViews.of(bot).serverBrand());
            assertEquals(links, TestViews.of(bot).serverLinks());
            assertEquals(features, TestViews.of(bot).featureFlags());
        }
    }

    /**
     * 同一时刻读到的世界状态与时钟
     */
    private record Sample(WorldStateSnapshot world, Map<Key, Double> clocks) {
    }

    @Test
    void worldStateFollowsTheWorldPackets() {
        withBot((peer, bot) -> {
            var clockId = TestServer.registryId(SyncedRegistries.WORLD_CLOCK, OVERWORLD_CLOCK);
            // 先冻结世界：World Age、时钟与世界边框都停在数据包给出的值
            peer.push(new ClientboundTickingStatePacket(20, true),
                    new ClientboundSetTimePacket(1000, Map.of(clockId, new ClockNetworkState(100, 0.25f, 0.5f))),
                    new ClientboundChangeDifficultyPacket(Difficulty.HARD, false),
                    new ClientboundSetDefaultSpawnPositionPacket(new GlobalPos(TestServer.OVERWORLD, Vector3i.from(3, 64, -7)), 0, 0),
                    new ClientboundSetChunkCacheRadiusPacket(5),
                    new ClientboundSetSimulationDistancePacket(7),
                    new ClientboundInitializeBorderPacket(10, -20, 100, 50, 1000, PORTAL_TELEPORT_BOUNDARY, 5, 15),
                    gameEvent(GameEvent.RAIN_LEVEL_CHANGE, 0.25f), gameEvent(GameEvent.THUNDER_LEVEL_CHANGE, 0.75f));
            peer.barrier();
            waitTicks(peer, 1);
            // 插值刚开始时大小为起始大小
            assertEquals(new WorldStateSnapshot(1000, Difficulty.HARD, new Vec(3, 64, -7), 5, 7,
                    new WorldStateSnapshot.Ticking(20, true, 0, false),
                    new WorldStateSnapshot.Border(10, -20, 100, 100, true, 50, 1000, PORTAL_TELEPORT_BOUNDARY, 15, 5),
                    0.25f, 0.75f), TestViews.of(bot).worldState());
            assertEquals(Map.of(OVERWORLD_CLOCK, 100.25), TestViews.of(bot).clocks());

            // 步进期间每个 tick 都正常运行：World Age 加一，时钟按速率 0.5 前进，世界边框每 tick 缩小 50 / 1000
            peer.push(new ClientboundTickingStepPacket(1000));
            peer.barrier();
            waitTicks(peer, 2);
            var sample = bot.inspect(reader -> new Sample(reader.worldState(), reader.clocks()));
            var ticking = sample.world().ticking();
            var border = sample.world().border();
            var advanced = 1000 - border.lerpRemainingTicks();
            assertTrue(advanced >= 1, sample::toString);
            assertTrue(ticking.frozen());
            assertTrue(ticking.runsNormally());
            assertEquals(1000 - advanced, ticking.stepTicks());
            assertEquals(1000 + advanced, sample.world().worldAge());
            assertEquals(100.25 + 0.5 * advanced, sample.clocks().get(OVERWORLD_CLOCK));
            assertTrue(border.moving());
            assertEquals(50, border.lerpTarget());
            assertEquals(100 - 0.05 * advanced, border.size(), 1e-9);
            // 范围判断与碰撞使用上一 tick 的大小
            assertEquals(100 - 0.05 * (advanced - 1), border.boundsSize(), 1e-9);

            peer.push(new ClientboundSetBorderSizePacket(30), new ClientboundSetBorderCenterPacket(1, 2),
                    new ClientboundSetBorderWarningDelayPacket(20), new ClientboundSetBorderWarningDistancePacket(8));
            peer.barrier();
            assertEquals(new WorldStateSnapshot.Border(1, 2, 30, 30, false, 30, 0, PORTAL_TELEPORT_BOUNDARY, 20, 8),
                    TestViews.of(bot).worldState().border());
        });
    }

    @Test
    void biomesAreReadByName() {
        withBot((peer, bot) -> {
            var biomes = TestServer.registryEntries(SyncedRegistries.BIOME);
            var data = ChunkBuilder.overworld().biome(3).biomeData(biomes.size());
            peer.push(new ClientboundChunksBiomesPacket(List.of(new ChunkBiomeData(-1, 0, data))));
            peer.barrier();
            assertEquals(biomes.get(3).getId(), TestViews.of(bot).biome(-5, 70, 3));
            assertEquals(biomes.getFirst().getId(), TestViews.of(bot).biome(new Vec(5, 70, 3)));
            assertThrows(IllegalArgumentException.class, () -> TestViews.of(bot).biome(5, 320, 3));
            assertThrows(ChunkNotLoadedException.class, () -> TestViews.of(bot).biome(16 * 4, 0, 0));
        });
    }

    /**
     * 同一时刻读到的本地玩家与 tick 数
     */
    private record PlayerSample(PlayerSnapshot player, long ticks) {
    }

    @Test
    void playerSnapshotCarriesCooldownsAndMetadata() {
        withBot((peer, bot) -> {
            var group = Key.key("ender_pearl");
            var before = TestViews.of(bot).ticks();
            peer.push(new ClientboundCooldownPacket(group, 1000),
                    new ClientboundSetEntityDataPacket(TestServer.ENTITY_ID, new EntityMetadata<?, ?>[]{
                            new BooleanEntityMetadata(EntityMetaIndex.HAS_NO_GRAVITY.index(), MetadataTypes.BOOLEAN, true)}));
            peer.barrier();
            var sample = bot.inspect(reader -> new PlayerSample(reader.player(), reader.ticks()));
            // 冷却每个 tick 减一
            int remaining = sample.player().cooldowns().get(group);
            assertTrue(remaining <= 1000 && remaining >= 1000 - (sample.ticks() - before), sample::toString);
            assertTrue(sample.player().meta(EntityMetaIndex.HAS_NO_GRAVITY));
            assertEquals(Pose.STANDING, sample.player().meta(EntityMetaIndex.POSE));
            assertEquals(List.of(EntityMetaIndex.HAS_NO_GRAVITY.index()), List.copyOf(sample.player().metadata().keySet()));
            // 冷却时间不大于 0 时删除这个冷却组
            peer.push(new ClientboundCooldownPacket(group, 0));
            peer.barrier();
            assertFalse(TestViews.of(bot).player().cooldowns().containsKey(group));
        });
    }
}
