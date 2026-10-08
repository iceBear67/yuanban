package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code apply_bonus}：按工具上的附魔等级加成物品数量。JSON 字段 {@code enchantment}
 * （附魔 ID，按附魔注册表检查）、{@code formula}（三选一），公式的参数在原版 {@code parameters} 对象里，
 * 由 {@link ApplyBonusFormula} 承载。
 */
public record ApplyBonusFunction(List<LootCondition> conditions,
                                 RegistryKey<Enchantment> enchantment,
                                 ApplyBonusFormula formula) implements LootFunction {
    public ApplyBonusFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(enchantment, "enchantment");
        Objects.requireNonNull(formula, "formula");
    }

    /**
     * 对应原版 {@code ApplyBonusCount#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.TOOL);
    }
}
