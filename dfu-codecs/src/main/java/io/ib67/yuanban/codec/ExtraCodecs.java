package io.ib67.yuanban.codec;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 原版 {@code net.minecraft.util.ExtraCodecs} 中数据包解码用到的部分，错误文本与原版相同。
 */
public final class ExtraCodecs {
    /**
     * 原版 {@code ExtraCodecs.UNSIGNED_BYTE}：按 byte 读取再按无符号解释，结果在 [0, 255]。
     */
    public static final Codec<Integer> UNSIGNED_BYTE = Codec.BYTE.flatComapMap(Byte::toUnsignedInt,
            integer -> integer > 255
                    ? DataResult.error(() -> "Unsigned byte was too large: " + integer + " > 255")
                    : DataResult.success(integer.byteValue()));
    public static final Codec<Integer> NON_NEGATIVE_INT = intRangeWithMessage(0, Integer.MAX_VALUE, n -> "Value must be non-negative: " + n);
    public static final Codec<Integer> POSITIVE_INT = intRangeWithMessage(1, Integer.MAX_VALUE, n -> "Value must be positive: " + n);
    public static final Codec<Long> NON_NEGATIVE_LONG = longRangeWithMessage(0L, Long.MAX_VALUE, n -> "Value must be non-negative: " + n);
    public static final Codec<Long> POSITIVE_LONG = longRangeWithMessage(1L, Long.MAX_VALUE, n -> "Value must be positive: " + n);
    public static final Codec<Float> NON_NEGATIVE_FLOAT = Codec.FLOAT.validate(value -> value.compareTo(0.0F) >= 0 && value.compareTo(Float.MAX_VALUE) <= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Value must be non-negative: " + value));
    public static final Codec<Float> POSITIVE_FLOAT = Codec.FLOAT.validate(value -> value.compareTo(0.0F) > 0 && value.compareTo(Float.MAX_VALUE) <= 0
            ? DataResult.success(value)
            : DataResult.error(() -> "Value must be positive: " + value));
    public static final Codec<String> NON_EMPTY_STRING = Codec.STRING.validate(value -> value.isEmpty()
            ? DataResult.error(() -> "Expected non-empty string")
            : DataResult.success(value));
    /**
     * 原版 {@code ExtraCodecs.PATTERN}。
     */
    public static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap(pattern -> {
        try {
            return DataResult.success(Pattern.compile(pattern));
        } catch (PatternSyntaxException e) {
            return DataResult.error(() -> "Invalid regex pattern '" + pattern + "': " + e.getMessage());
        }
    }, Pattern::pattern);
    /**
     * 原版 {@code ExtraCodecs.VECTOR3F}，元素为三个 float。
     */
    public static final Codec<List<Float>> VECTOR3F = Codec.FLOAT.listOf().comapFlatMap(input -> fixedSize(input, 3), Function.identity());
    /**
     * 原版 {@code ExtraCodecs.RGB_COLOR_CODEC}：整数，或者三个 [0, 1] 的 float 按 {@code ARGB.colorFromFloat(1, r, g, b)} 合成。
     */
    public static final Codec<Integer> RGB_COLOR_CODEC = Codec.withAlternative(Codec.INT, VECTOR3F,
            v -> colorFromFloat(1.0F, v.get(0), v.get(1), v.get(2)));
    /**
     * 原版 {@code ExtraCodecs.TAG_OR_ELEMENT_ID}：{@code #} 开头为标签，否则为元素。
     */
    public static final Codec<TagOrElementLocation> TAG_OR_ELEMENT_ID = Codec.STRING.comapFlatMap(
            name -> name.startsWith("#")
                    ? Identifiers.read(name.substring(1)).map(id -> new TagOrElementLocation(id, true))
                    : Identifiers.read(name).map(id -> new TagOrElementLocation(id, false)),
            TagOrElementLocation::decoratedId);
    /**
     * 原版 {@code ExtraCodecs.VECTOR4F}，元素为四个 float，依次是 x、y、z、w。
     */
    public static final Codec<List<Float>> VECTOR4F = Codec.FLOAT.listOf().comapFlatMap(input -> fixedSize(input, 4), Function.identity());
    /**
     * 原版 {@code ExtraCodecs.ARGB_COLOR_CODEC}：整数，或者四个 [0, 1] 的 float（r、g、b、a）按
     * {@code ARGB.colorFromFloat(a, r, g, b)} 合成。编码时总是写成整数。
     */
    public static final Codec<Integer> ARGB_COLOR_CODEC = Codec.withAlternative(Codec.INT, VECTOR4F,
            v -> colorFromFloat(v.get(3), v.get(0), v.get(1), v.get(2)));
    /**
     * 原版 {@code ExtraCodecs.JAVA}：把输入转换成 {@link JavaOps} 的 Java 对象（字符串、数字、布尔值、列表、映射）。
     */
    public static final Codec<Object> JAVA = converter(JavaOps.INSTANCE);
    /**
     * 原版 {@code ExtraCodecs.CHAT_STRING}：每个字符都要满足原版 {@code StringUtil.isAllowedChatCharacter}，
     * 否则为 {@code Disallowed chat character: '<c>'}。
     */
    public static final Codec<String> CHAT_STRING = Codec.STRING.validate(string -> {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (!isAllowedChatCharacter(c)) {
                return DataResult.error(() -> "Disallowed chat character: '" + c + "'");
            }
        }
        return DataResult.success(string);
    });
    /**
     * 原版 {@code ExtraCodecs.UNTRUSTED_URI}：原版 {@code Util.parseAndValidateUntrustedUri}，只接受
     * {@code http}、{@code https} 协议；错误文本是 {@link URISyntaxException#getMessage()}。
     */
    public static final Codec<URI> UNTRUSTED_URI = Codec.STRING.comapFlatMap(string -> {
        try {
            return DataResult.success(parseAndValidateUntrustedUri(string));
        } catch (URISyntaxException e) {
            return DataResult.error(e::getMessage);
        }
    }, URI::toString);
    /**
     * 原版 {@code ExtraCodecs.PLAYER_NAME}：长度 [0, 16]，并且满足原版 {@code StringUtil.isValidPlayerName}，
     * 否则为 {@code Player name contained disallowed characters: '<name>'}。
     */
    public static final Codec<String> PLAYER_NAME = Codec.string(0, 16).validate(name -> isValidPlayerName(name)
            ? DataResult.success(name)
            : DataResult.error(() -> "Player name contained disallowed characters: '" + name + "'"));

    /**
     * 原版 {@code Util.ALLOWED_UNTRUSTED_LINK_PROTOCOLS}。
     */
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");

    private ExtraCodecs() {
    }

    /**
     * 原版 {@code ExtraCodecs.converter}：解码时把输入转换到 {@code ops}，编码时从 {@code ops} 转换回来。
     */
    public static <T> Codec<T> converter(DynamicOps<T> ops) {
        return Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(ops).getValue(), value -> new Dynamic<>(ops, value));
    }

    /**
     * 原版 {@code ExtraCodecs.optionalAlwaysPresentFieldOf}：字段缺失时为 {@code defaultValue}，编码时总是写出。
     */
    public static <T> MapCodec<T> optionalAlwaysPresentFieldOf(Codec<T> codec, String name, T defaultValue) {
        return codec.optionalFieldOf(name).xmap(o -> o.orElse(defaultValue), Optional::of);
    }

    /**
     * 原版 {@code StringUtil.isAllowedChatCharacter}。
     */
    public static boolean isAllowedChatCharacter(int c) {
        return c != 167 && c >= 32 && c != 127;
    }

    /**
     * 原版 {@code StringUtil.isValidPlayerName}。
     */
    public static boolean isValidPlayerName(String name) {
        return name.length() <= 16 && name.chars().filter(c -> c <= 32 || c >= 127).findAny().isEmpty();
    }

    /**
     * 原版 {@code Util.parseAndValidateUntrustedUri}。
     *
     * @throws URISyntaxException 不是合法的 URI，或者协议缺失、不是 {@code http}/{@code https}
     */
    public static URI parseAndValidateUntrustedUri(String uri) throws URISyntaxException {
        URI parsed = new URI(uri);
        String scheme = parsed.getScheme();
        if (scheme == null) {
            throw new URISyntaxException(uri, "Missing protocol in URI: " + uri);
        }
        String protocol = scheme.toLowerCase(Locale.ROOT);
        if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(protocol)) {
            throw new URISyntaxException(uri, "Unsupported protocol in URI: " + uri);
        }
        return parsed;
    }

    private static Codec<Integer> intRangeWithMessage(int minInclusive, int maxInclusive, Function<Integer, String> error) {
        return Codec.INT.validate(value -> value.compareTo(minInclusive) >= 0 && value.compareTo(maxInclusive) <= 0
                ? DataResult.success(value)
                : DataResult.error(() -> error.apply(value)));
    }

    private static Codec<Long> longRangeWithMessage(long minInclusive, long maxInclusive, Function<Long, String> error) {
        return Codec.LONG.validate(value -> value.compareTo(minInclusive) >= 0 && value.compareTo(maxInclusive) <= 0
                ? DataResult.success(value)
                : DataResult.error(() -> error.apply(value)));
    }

    /**
     * 原版 {@code ExtraCodecs.intRange}：{@code Value must be within range [min;max]: n}。
     */
    public static Codec<Integer> intRange(int minInclusive, int maxInclusive) {
        return intRangeWithMessage(minInclusive, maxInclusive,
                n -> "Value must be within range [" + minInclusive + ";" + maxInclusive + "]: " + n);
    }

    /**
     * 原版 {@code ExtraCodecs.floatRange}（两端包含）：{@code Value must be within range [min;max]: n}。
     */
    public static Codec<Float> floatRange(float minInclusive, float maxInclusive) {
        return Codec.FLOAT.validate(value -> value.compareTo(minInclusive) >= 0 && value.compareTo(maxInclusive) <= 0
                ? DataResult.success(value)
                : DataResult.error(() -> "Value must be within range [" + minInclusive + ";" + maxInclusive + "]: " + value));
    }

    /**
     * 原版 {@code ExtraCodecs.nonEmptyList}：{@code List must have contents}。
     */
    public static <T> Codec<List<T>> nonEmptyList(Codec<List<T>> listCodec) {
        return listCodec.validate(list -> list.isEmpty()
                ? DataResult.error(() -> "List must have contents")
                : DataResult.success(list));
    }

    /**
     * 原版 {@code ExtraCodecs.nonEmptyHolderSet}：直接列表为空时 {@code List must have contents}，标签不检查。
     */
    public static <T> Codec<HolderSet<T>> nonEmptyHolderSet(Codec<HolderSet<T>> holderSetCodec) {
        return holderSetCodec.validate(set -> set instanceof HolderSet.Direct<T>(var keys) && keys.isEmpty()
                ? DataResult.error(() -> "List must have contents")
                : DataResult.success(set));
    }

    /**
     * 原版 {@code ExtraCodecs.nonEmptyMap}：{@code Map must have contents}。
     */
    public static <M extends Map<?, ?>> Codec<M> nonEmptyMap(Codec<M> mapCodec) {
        return mapCodec.validate(map -> map.isEmpty()
                ? DataResult.error(() -> "Map must have contents")
                : DataResult.success(map));
    }

    /**
     * 原版 {@code ExtraCodecs.sizeLimitedMap}：{@code Map is too long: n, expected range [0-max]}。
     */
    public static <K, V> Codec<Map<K, V>> sizeLimitedMap(Codec<Map<K, V>> codec, int maxSizeInclusive) {
        return codec.validate(map -> map.size() > maxSizeInclusive
                ? DataResult.error(() -> "Map is too long: " + map.size() + ", expected range [0-" + maxSizeInclusive + "]")
                : DataResult.success(map));
    }

    /**
     * 原版 {@code ExtraCodecs.compactListCodec(Codec)}：先按列表解码，失败再按单个元素解码成长度 1 的列表。
     */
    public static <E> Codec<List<E>> compactListCodec(Codec<E> elementCodec) {
        return compactListCodec(elementCodec, elementCodec.listOf());
    }

    /**
     * 原版 {@code ExtraCodecs.compactListCodec(Codec, Codec)}。
     */
    public static <E> Codec<List<E>> compactListCodec(Codec<E> elementCodec, Codec<List<E>> listCodec) {
        return Codec.either(listCodec, elementCodec).xmap(
                e -> e.map(l -> l, List::of),
                v -> v.size() == 1 ? Either.right(v.getFirst()) : Either.left(v));
    }

    /**
     * 原版 {@code ExtraCodecs.strictUnboundedMap}：遇到第一个无法解码的条目就失败，结果保持 JSON 中键的顺序。
     */
    public static <K, V> StrictUnboundedMapCodec<K, V> strictUnboundedMap(Codec<K> keyCodec, Codec<V> elementCodec) {
        return new StrictUnboundedMapCodec<>(keyCodec, elementCodec);
    }

    /**
     * 原版 {@code ExtraCodecs.intervalCodec}：单个值、长度为 2 的数组，或者带上下界字段的对象。
     */
    public static <P, I> Codec<I> intervalCodec(Codec<P> pointCodec, String lowerBoundName, String upperBoundName,
                                                BiFunction<P, P, DataResult<I>> makeInterval,
                                                Function<I, P> getMin, Function<I, P> getMax) {
        Codec<I> arrayCodec = Codec.list(pointCodec).comapFlatMap(
                list -> fixedSize(list, 2).flatMap(l -> makeInterval.apply(l.get(0), l.get(1))),
                p -> ImmutableList.of(getMin.apply(p), getMax.apply(p)));
        Codec<I> objectCodec = RecordCodecBuilder.<Pair<P, P>>create(i -> i.group(
                pointCodec.fieldOf(lowerBoundName).forGetter(Pair::getFirst),
                pointCodec.fieldOf(upperBoundName).forGetter(Pair::getSecond)
        ).apply(i, Pair::of)).comapFlatMap(
                p -> makeInterval.apply(p.getFirst(), p.getSecond()),
                i -> Pair.of(getMin.apply(i), getMax.apply(i)));
        Codec<I> arrayOrObjectCodec = Codec.withAlternative(arrayCodec, objectCodec);
        return Codec.either(pointCodec, arrayOrObjectCodec).comapFlatMap(
                either -> either.map(min -> makeInterval.apply(min, min), DataResult::success),
                p -> {
                    P min = getMin.apply(p);
                    P max = getMax.apply(p);
                    return Objects.equals(min, max) ? Either.left(min) : Either.right(p);
                });
    }

    /**
     * 原版 {@code Util.fixedSize}：{@code Input is not a list of n elements}；元素过多时保留前 n 个作为部分结果。
     */
    public static <T> DataResult<List<T>> fixedSize(List<T> list, int size) {
        if (list.size() != size) {
            if (list.size() >= size) {
                return DataResult.error(() -> "Input is not a list of " + size + " elements", list.subList(0, size));
            }
            return DataResult.error(() -> "Input is not a list of " + size + " elements");
        }
        return DataResult.success(list);
    }

    /**
     * 原版 {@code ExtraCodecs.idResolverCodec(ToIntFunction, IntFunction, int)}：{@code Unknown element id: n}。
     */
    public static <E> Codec<E> idResolverCodec(ToIntFunction<E> toInt, IntFunction<@Nullable E> fromInt, int unknownId) {
        return Codec.INT.flatXmap(
                id -> Optional.ofNullable(fromInt.apply(id)).map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unknown element id: " + id)),
                e -> {
                    int id = toInt.applyAsInt(e);
                    return id == unknownId
                            ? DataResult.error(() -> "Element with unknown id: " + e)
                            : DataResult.success(id);
                });
    }

    /**
     * 原版 {@code ExtraCodecs.idResolverCodec(Codec, Function, Function)}：{@code Unknown element id: <id>}。
     */
    public static <I, E> Codec<E> idResolverCodec(Codec<I> value, Function<I, @Nullable E> fromId, Function<E, @Nullable I> toId) {
        return value.flatXmap(id -> {
            E element = fromId.apply(id);
            return element == null ? DataResult.error(() -> "Unknown element id: " + id) : DataResult.success(element);
        }, e -> {
            I id = toId.apply(e);
            return id == null ? DataResult.error(() -> "Element with unknown id: " + e) : DataResult.success(id);
        });
    }

    /**
     * 原版 {@code ExtraCodecs.orCompressed}：按 {@link DynamicOps#compressMaps()} 选择编解码器。
     */
    public static <E> Codec<E> orCompressed(Codec<E> normal, Codec<E> compressed) {
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
                return ops.compressMaps() ? compressed.encode(input, ops, prefix) : normal.encode(input, ops, prefix);
            }

            @Override
            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
                return ops.compressMaps() ? compressed.decode(ops, input) : normal.decode(ops, input);
            }

            @Override
            public String toString() {
                return normal + " orCompressed " + compressed;
            }
        };
    }

    /**
     * 原版 {@code ExtraCodecs.orCompressed(MapCodec, MapCodec)}：按 {@link DynamicOps#compressMaps()} 选择编解码器，
     * 键的列表取自 {@code compressed}。
     */
    public static <E> MapCodec<E> orCompressed(MapCodec<E> normal, MapCodec<E> compressed) {
        return new MapCodec<>() {
            @Override
            public <T> RecordBuilder<T> encode(E input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return ops.compressMaps() ? compressed.encode(input, ops, prefix) : normal.encode(input, ops, prefix);
            }

            @Override
            public <T> DataResult<E> decode(DynamicOps<T> ops, MapLike<T> input) {
                return ops.compressMaps() ? compressed.decode(ops, input) : normal.decode(ops, input);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return compressed.keys(ops);
            }

            @Override
            public String toString() {
                return normal + " orCompressed " + compressed;
            }
        };
    }

    /**
     * 原版 {@code ExtraCodecs.dispatchOptionalValue}：类型字段缺失时 {@code Missing "<typeKey>" in: <input>}，
     * 值字段缺失时按空对象解码。
     */
    public static <K, V> MapCodec<V> dispatchOptionalValue(String typeKey, String valueKey, Codec<K> typeCodec,
                                                           Function<? super V, ? extends K> typeGetter,
                                                           Function<? super K, ? extends Codec<? extends V>> valueCodec) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(typeKey), ops.createString(valueKey));
            }

            @Override
            public <T> DataResult<V> decode(DynamicOps<T> ops, MapLike<T> input) {
                T typeName = input.get(typeKey);
                if (typeName == null) {
                    return DataResult.error(() -> "Missing \"" + typeKey + "\" in: " + input);
                }
                return typeCodec.decode(ops, typeName).flatMap(type -> {
                    T value = Objects.requireNonNullElseGet(input.get(valueKey), ops::emptyMap);
                    return valueCodec.apply(type.getFirst()).decode(ops, value).map(Pair::getFirst);
                });
            }

            @Override
            public <T> RecordBuilder<T> encode(V input, DynamicOps<T> ops, RecordBuilder<T> builder) {
                K type = typeGetter.apply(input);
                builder.add(typeKey, typeCodec.encodeStart(ops, type));
                DataResult<T> parameters = encodeValue(valueCodec.apply(type), input, ops);
                if (parameters.result().isEmpty() || !Objects.equals(parameters.result().get(), ops.emptyMap())) {
                    builder.add(valueKey, parameters);
                }
                return builder;
            }

            @SuppressWarnings("unchecked")
            private <T, V2 extends V> DataResult<T> encodeValue(Codec<V2> codec, V input, DynamicOps<T> ops) {
                return codec.encodeStart(ops, (V2) input);
            }
        };
    }

    /**
     * 原版 {@code ARGB.colorFromFloat}。
     */
    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return (as8BitChannel(alpha) & 255) << 24
                | (as8BitChannel(red) & 255) << 16
                | (as8BitChannel(green) & 255) << 8
                | as8BitChannel(blue) & 255;
    }

    private static int as8BitChannel(float value) {
        return (int) Math.floor(value * 255.0F);
    }

    /**
     * 原版 {@code ExtraCodecs.StrictUnboundedMapCodec}。
     */
    public record StrictUnboundedMapCodec<K, V>(Codec<K> keyCodec, Codec<V> elementCodec)
            implements BaseMapCodec<K, V>, Codec<Map<K, V>> {
        @Override
        public <T> DataResult<Map<K, V>> decode(DynamicOps<T> ops, MapLike<T> input) {
            ImmutableMap.Builder<K, V> read = ImmutableMap.builder();
            for (Pair<T, T> pair : input.entries().toList()) {
                DataResult<K> k = keyCodec.parse(ops, pair.getFirst());
                DataResult<V> v = elementCodec.parse(ops, pair.getSecond());
                DataResult<Pair<K, V>> entry = k.apply2stable(Pair::of, v);
                var error = entry.error();
                if (error.isPresent()) {
                    String errorMessage = error.get().message();
                    return DataResult.error(() -> k.result().isPresent()
                            ? "Map entry '" + k.result().get() + "' : " + errorMessage
                            : errorMessage);
                }
                if (entry.result().isEmpty()) {
                    return DataResult.error(() -> "Empty or invalid map contents are not allowed");
                }
                Pair<K, V> kv = entry.result().get();
                read.put(kv.getFirst(), kv.getSecond());
            }
            return DataResult.success(read.build());
        }

        @Override
        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input).setLifecycle(Lifecycle.stable()).flatMap(map -> decode(ops, map)).map(r -> Pair.of(r, input));
        }

        @Override
        public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
            return encode(input, ops, ops.mapBuilder()).build(prefix);
        }

        @Override
        public @NotNull String toString() {
            return "StrictUnboundedMapCodec[" + keyCodec + " -> " + elementCodec + "]";
        }
    }

    /**
     * 原版 {@code ExtraCodecs.TagOrElementLocation}。
     */
    public record TagOrElementLocation(Key id, boolean tag) {
        public String decoratedId() {
            return tag ? "#" + id.asString() : id.asString();
        }

        @Override
        public @NotNull String toString() {
            return decoratedId();
        }
    }

    /**
     * 原版 {@code ExtraCodecs.LateBoundIdMapper}。
     */
    public static final class LateBoundIdMapper<I, V> {
        private final BiMap<I, V> idToValue = HashBiMap.create();

        public Codec<V> codec(Codec<I> idCodec) {
            BiMap<V, I> valueToId = idToValue.inverse();
            return idResolverCodec(idCodec, idToValue::get, valueToId::get);
        }

        public LateBoundIdMapper<I, V> put(I id, V value) {
            Objects.requireNonNull(value, () -> "Value for " + id + " is null");
            idToValue.put(id, value);
            return this;
        }

        public Set<V> values() {
            return Collections.unmodifiableSet(idToValue.values());
        }
    }
}
