package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.IntRange;

import java.util.List;

/**
 * 原版 {@code limit_count}：把数量限制在区间内。JSON 字段 {@code limit}（原版 {@code IntRange}）。
 */
public record LimitCountFunction(List<LootCondition> conditions, IntRange limit) implements LootFunction {
    public LimitCountFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
