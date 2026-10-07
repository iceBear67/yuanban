package io.ib67.yuanban.bot.hud;

import io.ib67.yuanban.bot.view.BossBarSnapshot;
import io.ib67.yuanban.bot.view.HudSnapshot;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.GameMode;
import net.minestom.server.network.packet.server.play.BossBarPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.ScoreboardObjectivePacket;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<Integer, String> displaySlots = new HashMap<>();
    private final Map<String, Team> teams = new HashMap<>();
    private @Nullable Component serverMotd;

    /**
     * @return 状态是否改变：移除没有添加过的 Boss Bar 时什么都不做（原版 {@code BossHealthOverlay} 的 remove），返回 false
     * @throws IllegalStateException 更新没有添加过的 Boss Bar（原版客户端读取不存在的条目时出错并断开连接）
     */
    public boolean bossBar(UUID id, BossBarPacket.Action action) {
        if (action instanceof BossBarPacket.RemoveAction && !bossBars.containsKey(id)) {
            return false;
        }
        BossBarSnapshot next = switch (action) {
            case BossBarPacket.AddAction add ->
                    new BossBarSnapshot(add.title(), add.health(), add.color(), add.overlay(), add.flags());
            case BossBarPacket.RemoveAction _ -> null;
            case BossBarPacket.UpdateHealthAction health -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(current.title(), health.health(), current.color(), current.overlay(), current.flags());
            }
            case BossBarPacket.UpdateTitleAction updateTitle -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(updateTitle.title(), current.health(), current.color(), current.overlay(), current.flags());
            }
            case BossBarPacket.UpdateStyleAction style -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(current.title(), current.health(), style.color(), style.overlay(), current.flags());
            }
            case BossBarPacket.UpdateFlagsAction flags -> {
                var current = existingBossBar(id);
                yield new BossBarSnapshot(current.title(), current.health(), current.color(), current.overlay(), flags.flags());
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
    public void playerInfo(PlayerInfoUpdatePacket packet) {
        for (var entry : packet.entries()) {
            var current = playerList.get(entry.uuid());
            if (current == null && packet.actions().contains(PlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                current = new HudSnapshot.PlayerListEntry(entry.uuid(), entry.username(), GameMode.SURVIVAL, 0, false, null, 0);
            } else if (current == null) {
                continue;
            }
            var actions = packet.actions();
            current = new HudSnapshot.PlayerListEntry(current.uuid(), current.username(),
                    actions.contains(PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE) ? entry.gameMode() : current.gameMode(),
                    actions.contains(PlayerInfoUpdatePacket.Action.UPDATE_LATENCY) ? entry.latency() : current.latency(),
                    actions.contains(PlayerInfoUpdatePacket.Action.UPDATE_LISTED) ? entry.listed() : current.listed(),
                    actions.contains(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME) ? entry.displayName() : current.displayName(),
                    actions.contains(PlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER) ? entry.listOrder() : current.listOrder());
            playerList.put(entry.uuid(), current);
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
     * Update Objectives（原版 {@code handleAddObjective}）：模式 0 创建，1 删除，2 更新显示文本；删除与更新不存在的目标时什么都不做。
     * 删除目标时清空指向它的显示位置（原版 {@code Scoreboard.removeObjective}）
     *
     * @throws IllegalStateException 创建已经存在的目标（原版 {@code Scoreboard.addObjective} 抛出异常，客户端断开连接）
     */
    public void objective(ScoreboardObjectivePacket packet) {
        switch (packet.mode()) {
            case 0 -> {
                if (objectives.containsKey(packet.objectiveName())) {
                    throw new IllegalStateException("objective " + packet.objectiveName() + " already exists");
                }
                objectives.put(packet.objectiveName(), new Objective(
                        packet.objectiveValue() == null ? Component.empty() : packet.objectiveValue(), packet.type(), new HashMap<>()));
            }
            case 1 -> {
                if (objectives.remove(packet.objectiveName()) != null) {
                    displaySlots.values().removeIf(packet.objectiveName()::equals);
                }
            }
            case 2 -> {
                var current = objectives.get(packet.objectiveName());
                if (current != null) {
                    objectives.put(packet.objectiveName(), new Objective(
                            packet.objectiveValue() == null ? current.displayName() : packet.objectiveValue(),
                            packet.type(), current.scores()));
                }
            }
            default -> throw new IllegalArgumentException("unknown objective mode " + packet.mode());
        }
    }

    /**
     * Display Objective（原版 {@code handleSetDisplayObjective}）：名称为空或者没有这个目标时清空这个显示位置
     */
    public void display(int position, String objective) {
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
    public void team(TeamsPacket packet) {
        var name = packet.teamName();
        switch (packet.action()) {
            case TeamsPacket.CreateTeamAction create -> {
                var existing = teams.get(name);
                var team = new Team(create.settings().displayName(), create.settings().teamPrefix(),
                        create.settings().teamSuffix(), existing != null ? existing.members() : new HashSet<>());
                teams.put(name, team);
                create.entities().forEach(member -> addMember(team, member));
            }
            case TeamsPacket.RemoveTeamAction _ -> teams.remove(name);
            case TeamsPacket.UpdateTeamAction update -> {
                var current = teams.get(name);
                if (current != null) {
                    teams.put(name, new Team(update.settings().displayName(), update.settings().teamPrefix(),
                            update.settings().teamSuffix(), current.members()));
                }
            }
            case TeamsPacket.AddEntitiesToTeamAction add -> {
                var current = teams.get(name);
                if (current != null) {
                    add.entities().forEach(member -> addMember(current, member));
                }
            }
            case TeamsPacket.RemoveEntitiesToTeamAction remove -> {
                var current = teams.get(name);
                if (current != null) {
                    for (var member : remove.entities()) {
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

    private record Objective(Component displayName, ScoreboardObjectivePacket.@Nullable Type type, Map<String, Integer> scores) {
    }

    private record Team(Component displayName, Component prefix, Component suffix, Set<String> members) {
    }
}
