package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EntityTarget;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code entity_properties}：指定实体匹配谓词。JSON 字段 {@code predicate}（原版
 * {@code EntityPredicate}）、{@code entity}（默认 {@code this}）。
 */
public record EntityPropertiesCondition(Optional<EntityPredicate> predicate, EntityTarget entity) implements LootCondition {
    public EntityPropertiesCondition {
        predicate = predicate == null ? Optional.empty() : predicate;
        entity = entity == null ? EntityTarget.THIS : entity;
    }

    public EntityPropertiesCondition(@Nullable EntityPredicate predicate, @Nullable EntityTarget entity) {
        this(Optional.ofNullable(predicate), entity);
    }

    /**
     * 对应原版 {@code LootItemEntityPropertyCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN, entity.contextParam());
    }
}
