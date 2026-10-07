package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.NumberProvider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code enchant_with_levels}：按等级随机附魔。JSON 字段 {@code levels}（数字提供器）、
 * {@code options}（可选；缺省时全部非宝藏魔咒）、
 * {@code include_additional_cost_component}（默认 {@code false}）。
 */
public record EnchantWithLevelsFunction(List<LootCondition> conditions,
                                        NumberProvider levels,
                                        Optional<EnchantedOptions> options,
                                        boolean includeAdditionalCostComponent) implements LootFunction {
    public EnchantWithLevelsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        options = options == null ? Optional.empty() : options;
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
