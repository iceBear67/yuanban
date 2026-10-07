package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;

import java.util.List;

/**
 * 原版 {@code empty}：不产出任何物品，权重通常用来"空气位"。无独有字段。
 */
public record EmptyEntry(List<LootCondition> conditions,
                         List<LootFunction> functions,
                         int weight,
                         int quality) implements SingletonEntry {
    public EmptyEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }

    public EmptyEntry() {
        this(List.of(), List.of(), 1, 0);
    }
}
