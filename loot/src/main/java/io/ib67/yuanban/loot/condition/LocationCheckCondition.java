package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.predicate.LocationPredicate;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code location_check}：{@code origin} 加上偏移之后的位置匹配谓词。JSON 字段 {@code predicate}（可选）、
 * {@code offsetX} / {@code offsetY} / {@code offsetZ}（默认 0）。上下文缺少 {@code origin} 时不通过，
 * 没有谓词时通过。
 */
public record LocationCheckCondition(Optional<LocationPredicate> predicate,
                                     int offsetX, int offsetY, int offsetZ) implements LootCondition {
    public LocationCheckCondition {
        Objects.requireNonNull(predicate, "predicate");
    }

    /**
     * 对应原版 {@code LocationCheck#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN);
    }
}
