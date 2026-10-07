package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code set_item}：替换物品 ID，保留数量与组件。JSON 字段 {@code id}。
 */
public record SetItemFunction(List<LootCondition> conditions, Key id) implements LootFunction {
    public SetItemFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
