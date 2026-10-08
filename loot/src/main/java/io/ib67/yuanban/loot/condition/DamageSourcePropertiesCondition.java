package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.predicate.DamageSourcePredicate;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code damage_source_properties}：伤害来源匹配。JSON 字段 {@code predicate}
 * （可选，原版 {@code DamageSourcePredicate}）。上下文缺少伤害来源或 {@code origin} 时不通过。
 */
public record DamageSourcePropertiesCondition(Optional<DamageSourcePredicate> predicate) implements LootCondition {
    public DamageSourcePropertiesCondition {
        Objects.requireNonNull(predicate, "predicate");
    }

    /**
     * 对应原版 {@code DamageSourceCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN, ContextKey.DAMAGE_SOURCE);
    }
}
