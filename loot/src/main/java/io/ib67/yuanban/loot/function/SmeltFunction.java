package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code furnace_smelt}：把物品替换为它的熔炼产物。JSON 字段
 * {@code use_input_count}（默认 {@code true}；为 {@code false} 时产物数量按配方输出而不是输入数量）。
 */
public record SmeltFunction(List<LootCondition> conditions, boolean useInputCount) implements LootFunction {
    public SmeltFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public SmeltFunction(List<LootCondition> conditions) {
        this(conditions, true);
    }

    public SmeltFunction() {
        this(List.of(), true);
    }
}
