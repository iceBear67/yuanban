package io.ib67.yuanban.bot.hud;

import io.ib67.yuanban.bot.view.HudSnapshot;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarDivision;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.CollisionRule;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.NameTagVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ObjectiveAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreType;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamColor;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudModelTest {
    private static final UUID BOSS_BAR = UUID.randomUUID();

    private static ClientboundSetPlayerTeamPacket team(String name, TeamAction action, String... players) {
        return switch (action) {
            // 带玩家的成员列表只有加入与移除玩家两种动作使用
            case ADD_PLAYER, REMOVE_PLAYER -> new ClientboundSetPlayerTeamPacket(name, action, players);
            case CREATE -> createTeam(name, players);
            case UPDATE -> new ClientboundSetPlayerTeamPacket(name, Component.text(name), Component.text("<"),
                    Component.text(">"), false, false, NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, TeamColor.RED);
            case REMOVE -> new ClientboundSetPlayerTeamPacket(name);
        };
    }

    private static ClientboundSetPlayerTeamPacket createTeam(String name, String... players) {
        return new ClientboundSetPlayerTeamPacket(name, Component.text(name), Component.text("<"), Component.text(">"),
                false, false, NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, TeamColor.RED, players);
    }

    @Test
    void bossBarActionsOnlyChangeTheFieldsTheyCarry() {
        var hud = new HudModel();
        var title = Component.text("Boss");
        assertTrue(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, title, 0.5f, BossBarColor.RED,
                BossBarDivision.NOTCHES_10, true, false, true)));
        var added = hud.currentBossBar(BOSS_BAR);
        assertEquals(title, added.title());
        assertEquals(0.5f, added.health());

        // 每种更新只替换自己的字段
        assertTrue(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, 0.75f)));
        var health = hud.currentBossBar(BOSS_BAR);
        assertEquals(0.75f, health.health());
        assertEquals(title, health.title());
        assertEquals(BossBarColor.RED, health.color());
        assertTrue(health.darkenSky());

        assertTrue(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, Component.text("Renamed"))));
        assertEquals(Component.text("Renamed"), hud.currentBossBar(BOSS_BAR).title());
        assertEquals(0.75f, hud.currentBossBar(BOSS_BAR).health());

        assertTrue(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, BossBarColor.CYAN, BossBarDivision.NONE)));
        assertEquals(BossBarColor.CYAN, hud.currentBossBar(BOSS_BAR).color());
        assertEquals(BossBarDivision.NONE, hud.currentBossBar(BOSS_BAR).division());

        assertTrue(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, false, true, false)));
        var flags = hud.currentBossBar(BOSS_BAR);
        assertFalse(flags.darkenSky());
        assertTrue(flags.playEndMusic());
        assertFalse(flags.showFog());

        // 移除之后不再有这条 Boss Bar
        assertTrue(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR)));
        assertNull(hud.currentBossBar(BOSS_BAR));
        assertTrue(hud.snapshot().bossBars().isEmpty());
    }

    @Test
    void removingAnUnknownBossBarDoesNothing() {
        var hud = new HudModel();
        assertFalse(hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR)));
        // 更新没有添加过的 Boss Bar 是无效数据：原版客户端断开连接
        assertThrows(IllegalStateException.class, () -> hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, 1.0f)));
        assertThrows(IllegalStateException.class,
                () -> hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, Component.text("late"))));
        assertThrows(IllegalStateException.class,
                () -> hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, BossBarColor.PINK, BossBarDivision.NONE)));
        assertThrows(IllegalStateException.class,
                () -> hud.bossBar(new ClientboundBossEventPacket(BOSS_BAR, true, true, true)));
    }

    @Test
    void titlesAndTheActionBarAreKeptSeparately() {
        var hud = new HudModel();
        hud.title(Component.text("title"));
        hud.subtitle(Component.text("subtitle"));
        hud.titleTimes(10, 70, 20);
        hud.actionBar(Component.text("action"));
        var snapshot = hud.snapshot();
        assertEquals(Component.text("title"), snapshot.title());
        assertEquals(Component.text("subtitle"), snapshot.subtitle());
        assertEquals(new HudSnapshot.TitleTimes(10, 70, 20), snapshot.titleTimes());
        assertEquals(Component.text("action"), snapshot.actionBar());

        // Clear Titles 不带 reset 时保留标题时间
        hud.clearTitles(false);
        assertNull(hud.title());
        assertNull(hud.subtitle());
        assertEquals(new HudSnapshot.TitleTimes(10, 70, 20), hud.snapshot().titleTimes());
        hud.titleTimes(1, 2, 3);
        hud.clearTitles(true);
        assertNull(hud.snapshot().titleTimes());
    }

    private static PlayerListEntry entry(UUID uuid, String name) {
        var entry = new PlayerListEntry(uuid);
        entry.setProfile(new GameProfile(uuid, name));
        return entry;
    }

    private static ClientboundPlayerInfoUpdatePacket playerInfo(EnumSet<PlayerListEntryAction> actions, PlayerListEntry... entries) {
        return new ClientboundPlayerInfoUpdatePacket(actions, entries);
    }

    @Test
    void playerInfoAddsEntriesWithTheVanillaDefaults() {
        var hud = new HudModel();
        var uuid = UUID.randomUUID();
        hud.playerInfo(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER), entry(uuid, "alice")));
        var entry = hud.player(uuid);
        assertEquals("alice", entry.username());
        assertEquals(GameMode.SURVIVAL, entry.gameMode());
        assertEquals(0, entry.latency());
        assertFalse(entry.listed());
        assertEquals(0, entry.listOrder());
        assertNull(entry.displayName());
        assertTrue(hud.hasPlayer(uuid));
    }

    @Test
    void updatesOnlyChangeTheFieldsListedInThePacket() {
        var hud = new HudModel();
        var uuid = UUID.randomUUID();
        hud.playerInfo(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER), entry(uuid, "alice")));
        var change = entry(uuid, "alice");
        change.setGameMode(GameMode.CREATIVE);
        change.setLatency(42);
        change.setListed(true);
        hud.playerInfo(playerInfo(EnumSet.of(PlayerListEntryAction.UPDATE_GAME_MODE, PlayerListEntryAction.UPDATE_LATENCY,
                PlayerListEntryAction.UPDATE_LISTED), change));
        var updated = hud.player(uuid);
        assertEquals(GameMode.CREATIVE, updated.gameMode());
        assertEquals(42, updated.latency());
        assertTrue(updated.listed());
        // 没有列出的动作保留原来的值
        assertEquals("alice", updated.username());
        assertEquals(0, updated.listOrder());
    }

    @Test
    void updatesForUnknownPlayersAreIgnored() {
        var hud = new HudModel();
        var uuid = UUID.randomUUID();
        var change = entry(uuid, "stranger");
        change.setLatency(5);
        hud.playerInfo(playerInfo(EnumSet.of(PlayerListEntryAction.UPDATE_LATENCY), change));
        assertFalse(hud.hasPlayer(uuid));
        assertNull(hud.player(uuid));
    }

    @Test
    void addingAnExistingPlayerKeepsItsEntry() {
        var hud = new HudModel();
        var uuid = UUID.randomUUID();
        hud.playerInfo(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER, PlayerListEntryAction.UPDATE_LISTED),
                entry(uuid, "alice")));
        var duplicate = entry(uuid, "alice");
        duplicate.setListed(true);
        hud.playerInfo(playerInfo(EnumSet.of(PlayerListEntryAction.ADD_PLAYER), duplicate));
        // ADD_PLAYER 对已有的玩家是 putIfAbsent
        assertFalse(hud.player(uuid).listed());
        hud.removePlayers(List.of(uuid));
        assertFalse(hud.hasPlayer(uuid));
    }

    private static ClientboundSetObjectivePacket objective(String name, ObjectiveAction action) {
        return new ClientboundSetObjectivePacket(name, action, Component.text(name), ScoreType.INTEGER, null);
    }

    @Test
    void objectivesAreCreatedUpdatedAndRemoved() {
        var hud = new HudModel();
        hud.objective(objective("board", ObjectiveAction.ADD));
        hud.display(ScoreboardPosition.SIDEBAR, "board");
        assertEquals("board", hud.snapshot().displaySlots().get(ScoreboardPosition.SIDEBAR));

        assertTrue(hud.score("alice", "board", 5));
        assertEquals(5, hud.snapshot().objectives().get("board").scores().get("alice"));
        // 更新不存在的目标与分数什么都不做
        hud.objective(objective("missing", ObjectiveAction.UPDATE));
        assertFalse(hud.score("alice", "missing", 1));

        assertEquals(List.of("board"), hud.resetScore("alice", null));
        assertTrue(hud.snapshot().objectives().get("board").scores().isEmpty());
        assertTrue(hud.resetScore("alice", null).isEmpty());

        // 删除目标时清空指向它的显示位置
        hud.objective(objective("board", ObjectiveAction.REMOVE));
        assertFalse(hud.snapshot().objectives().containsKey("board"));
        assertFalse(hud.snapshot().displaySlots().containsKey(ScoreboardPosition.SIDEBAR));
        // 删除不存在的目标什么都不做
        hud.objective(objective("board", ObjectiveAction.REMOVE));
    }

    @Test
    void creatingAnObjectiveTwiceIsAnError() {
        var hud = new HudModel();
        hud.objective(objective("board", ObjectiveAction.ADD));
        // 原版 Scoreboard.addObjective 抛出异常：客户端断开连接
        assertThrows(IllegalStateException.class, () -> hud.objective(objective("board", ObjectiveAction.ADD)));
    }

    @Test
    void displayPointsOnlyToExistingObjectives() {
        var hud = new HudModel();
        hud.display(ScoreboardPosition.SIDEBAR, "missing");
        assertTrue(hud.snapshot().displaySlots().isEmpty());
        hud.objective(objective("board", ObjectiveAction.ADD));
        hud.display(ScoreboardPosition.SIDEBAR, "board");
        hud.display(ScoreboardPosition.SIDEBAR, "");
        // 空名称清空这个显示位置
        assertTrue(hud.snapshot().displaySlots().isEmpty());
        hud.display(ScoreboardPosition.PLAYER_LIST, "board");
        assertEquals("board", hud.snapshot().displaySlots().get(ScoreboardPosition.PLAYER_LIST));
    }

    @Test
    void resettingOneObjectiveLeavesTheOthers() {
        var hud = new HudModel();
        hud.objective(objective("first", ObjectiveAction.ADD));
        hud.objective(objective("second", ObjectiveAction.ADD));
        hud.score("alice", "first", 1);
        hud.score("alice", "second", 2);
        assertEquals(List.of("second"), hud.resetScore("alice", "second"));
        assertEquals(1, hud.snapshot().objectives().get("first").scores().get("alice"));
        assertFalse(hud.snapshot().objectives().get("second").scores().containsKey("alice"));
    }

    @Test
    void teamMembersAreExclusiveToTheirTeam() {
        var hud = new HudModel();
        hud.team(createTeam("red", "alice", "bob"));
        assertEquals(Set.of("alice", "bob"), hud.snapshot().teams().get("red").members());
        // 加入 blue 时先离开 red
        hud.team(createTeam("blue", "bob"));
        assertEquals(Set.of("alice"), hud.snapshot().teams().get("red").members());
        assertTrue(hud.snapshot().teams().get("blue").members().contains("bob"));
    }

    @Test
    void creatingAnExistingTeamKeepsItsMembers() {
        var hud = new HudModel();
        hud.team(createTeam("red", "alice"));
        hud.team(new ClientboundSetPlayerTeamPacket("red", Component.text("renamed"), Component.text("["),
                Component.text("]"), false, false, NameTagVisibility.NEVER, CollisionRule.PUSH_OTHER_TEAMS, TeamColor.BLUE));
        var team = hud.snapshot().teams().get("red");
        assertEquals(Component.text("renamed"), team.displayName());
        assertEquals(Set.of("alice"), team.members());
    }

    @Test
    void teamActionsForUnknownTeamsAreIgnored() {
        var hud = new HudModel();
        hud.team(team("missing", TeamAction.UPDATE, "alice"));
        hud.team(team("missing", TeamAction.ADD_PLAYER, "alice"));
        hud.team(team("missing", TeamAction.REMOVE_PLAYER, "alice"));
        hud.team(team("missing", TeamAction.REMOVE));
        assertTrue(hud.snapshot().teams().isEmpty());
    }

    @Test
    void removingAMemberOfAnotherTeamIsAnError() {
        var hud = new HudModel();
        hud.team(createTeam("red", "alice"));
        // 原版 Scoreboard.removePlayerFromTeam 抛出异常：客户端断开连接
        assertThrows(IllegalStateException.class, () -> hud.team(team("red", TeamAction.REMOVE_PLAYER, "bob")));
        hud.team(team("red", TeamAction.REMOVE_PLAYER, "alice"));
        assertTrue(hud.snapshot().teams().get("red").members().isEmpty());
    }

    @Test
    void removingATeamDropsItsMembers() {
        var hud = new HudModel();
        hud.team(createTeam("red", "alice"));
        hud.team(team("red", TeamAction.REMOVE));
        assertTrue(hud.snapshot().teams().isEmpty());
    }

    @Test
    void theSnapshotKeepsHeaderFooterAndMotd() {
        var hud = new HudModel();
        hud.headerFooter(Component.text("header"), Component.text("footer"));
        hud.serverMotd(Component.text("motd"));
        var snapshot = hud.snapshot();
        assertEquals(Component.text("header"), snapshot.header());
        assertEquals(Component.text("footer"), snapshot.footer());
        assertEquals(Component.text("motd"), snapshot.serverMotd());
        assertTrue(snapshot.bossBars().isEmpty());
    }
}
