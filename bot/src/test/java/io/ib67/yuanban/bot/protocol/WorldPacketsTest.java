package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.ChunkBuilder;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.chat.LastSeenTracker;
import io.ib67.yuanban.bot.chat.Signature;
import io.ib67.yuanban.bot.data.EntityTypeData;
import io.ib67.yuanban.bot.observe.BlockEntityChanged;
import io.ib67.yuanban.bot.observe.BossBarChanged;
import io.ib67.yuanban.bot.observe.BotTicked;
import io.ib67.yuanban.bot.observe.EntityDamaged;
import io.ib67.yuanban.bot.observe.EntityStatusReceived;
import io.ib67.yuanban.bot.observe.PlayerMoved;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import io.ib67.yuanban.bot.view.AbilitiesSnapshot;
import io.ib67.yuanban.bot.view.ChatKind;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntityMeta;
import io.ib67.yuanban.bot.view.EntityMetaIndex;
import io.ib67.yuanban.bot.view.HudSnapshot;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarDivision;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatFilterType;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatType;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatTypeDecoration;
import org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkBiomeData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.CollisionRule;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.NameTagVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ObjectiveAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreType;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDeleteChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTickingStepPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatAckPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 游戏阶段的数据包对模型的修改，以及原版客户端视为错误或者忽略的输入。
 */
class WorldPacketsTest {
    private static void withBot(Pos spawn, TestServer.ChunkConsumer chunks, BiConsumer<TestPeer, Bot> test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "world", _ -> {
            });
            TestServer.join(peer, spawn, GameMode.SURVIVAL, chunks);
            test.accept(peer, bot);
        }
    }

    private static void withBot(BiConsumer<TestPeer, Bot> test) {
        withBot(new Pos(0.5, 0, 0.5), (_, _, _) -> {
        }, test);
    }

    @Test
    void updateSectionBlocksDecodesNegativeSectionCoordinates() {
        withBot(new Pos(-8.5, 0, -8.5), (_, _, _) -> {
        }, (peer, bot) -> {
            // 区段 (-1, -1, -1)：方块 (-16, -16, -1) 到 (-1, -1, -16)。按协议的格式写出，由 MCProtocolLib 解码：
            // 区段坐标 x 为高 22 位，z 为中间 22 位，y 为低 20 位；每项为状态编号左移 12 位加上区段内坐标
            var gold = ((long) Block.GOLD_BLOCK.stateId() << 12) | (0 << 8) | (15 << 4) | 0;
            var diamond = ((long) Block.DIAMOND_BLOCK.stateId() << 12) | (15 << 8) | (0 << 4) | 15;
            var buffer = Unpooled.buffer();
            buffer.writeLong(((long) (-1 & 0x3FFFFF) << 42) | ((long) (-1 & 0x3FFFFF) << 20) | (-1 & 0xFFFFF));
            MinecraftTypes.writeVarInt(buffer, 2);
            MinecraftTypes.writeVarLong(buffer, gold);
            MinecraftTypes.writeVarLong(buffer, diamond);
            var packet = new ClientboundSectionBlocksUpdatePacket(buffer);
            buffer.release();
            peer.push(packet);
            peer.barrier();
            assertEquals(Block.GOLD_BLOCK, bot.view().block(-16, -16, -1));
            assertEquals(Block.DIAMOND_BLOCK, bot.view().block(-1, -1, -16));
        });
    }

    @Test
    void chunkBiomesReplaceTheBiomesOfEverySection() {
        withBot((peer, bot) -> {
            var biomeCount = TestServer.biomeCount();
            var data = ChunkBuilder.overworld().biome(3).biomeData(biomeCount);
            peer.push(new ClientboundChunksBiomesPacket(List.of(new ChunkBiomeData(-1, 0, data))));
            peer.barrier();
            var instance = (BotInstance) bot;
            assertEquals(3, (int) bot.inspect(_ -> instance.state().world().biome(-5, 70, 3)));
            assertEquals(0, (int) bot.inspect(_ -> instance.state().world().biome(5, 70, 3)));
        });
    }

    @Test
    void chunkBlockEntitiesKeepTheNbtFromThePacket() {
        var nbt = NbtMap.builder().putString("CustomName", "box").build();
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(2, 0, 2, Block.CHEST).blockEntity(2, 0, 2, BlockEntityType.CHEST, nbt);
            }
        }, (_, bot) -> {
            // 方块不带 NBT，方块实体的 NBT 单独读取
            assertNull(bot.view().block(2, 0, 2).nbt());
            assertEquals(nbt, bot.view().blockEntity(2, 0, 2));
        });
    }

    @Test
    void blockEntityDataNeedsABlockEntityOfTheSameType() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(2, 0, 2, Block.CHEST);
            }
        }, (peer, bot) -> {
            var changes = bot.observe(Observers.observations(BlockEntityChanged.class));
            var named = NbtMap.builder().putString("CustomName", "\"box\"").build();
            var chest = new Vec(2, 0, 2);
            // 原版 handleBlockEntityData 只应用到 getBlockEntity(pos, type) 存在的方块实体：
            // 类型不同、方块没有方块实体、区块没有加载、超出维度高度时忽略
            peer.push(new ClientboundBlockEntityDataPacket(Vector3i.from(2, 0, 2), BlockEntityType.SIGN, named));
            peer.push(new ClientboundBlockEntityDataPacket(Vector3i.from(2, -1, 2), BlockEntityType.CHEST, named));
            peer.push(new ClientboundBlockEntityDataPacket(Vector3i.from(16 * 4 + 2, 0, 2), BlockEntityType.CHEST, named));
            peer.push(new ClientboundBlockEntityDataPacket(Vector3i.from(2, 320, 2), BlockEntityType.CHEST, named));
            peer.barrier();
            assertNull(bot.view().blockEntity(chest));
            peer.push(new ClientboundBlockEntityDataPacket(Vector3i.from(2, 0, 2), BlockEntityType.CHEST, named));
            peer.barrier();
            assertEquals(named, bot.view().blockEntity(chest));
            changes.close();
            assertEquals(List.of(chest), changes.observer().observations().stream().map(BlockEntityChanged::pos).toList());
        });
    }

    private static ClientboundBossEventPacket addBossBar(UUID id) {
        return new ClientboundBossEventPacket(id, Component.text("boss"), 1f, BossBarColor.RED, BossBarDivision.NONE,
                false, false, false);
    }

    @Test
    void updatingAnUnknownBossBarEndsTheBot() {
        withBot((peer, bot) -> {
            var known = UUID.randomUUID();
            peer.push(addBossBar(known));
            peer.push(new ClientboundBossEventPacket(known, 0.5f));
            peer.barrier();
            assertEquals(0.5f, bot.view().hud().bossBars().get(known).health());
            // 原版客户端读取不存在的条目时出错并断开连接
            peer.push(new ClientboundBossEventPacket(UUID.randomUUID(), 0.5f));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    private static void waitTicks(TestPeer peer, int ticks) {
        var mark = peer.mark();
        var deadline = System.nanoTime() + TestServer.TIMEOUT.toNanos();
        while (peer.sentSince(mark, ServerboundClientTickEndPacket.class).size() < ticks) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + ticks + " ticks");
            }
            Thread.onSpinWait();
        }
    }

    @Test
    void frozenWorldsAdvanceTimeAndTheBorderOnlyForStepTicks() {
        withBot((peer, bot) -> {
            var instance = (BotInstance) bot;
            Supplier<Long> age = () -> bot.inspect(_ -> instance.state().world().worldAge());
            Supplier<Double> diameter = () -> bot.inspect(_ -> instance.state().world().border().toWorldBorder().diameter());
            // 先冻结再开始插值：两个数据包之间即使有 tick，世界边框也不推进
            peer.push(new ClientboundTickingStatePacket(20, true), new ClientboundSetTimePacket(1000, Map.of()),
                    new ClientboundSetBorderLerpSizePacket(100, 50, 1000));
            peer.barrier();
            var frozenAge = age.get();
            var frozenDiameter = diameter.get();
            // 原版 ClientLevel.tick 只在 TickRateManager.runsNormally 时推进世界边框与时间
            waitTicks(peer, 5);
            assertEquals(frozenAge, age.get());
            assertEquals(frozenDiameter, diameter.get());
            // Step Tick 给出几步就正常运行几个 tick（原版 TickRateManager.tick 每次减一）
            peer.push(new ClientboundTickingStepPacket(3));
            peer.barrier();
            waitTicks(peer, 6);
            assertEquals(frozenAge + 3, age.get());
            // 世界边框推进了 3 次；插值期间范围使用上一 tick 的大小（原版 MovingBorderExtent.previousSize），即推进 2 次的大小
            assertEquals(frozenDiameter - 2 * 0.05, diameter.get(), 1e-9);
        });
    }

    /**
     * 移动步骤（{@code PlayerMoved}）或 tick 结束（{@code BotTicked}）时读到的 World Age、时钟与世界边框的直径
     */
    private record TickSample(boolean ended, long worldAge, double clock, double border) {
    }

    @Test
    void theBorderAndTheClocksAdvanceAfterTheMovementStep() {
        // 出生在高处，每个 tick 都在下落，产生 PlayerMoved
        withBot(new Pos(0.5, 200, 0.5), (_, _, _) -> {
        }, (peer, bot) -> {
            var instance = (BotInstance) bot;
            var overworldClock = Key.key("overworld");
            var clockId = TestServer.registryId(SyncedRegistries.WORLD_CLOCK, overworldClock);
            peer.push(new ClientboundSetTimePacket(1000, Map.of(clockId, new ClockNetworkState(0, 0f, 1f))),
                    new ClientboundSetBorderLerpSizePacket(1000, 0, 1000));
            peer.barrier();
            // 插值开始之后的第一个 tick 范围仍为起始大小，之后每个 tick 都缩小
            waitTicks(peer, 2);
            var samples = new CopyOnWriteArrayList<TickSample>();
            Function<Boolean, TickSample> sample = ended -> new TickSample(ended, instance.state().world().worldAge(),
                    instance.state().clocks().clocks().get(overworldClock), instance.state().world().border().toWorldBorder().diameter());
            var moved = bot.observe(Observers.listener(PlayerMoved.class, _ -> samples.add(sample.apply(false))));
            var ticked = bot.observe(Observers.listener(BotTicked.class, _ -> samples.add(sample.apply(true))));
            waitTicks(peer, 5);
            moved.close();
            ticked.close();
            // 原版 Minecraft.tick：level.tickEntities（本地玩家的移动）之后才调用 ClientLevel.tick（世界边框、World Age 与时钟），
            // 所以移动步骤读到的是上一个 tick 结束时的值，这个 tick 结束时三者都已推进
            var checked = 0;
            for (int i = 0; i + 2 < samples.size(); i++) {
                var previous = samples.get(i);
                var during = samples.get(i + 1);
                var after = samples.get(i + 2);
                if (previous.ended() && !during.ended() && after.ended()) {
                    assertEquals(previous.worldAge(), during.worldAge());
                    assertEquals(previous.clock(), during.clock());
                    assertEquals(previous.border(), during.border());
                    assertEquals(during.worldAge() + 1, after.worldAge());
                    assertEquals(during.clock() + 1, after.clock());
                    assertTrue(after.border() < during.border(), samples::toString);
                    checked++;
                }
            }
            assertTrue(checked >= 3, samples::toString);
        });
    }

    @Test
    void rainAndThunderLevelsAreLimitedToZeroAndOne() {
        withBot((peer, bot) -> {
            var instance = (BotInstance) bot;
            // 按协议的格式写出 Game Event 7、8，由 MCProtocolLib 解码：超出 [0, 1] 的强度被限制到这个范围，
            // 与原版 setRainLevel、setThunderLevel 相同
            peer.push(gameEvent(GameEvent.RAIN_LEVEL_CHANGE, 1.5f), gameEvent(GameEvent.THUNDER_LEVEL_CHANGE, -0.5f));
            peer.barrier();
            assertEquals(1f, (float) bot.inspect(_ -> instance.state().world().weather().rainLevel()));
            assertEquals(0f, (float) bot.inspect(_ -> instance.state().world().weather().thunderLevel()));
            peer.push(gameEvent(GameEvent.RAIN_LEVEL_CHANGE, 0.25f), gameEvent(GameEvent.THUNDER_LEVEL_CHANGE, 0.75f));
            peer.barrier();
            assertEquals(0.25f, (float) bot.inspect(_ -> instance.state().world().weather().rainLevel()));
            assertEquals(0.75f, (float) bot.inspect(_ -> instance.state().world().weather().thunderLevel()));
        });
    }

    private static ClientboundGameEventPacket gameEvent(GameEvent event, float value) {
        var buffer = Unpooled.buffer();
        buffer.writeByte(event.ordinal());
        buffer.writeFloat(value);
        var packet = new ClientboundGameEventPacket(buffer);
        buffer.release();
        return packet;
    }

    /**
     * 玩家列表的一项；username 为 null 时没有档案（不带 ADD_PLAYER 时 MCProtocolLib 不读取档案）
     */
    private static PlayerListEntry entry(UUID uuid, @Nullable String username, boolean listed, int latency, GameMode gameMode,
                                         int listOrder) {
        return new PlayerListEntry(uuid, username == null ? null : new GameProfile(uuid, username), listed, latency, gameMode,
                null, true, listOrder, null, 0, null, null);
    }

    private static ClientboundPlayerInfoUpdatePacket playerInfo(EnumSet<PlayerListEntryAction> actions, PlayerListEntry... entries) {
        return new ClientboundPlayerInfoUpdatePacket(actions, entries);
    }

    @Test
    void becomingASpectatorThroughThePlayerListStopsVerticalMotion() {
        withBot((peer, bot) -> {
            // 暂停移动：速度只由数据包改变
            bot.suspendMovement();
            bot.await(state -> state.movementSuspended(), TestServer.TIMEOUT);
            var self = bot.inspect(state -> state.player().uuid());
            var velocity = Vector3d.from(0.25, 0.5, 0);
            peer.push(new ClientboundSetEntityMotionPacket(TestServer.ENTITY_ID, velocity));
            peer.push(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER, PlayerListEntryAction.UPDATE_GAME_MODE),
                    entry(self, "world", true, 0, GameMode.SPECTATOR, 0)));
            peer.barrier();
            // 原版 LocalPlayer.onGameModeChanged：玩家列表中的游戏模式（新条目为生存模式）改成旁观模式时竖直速度清零
            assertEquals(new Vec(0.25, 0, 0), bot.view().player().velocity());
            assertEquals(GameMode.SPECTATOR, bot.view().player().gameMode());
            // 列表中已经是旁观模式：不再清零
            peer.push(new ClientboundSetEntityMotionPacket(TestServer.ENTITY_ID, velocity));
            peer.push(playerInfo(EnumSet.of(PlayerListEntryAction.UPDATE_GAME_MODE), entry(self, null, true, 0, GameMode.SPECTATOR, 0)));
            peer.barrier();
            assertEquals(new Vec(0.25, 0.5, 0), bot.view().player().velocity());
        });
    }

    private static ClientboundSetPlayerTeamPacket createTeam(String name, String displayName, String... members) {
        return new ClientboundSetPlayerTeamPacket(name, Component.text(displayName), Component.empty(), Component.empty(),
                false, false, NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, null, members);
    }

    private static ClientboundSetPlayerTeamPacket teamMembers(String name, TeamAction action, String... members) {
        return new ClientboundSetPlayerTeamPacket(name, action, members);
    }

    @Test
    void joiningATeamLeavesThePreviousOne() {
        withBot((peer, bot) -> {
            peer.push(createTeam("red", "Red", "alice", "bob"));
            // 原版 Scoreboard.addPlayerToTeam：一个名称只属于一个队伍，加入新队伍时先离开原来的队伍
            peer.push(createTeam("blue", "Blue", "alice"));
            peer.push(teamMembers("blue", TeamAction.ADD_PLAYER, "bob"));
            peer.barrier();
            var teams = bot.view().hud().teams();
            assertTrue(teams.get("red").members().isEmpty(), teams::toString);
            assertEquals(Set.of("alice", "bob"), teams.get("blue").members());
            // 原版 Scoreboard.addPlayerTeam：创建已经存在的队伍时沿用它与它的成员，更新显示设置
            peer.push(createTeam("blue", "Navy", "carol"));
            peer.barrier();
            var blue = bot.view().hud().teams().get("blue");
            assertEquals(Set.of("alice", "bob", "carol"), blue.members());
            assertEquals(Component.text("Navy"), blue.displayName());
        });
    }

    @Test
    void removingAPlayerThatIsNotInTheTeamEndsTheBot() {
        withBot((peer, bot) -> {
            peer.push(createTeam("red", "Red", "alice"), createTeam("blue", "Blue", "bob"));
            peer.push(teamMembers("red", TeamAction.REMOVE_PLAYER, "alice"));
            // 不存在的队伍：原版 handleSetPlayerTeamPacket 什么都不做
            peer.push(teamMembers("green", TeamAction.REMOVE_PLAYER, "bob"));
            peer.barrier();
            assertTrue(bot.view().hud().teams().get("red").members().isEmpty());
            assertEquals(Set.of("bob"), bot.view().hud().teams().get("blue").members());
            // 原版 Scoreboard.removePlayerFromTeam：成员不在这个队伍中时抛出异常，客户端断开连接
            peer.push(teamMembers("red", TeamAction.REMOVE_PLAYER, "bob"));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    private static ClientboundSetObjectivePacket objective(String name, ObjectiveAction action) {
        return action == ObjectiveAction.REMOVE ? new ClientboundSetObjectivePacket(name)
                : new ClientboundSetObjectivePacket(name, action, Component.text(name), ScoreType.INTEGER, null);
    }

    @Test
    void objectivesAndDisplaySlotsFollowTheVanillaScoreboard() {
        withBot((peer, bot) -> {
            peer.push(objective("kills", ObjectiveAction.ADD), new ClientboundSetDisplayObjectivePacket(ScoreboardPosition.SIDEBAR, "kills"),
                    new ClientboundSetDisplayObjectivePacket(ScoreboardPosition.BELOW_NAME, "kills"),
                    new ClientboundSetDisplayObjectivePacket(ScoreboardPosition.PLAYER_LIST, "missing"));
            // 原版 handleSetDisplayObjective：没有这个目标时清空这个显示位置
            peer.push(new ClientboundSetDisplayObjectivePacket(ScoreboardPosition.BELOW_NAME, "missing"));
            peer.barrier();
            assertEquals(Map.of(ScoreboardPosition.SIDEBAR, "kills"), bot.view().hud().displaySlots());
            // 原版 Scoreboard.removeObjective：删除目标时清空指向它的显示位置，重新创建之后也不再指向它
            peer.push(objective("kills", ObjectiveAction.REMOVE), objective("kills", ObjectiveAction.ADD));
            peer.barrier();
            assertEquals(Map.of(), bot.view().hud().displaySlots());
            // 原版 Scoreboard.addObjective：创建已经存在的目标时抛出异常，客户端断开连接
            peer.push(objective("kills", ObjectiveAction.ADD));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void addingAListedPlayerAgainKeepsItsEntry() {
        withBot((peer, bot) -> {
            var friend = UUID.randomUUID();
            var actions = EnumSet.of(PlayerListEntryAction.ADD_PLAYER, PlayerListEntryAction.UPDATE_LATENCY);
            peer.push(playerInfo(actions, entry(friend, "friend", true, 10, GameMode.SURVIVAL, 0)));
            // 原版 handlePlayerInfoUpdate 用 putIfAbsent 加入：保留原来的条目，只应用同一数据包中的其它更新
            peer.push(playerInfo(actions, entry(friend, "renamed", true, 99, GameMode.SURVIVAL, 0)));
            peer.barrier();
            var entry = bot.view().hud().playerList().get(friend);
            assertEquals("friend", entry.username());
            assertEquals(99, entry.latency());
        });
    }

    @Test
    void newPlayerListEntriesStartInSurvivalMode() {
        withBot((peer, bot) -> {
            var friend = UUID.randomUUID();
            peer.push(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER), entry(friend, "friend", true, 10, GameMode.CREATIVE, 3)));
            peer.barrier();
            // 原版 PlayerInfo 的初始值：生存模式，延迟 0，不显示在列表中，排序 0；没有对应的更新时不取条目中的值
            assertEquals(new HudSnapshot.PlayerListEntry(friend, "friend", GameMode.SURVIVAL, 0, false, null, 0),
                    bot.view().hud().playerList().get(friend));
        });
    }

    @Test
    void removingAnUnknownBossBarChangesNothing() {
        withBot((peer, bot) -> {
            var changes = bot.observe(Observers.observations(BossBarChanged.class));
            var known = UUID.randomUUID();
            // 原版 BossHealthOverlay 的 remove 只是从表中删除：移除没有添加过的 Boss Bar 什么都不做
            peer.push(new ClientboundBossEventPacket(UUID.randomUUID()));
            peer.push(addBossBar(known));
            peer.push(new ClientboundBossEventPacket(known));
            peer.barrier();
            changes.close();
            var observed = changes.observer().observations();
            assertEquals(List.of(known, known), observed.stream().map(BossBarChanged::id).toList());
            assertNull(observed.getLast().now());
            assertTrue(bot.view().hud().bossBars().isEmpty());
        });
    }

    @Test
    void playerInfoUpdatesForUnknownPlayersAreIgnored() {
        withBot((peer, bot) -> {
            var stranger = UUID.randomUUID();
            var self = bot.inspect(state -> state.player().uuid());
            peer.push(playerInfo(EnumSet.of(PlayerListEntryAction.UPDATE_LATENCY, PlayerListEntryAction.UPDATE_GAME_MODE),
                    entry(stranger, null, true, 50, GameMode.CREATIVE, 0),
                    entry(self, null, true, 50, GameMode.CREATIVE, 0)));
            peer.barrier();
            assertFalse(bot.view().hud().playerList().containsKey(stranger));
            // 自己不在玩家列表中时，游戏模式的更新同样被忽略
            assertEquals(GameMode.SURVIVAL, bot.view().player().gameMode());
            peer.push(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER, PlayerListEntryAction.UPDATE_GAME_MODE),
                    entry(self, "world", true, 0, GameMode.CREATIVE, 0)));
            peer.barrier();
            assertTrue(bot.view().hud().playerList().containsKey(self));
            assertEquals(GameMode.CREATIVE, bot.view().player().gameMode());
        });
    }

    /**
     * 聊天测试中玩家聊天的发送者
     */
    private static final UUID SENDER = UUID.nameUUIDFromBytes("sender".getBytes());

    /**
     * 进入世界之后把 {@link #SENDER} 加入玩家列表：原版只显示玩家列表中的玩家发送的消息
     */
    private static void withChatBot(BiConsumer<TestPeer, Bot> test) {
        withBot((peer, bot) -> {
            peer.push(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER), entry(SENDER, "sender", false, 0, GameMode.SURVIVAL, 0)));
            test.accept(peer, bot);
        });
    }

    /**
     * 注册表中的聊天类型：数据包中的编号为 {@code minecraft:chat_type} 注册表中的编号（MCProtocolLib 已经减去 1）
     */
    private static Holder<ChatType> chatType(String name) {
        return Holder.ofId(TestServer.registryId(SyncedRegistries.CHAT_TYPE, Key.key(name)));
    }

    /**
     * 已读列表中按编号引用签名缓存中的签名（MCProtocolLib 已经把编号减去 1）
     */
    private static MessageSignature cached(int id) {
        return new MessageSignature(id, null);
    }

    /**
     * 完整给出的签名：MCProtocolLib 以编号 -1 表示
     */
    private static MessageSignature full(byte[] signature) {
        return new MessageSignature(-1, signature);
    }

    private static ClientboundPlayerChatPacket chat(int globalIndex, List<MessageSignature> lastSeen) {
        return chat(globalIndex, lastSeen, new byte[256]);
    }

    private static ClientboundPlayerChatPacket chat(int globalIndex, List<MessageSignature> lastSeen, byte[] signature) {
        return chat(globalIndex, lastSeen, signature, "hi", null, ChatFilterType.PASS_THROUGH);
    }

    private static ClientboundPlayerChatPacket chat(int globalIndex, List<MessageSignature> lastSeen, byte @Nullable [] signature,
                                                    String content, @Nullable Component unsignedContent, ChatFilterType filter) {
        return new ClientboundPlayerChatPacket(globalIndex, SENDER, 0, signature, content, System.currentTimeMillis(), 0,
                lastSeen, unsignedContent, filter, chatType("chat"), Component.text("sender"), null);
    }

    @Test
    void unknownMessageSignatureIdsEndTheBot() {
        withChatBot((peer, bot) -> {
            // 第一条消息把自己的签名放进缓存的编号 0
            peer.push(chat(0, List.of()));
            peer.push(chat(1, List.of(cached(0))));
            peer.barrier();
            assertEquals(2, bot.view().chat().size());
            // 缓存中没有编号 5 的签名：原版客户端以无效数据包断开连接
            peer.push(chat(2, List.of(cached(5))));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
        });
        withBot((peer, bot) -> {
            peer.push(new ClientboundDeleteChatPacket(cached(7)));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
        });
    }

    @Test
    void playerChatGlobalIndicesMustBeConsecutive() {
        withChatBot((peer, bot) -> {
            peer.push(chat(0, List.of()), chat(1, List.of()));
            peer.barrier();
            assertEquals(2, bot.view().chat().size());
            // 原版 handlePlayerChat：全局编号不连续时断开连接
            peer.push(chat(3, List.of()));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void deletingAMessageNotYetAcknowledgedRemovesItFromTheSeenMessages() {
        withChatBot((peer, bot) -> {
            var first = filled(1);
            var second = filled(2);
            peer.push(chat(0, List.of(), first), chat(1, List.of(), second));
            // 原版 handleDeleteChat 调用 ignorePending：第一条还没有随 Chat Message 告知服务端，不再计入已读
            peer.push(new ClientboundDeleteChatPacket(full(first)));
            peer.barrier();
            var mark = peer.mark();
            assertTrue(bot.chat("seen").await(TestServer.TIMEOUT));
            var message = peer.awaitSent(mark, ServerboundChatPacket.class, TestServer.TIMEOUT);
            assertEquals(2, message.getOffset());
            assertEquals(1, message.getAcknowledgedMessages().cardinality());
            assertEquals(LastSeenTracker.checksum(List.of(new Signature(second))), message.getChecksum());
            // 已经告知过的消息被删除时保持已读
            peer.push(new ClientboundDeleteChatPacket(full(second)));
            peer.barrier();
            mark = peer.mark();
            assertTrue(bot.chat("still seen").await(TestServer.TIMEOUT));
            assertEquals(1, peer.awaitSent(mark, ServerboundChatPacket.class, TestServer.TIMEOUT).getAcknowledgedMessages().cardinality());
        });
    }

    @Test
    void startConfigurationAcknowledgesPendingMessagesFirst() {
        withChatBot((peer, bot) -> {
            peer.push(chat(0, List.of()));
            peer.barrier();
            var mark = peer.mark();
            peer.push(new ClientboundStartConfigurationPacket());
            var ack = peer.awaitSent(mark, ServerboundConfigurationAcknowledgedPacket.class, TestServer.TIMEOUT);
            // 原版 handleConfigurationStart：未确认的消息数大于 0 时先发送 Acknowledge Message
            var sent = peer.sentSince(mark);
            assertEquals(new ServerboundChatAckPacket(1), sent.get(sent.indexOf(ack) - 1));
            assertEquals(1, peer.sentSince(mark, ServerboundChatAckPacket.class).size());
        });
    }

    private static byte[] filled(int value) {
        var bytes = new byte[256];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    @Test
    void loginRestartsTheMessageSignatureCache() {
        withChatBot((peer, bot) -> {
            peer.push(chat(0, List.of()));
            peer.push(chat(1, List.of(cached(0))));
            peer.barrier();
            // 原版 handleLogin 新建 MessageSignatureCache：之前的签名不再能按编号引用
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.push(chat(0, List.of(cached(0))));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void fullyFilteredPlayerChatIsNotShown() {
        withChatBot((peer, bot) -> {
            var shown = filled(1);
            peer.push(chat(0, List.of(), shown, "hello", null, ChatFilterType.PASS_THROUGH));
            // 原版 ChatListener.showMessageToPlayer：完全过滤的消息不显示
            peer.push(chat(1, List.of(), filled(2), "secret", null, ChatFilterType.FULLY_FILTERED));
            peer.barrier();
            var lines = bot.view().chat();
            assertEquals(1, lines.size());
            assertEquals(Component.text("hello"), lines.getFirst().message());
            // 原版 markMessageAsProcessed(signature, false)：没有显示的消息只计入未确认的消息数，不计入已读
            var mark = peer.mark();
            assertTrue(bot.chat("seen").await(TestServer.TIMEOUT));
            var message = peer.awaitSent(mark, ServerboundChatPacket.class, TestServer.TIMEOUT);
            assertEquals(2, message.getOffset());
            assertEquals(1, message.getAcknowledgedMessages().cardinality());
            assertEquals(LastSeenTracker.checksum(List.of(new Signature(shown))), message.getChecksum());
        });
    }

    @Test
    void partiallyFilteredPlayerChatEndsTheBot() {
        withChatBot((peer, bot) -> {
            // 待办 T2：MCProtocolLib 解码部分过滤的消息时不读取过滤标记，本库不支持这种消息，bot 因异常结束
            peer.push(chat(0, List.of(), filled(1), "hello", null, ChatFilterType.PARTIALLY_FILTERED));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(UnsupportedOperationException.class, bot.failure());
        });
    }

    @Test
    void chatFromPlayersOutsideThePlayerListShowsAValidationError() {
        withBot((peer, bot) -> {
            var signature = filled(3);
            peer.push(chat(0, List.of(), signature));
            peer.barrier();
            // 原版 handleChatMessageError：不显示这条消息，代替它显示 chat.validation_error
            var line = bot.view().chat().getFirst();
            assertEquals(Component.translatable("chat.validation_error", NamedTextColor.RED, TextDecoration.ITALIC), line.message());
            assertEquals(SENDER, line.sender());
            // 代替显示的文本没有签名，Delete Message 不删除它
            peer.push(new ClientboundDeleteChatPacket(full(signature)));
            peer.barrier();
            assertEquals(1, bot.view().chat().size());
            // 签名只计入未确认的消息数，不计入已读（原版 markMessageAsProcessed(signature, false)）
            var mark = peer.mark();
            assertTrue(bot.chat("seen").await(TestServer.TIMEOUT));
            var message = peer.awaitSent(mark, ServerboundChatPacket.class, TestServer.TIMEOUT);
            assertEquals(1, message.getOffset());
            assertEquals(0, message.getAcknowledgedMessages().cardinality());
        });
    }

    @Test
    void chatLinesRecordTheChatTypeAndTheNames() {
        withChatBot((peer, bot) -> {
            peer.push(new ClientboundPlayerChatPacket(0, SENDER, 0, null, "hi", System.currentTimeMillis(), 0, List.of(), null,
                    ChatFilterType.PASS_THROUGH, chatType("msg_command_incoming"), Component.text("alice"), Component.text("bob")));
            peer.push(new ClientboundDisguisedChatPacket(Component.text("disguised"), chatType("say_command"), Component.text("server"), null));
            peer.push(new ClientboundSystemChatPacket(Component.text("system"), false));
            peer.barrier();
            assertEquals(List.of(
                    new ChatLine(ChatKind.PLAYER, SENDER, Key.key("msg_command_incoming"), null, Component.text("alice"),
                            Component.text("bob"), Component.text("hi"), "hi"),
                    new ChatLine(ChatKind.DISGUISED, null, Key.key("say_command"), null, Component.text("server"), null,
                            Component.text("disguised"), "disguised"),
                    new ChatLine(ChatKind.SYSTEM, null, null, null, null, null, Component.text("system"), "system")), bot.view().chat());
        });
        withBot((peer, bot) -> {
            // 编号不在 minecraft:chat_type 注册表中：原版解码时出错，客户端断开连接
            var unknown = TestServer.registryEntries(SyncedRegistries.CHAT_TYPE).size();
            peer.push(new ClientboundDisguisedChatPacket(Component.text("unknown"), Holder.ofId(unknown), Component.text("server"), null));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void chatTypesDefinedInThePacketAreKeptInTheChatLine() {
        var decoration = new ChatType.ChatTypeDecorationImpl("chat.type.text",
                List.of(ChatTypeDecoration.Parameter.SENDER, ChatTypeDecoration.Parameter.CONTENT), NbtMap.EMPTY);
        var inline = new ChatType(decoration, decoration);
        withChatBot((peer, bot) -> {
            // 聊天类型随数据包给出定义时没有注册表中的名称
            peer.push(new ClientboundPlayerChatPacket(0, SENDER, 0, null, "hi", System.currentTimeMillis(), 0, List.of(), null,
                    ChatFilterType.PASS_THROUGH, Holder.ofCustom(inline), Component.text("alice"), null));
            peer.push(new ClientboundDisguisedChatPacket(Component.text("disguised"), Holder.ofCustom(inline), Component.text("server"), null));
            peer.barrier();
            var lines = bot.view().chat();
            assertEquals(2, lines.size());
            for (var line : lines) {
                assertNull(line.chatType());
                assertEquals(inline, line.inlineChatType());
            }
            assertEquals(new ChatLine(ChatKind.PLAYER, SENDER, null, inline, Component.text("alice"), null, Component.text("hi"), "hi"),
                    lines.getFirst());
        });
    }

    @Test
    void newPlayersUseTheVanillaPlayerAttributes() {
        withBot((_, bot) -> {
            var instance = (BotInstance) bot;
            // 原版 Player.createAttributes：移动速度为 (double) 0.1F，攻击伤害为 1
            assertEquals((double) 0.1F, (double) bot.inspect(_ -> instance.state().player().attributes()
                    .value(AttributeType.Builtin.MOVEMENT_SPEED)));
            assertEquals(1.0, (double) bot.inspect(_ -> instance.state().player().attributes().value(AttributeType.Builtin.ATTACK_DAMAGE)));
        });
    }

    private static ClientboundGameEventPacket gameMode(GameMode gameMode) {
        return new ClientboundGameEventPacket(GameEvent.CHANGE_GAME_MODE, gameMode);
    }

    @Test
    void gameModeChangesSetAbilitiesLikeTheVanillaClient() {
        withBot((peer, bot) -> {
            // 原版 GameType.updatePlayerAbilities：创造模式可以飞行、瞬间破坏、无敌；旁观模式可以飞行、无敌并且正在飞行；
            // 生存与冒险模式这几项都为 false；冒险模式与旁观模式不能建造
            peer.push(gameMode(GameMode.CREATIVE));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(true, false, true, true, true, 0.05f, 0.1f), bot.view().player().abilities());
            peer.push(gameMode(GameMode.SPECTATOR));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(true, true, true, false, false, 0.05f, 0.1f), bot.view().player().abilities());
            peer.push(gameMode(GameMode.SURVIVAL));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(false, false, false, false, true, 0.05f, 0.1f), bot.view().player().abilities());
            peer.push(gameMode(GameMode.ADVENTURE));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(false, false, false, false, false, 0.05f, 0.1f), bot.view().player().abilities());
        });
    }

    @Test
    void entityEventsAreObserved() {
        withBot((peer, bot) -> {
            var events = bot.observe(Observers.observations(EntityStatusReceived.class));
            peer.push(spawn(100, EntityType.PIG), new ClientboundEntityEventPacket(100, EntityEvent.LIVING_DEATH));
            peer.barrier();
            events.close();
            assertEquals(List.of(new EntityStatusReceived(100, EntityEvent.LIVING_DEATH)), events.observer().observations());
        });
    }

    @Test
    void damageEventsNameTheDamageTypeAndTheSources() {
        withBot((peer, bot) -> {
            var damages = bot.observe(Observers.observations(EntityDamaged.class));
            var generic = TestServer.registryId(SyncedRegistries.DAMAGE_TYPE, Key.key("generic"));
            var arrow = TestServer.registryId(SyncedRegistries.DAMAGE_TYPE, Key.key("arrow"));
            // MCProtocolLib 以 -1 表示没有来源实体
            peer.push(new ClientboundDamageEventPacket(100, generic, -1, -1, null),
                    new ClientboundDamageEventPacket(100, arrow, 101, 102, Vector3d.from(1, 2, 3)));
            peer.barrier();
            damages.close();
            assertEquals(List.of(new EntityDamaged(100, Key.key("generic"), null, null, null),
                            new EntityDamaged(100, Key.key("arrow"), 101, 102, new Vec(1, 2, 3))),
                    damages.observer().observations());
            // 伤害类型的编号不在注册表中：原版解码时出错，客户端断开连接
            bot.observe(Observers.observations(EntityDamaged.class));
            var unknown = TestServer.registryEntries(SyncedRegistries.DAMAGE_TYPE).size();
            peer.push(new ClientboundDamageEventPacket(100, unknown, -1, -1, null));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    private static ClientboundAddEntityPacket spawn(int id, EntityType type) {
        return new ClientboundAddEntityPacket(id, UUID.randomUUID(), type, 2.5, 0, 2.5, 0, 0, 0);
    }

    @Test
    void passengersThatWouldFormACycleAreRejected() {
        withBot((peer, bot) -> {
            peer.push(spawn(100, EntityType.PIG), spawn(101, EntityType.PIG), spawn(102, EntityType.PIG));
            // 100 乘 101，101 乘 102；再让 102 乘 100 会形成循环，原版 startRiding 拒绝
            peer.push(new ClientboundSetPassengersPacket(101, new int[]{100}), new ClientboundSetPassengersPacket(102, new int[]{101}),
                    new ClientboundSetPassengersPacket(100, new int[]{102}));
            peer.barrier();
            assertNull(bot.view().entity(102).vehicle());
            assertEquals(List.of(), bot.view().entity(100).passengers());
            assertEquals(101, bot.view().entity(100).vehicle());
            // 乘坐关系的计算没有陷入循环：bot 照常 tick
            var ticks = bot.view().ticks();
            bot.await(state -> state.ticks() >= ticks + 3, TestServer.TIMEOUT);

            // 实体乘坐自己：原版之后的乘坐计算会无限循环，bot 因异常结束
            peer.push(new ClientboundSetPassengersPacket(102, new int[]{102}));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void passengersLeaveTheirOldVehicleAndUnknownOnesAreSkipped() {
        withBot((peer, bot) -> {
            peer.push(spawn(100, EntityType.PIG), spawn(101, EntityType.PIG), spawn(102, EntityType.PIG));
            peer.push(new ClientboundSetPassengersPacket(101, new int[]{100, 999}));
            peer.barrier();
            // 模型中没有的乘客编号被跳过
            assertEquals(List.of(100), bot.view().entity(101).passengers());
            // 100 改乘 102：先离开 101（原版 startRiding 中的 stopRiding）
            peer.push(new ClientboundSetPassengersPacket(102, new int[]{100}));
            peer.barrier();
            assertEquals(List.of(), bot.view().entity(101).passengers());
            assertEquals(List.of(100), bot.view().entity(102).passengers());
            assertEquals(102, bot.view().entity(100).vehicle());
        });
    }

    @Test
    void synchronizeVehiclePositionRotatesTheVelocityAndLimitsThePitch() {
        withBot((peer, bot) -> {
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.MINECART, 2.5, 0, 2.5,
                    Vector3d.from(0, 0, 1), 0, 0, 0));
            // 原版 PositionMoveRotation.calculateAbsolute：俯仰角从 0 变成 90，带 ROTATE_DELTA，速度先绕 x 轴旋转 -90 度，
            // (0, 0, 1) 变成 (0, -1, 0)
            peer.push(new ClientboundTeleportEntityPacket(100, Vector3d.ZERO, Vector3d.ZERO, 0, 90,
                    List.of(PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT,
                            PositionElement.DELTA_X, PositionElement.DELTA_Y, PositionElement.DELTA_Z, PositionElement.ROTATE_DELTA), true));
            peer.barrier();
            var cart = bot.view().entity(100);
            assertEquals(new Vec(2.5, 0, 2.5), cart.position().asVec());
            assertEquals(90, cart.position().pitch());
            assertEquals(0, cart.velocity().x(), 1e-6);
            assertEquals(-1, cart.velocity().y(), 1e-6);
            assertEquals(0, cart.velocity().z(), 1e-6);
            // 俯仰角限制到 [-90, 90]：相对的 60 加上原来的 90，以及绝对的 -120
            peer.push(new ClientboundTeleportEntityPacket(100, Vector3d.ZERO, Vector3d.ZERO, 0, 60,
                    List.of(PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT, PositionElement.X_ROT), true));
            peer.barrier();
            assertEquals(90, bot.view().entity(100).position().pitch());
            peer.push(new ClientboundTeleportEntityPacket(100, Vector3d.from(2.5, 0, 2.5), Vector3d.ZERO, 0, -120, List.of(), true));
            peer.barrier();
            assertEquals(-90, bot.view().entity(100).position().pitch());
        });
    }

    @Test
    void teleportEntityIgnoresTheVelocityAndLocallyControlledEntities() {
        withBot((peer, bot) -> {
            // 原版 handleEntityPositionSync：位置、朝向与是否着地取数据包的值，速度不变
            peer.push(new ClientboundAddEntityPacket(100, UUID.randomUUID(), EntityType.PIG, 2.5, 0, 2.5,
                    Vector3d.from(0, 0, 1), 0, 0, 0));
            peer.push(new ClientboundEntityPositionSyncPacket(100, Vector3d.from(5, 0, 5), Vector3d.from(1, 1, 1), 30, 10, true));
            peer.barrier();
            var pig = bot.view().entity(100);
            assertEquals(new Pos(5, 0, 5, 30, 10), pig.position());
            assertTrue(pig.onGround());
            assertEquals(new Vec(0, 0, 1), pig.velocity());

            // 本地玩家控制的船（原版 isLocalInstanceAuthoritative）不改变；下船之后照常应用
            var boat = new Pos(1.5, 0, 0.5);
            peer.push(new ClientboundAddEntityPacket(101, UUID.randomUUID(), EntityType.OAK_BOAT, boat.x(), boat.y(), boat.z(), 0, 0, 0),
                    new ClientboundSetPassengersPacket(101, new int[]{TestServer.ENTITY_ID}));
            peer.push(new ClientboundEntityPositionSyncPacket(101, Vector3d.from(9, 0, 9), Vector3d.ZERO, 45, 0, true));
            peer.barrier();
            assertEquals(boat, bot.view().entity(101).position());
            assertFalse(bot.view().entity(101).onGround());
            peer.push(new ClientboundSetPassengersPacket(101, new int[0]),
                    new ClientboundEntityPositionSyncPacket(101, Vector3d.from(9, 0, 9), Vector3d.ZERO, 45, 0, true));
            peer.barrier();
            assertEquals(new Pos(9, 0, 9, 45, 0), bot.view().entity(101).position());
        });
    }

    private static ClientboundSetEntityDataPacket metadata(int entityId, EntityMetadata<?, ?>... entries) {
        return new ClientboundSetEntityDataPacket(entityId, entries);
    }

    private static EntityMetadata<?, ?> flag(EntityMeta<Boolean> meta, boolean value) {
        return new BooleanEntityMetadata(meta.index(), MetadataTypes.BOOLEAN, value);
    }

    private static EntityMetadata<?, ?> pose(Pose pose) {
        return new ObjectEntityMetadata<>(EntityMetaIndex.POSE.index(), MetadataTypes.POSE, pose);
    }

    @Test
    void entityBoundingBoxesFollowBabiesAndFixedSizes() {
        withBot((peer, bot) -> {
            var scaled = List.of(new Attribute(AttributeType.Builtin.SCALE, 2));
            // 幼年僵尸（原版 Zombie.BABY_DIMENSIONS）；幼年牛按 Cow.BABY_DIMENSIONS 再乘以 scale；
            // 史莱姆不会是幼年（原版 canBeABaby 为 false），幼年标记被忽略
            peer.push(spawn(100, EntityType.ZOMBIE), metadata(100, flag(EntityMetaIndex.ZOMBIE_IS_BABY, true)));
            peer.push(spawn(101, EntityType.COW), metadata(101, flag(EntityMetaIndex.AGEABLE_IS_BABY, true)),
                    new ClientboundUpdateAttributesPacket(101, scaled));
            peer.push(spawn(102, EntityType.SLIME), metadata(102, flag(EntityMetaIndex.AGEABLE_IS_BABY, true)));
            // 幼年骆驼坐下（原版 Camel.BABY_SITTING_DIMENSIONS）
            peer.push(spawn(103, EntityType.CAMEL), metadata(103, flag(EntityMetaIndex.AGEABLE_IS_BABY, true), pose(Pose.SITTING)));
            // scale 为 2 的玩家：潜行的尺寸乘以 scale，死亡与睡觉为固定尺寸 0.2，不乘；睡觉的村民同样
            peer.push(spawn(104, EntityType.PLAYER), new ClientboundUpdateAttributesPacket(104, scaled),
                    metadata(104, pose(Pose.SNEAKING)));
            peer.push(spawn(105, EntityType.PLAYER), new ClientboundUpdateAttributesPacket(105, scaled),
                    metadata(105, pose(Pose.DYING)));
            peer.push(spawn(106, EntityType.VILLAGER), new ClientboundUpdateAttributesPacket(106, scaled),
                    metadata(106, pose(Pose.SLEEPING)));
            peer.barrier();
            var slimeWidth = EntityTypeData.width(EntityType.SLIME);
            assertEquals(new BoundingBox(0.49, 0.98, 0.49), bot.view().entity(100).boundingBox());
            assertEquals(new BoundingBox(0.9, 1.4, 0.9), bot.view().entity(101).boundingBox());
            assertEquals(new BoundingBox(slimeWidth, EntityTypeData.height(EntityType.SLIME), slimeWidth),
                    bot.view().entity(102).boundingBox());
            assertEquals(new BoundingBox(0.95, 0.425, 0.95), bot.view().entity(103).boundingBox());
            assertEquals(new BoundingBox(1.2, 3.0, 1.2), bot.view().entity(104).boundingBox());
            assertEquals(new BoundingBox(0.2, 0.2, 0.2), bot.view().entity(105).boundingBox());
            assertEquals(new BoundingBox(0.2, 0.2, 0.2), bot.view().entity(106).boundingBox());
        });
    }
}
