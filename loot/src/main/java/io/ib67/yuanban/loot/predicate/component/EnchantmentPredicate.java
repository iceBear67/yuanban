package io.ib67.yuanban.loot.predicate.component;

import io.ib67.yuanban.codec.HolderSet;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.utils.Range;

import java.util.Objects;
import java.util.Optional;

/**
 * 附魔谓词，对应原版 {@code net.minecraft.advancements.predicates.EnchantmentPredicate}。
 *
 * @param enchantments JSON 字段 {@code enchantments}：附魔集合，缺省时任一附魔都可以
 * @param levels       JSON 字段 {@code levels}：等级范围，缺省不限
 */
public record EnchantmentPredicate(Optional<HolderSet<Enchantment>> enchantments, Range.Int levels) {
    public EnchantmentPredicate {
        Objects.requireNonNull(enchantments, "enchantments");
        Objects.requireNonNull(levels, "levels");
    }
}
