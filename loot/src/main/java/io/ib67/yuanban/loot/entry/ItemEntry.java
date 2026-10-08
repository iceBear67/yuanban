package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.minestom.server.item.Material;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code item}：产出一个指定物品。JSON 独有字段 {@code name}（物品 ID，按物品注册表解码，不接受空气）。
 */
public record ItemEntry(List<LootCondition> conditions,
                        List<LootFunction> functions,
                        int weight,
                        int quality,
                        Material name) implements SingletonEntry {
    public ItemEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
        Objects.requireNonNull(name, "name");
    }

    public ItemEntry(Material name) {
        this(List.of(), List.of(), 1, 0, name);
    }
}
