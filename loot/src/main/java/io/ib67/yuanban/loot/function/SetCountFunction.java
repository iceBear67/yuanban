package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_count}：设定物品数量。JSON 字段 {@code count}（数字提供器）、{@code add}（默认
 * {@code false}；为 {@code true} 时在原数量上叠加）。
 */
public record SetCountFunction(List<LootCondition> conditions, NumberProvider count, boolean add)
        implements LootFunction {
    public SetCountFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public SetCountFunction(List<LootCondition> conditions, NumberProvider count) {
        this(conditions, count, false);
    }
}
