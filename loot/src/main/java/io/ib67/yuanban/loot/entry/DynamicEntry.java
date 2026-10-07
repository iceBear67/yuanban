package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code dynamic}：从宿主动态取材。JSON 独有字段 {@code name}
 * （取值 {@code contents} 或 {@code sherds}）。
 */
public record DynamicEntry(List<LootCondition> conditions,
                           List<LootFunction> functions,
                           int weight,
                           int quality,
                           Key name) implements SingletonEntry {
    public DynamicEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }
}
