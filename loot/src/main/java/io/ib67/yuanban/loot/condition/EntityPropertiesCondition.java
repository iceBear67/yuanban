package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code entity_properties}：指定实体匹配谓词。JSON 字段 {@code predicate}（可选，原版
 * {@code EntityPredicate}）、{@code entity}（必填）。没有谓词时总是通过；有谓词时实体缺失不通过。
 */
public record EntityPropertiesCondition(Optional<EntityPredicate> predicate, EntityTarget entity) implements LootCondition {
    public EntityPropertiesCondition {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(entity, "entity");
    }

    /**
     * 对应原版 {@code LootItemEntityPropertyCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN, entity.contextParam());
    }
}
