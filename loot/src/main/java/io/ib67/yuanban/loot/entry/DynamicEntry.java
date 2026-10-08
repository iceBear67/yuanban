package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code dynamic}：从宿主动态取材。JSON 独有字段 {@code name}（任意命名空间 ID；
 * 原版方块使用 {@code contents} 和 {@code sherds}）。
 */
public record DynamicEntry(List<LootCondition> conditions,
                           List<LootFunction> functions,
                           int weight,
                           int quality,
                           Key name) implements SingletonEntry {
    public DynamicEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
        Objects.requireNonNull(name, "name");
    }
}
