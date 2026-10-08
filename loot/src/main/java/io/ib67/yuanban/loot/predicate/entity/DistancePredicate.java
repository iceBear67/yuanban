package io.ib67.yuanban.loot.predicate.entity;

import net.minestom.server.utils.Range;

import java.util.Objects;

/**
 * 距离谓词，对应原版 {@code net.minecraft.advancements.predicates.DistancePredicate}。
 * {@code x}、{@code y}、{@code z} 比较各轴距离的绝对值，{@code horizontal}、{@code absolute} 先把范围平方再比较。
 */
public record DistancePredicate(Range.Double x, Range.Double y, Range.Double z, Range.Double horizontal,
                                Range.Double absolute) {
    public DistancePredicate {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
        Objects.requireNonNull(horizontal, "horizontal");
        Objects.requireNonNull(absolute, "absolute");
    }
}
