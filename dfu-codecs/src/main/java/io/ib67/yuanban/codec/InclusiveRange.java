package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.NotNull;

/**
 * 原版 {@code net.minecraft.util.InclusiveRange}：两端包含的范围。
 */
public record InclusiveRange<T extends Comparable<T>>(T minInclusive, T maxInclusive) {
    public static final Codec<InclusiveRange<Integer>> INT = codec(Codec.INT);

    public InclusiveRange {
        if (minInclusive.compareTo(maxInclusive) > 0) {
            throw new IllegalArgumentException("min_inclusive must be less than or equal to max_inclusive");
        }
    }

    public InclusiveRange(T value) {
        this(value, value);
    }

    /**
     * 原版 {@code InclusiveRange.codec(Codec)}：单个值、{@code [min, max]}，
     * 或者 {@code {"min_inclusive": min, "max_inclusive": max}}。
     */
    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> elementCodec) {
        return ExtraCodecs.intervalCodec(elementCodec, "min_inclusive", "max_inclusive",
                InclusiveRange::create, InclusiveRange::minInclusive, InclusiveRange::maxInclusive);
    }

    /**
     * 原版 {@code InclusiveRange.codec(Codec, T, T)}：再检查范围在 {@code [minAllowed, maxAllowed]} 之内。
     */
    public static <T extends Comparable<T>> Codec<InclusiveRange<T>> codec(Codec<T> elementCodec, T minAllowedInclusive, T maxAllowedInclusive) {
        return codec(elementCodec).validate(value -> {
            if (value.minInclusive().compareTo(minAllowedInclusive) < 0) {
                return DataResult.error(() -> "Range limit too low, expected at least " + minAllowedInclusive
                        + " [" + value.minInclusive() + "-" + value.maxInclusive() + "]");
            }
            return value.maxInclusive().compareTo(maxAllowedInclusive) > 0
                    ? DataResult.error(() -> "Range limit too high, expected at most " + maxAllowedInclusive
                    + " [" + value.minInclusive() + "-" + value.maxInclusive() + "]")
                    : DataResult.success(value);
        });
    }

    /**
     * 原版 {@code InclusiveRange.create}。
     */
    public static <T extends Comparable<T>> DataResult<InclusiveRange<T>> create(T minInclusive, T maxInclusive) {
        return minInclusive.compareTo(maxInclusive) <= 0
                ? DataResult.success(new InclusiveRange<>(minInclusive, maxInclusive))
                : DataResult.error(() -> "min_inclusive must be less than or equal to max_inclusive");
    }

    public boolean isValueInRange(T value) {
        return value.compareTo(minInclusive) >= 0 && value.compareTo(maxInclusive) <= 0;
    }

    @Override
    public @NotNull String toString() {
        return "[" + minInclusive + ", " + maxInclusive + "]";
    }
}
