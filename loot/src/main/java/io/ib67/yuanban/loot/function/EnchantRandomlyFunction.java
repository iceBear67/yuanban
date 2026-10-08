package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.minestom.server.item.enchant.Enchantment;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code enchant_randomly}：从候选附魔中随机选一个，以随机等级附魔。JSON 字段 {@code options}
 * （可选，附魔集合；缺省时为附魔注册表中的全部附魔）、{@code only_compatible}（默认 {@code true}）、
 * {@code include_additional_cost_component}（默认 {@code false}）。
 */
public record EnchantRandomlyFunction(List<LootCondition> conditions,
                                      Optional<HolderSet<Enchantment>> options,
                                      boolean onlyCompatible,
                                      boolean includeAdditionalCostComponent) implements LootFunction {
    public EnchantRandomlyFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(options, "options");
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
