package io.ib67.yuanban.bot.hud;

import io.ib67.yuanban.bot.view.BossBarSnapshot;
import io.ib67.yuanban.bot.view.HudSnapshot;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarAction;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreType;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 界面状态：Boss Bar、标题、动作栏、玩家列表、计分板、Server Data。
 */
public final class HudModel {
    private final Map<UUID, BossBarSnapshot> bossBars = new HashMap<>();
    private @Nullable Component title;
    private @Nullable Component subtitle;
    private HudSnapshot.@Nullable TitleTimes titleTimes;
    private @Nullable Component actionBar;
    private final Map<UUID, HudSnapshot.PlayerListEntry> playerList = new HashMap<>();
    private Component header = Component.empty();
    private Component footer = Component.empty();
    private final Map<String, Objective> objectives = new HashMap<>();
    private final Map<ScoreboardPosition, String> displaySlots = new EnumMap<>(ScoreboardPosition.class);
    private final Map<String, Team> teams = new HashMap<>();
    private @Nullable Component serverMotd;

    /**
     * Boss Bar：每种动作只读取它带有的字段
     *
     * @return 状态是否改变：移除没有添加过的 Boss Bar 时什么都不做（原版 {@code BossHealthOverlay} 的 remove），返回 false
     * @throws IllegalStateException 更新没有添加过的 Boss Bar（原版客户端读取不存在的条目时出错并断开连接）
     */
    public boolean bossBar(ClientboundBossEventPacket packet) {
        var id = packet.getUuid();
        if (packet.getAction() == BossBarAction.REMOVE && !bossBars.containsKey(id)) {
            return false;
        }
        BossBarSnapshot next = switch (packet.getAction()) {
            case ADD -> new BossBarSnapshot(packet.getTitle(), packet.getHealth(), packet.getColor(), packet.getDivision(),
                    packet.isDarkenSky(), packet.isPlayEndMusic(), packet.isShowFog());
            case REMOVE -> null;
            case UPDATE_HEALTH -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(current.title(), packet.getHealth(), current.color(), current.division(),
                        current.darkenSky(), current.playEndMusic(), current.showFog());
            }
            case UPDATE_TITLE -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(packet.getTitle(), current.health(), current.color(), current.division(),
                        current.darkenSky(), current.playEndMusic(), current.showFog());
            }
            case UPDATE_STYLE -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(current.title(), current.health(), packet.getColor(), packet.getDivision(),
                        current.darkenSky(), current.playEndMusic(), current.showFog());
            }
            case UPDATE_FLAGS -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(current.title(), current.health(), current.color(), current.division(),
                        packet.isDarkenSky(), packet.isPlayEndMusic(), packet.isShowFog());
            }
        };
        if (next == null) {
            bossBars.remove(id);
        } else {
            bossBars.put(id, next);
        }
        return true;
    }

    /**
     * @return 当前的状态；没有这个 Boss Bar 时为 null
     */
    public @Nullable BossBarSnapshot currentBossBar(UUID id) {
        return bossBars.get(id);
    }

    private BossBarSnapshot existingBossBar(UUID id) {
        var current = bossBars.get(id);
        if (current == null) {
            throw new IllegalStateException("boss bar " + id + " is updated before it is added");
        }
        return current;
    }

    public void title(Component title) {
        this.title = title;
    }

    public void subtitle(Component subtitle) {
        this.subtitle = subtitle;
    }

    public @Nullable Component title() {
        return title;
    }

    public @Nullable Component subtitle() {
        return subtitle;
    }

    public void titleTimes(int fadeIn, int stay, int fadeOut) {
        this.titleTimes = new HudSnapshot.TitleTimes(fadeIn, stay, fadeOut);
    }

    /**
     * Clear Titles
     *
     * @param reset 同时重置标题时间
     */
    public void clearTitles(boolean reset) {
        title = null;
        subtitle = null;
        if (reset) {
            titleTimes = null;
        }
    }

    public void actionBar(Component text) {
        this.actionBar = text;
    }

    /**
     * Player Info Update：先加入带 ADD_PLAYER 的条目（已在列表中的玩家保留原来的条目，原版 {@code putIfAbsent}），
     * 再把各项更新应用到已知的玩家；不在列表中的玩家的更新被忽略（原版客户端行为）。
     * 新条目的初始值与原版 {@code PlayerInfo} 相同：生存模式，延迟 0，不显示在列表中，没有显示名称，排序 0
     */
    public void playerInfo(ClientboundPlayerInfoUpdatePacket packet) {
        var actions = packet.getActions();
        for (var entry : packet.getEntries()) {
            var uuid = entry.getProfileId();
            var current = playerList.get(uuid);
            if (current == null && actions.contains(PlayerListEntryAction.ADD_PLAYER)) {
                // 带 ADD_PLAYER 时 MCProtocolLib 一定设置了 profile
                var username = Objects.requireNonNull(entry.getProfile()).getName();
                current = new HudSnapshot.PlayerListEntry(uuid, username, GameMode.SURVIVAL, 0, false, null, 0);
            } else if (current == null) {
                continue;
            }
            current = new HudSnapshot.PlayerListEntry(current.uuid(), current.username(),
                    actions.contains(PlayerListEntryAction.UPDATE_GAME_MODE) ? entry.getGameMode() : current.gameMode(),
                    actions.contains(PlayerListEntryAction.UPDATE_LATENCY) ? entry.getLatency() : current.latency(),
                    actions.contains(PlayerListEntryAction.UPDATE_LISTED) ? entry.isListed() : current.listed(),
                    actions.contains(PlayerListEntryAction.UPDATE_DISPLAY_NAME) ? entry.getDisplayName() : current.displayName(),
                    actions.contains(PlayerListEntryAction.UPDATE_LIST_ORDER) ? entry.getListOrder() : current.listOrder());
            playerList.put(uuid, current);
        }
    }

    /**
     * 玩家列表中是否有这个玩家
     */
    public boolean hasPlayer(UUID uuid) {
        return playerList.containsKey(uuid);
    }

    /**
     * @return 玩家列表中这个玩家的条目；不在列表中时为 null
     */
    public HudSnapshot.@Nullable PlayerListEntry player(UUID uuid) {
        return playerList.get(uuid);
    }

    public void removePlayers(List<UUID> uuids) {
        uuids.forEach(playerList::remove);
    }

    public void headerFooter(Component header, Component footer) {
        this.header = header;
        this.footer = footer;
    }

    /**
     * Update Objectives（原版 {@code handleAddObjective}）：ADD 创建，REMOVE 删除，UPDATE 更新显示文本与类型；
     * 删除与更新不存在的目标时什么都不做。删除目标时清空指向它的显示位置（原版 {@code Scoreboard.removeObjective}）
     *
     * @throws IllegalStateException 创建已经存在的目标（原版 {@code Scoreboard.addObjective} 抛出异常，客户端断开连接）
     */
    public void objective(ClientboundSetObjectivePacket packet) {
        var name = packet.getName();
        switch (packet.getAction()) {
            case ADD -> {
                if (objectives.containsKey(name)) {
                    throw new IllegalStateException("objective " + name + " already exists");
                }
                // ADD 与 UPDATE 一定带有显示文本与类型
                objectives.put(name, new Objective(Objects.requireNonNull(packet.getDisplayName()),
                        Objects.requireNonNull(packet.getType()), new HashMap<>()));
            }
            case REMOVE -> {
                if (objectives.remove(name) != null) {
                    displaySlots.values().removeIf(name::equals);
                }
            }
            case UPDATE -> {
                var current = objectives.get(name);
                if (current != null) {
                    objectives.put(name, new Objective(Objects.requireNonNull(packet.getDisplayName()),
                            Objects.requireNonNull(packet.getType()), current.scores()));
                }
            }
        }
    }

    /**
     * Display Objective（原版 {@code handleSetDisplayObjective}）：名称为空或者没有这个目标时清空这个显示位置
     */
    public void display(ScoreboardPosition position, String objective) {
        if (!objective.isEmpty() && objectives.containsKey(objective)) {
            displaySlots.put(position, objective);
        } else {
            displaySlots.remove(position);
        }
    }

    /**
     * Update Score
     *
     * @return 目标是否存在
     */
    public boolean score(String holder, String objective, int score) {
        var target = objectives.get(objective);
        if (target == null) {
            return false;
        }
        target.scores().put(holder, score);
        return true;
    }

    /**
     * Reset Score
     *
     * @param objective 为 null 时删除持有者在全部目标中的分数
     * @return 被删除的分数所在的目标
     */
    public List<String> resetScore(String holder, @Nullable String objective) {
        var removed = new ArrayList<String>();
        objectives.forEach((name, target) -> {
            if ((objective == null || objective.equals(name)) && target.scores().remove(holder) != null) {
                removed.add(name);
            }
        });
        return removed;
    }

    /**
     * Update Teams（原版 {@code handleSetPlayerTeamPacket}）：创建已经存在的队伍时沿用它与它的成员，只更新显示设置
     * （原版 {@code Scoreboard.addPlayerTeam}）；一个名称只属于一个队伍，加入队伍时先离开原来的队伍（原版 {@code addPlayerToTeam}）；
     * 其它操作指向不存在的队伍时什么都不做
     *
     * @throws IllegalStateException 移除不在这个队伍中的成员（原版 {@code Scoreboard.removePlayerFromTeam} 抛出异常，客户端断开连接）
     */
    public void team(ClientboundSetPlayerTeamPacket packet) {
        var name = packet.getTeamName();
        switch (packet.getAction()) {
            case CREATE -> {
                var existing = teams.get(name);
                var team = new Team(packet.getDisplayName(), packet.getPlayerPrefix(), packet.getPlayerSuffix(),
                        existing != null ? existing.members() : new HashSet<>());
                teams.put(name, team);
                for (var member : packet.getPlayers()) {
                    addMember(team, member);
                }
            }
            case REMOVE -> teams.remove(name);
            case UPDATE -> {
                var current = teams.get(name);
                if (current != null) {
                    teams.put(name, new Team(packet.getDisplayName(), packet.getPlayerPrefix(), packet.getPlayerSuffix(),
                            current.members()));
                }
            }
            case ADD_PLAYER -> {
                var current = teams.get(name);
                if (current != null) {
                    for (var member : packet.getPlayers()) {
                        addMember(current, member);
                    }
                }
            }
            case REMOVE_PLAYER -> {
                var current = teams.get(name);
                if (current != null) {
                    for (var member : packet.getPlayers()) {
                        if (!current.members().remove(member)) {
                            throw new IllegalStateException(member + " is not a member of team " + name);
                        }
                    }
                }
            }
        }
    }

    private void addMember(Team team, String member) {
        teams.values().forEach(other -> other.members().remove(member));
        team.members().add(member);
    }

    public void serverMotd(Component motd) {
        this.serverMotd = motd;
    }

    public HudSnapshot snapshot() {
        var objectiveSnapshots = new HashMap<String, HudSnapshot.Objective>();
        objectives.forEach((name, objective) -> objectiveSnapshots.put(name,
                new HudSnapshot.Objective(name, objective.displayName(), objective.type(), objective.scores())));
        var teamSnapshots = new HashMap<String, HudSnapshot.Team>();
        teams.forEach((name, team) -> teamSnapshots.put(name,
                new HudSnapshot.Team(name, team.displayName(), team.prefix(), team.suffix(), team.members())));
        return new HudSnapshot(bossBars, title, subtitle, titleTimes, actionBar, playerList, header, footer,
                objectiveSnapshots, displaySlots, teamSnapshots, serverMotd);
    }

    private record Objective(Component displayName, ScoreType type, Map<String, Integer> scores) {
    }

    private record Team(Component displayName, Component prefix, Component suffix, Set<String> members) {
    }
}
