package io.ib67.yuanban.codec;

import com.google.common.collect.Range;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * 原版 {@code net.minecraft.advancements.predicates.MinMaxBounds}：{@code {"min": a, "max": b}} 的对象，
 * 或者一个数字（上下界相同）；上下界都可以省略。结果是 Minestom 的 {@link net.minestom.server.utils.Range}，
 * 省略的一端为 {@code null}。
 */
public final class MinMaxBounds {
    /**
     * 原版 {@code MinMaxBounds.Ints.CODEC}。
     */
    public static final Codec<net.minestom.server.utils.Range.Int> INTS = Bounds.codec(Codec.INT)
            .validate(Bounds::validateSwappedBoundsInCodec)
            .xmap(b -> new net.minestom.server.utils.Range.Int(b.min().orElse(null), b.max().orElse(null)),
                    r -> new Bounds<>(Optional.ofNullable(r.min()), Optional.ofNullable(r.max())));
    /**
     * 原版 {@code MinMaxBounds.Doubles.CODEC}。
     */
    public static final Codec<net.minestom.server.utils.Range.Double> DOUBLES = Bounds.codec(Codec.DOUBLE)
            .validate(Bounds::validateSwappedBoundsInCodec)
            .xmap(b -> new net.minestom.server.utils.Range.Double(b.min().orElse(null), b.max().orElse(null)),
                    r -> new Bounds<>(Optional.ofNullable(r.min()), Optional.ofNullable(r.max())));
    /**
     * 原版 {@code MinMaxBounds.Ints.ANY}。
     */
    public static final net.minestom.server.utils.Range.Int ANY_INT = new net.minestom.server.utils.Range.Int(null, null);
    /**
     * 原版 {@code MinMaxBounds.Doubles.ANY}。
     */
    public static final net.minestom.server.utils.Range.Double ANY_DOUBLE = new net.minestom.server.utils.Range.Double(null, null);

    private MinMaxBounds() {
    }

    /**
     * 原版 {@code MinMaxBounds.validateContainedInRange}：
     * {@code Range must be within <allowed>, but was <self>}，两个范围按 Guava {@link Range#toString()} 打印。
     */
    public static Function<net.minestom.server.utils.Range.Int, DataResult<net.minestom.server.utils.Range.Int>> validateContainedInRange(
            net.minestom.server.utils.Range.Int allowed) {
        Range<Integer> allowedRange = asRange(allowed.min(), allowed.max());
        return target -> {
            Range<Integer> selfAsRange = asRange(target.min(), target.max());
            return !allowedRange.encloses(selfAsRange)
                    ? DataResult.error(() -> "Range must be within " + allowedRange + ", but was " + selfAsRange)
                    : DataResult.success(target);
        };
    }

    /**
     * 原版 {@code MinMaxBounds.Bounds.asRange}。
     */
    public static <T extends Comparable<? super T>> Range<T> asRange(@Nullable T min, @Nullable T max) {
        if (min != null) {
            return max != null ? Range.closed(min, max) : Range.atLeast(min);
        }
        return max != null ? Range.atMost(max) : Range.all();
    }

    /**
     * 原版 {@code MinMaxBounds.Ints.matchesSqr}：上下界先平方再比较，负数的上下界同样平方。
     */
    public static boolean matchesSqr(net.minestom.server.utils.Range.Int bounds, long valueSqr) {
        if (bounds.min() != null && (long) bounds.min() * bounds.min() > valueSqr) {
            return false;
        }
        return bounds.max() == null || (long) bounds.max() * bounds.max() >= valueSqr;
    }

    /**
     * 原版 {@code MinMaxBounds.Doubles.matchesSqr}：上下界先平方再比较，负数的上下界同样平方。
     */
    public static boolean matchesSqr(net.minestom.server.utils.Range.Double bounds, double valueSqr) {
        if (bounds.min() != null && bounds.min() * bounds.min() > valueSqr) {
            return false;
        }
        return bounds.max() == null || bounds.max() * bounds.max() >= valueSqr;
    }

    /**
     * 原版 {@code MinMaxBounds.Bounds}：上下界都可以省略。
     */
    private record Bounds<T extends Number & Comparable<T>>(Optional<T> min, Optional<T> max) {
        static <T extends Number & Comparable<T>> Codec<Bounds<T>> codec(Codec<T> numberCodec) {
            Codec<Bounds<T>> rangeCodec = RecordCodecBuilder.create(i -> i.group(
                    numberCodec.optionalFieldOf("min").forGetter(Bounds::min),
                    numberCodec.optionalFieldOf("max").forGetter(Bounds::max)
            ).apply(i, Bounds::new));
            return Codec.either(rangeCodec, numberCodec).xmap(
                    either -> either.map(v -> v, v -> new Bounds<>(Optional.of(v), Optional.of(v))),
                    bounds -> bounds.min().equals(bounds.max()) && bounds.min().isPresent()
                            ? Either.right(bounds.min().get())
                            : Either.left(bounds));
        }

        DataResult<Bounds<T>> validateSwappedBoundsInCodec() {
            return min.isPresent() && max.isPresent() && min.get().compareTo(max.get()) > 0
                    ? DataResult.error(() -> "Swapped bounds in range: " + min + " is higher than " + max)
                    : DataResult.success(this);
        }
    }
}
