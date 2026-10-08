package io.ib67.yuanban.loot.predicate.component;

import io.ib67.yuanban.codec.HolderSet;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.utils.Range;

import java.util.Objects;
import java.util.Optional;

/**
 * 属性修饰符谓词，对应原版 {@code AttributeModifiersPredicate$EntryPredicate}。
 *
 * @param attribute JSON 字段 {@code attribute}：属性集合
 * @param id        JSON 字段 {@code id}：修饰符 ID
 * @param amount    JSON 字段 {@code amount}：数值范围，缺省不限
 * @param operation JSON 字段 {@code operation}
 * @param slot      JSON 字段 {@code slot}
 */
public record AttributeEntryPredicate(Optional<HolderSet<Attribute>> attribute, Optional<Key> id, Range.Double amount,
                                      Optional<AttributeOperation> operation, Optional<EquipmentSlotGroup> slot) {
    public AttributeEntryPredicate {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(slot, "slot");
    }
}
