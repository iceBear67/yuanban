package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版 {@code set_enchantments}：设置物品的附魔等级。JSON 字段 {@code enchantments}
 * （可选，附魔 ID 到等级数字提供器的映射，缺省为空，保持 JSON 中的顺序）、{@code add}（默认 {@code false}；
 * 为 {@code true} 时在现有等级上增加）。普通的书先变成附魔书。
 */
public record SetEnchantmentsFunction(List<LootCondition> conditions,
                                      Map<RegistryKey<Enchantment>, NumberProvider> enchantments,
                                      boolean add) implements LootFunction {
    public SetEnchantmentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        enchantments = enchantments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
    }

    public SetEnchantmentsFunction(List<LootCondition> conditions, Map<RegistryKey<Enchantment>, NumberProvider> enchantments) {
        this(conditions, enchantments, false);
    }
}
