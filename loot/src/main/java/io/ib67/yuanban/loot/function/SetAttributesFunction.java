package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code set_attributes}：向物品的属性修饰符组件加入修饰符。JSON 字段 {@code modifiers}、
 * {@code replace}（默认 {@code true}；为 {@code true} 时从空列表开始，否则在物品现有的修饰符上追加）。
 */
public record SetAttributesFunction(List<LootCondition> conditions,
                                    List<AttributeModifierRecord> modifiers,
                                    boolean replace) implements LootFunction {
    public SetAttributesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public SetAttributesFunction(List<LootCondition> conditions, List<AttributeModifierRecord> modifiers) {
        this(conditions, modifiers, true);
    }
}
