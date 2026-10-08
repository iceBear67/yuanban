package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code table_bonus}：按工具上的附魔等级在表中查概率。JSON 字段 {@code enchantment}
 * （附魔 ID，按附魔注册表检查）、{@code chances}（非空的浮点数列表）。
 */
public record TableBonusCondition(RegistryKey<Enchantment> enchantment, List<Float> values) implements LootCondition {
    public TableBonusCondition {
        Objects.requireNonNull(enchantment, "enchantment");
        values = List.copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("chances must not be empty");
        }
    }

    /**
     * 对应原版 {@code BonusLevelTableCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.TOOL);
    }
}
