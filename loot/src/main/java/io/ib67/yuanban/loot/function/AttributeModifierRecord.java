package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * {@code set_attributes} 的一项修饰器：原版 {@code SetAttributesFunction$Modifier}。
 * JSON 字段 {@code id}、{@code attribute}、{@code operation}、{@code amount}、{@code slot}
 * （原版编码为单值或列表，解析时归一为列表）。
 */
public record AttributeModifierRecord(Key id,
                                      Key attribute,
                                      AttributeOperation operation,
                                      NumberProvider amount,
                                      List<EquipmentSlotGroup> slots) {
    public AttributeModifierRecord {
        slots = List.copyOf(slots);
    }
}
