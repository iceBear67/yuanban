package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版 {@code set_enchantments}：改写物品的魔咒。JSON 字段 {@code enchantments}
 * （魔咒 ID 到数字提供器的映射）、{@code add}（默认 {@code false}；为 {@code true} 时叠加
 * 而不是覆盖）。
 */
public record SetEnchantmentsFunction(List<LootCondition> conditions,
                                      Map<Key, NumberProvider> enchantments,
                                      boolean add) implements LootFunction {
    public SetEnchantmentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }

    public SetEnchantmentsFunction(List<LootCondition> conditions, Map<Key, NumberProvider> enchantments) {
        this(conditions, enchantments, false);
    }
}
