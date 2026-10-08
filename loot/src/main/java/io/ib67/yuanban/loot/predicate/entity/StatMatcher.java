package io.ib67.yuanban.loot.predicate.entity;

import net.kyori.adventure.key.Key;
import net.minestom.server.statistic.StatisticCategory;
import net.minestom.server.utils.Range;

import java.util.Objects;

/**
 * 玩家统计谓词，对应原版 {@code PlayerPredicate$StatMatcher}。JSON 字段 {@code type}（统计类型）、
 * {@code stat}（统计项，按统计类型在方块、物品、实体类型或自定义统计注册表中查找）、{@code value}（数值范围）。
 * 玩家没有这项统计时按 0 比较，与原版 {@code StatsCounter#getValue} 相同。
 *
 * @param category 统计类型，原版 {@code stat_type} 注册表的 9 种
 * @param stat     统计项的 ID，解码时已经确认存在
 */
public record StatMatcher(StatisticCategory category, Key stat, Range.Int value) {
    public StatMatcher {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(value, "value");
    }
}
