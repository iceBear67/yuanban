package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code entity_scores}：指定实体的计分项都落在区间内。JSON 字段 {@code scores}（计分项到
 * {@code IntRange} 的映射，保持 JSON 中的顺序）、{@code entity}（必填）。实体缺失时不通过。
 */
public record EntityScoresCondition(Map<String, IntRange> scores, EntityTarget entity) implements LootCondition {
    public EntityScoresCondition {
        scores = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
        Objects.requireNonNull(entity, "entity");
    }

    /**
     * 对应原版 {@code EntityHasScoreCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(entity.contextParam());
    }
}
