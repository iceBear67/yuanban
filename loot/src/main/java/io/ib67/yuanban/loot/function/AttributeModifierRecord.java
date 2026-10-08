package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.List;
import java.util.Objects;

/**
 * {@code set_attributes} 的一项修饰符：原版 {@code SetAttributesFunction$Modifier}。
 * JSON 字段 {@code id}、{@code attribute}（属性 ID，按属性注册表解码）、{@code operation}、{@code amount}、
 * {@code slot}（单个槽位组或非空的槽位组列表，求值时随机取一个）。
 */
public record AttributeModifierRecord(Key id,
                                      Attribute attribute,
                                      AttributeOperation operation,
                                      NumberProvider amount,
                                      List<EquipmentSlotGroup> slots) {
    public AttributeModifierRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(amount, "amount");
        slots = List.copyOf(slots);
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("slots must not be empty");
        }
    }
}
