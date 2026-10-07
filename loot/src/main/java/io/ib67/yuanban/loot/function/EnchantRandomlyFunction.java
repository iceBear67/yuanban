package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code enchant_randomly}：随机附魔。JSON 字段 {@code options}（可选；缺省时所有魔咒）、
 * {@code only_compatible}（默认 {@code true}）、
 * {@code include_additional_cost_component}（默认 {@code false}）。
 */
public record EnchantRandomlyFunction(List<LootCondition> conditions,
                                      Optional<EnchantedOptions> options,
                                      boolean onlyCompatible,
                                      boolean includeAdditionalCostComponent) implements LootFunction {
    public EnchantRandomlyFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        options = options == null ? Optional.empty() : options;
    }

    public EnchantRandomlyFunction(List<LootCondition> conditions, EnchantedOptions options) {
        this(conditions, Optional.ofNullable(options), true, false);
    }

    /**
     * 对应原版 {@code EnchantRandomlyFunction#getReferencedContextParams}：仅
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
