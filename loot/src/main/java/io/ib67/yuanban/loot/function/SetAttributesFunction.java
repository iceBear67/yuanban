package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_attributes}：重写物品的属性修饰器列表。JSON 字段 {@code modifiers}、
 * {@code replace}（默认 {@code false}；为 {@code true} 时覆盖原列表而非追加）。
 */
public record SetAttributesFunction(List<LootCondition> conditions,
                                    List<AttributeModifierRecord> modifiers,
                                    boolean replace) implements LootFunction {
    public SetAttributesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public SetAttributesFunction(List<LootCondition> conditions, List<AttributeModifierRecord> modifiers) {
        this(conditions, modifiers, false);
    }
}
