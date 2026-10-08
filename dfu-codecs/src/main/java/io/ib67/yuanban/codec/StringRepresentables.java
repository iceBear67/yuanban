package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 原版 {@code net.minecraft.util.StringRepresentable} 的编解码器。原版要求取值类型实现
 * {@code getSerializedName()}；这里由调用者给出取值到名字的映射，使 Minestom 等外部类型也能使用。
 * <p>
 * 非压缩格式（JSON）按名字解码，未知名字的错误为 {@code Unknown element name:<name>}；
 * 压缩格式按数组下标解码，与原版相同。
 */
public final class StringRepresentables {
    /**
     * 原版 {@code StringRepresentable.PRE_BUILT_MAP_THRESHOLD}：取值个数超过它时用哈希表查找名字。
     */
    private static final int PRE_BUILT_MAP_THRESHOLD = 16;

    private StringRepresentables() {
    }

    /**
     * 原版 {@code StringRepresentable.fromEnum}：压缩格式按 {@link Enum#ordinal()} 编码。
     */
    public static <E extends Enum<E>> Codec<E> fromEnum(E[] values, Function<E, String> serializedName) {
        var lookup = createNameLookup(values, serializedName);
        return ExtraCodecs.orCompressed(
                Codec.stringResolver(serializedName, lookup::apply),
                ExtraCodecs.idResolverCodec(Enum::ordinal, i -> i >= 0 && i < values.length ? values[i] : null, -1));
    }

    /**
     * 原版 {@code StringRepresentable.fromValues}：压缩格式按数组下标编码。
     */
    public static <T> Codec<T> fromValues(T[] values, Function<T, String> serializedName) {
        var lookup = createNameLookup(values, serializedName);
        var index = List.of(values);
        return ExtraCodecs.orCompressed(
                Codec.stringResolver(serializedName, lookup::apply),
                ExtraCodecs.idResolverCodec(index::indexOf, i -> i >= 0 && i < values.length ? values[i] : null, -1));
    }

    /**
     * 原版 {@code StringRepresentable.createNameLookup}。
     */
    public static <T> Function<String, @Nullable T> createNameLookup(T[] values, Function<T, String> serializedName) {
        if (values.length > PRE_BUILT_MAP_THRESHOLD) {
            Map<String, T> byName = Arrays.stream(values).collect(Collectors.toMap(serializedName, d -> d));
            return byName::get;
        }
        return id -> {
            for (T value : values) {
                if (serializedName.apply(value).equals(id)) {
                    return value;
                }
            }
            return null;
        };
    }

    /**
     * 原版 {@code StringRepresentable.keys}，供 {@link Codec#simpleMap} 使用。
     */
    public static <T> Keyable keys(T[] values, Function<T, String> serializedName) {
        return new Keyable() {
            @Override
            public <D> Stream<D> keys(DynamicOps<D> ops) {
                return Arrays.stream(values).map(serializedName).map(ops::createString);
            }
        };
    }
}
