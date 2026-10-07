package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.ChunkBuilder;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.chat.LastSeenTracker;
import io.ib67.yuanban.bot.observe.BlockEntityChanged;
import io.ib67.yuanban.bot.observe.BossBarChanged;
import io.ib67.yuanban.bot.observe.BotTicked;
import io.ib67.yuanban.bot.observe.PlayerMoved;
import io.ib67.yuanban.bot.observe.builtin.Observers;
import io.ib67.yuanban.bot.scheduler.BotInstance;
import io.ib67.yuanban.bot.view.AbilitiesSnapshot;
import io.ib67.yuanban.bot.view.ChatKind;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.HudSnapshot;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.crypto.FilterMask;
import net.minestom.server.crypto.LastSeenMessages;
import net.minestom.server.crypto.MessageSignature;
import net.minestom.server.crypto.SignedMessageBody;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.message.ChatType;
import net.minestom.server.network.packet.client.play.ClientChatAckPacket;
import net.minestom.server.network.packet.client.play.ClientChatMessagePacket;
import net.minestom.server.network.packet.client.play.ClientConfigurationAckPacket;
import net.minestom.server.network.packet.client.play.ClientTickEndPacket;
import net.minestom.server.network.packet.server.play.BlockEntityDataPacket;
import net.minestom.server.network.packet.server.play.BossBarPacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.ChunkBiomesPacket;
import net.minestom.server.network.packet.server.play.DeleteChatPacket;
import net.minestom.server.network.packet.server.play.DisguisedChatPacket;
import net.minestom.server.network.packet.server.play.DisplayScoreboardPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.MultiBlockChangePacket;
import net.minestom.server.network.packet.server.play.PlayerChatMessagePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.ScoreboardObjectivePacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.SetTickStatePacket;
import net.minestom.server.network.packet.server.play.SetTimePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.StartConfigurationPacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.packet.server.play.TickStepPacket;
import net.minestom.server.network.packet.server.play.WorldBorderLerpSizePacket;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.clock.WorldClock;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
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
            // 区段 (-1, -1, -1)：方块 (-16, -16, -1) 到 (-1, -1, -16)；每项为状态编号左移 12 位加上区段内坐标
            var gold = ((long) Block.GOLD_BLOCK.stateId() << 12) | (0 << 8) | (15 << 4) | 0;
            var diamond = ((long) Block.DIAMOND_BLOCK.stateId() << 12) | (15 << 8) | (0 << 4) | 15;
            peer.push(new MultiBlockChangePacket(-1, -1, -1, new long[]{gold, diamond}));
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
            peer.push(new ChunkBiomesPacket(List.of(new ChunkBiomesPacket.ChunkBiomeData(-1, 0, data))));
            peer.barrier();
            var instance = (BotInstance) bot;
            assertEquals(3, (int) bot.inspect(_ -> instance.state().world().biome(-5, 70, 3)));
            assertEquals(0, (int) bot.inspect(_ -> instance.state().world().biome(5, 70, 3)));
        });
    }

    @Test
    void chunkBlockEntitiesKeepTheNbtSentToClients() {
        var handler = new BlockHandler() {
            @Override
            public Collection<Tag<?>> getBlockEntityTags() {
                return List.of(Tag.String("CustomName"));
            }

            @Override
            public Key getKey() {
                return Key.key("test:chest");
            }
        };
        var nbt = CompoundBinaryTag.builder().putString("CustomName", "box").putString("Secret", "server only").build();
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(2, 0, 2, Block.CHEST).blockEntity(2, 0, 2, Block.CHEST.withHandler(handler).withNbt(nbt));
            }
        }, (_, bot) -> assertEquals(CompoundBinaryTag.builder().putString("CustomName", "box").build(),
                bot.view().block(2, 0, 2).nbt()));
    }

    @Test
    void blockEntityDataNeedsABlockEntityOfTheSameType() {
        withBot(new Pos(0.5, 0, 0.5), (x, z, chunk) -> {
            if (x == 0 && z == 0) {
                chunk.set(2, 0, 2, Block.CHEST);
            }
        }, (peer, bot) -> {
            var changes = bot.observe(Observers.observations(BlockEntityChanged.class));
            var named = CompoundBinaryTag.builder().putString("CustomName", "\"box\"").build();
            var chest = new Vec(2, 0, 2);
            // 原版 handleBlockEntityData 只应用到 getBlockEntity(pos, type) 存在的方块实体：
            // 类型不同、方块没有方块实体、区块没有加载、超出维度高度时忽略
            peer.push(new BlockEntityDataPacket(chest, BlockEntityType.SIGN, named));
            peer.push(new BlockEntityDataPacket(new Vec(2, -1, 2), BlockEntityType.CHEST, named));
            peer.push(new BlockEntityDataPacket(new Vec(16 * 4 + 2, 0, 2), BlockEntityType.CHEST, named));
            peer.push(new BlockEntityDataPacket(new Vec(2, 320, 2), BlockEntityType.CHEST, named));
            peer.barrier();
            assertNull(bot.view().block(chest).nbt());
            peer.push(new BlockEntityDataPacket(chest, BlockEntityType.CHEST, named));
            peer.barrier();
            assertEquals(named, bot.view().block(chest).nbt());
            changes.close();
            assertEquals(List.of(chest), changes.observer().observations().stream().map(BlockEntityChanged::pos).toList());
        });
    }

    @Test
    void updatingAnUnknownBossBarEndsTheBot() {
        withBot((peer, bot) -> {
            var known = UUID.randomUUID();
            peer.push(new BossBarPacket(known, new BossBarPacket.AddAction(Component.text("boss"), 1f, BossBar.Color.RED,
                    BossBar.Overlay.PROGRESS, (byte) 0)));
            peer.push(new BossBarPacket(known, new BossBarPacket.UpdateHealthAction(0.5f)));
            peer.barrier();
            assertEquals(0.5f, bot.view().hud().bossBars().get(known).health());
            // 原版客户端读取不存在的条目时出错并断开连接
            peer.push(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.UpdateHealthAction(0.5f)));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    private static void waitTicks(TestPeer peer, int ticks) {
        var mark = peer.mark();
        var deadline = System.nanoTime() + TestServer.TIMEOUT.toNanos();
        while (peer.sentSince(mark, ClientTickEndPacket.class).size() < ticks) {
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
            peer.push(new SetTickStatePacket(20, true), new SetTimePacket(1000, Map.of()), new WorldBorderLerpSizePacket(100, 50, 1000));
            peer.barrier();
            var frozenAge = age.get();
            var frozenDiameter = diameter.get();
            // 原版 ClientLevel.tick 只在 TickRateManager.runsNormally 时推进世界边框与时间
            waitTicks(peer, 5);
            assertEquals(frozenAge, age.get());
            assertEquals(frozenDiameter, diameter.get());
            // Step Tick 给出几步就正常运行几个 tick（原版 TickRateManager.tick 每次减一）
            peer.push(new TickStepPacket(3));
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
            peer.push(new SetTimePacket(1000, Map.of(WorldClock.OVERWORLD, new SetTimePacket.ClockState(0, 0f, 1f))),
                    new WorldBorderLerpSizePacket(1000, 0, 1000));
            peer.barrier();
            // 插值开始之后的第一个 tick 范围仍为起始大小，之后每个 tick 都缩小
            waitTicks(peer, 2);
            var samples = new CopyOnWriteArrayList<TickSample>();
            Function<Boolean, TickSample> sample = ended -> new TickSample(ended, instance.state().world().worldAge(),
                    instance.state().clocks().clocks().get(WorldClock.OVERWORLD), instance.state().world().border().toWorldBorder().diameter());
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
    void becomingASpectatorThroughThePlayerListStopsVerticalMotion() {
        withBot((peer, bot) -> {
            // 暂停移动：速度只由数据包改变
            bot.suspendMovement();
            bot.await(state -> state.movementSuspended(), TestServer.TIMEOUT);
            var self = bot.inspect(state -> state.player().uuid());
            var velocity = new Vec(0.25, 0.5, 0);
            peer.push(new EntityVelocityPacket(TestServer.ENTITY_ID, velocity));
            peer.push(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE),
                    List.of(new PlayerInfoUpdatePacket.Entry(self, "world", List.of(), true, 0, GameMode.SPECTATOR, null, null, 0, true))));
            peer.barrier();
            // 原版 LocalPlayer.onGameModeChanged：玩家列表中的游戏模式（新条目为生存模式）改成旁观模式时竖直速度清零
            assertEquals(new Vec(0.25, 0, 0), bot.view().player().velocity());
            assertEquals(GameMode.SPECTATOR, bot.view().player().gameMode());
            // 列表中已经是旁观模式：不再清零
            peer.push(new EntityVelocityPacket(TestServer.ENTITY_ID, velocity));
            peer.push(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE),
                    List.of(new PlayerInfoUpdatePacket.Entry(self, "world", List.of(), true, 0, GameMode.SPECTATOR, null, null, 0, true))));
            peer.barrier();
            assertEquals(velocity, bot.view().player().velocity());
        });
    }

    private static TeamsPacket.Settings teamSettings(String displayName) {
        return new TeamsPacket.Settings(Component.text(displayName), Component.empty(), Component.empty(),
                TeamsPacket.NameTagVisibility.ALWAYS, TeamsPacket.CollisionRule.ALWAYS, null, (byte) 0);
    }

    @Test
    void joiningATeamLeavesThePreviousOne() {
        withBot((peer, bot) -> {
            peer.push(new TeamsPacket("red", new TeamsPacket.CreateTeamAction(teamSettings("Red"), List.of("alice", "bob"))));
            // 原版 Scoreboard.addPlayerToTeam：一个名称只属于一个队伍，加入新队伍时先离开原来的队伍
            peer.push(new TeamsPacket("blue", new TeamsPacket.CreateTeamAction(teamSettings("Blue"), List.of("alice"))));
            peer.push(new TeamsPacket("blue", new TeamsPacket.AddEntitiesToTeamAction(List.of("bob"))));
            peer.barrier();
            var teams = bot.view().hud().teams();
            assertTrue(teams.get("red").members().isEmpty(), teams::toString);
            assertEquals(Set.of("alice", "bob"), teams.get("blue").members());
            // 原版 Scoreboard.addPlayerTeam：创建已经存在的队伍时沿用它与它的成员，更新显示设置
            peer.push(new TeamsPacket("blue", new TeamsPacket.CreateTeamAction(teamSettings("Navy"), List.of("carol"))));
            peer.barrier();
            var blue = bot.view().hud().teams().get("blue");
            assertEquals(Set.of("alice", "bob", "carol"), blue.members());
            assertEquals(Component.text("Navy"), blue.displayName());
        });
    }

    @Test
    void removingAPlayerThatIsNotInTheTeamEndsTheBot() {
        withBot((peer, bot) -> {
            peer.push(new TeamsPacket("red", new TeamsPacket.CreateTeamAction(teamSettings("Red"), List.of("alice"))),
                    new TeamsPacket("blue", new TeamsPacket.CreateTeamAction(teamSettings("Blue"), List.of("bob"))));
            peer.push(new TeamsPacket("red", new TeamsPacket.RemoveEntitiesToTeamAction(List.of("alice"))));
            // 不存在的队伍：原版 handleSetPlayerTeamPacket 什么都不做
            peer.push(new TeamsPacket("green", new TeamsPacket.RemoveEntitiesToTeamAction(List.of("bob"))));
            peer.barrier();
            assertTrue(bot.view().hud().teams().get("red").members().isEmpty());
            assertEquals(Set.of("bob"), bot.view().hud().teams().get("blue").members());
            // 原版 Scoreboard.removePlayerFromTeam：成员不在这个队伍中时抛出异常，客户端断开连接
            peer.push(new TeamsPacket("red", new TeamsPacket.RemoveEntitiesToTeamAction(List.of("bob"))));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    private static ScoreboardObjectivePacket objective(String name, int mode) {
        return mode == 1 ? new ScoreboardObjectivePacket(name, (byte) mode, null, null, null)
                : new ScoreboardObjectivePacket(name, (byte) mode, Component.text(name), ScoreboardObjectivePacket.Type.INTEGER, null);
    }

    @Test
    void objectivesAndDisplaySlotsFollowTheVanillaScoreboard() {
        withBot((peer, bot) -> {
            peer.push(objective("kills", 0), new DisplayScoreboardPacket((byte) 1, "kills"),
                    new DisplayScoreboardPacket((byte) 2, "kills"), new DisplayScoreboardPacket((byte) 0, "missing"));
            // 原版 handleSetDisplayObjective：没有这个目标时清空这个显示位置
            peer.push(new DisplayScoreboardPacket((byte) 2, "missing"));
            peer.barrier();
            assertEquals(Map.of(1, "kills"), bot.view().hud().displaySlots());
            // 原版 Scoreboard.removeObjective：删除目标时清空指向它的显示位置，重新创建之后也不再指向它
            peer.push(objective("kills", 1), objective("kills", 0));
            peer.barrier();
            assertEquals(Map.of(), bot.view().hud().displaySlots());
            // 原版 Scoreboard.addObjective：创建已经存在的目标时抛出异常，客户端断开连接
            peer.push(objective("kills", 0));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void addingAListedPlayerAgainKeepsItsEntry() {
        withBot((peer, bot) -> {
            var friend = UUID.randomUUID();
            var actions = EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LATENCY);
            peer.push(new PlayerInfoUpdatePacket(actions,
                    List.of(new PlayerInfoUpdatePacket.Entry(friend, "friend", List.of(), true, 10, GameMode.SURVIVAL, null, null, 0, true))));
            // 原版 handlePlayerInfoUpdate 用 putIfAbsent 加入：保留原来的条目，只应用同一数据包中的其它更新
            peer.push(new PlayerInfoUpdatePacket(actions,
                    List.of(new PlayerInfoUpdatePacket.Entry(friend, "renamed", List.of(), true, 99, GameMode.SURVIVAL, null, null, 0, true))));
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
            peer.push(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER),
                    List.of(new PlayerInfoUpdatePacket.Entry(friend, "friend", List.of(), true, 10, GameMode.CREATIVE, null, null, 3, true))));
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
            peer.push(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.RemoveAction()));
            peer.push(new BossBarPacket(known, new BossBarPacket.AddAction(Component.text("boss"), 1f, BossBar.Color.RED,
                    BossBar.Overlay.PROGRESS, (byte) 0)));
            peer.push(new BossBarPacket(known, new BossBarPacket.RemoveAction()));
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
            peer.push(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.UPDATE_LATENCY, PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE),
                    List.of(new PlayerInfoUpdatePacket.Entry(stranger, "", List.of(), true, 50, GameMode.CREATIVE, null, null, 0, true),
                            new PlayerInfoUpdatePacket.Entry(self, "", List.of(), true, 50, GameMode.CREATIVE, null, null, 0, true))));
            peer.barrier();
            assertFalse(bot.view().hud().playerList().containsKey(stranger));
            // 自己不在玩家列表中时，游戏模式的更新同样被忽略
            assertEquals(GameMode.SURVIVAL, bot.view().player().gameMode());
            peer.push(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE),
                    List.of(new PlayerInfoUpdatePacket.Entry(self, "world", List.of(), true, 0, GameMode.CREATIVE, null, null, 0, true))));
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
            peer.push(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER),
                    List.of(new PlayerInfoUpdatePacket.Entry(SENDER, "sender", List.of(), false, 0, GameMode.SURVIVAL, null, null, 0, true))));
            test.accept(peer, bot);
        });
    }

    /**
     * wiki：聊天类型的编号为 {@code minecraft:chat_type} 注册表中的编号加一，0 表示数据包随后给出定义
     */
    private static int chatTypeId(RegistryKey<ChatType> type) {
        return TestServer.vanilla().chatType().getId(type) + 1;
    }

    private static PlayerChatMessagePacket chat(int globalIndex, List<MessageSignature.Packed> lastSeen) {
        return chat(globalIndex, lastSeen, new MessageSignature(new byte[256]));
    }

    private static PlayerChatMessagePacket chat(int globalIndex, List<MessageSignature.Packed> lastSeen, MessageSignature signature) {
        return chat(globalIndex, lastSeen, signature, "hi", null, new FilterMask(FilterMask.Type.PASS_THROUGH, new BitSet()));
    }

    private static PlayerChatMessagePacket chat(int globalIndex, List<MessageSignature.Packed> lastSeen, @Nullable MessageSignature signature,
                                                String content, @Nullable Component unsignedContent, FilterMask filter) {
        var body = new SignedMessageBody.Packed(content, Instant.now(), 0, new LastSeenMessages.Packed(lastSeen));
        return new PlayerChatMessagePacket(globalIndex, SENDER, 0, signature, body, unsignedContent, filter,
                chatTypeId(ChatType.CHAT), Component.text("sender"), null);
    }

    @Test
    void unknownMessageSignatureIdsEndTheBot() {
        withChatBot((peer, bot) -> {
            // 第一条消息把自己的签名放进缓存的编号 0
            peer.push(chat(0, List.of()));
            peer.push(chat(1, List.of(new MessageSignature.Packed(0, null))));
            peer.barrier();
            assertEquals(2, bot.view().chat().size());
            // 缓存中没有编号 5 的签名：原版客户端以无效数据包断开连接
            peer.push(chat(2, List.of(new MessageSignature.Packed(5, null))));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
        });
        withBot((peer, bot) -> {
            peer.push(new DeleteChatPacket(new MessageSignature.Packed(7, null)));
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
            var first = new MessageSignature(filled(1));
            var second = new MessageSignature(filled(2));
            peer.push(chat(0, List.of(), first), chat(1, List.of(), second));
            // 原版 handleDeleteChat 调用 ignorePending：第一条还没有随 Chat Message 告知服务端，不再计入已读
            peer.push(new DeleteChatPacket(new MessageSignature.Packed(first)));
            peer.barrier();
            var mark = peer.mark();
            assertTrue(bot.chat("seen").await(TestServer.TIMEOUT));
            var message = peer.awaitSent(mark, ClientChatMessagePacket.class, TestServer.TIMEOUT);
            assertEquals(2, message.ackOffset());
            assertEquals(1, message.ackList().cardinality());
            assertEquals(LastSeenTracker.checksum(List.of(second)), message.checksum());
            // 已经告知过的消息被删除时保持已读
            peer.push(new DeleteChatPacket(new MessageSignature.Packed(second)));
            peer.barrier();
            mark = peer.mark();
            assertTrue(bot.chat("still seen").await(TestServer.TIMEOUT));
            assertEquals(1, peer.awaitSent(mark, ClientChatMessagePacket.class, TestServer.TIMEOUT).ackList().cardinality());
        });
    }

    @Test
    void startConfigurationAcknowledgesPendingMessagesFirst() {
        withChatBot((peer, bot) -> {
            peer.push(chat(0, List.of()));
            peer.barrier();
            var mark = peer.mark();
            peer.push(new StartConfigurationPacket());
            var ack = peer.awaitSent(mark, ClientConfigurationAckPacket.class, TestServer.TIMEOUT);
            // 原版 handleConfigurationStart：未确认的消息数大于 0 时先发送 Acknowledge Message
            var sent = peer.sentSince(mark);
            assertEquals(new ClientChatAckPacket(1), sent.get(sent.indexOf(ack) - 1));
            assertEquals(1, peer.sentSince(mark, ClientChatAckPacket.class).size());
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
            peer.push(chat(1, List.of(new MessageSignature.Packed(0, null))));
            peer.barrier();
            // 原版 handleLogin 新建 MessageSignatureCache：之前的签名不再能按编号引用
            peer.push(TestServer.joinPacket(GameMode.SURVIVAL));
            peer.push(chat(0, List.of(new MessageSignature.Packed(0, null))));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void filteredPlayerChatFollowsTheVanillaClient() {
        withChatBot((peer, bot) -> {
            var shown = new MessageSignature(filled(1));
            var mask = new BitSet();
            mask.set(1, 3);
            // 原版 FilterMask.applyWithFormatting：部分过滤时显示过滤之后的消息正文（不使用未签名内容），被过滤的字符换成 #
            peer.push(chat(0, List.of(), shown, "hello", Component.text("unsigned"), new FilterMask(FilterMask.Type.PARTIALLY_FILTERED, mask)));
            // 原版 ChatListener.showMessageToPlayer：完全过滤的消息不显示
            peer.push(chat(1, List.of(), new MessageSignature(filled(2)), "secret", null,
                    new FilterMask(FilterMask.Type.FULLY_FILTERED, new BitSet())));
            peer.barrier();
            var lines = bot.view().chat();
            assertEquals(1, lines.size());
            var filteredStyle = Style.style(NamedTextColor.DARK_GRAY, HoverEvent.showText(Component.translatable("chat.filtered")));
            assertEquals(Component.text().append(Component.text("h"), Component.text("##", filteredStyle), Component.text("lo")).build(),
                    lines.getFirst().message());
            assertEquals("h##lo", lines.getFirst().plainText());
            // 原版 markMessageAsProcessed(signature, false)：没有显示的消息只计入未确认的消息数，不计入已读
            var mark = peer.mark();
            assertTrue(bot.chat("seen").await(TestServer.TIMEOUT));
            var message = peer.awaitSent(mark, ClientChatMessagePacket.class, TestServer.TIMEOUT);
            assertEquals(2, message.ackOffset());
            assertEquals(1, message.ackList().cardinality());
            assertEquals(LastSeenTracker.checksum(List.of(shown)), message.checksum());
        });
    }

    @Test
    void chatFromPlayersOutsideThePlayerListShowsAValidationError() {
        withBot((peer, bot) -> {
            var signature = new MessageSignature(filled(3));
            peer.push(chat(0, List.of(), signature));
            peer.barrier();
            // 原版 handleChatMessageError：不显示这条消息，代替它显示 chat.validation_error
            var line = bot.view().chat().getFirst();
            assertEquals(Component.translatable("chat.validation_error", NamedTextColor.RED, TextDecoration.ITALIC), line.message());
            assertEquals(SENDER, line.sender());
            // 代替显示的文本没有签名，Delete Message 不删除它
            peer.push(new DeleteChatPacket(new MessageSignature.Packed(signature)));
            peer.barrier();
            assertEquals(1, bot.view().chat().size());
            // 签名只计入未确认的消息数，不计入已读（原版 markMessageAsProcessed(signature, false)）
            var mark = peer.mark();
            assertTrue(bot.chat("seen").await(TestServer.TIMEOUT));
            var message = peer.awaitSent(mark, ClientChatMessagePacket.class, TestServer.TIMEOUT);
            assertEquals(1, message.ackOffset());
            assertEquals(0, message.ackList().cardinality());
        });
    }

    @Test
    void chatLinesRecordTheChatTypeAndTheNames() {
        withChatBot((peer, bot) -> {
            var body = new SignedMessageBody.Packed("hi", Instant.now(), 0, new LastSeenMessages.Packed(List.of()));
            peer.push(new PlayerChatMessagePacket(0, SENDER, 0, null, body, null, new FilterMask(FilterMask.Type.PASS_THROUGH, new BitSet()),
                    chatTypeId(ChatType.MSG_COMMAND_INCOMING), Component.text("alice"), Component.text("bob")));
            peer.push(new DisguisedChatPacket(Component.text("disguised"), chatTypeId(ChatType.SAY_COMMAND), Component.text("server"), null));
            peer.push(new SystemChatPacket(Component.text("system"), false));
            peer.barrier();
            assertEquals(List.of(
                    new ChatLine(ChatKind.PLAYER, SENDER, ChatType.MSG_COMMAND_INCOMING, Component.text("alice"), Component.text("bob"),
                            Component.text("hi"), "hi"),
                    new ChatLine(ChatKind.DISGUISED, null, ChatType.SAY_COMMAND, Component.text("server"), null,
                            Component.text("disguised"), "disguised"),
                    new ChatLine(ChatKind.SYSTEM, null, null, null, null, Component.text("system"), "system")), bot.view().chat());
            // 编号 0 表示数据包随后给出聊天类型的定义，Minestom 的数据包不能表示（M18）：bot 因异常结束
            peer.push(new DisguisedChatPacket(Component.text("inline"), 0, Component.text("server"), null));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
        withBot((peer, bot) -> {
            // 编号不在 minecraft:chat_type 注册表中：原版解码时出错，客户端断开连接
            peer.push(new DisguisedChatPacket(Component.text("unknown"), TestServer.vanilla().chatType().size() + 1,
                    Component.text("server"), null));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void newPlayersUseTheVanillaPlayerAttributes() {
        withBot((_, bot) -> {
            var instance = (BotInstance) bot;
            // 原版 Player.createAttributes：移动速度为 (double) 0.1F，攻击伤害为 1
            assertEquals((double) 0.1F, (double) bot.inspect(_ -> instance.state().player().attributes().value(Attribute.MOVEMENT_SPEED)));
            assertEquals(1.0, (double) bot.inspect(_ -> instance.state().player().attributes().value(Attribute.ATTACK_DAMAGE)));
        });
    }

    @Test
    void gameModeChangesSetAbilitiesLikeTheVanillaClient() {
        withBot((peer, bot) -> {
            // 原版 GameType.updatePlayerAbilities：创造模式可以飞行、瞬间破坏、无敌；旁观模式可以飞行、无敌并且正在飞行；
            // 生存与冒险模式这几项都为 false；冒险模式与旁观模式不能建造
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, GameMode.CREATIVE.ordinal()));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(true, false, true, true, true, 0.05f, 0.1f), bot.view().player().abilities());
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, GameMode.SPECTATOR.ordinal()));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(true, true, true, false, false, 0.05f, 0.1f), bot.view().player().abilities());
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, GameMode.SURVIVAL.ordinal()));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(false, false, false, false, true, 0.05f, 0.1f), bot.view().player().abilities());
            peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, GameMode.ADVENTURE.ordinal()));
            peer.barrier();
            assertEquals(new AbilitiesSnapshot(false, false, false, false, false, 0.05f, 0.1f), bot.view().player().abilities());
        });
    }

    private static SpawnEntityPacket pig(int id) {
        return new SpawnEntityPacket(id, UUID.randomUUID(), EntityType.PIG, new Pos(2.5, 0, 2.5), 0, 0, Vec.ZERO);
    }

    @Test
    void passengersThatWouldFormACycleAreRejected() {
        withBot((peer, bot) -> {
            peer.push(pig(100), pig(101), pig(102));
            // 100 乘 101，101 乘 102；再让 102 乘 100 会形成循环，原版 startRiding 拒绝
            peer.push(new SetPassengersPacket(101, List.of(100)), new SetPassengersPacket(102, List.of(101)),
                    new SetPassengersPacket(100, List.of(102)));
            peer.barrier();
            assertNull(bot.view().entity(102).vehicle());
            assertEquals(List.of(), bot.view().entity(100).passengers());
            assertEquals(101, bot.view().entity(100).vehicle());
            // 乘坐关系的计算没有陷入循环：bot 照常 tick
            var ticks = bot.view().ticks();
            bot.await(state -> state.ticks() >= ticks + 3, TestServer.TIMEOUT);

            // 实体乘坐自己：原版之后的乘坐计算会无限循环，bot 因异常结束
            peer.push(new SetPassengersPacket(102, List.of(102)));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertInstanceOf(IllegalStateException.class, bot.failure());
        });
    }

    @Test
    void passengersLeaveTheirOldVehicleAndUnknownOnesAreSkipped() {
        withBot((peer, bot) -> {
            peer.push(pig(100), pig(101), pig(102));
            peer.push(new SetPassengersPacket(101, List.of(100, 999)));
            peer.barrier();
            // 模型中没有的乘客编号被跳过
            assertEquals(List.of(100), bot.view().entity(101).passengers());
            // 100 改乘 102：先离开 101（原版 startRiding 中的 stopRiding）
            peer.push(new SetPassengersPacket(102, List.of(100)));
            peer.barrier();
            assertEquals(List.of(), bot.view().entity(101).passengers());
            assertEquals(List.of(100), bot.view().entity(102).passengers());
            assertEquals(102, bot.view().entity(100).vehicle());
        });
    }

    @Test
    void synchronizeVehiclePositionRotatesTheVelocityAndLimitsThePitch() {
        withBot((peer, bot) -> {
            peer.push(new SpawnEntityPacket(100, UUID.randomUUID(), EntityType.MINECART, new Pos(2.5, 0, 2.5), 0, 0, new Vec(0, 0, 1)));
            // 原版 PositionMoveRotation.calculateAbsolute：俯仰角从 0 变成 90，带 ROTATE_DELTA，速度先绕 x 轴旋转 -90 度，
            // (0, 0, 1) 变成 (0, -1, 0)
            peer.push(new EntityTeleportPacket(100, new Pos(0, 0, 0, 0, 90), Vec.ZERO,
                    RelativeFlags.COORD | RelativeFlags.YAW | RelativeFlags.DELTA_COORD | RelativeFlags.ROTATE_DELTA, true));
            peer.barrier();
            var cart = bot.view().entity(100);
            assertEquals(new Vec(2.5, 0, 2.5), cart.position().asVec());
            assertEquals(90, cart.position().pitch());
            assertEquals(0, cart.velocity().x(), 1e-6);
            assertEquals(-1, cart.velocity().y(), 1e-6);
            assertEquals(0, cart.velocity().z(), 1e-6);
            // 俯仰角限制到 [-90, 90]：相对的 60 加上原来的 90，以及绝对的 -120
            peer.push(new EntityTeleportPacket(100, new Pos(0, 0, 0, 0, 60), Vec.ZERO, RelativeFlags.COORD | RelativeFlags.VIEW, true));
            peer.barrier();
            assertEquals(90, bot.view().entity(100).position().pitch());
            peer.push(new EntityTeleportPacket(100, new Pos(2.5, 0, 2.5, 0, -120), Vec.ZERO, 0, true));
            peer.barrier();
            assertEquals(-90, bot.view().entity(100).position().pitch());
        });
    }
}
