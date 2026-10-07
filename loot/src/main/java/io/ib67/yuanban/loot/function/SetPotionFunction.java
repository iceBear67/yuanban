package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code set_potion}：写入药水 ID。JSON 字段 {@code id}。
 */
public record SetPotionFunction(List<LootCondition> conditions, Key id) implements LootFunction {
    public SetPotionFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
