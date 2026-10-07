package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code damage_source_properties}：伤害来源匹配。JSON 字段 {@code predicate}
 * （原版 {@code DamageSourcePredicate}）。
 */
public record DamageSourcePropertiesCondition(Optional<DamageSourcePredicate> predicate) implements LootCondition {
    public DamageSourcePropertiesCondition {
        predicate = predicate == null ? Optional.empty() : predicate;
    }

    public DamageSourcePropertiesCondition(@Nullable DamageSourcePredicate predicate) {
        this(Optional.ofNullable(predicate));
    }

    /**
     * 对应原版 {@code DamageSourceCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN, ContextKey.DAMAGE_SOURCE);
    }
}
