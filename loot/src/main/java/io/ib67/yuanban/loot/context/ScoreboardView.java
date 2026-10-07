package io.ib67.yuanban.loot.context;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 记分板的只读视图：loot 求值期 {@code score} 数字提供器与 {@code entity_scores} 条件需要的
 * 最小读取面。原版对应物是 {@code ServerScoreboard} 的读取路径；Minestom 没有完整的记分板实现，
 * 底层数据结构由宿主决定。
 */
public interface ScoreboardView {
    /**
     * 读取某个记录名在指定计分项上的分值，对应原版 {@code ReadOnlyScoreInfo#value}；记录不存在时为空。
     * 原版只允许固定玩家名读取；{@code context}-目标实体的名字先经
     * {@link #getScoreboardName(Entity)} 转换（原版 {@code Entity#getScoreboardName}）。
     */
    OptionalInt getScore(String name, String objective);

    /**
     * 实体在记分板上的记录名：玩家是玩家名，其他实体是 UUID 字符串（原版
     * {@code Entity#getScoreboardName}）。实体不参与记分时为空。
     */
    Optional<String> getScoreboardName(@Nullable Entity entity);
}
