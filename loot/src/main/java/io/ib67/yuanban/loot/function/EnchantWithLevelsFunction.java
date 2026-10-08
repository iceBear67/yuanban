package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.minestom.server.item.enchant.Enchantment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code enchant_with_levels}：按附魔台的规则以给定的经验等级附魔。JSON 字段 {@code levels}（数字提供器）、
 * {@code options}（可选，附魔集合；缺省时为附魔注册表中的全部附魔）、
 * {@code include_additional_cost_component}（默认 {@code false}）。
 */
public record EnchantWithLevelsFunction(List<LootCondition> conditions,
                                        NumberProvider levels,
                                        Optional<HolderSet<Enchantment>> options,
                                        boolean includeAdditionalCostComponent) implements LootFunction {
    public EnchantWithLevelsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(options, "options");
    }

    public EnchantWithLevelsFunction(List<LootCondition> conditions, NumberProvider levels) {
        this(conditions, levels, Optional.empty(), false);
    }

    /**
     * 对应原版 {@code EnchantWithLevelsFunction#getReferencedContextParams}：仅
     * {@code include_additional_cost_component} 为 {@code true} 时引用
     * {@code ADDITIONAL_COST_COMPONENT_ALLOWED}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return includeAdditionalCostComponent
                ? Set.of(ContextKey.ADDITIONAL_COST_COMPONENT_ALLOWED)
                : Set.of();
    }
}
