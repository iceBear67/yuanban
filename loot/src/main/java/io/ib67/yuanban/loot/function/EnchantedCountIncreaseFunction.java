package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Set;

/**
 * 原版 {@code enchanted_count_increase}：按魔咒等级增加值。JSON 字段 {@code enchantment}、
 * {@code count}（数字提供器）、{@code limit}（默认 {@code 0}，表示不限制）。
 */
public record EnchantedCountIncreaseFunction(List<LootCondition> conditions,
                                             Key enchantment,
                                             NumberProvider count,
                                             int limit) implements LootFunction {
    public EnchantedCountIncreaseFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public EnchantedCountIncreaseFunction(List<LootCondition> conditions,
                                          Key enchantment,
                                          NumberProvider count) {
        this(conditions, enchantment, count, 0);
    }

    /**
     * 对应原版 {@code EnchantedCountIncreaseFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ATTACKING_ENTITY);
    }
}
