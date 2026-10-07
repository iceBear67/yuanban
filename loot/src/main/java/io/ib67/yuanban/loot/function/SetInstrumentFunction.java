package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code set_instrument}：写入乐器（山羊角）。JSON 字段 {@code options}（乐器 ID 列表或标签）。
 */
public record SetInstrumentFunction(List<LootCondition> conditions,
                                    EnchantedOptions options) implements LootFunction {
    public SetInstrumentFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
