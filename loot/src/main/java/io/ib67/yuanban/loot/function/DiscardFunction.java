package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code discard}：直接丢弃物品。原版 JSON 无字段。
 */
public record DiscardFunction(List<LootCondition> conditions) implements LootFunction {
    public DiscardFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public DiscardFunction() {
        this(List.of());
    }
}
