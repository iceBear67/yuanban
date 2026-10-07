package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 原版 {@code item}：产出一个指定物品。JSON 独有字段 {@code name}。
 */
public record ItemEntry(List<LootCondition> conditions,
                        List<LootFunction> functions,
                        int weight,
                        int quality,
                        Key name) implements SingletonEntry {
    public ItemEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }

    public ItemEntry(Key name) {
        this(List.of(), List.of(), 1, 0, name);
    }
}
