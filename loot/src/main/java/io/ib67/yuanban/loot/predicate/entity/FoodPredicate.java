package io.ib67.yuanban.loot.predicate.entity;

import net.minestom.server.utils.Range;

import java.util.Objects;

/**
 * 饥饿谓词，对应原版 {@code net.minecraft.advancements.predicates.FoodPredicate}。
 *
 * @param level      饥饿值
 * @param saturation 饱和度
 */
public record FoodPredicate(Range.Int level, Range.Double saturation) {
    /**
     * 原版 {@code FoodPredicate.ANY}。
     */
    public static final FoodPredicate ANY = new FoodPredicate(new Range.Int(null, null), new Range.Double(null, null));

    public FoodPredicate {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(saturation, "saturation");
    }
}
