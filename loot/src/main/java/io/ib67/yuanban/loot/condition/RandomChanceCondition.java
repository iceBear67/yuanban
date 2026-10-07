package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.number.NumberProvider;

/**
 * 原版 {@code random_chance}：按概率通过。JSON 字段 {@code chance}，可以是裸浮点数（解析时归约为
 * {@code constant}）。原版允许负数，按不通过处理。
 */
public record RandomChanceCondition(NumberProvider chance) implements LootCondition {
}
