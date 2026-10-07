package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code location_check}：判定位置匹配谓词并允许偏移。JSON 字段 {@code predicate}、
 * {@code offsetX} / {@code offsetY} / {@code offsetZ}（默认 0）。
 */
public record LocationCheckCondition(Optional<LocationPredicate> predicate,
                                     int offsetX, int offsetY, int offsetZ) implements LootCondition {
    public LocationCheckCondition {
        predicate = predicate == null ? Optional.empty() : predicate;
    }

    public LocationCheckCondition(@Nullable LocationPredicate predicate, int offsetX, int offsetY, int offsetZ) {
        this(Optional.ofNullable(predicate), offsetX, offsetY, offsetZ);
    }

    public LocationCheckCondition(@Nullable LocationPredicate predicate) {
        this(predicate, 0, 0, 0);
    }

    /**
     * 对应原版 {@code LocationCheck#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.ORIGIN);
    }
}
