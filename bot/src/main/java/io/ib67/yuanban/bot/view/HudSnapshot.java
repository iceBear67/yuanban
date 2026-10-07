package io.ib67.yuanban.bot.view;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.GameMode;
import net.minestom.server.network.packet.server.play.ScoreboardObjectivePacket;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 界面状态的快照。
 *
 * @param titleTimes     最近一次 Set Title Animation Times；没有收到时为 null
 * @param actionBar      最近一次显示的动作栏文本
 * @param displaySlots   Display Objective：显示位置到目标名称，只指向存在的目标（目标被删除时清空指向它的位置）
 * @param serverMotd     Server Data 中的服务器描述
 */
public record HudSnapshot(Map<UUID, BossBarSnapshot> bossBars,
                          @Nullable Component title, @Nullable Component subtitle, @Nullable TitleTimes titleTimes,
                          @Nullable Component actionBar,
                          Map<UUID, PlayerListEntry> playerList, Component header, Component footer,
                          Map<String, Objective> objectives, Map<Integer, String> displaySlots,
                          Map<String, Team> teams,
                          @Nullable Component serverMotd) {
    public HudSnapshot {
        bossBars = Map.copyOf(bossBars);
        playerList = Map.copyOf(playerList);
        objectives = Map.copyOf(objectives);
        displaySlots = Map.copyOf(displaySlots);
        teams = Map.copyOf(teams);
    }

    public record TitleTimes(int fadeIn, int stay, int fadeOut) {
    }

    /**
     * 玩家列表中的一项
     *
     * @param gameMode 游戏模式；Player Info Update 没有给出时为生存模式（原版 {@code PlayerInfo} 的初始值）
     */
    public record PlayerListEntry(UUID uuid, String username, GameMode gameMode, int latency, boolean listed,
                                  @Nullable Component displayName, int listOrder) {
    }

    /**
     * 计分板目标
     *
     * @param scores 分数持有者到分数
     */
    public record Objective(String name, Component displayName, ScoreboardObjectivePacket.@Nullable Type type,
                            Map<String, Integer> scores) {
        public Objective {
            scores = Map.copyOf(scores);
        }
    }

    public record Team(String name, Component displayName, Component prefix, Component suffix, Set<String> members) {
        public Team {
            members = Set.copyOf(members);
        }
    }
}
