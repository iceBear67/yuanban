package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 原版 {@code enchantment_active_check}：期望魔咒激活与否。JSON 字段 {@code active}。
 */
public record EnchantmentActiveCheckCondition(boolean active) implements LootCondition {

    /**
     * 对应原版 {@code EnchantmentActiveCheck#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ENCHANTMENT_ACTIVE);
    }
}
