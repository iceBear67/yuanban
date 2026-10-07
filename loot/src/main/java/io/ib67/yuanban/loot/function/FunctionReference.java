package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code reference}：仅记录 item_modifier 的 ID，运行时经解析器查找。JSON 字段 {@code name}。
 */
public record FunctionReference(List<LootCondition> conditions, Key name) implements LootFunction {
    public FunctionReference {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
