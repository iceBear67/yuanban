package io.ib67.yuanban.crafting.recipe.kind.misc;

import java.util.Optional;

/**
 * 整数范围，对应原版 {@code MinMaxBounds.Ints}。两端都可以缺省。
 */
public record IntBounds(Optional<Integer> min, Optional<Integer> max) {
    public static IntBounds exactly(int value) {
        return new IntBounds(Optional.of(value), Optional.of(value));
    }

    public static IntBounds between(int min, int max) {
        return new IntBounds(Optional.of(min), Optional.of(max));
    }

    /**
     * 这个范围是否在 {@code [lowest, highest]} 之内。缺省的一端没有限制，所以缺省任何一端都不在有限的范围之内，
     * 和原版 {@code MinMaxBounds.validateContainedInRange} 相同。
     */
    public boolean within(int lowest, int highest) {
        return min.isPresent() && min.get() >= lowest && max.isPresent() && max.get() <= highest;
    }

    /**
     * 原版 {@code MinMaxBounds.Ints.matches}：缺省的一端没有限制。
     */
    public boolean matches(int value) {
        return (min.isEmpty() || min.get() <= value) && (max.isEmpty() || max.get() >= value);
    }
}
