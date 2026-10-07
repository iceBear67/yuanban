package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.NumberProvider;

/**
 * 原版 {@code value_check}：数字提供器取值落在 {@code IntRange} 区间内。JSON 字段 {@code value}、
 * {@code range}（原版 {@code IntRange}）。
 */
public record ValueCheckCondition(NumberProvider value, IntRange range) implements LootCondition {
}
