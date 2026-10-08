package io.ib67.yuanban.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.kyori.adventure.key.Key;
import net.minestom.server.registry.Holder;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * 原版按注册表解码引用的编解码器：{@code RegistryFixedCodec}、{@code RegistryFileCodec}、{@code HolderSetCodec}、
 * {@code Registry.byNameCodec}、{@code ResourceKey.codec}、{@code TagKey.codec}/{@code hashedCodec}。
 * 需要注册表的编解码器从 {@link RegistryOps} 取得 {@link RegistryGetter}，错误文本与原版相同。
 */
public final class RegistryCodecs {
    private RegistryCodecs() {
    }

    /**
     * 原版 {@code RegistryFixedCodec.create}：ID 必须是注册表中已有的元素。
     * 错误为 {@code Failed to get element <id>}，ops 不带注册表时为 {@code Can't access registry <registry>}。
     */
    public static <T> Codec<RegistryKey<T>> fixed(RegistryId<T> registry) {
        return new Codec<>() {
            @Override
            public <D> DataResult<Pair<RegistryKey<T>, D>> decode(DynamicOps<D> ops, D input) {
                if (ops instanceof RegistryOps<D> registryOps) {
                    var lookup = registryOps.getter(registry);
                    if (lookup.isPresent()) {
                        var getter = lookup.get();
                        return Identifiers.CODEC.decode(ops, input).flatMap(pair -> {
                            Key id = pair.getFirst();
                            return getter.containsElement(id)
                                    ? DataResult.success(Pair.of(RegistryKey.of(id), pair.getSecond()), Lifecycle.stable())
                                    : DataResult.error(() -> "Failed to get element " + id.asString());
                        });
                    }
                }
                return DataResult.error(() -> "Can't access registry " + registry);
            }

            @Override
            public <D> DataResult<D> encode(RegistryKey<T> input, DynamicOps<D> ops, D prefix) {
                return Identifiers.CODEC.encode(input.key(), ops, prefix);
            }

            @Override
            public String toString() {
                return "RegistryFixedCodec[" + registry + "]";
            }
        };
    }

    /**
     * 原版 {@code RegistryFileCodec.create}：注册表中已有元素的 ID，或者内联的元素定义。
     * 错误为 {@code Failed to get element ResourceKey[<registry> / <id>]}、{@code Registry does not exist: <registry>}，
     * 不允许内联时为 {@code Inline definitions not allowed here}。
     */
    public static <T extends Holder<T>> Codec<Holder<T>> file(RegistryId<T> registry, Codec<T> elementCodec, boolean allowInline) {
        return new Codec<>() {
            @Override
            public <D> DataResult<Pair<Holder<T>, D>> decode(DynamicOps<D> ops, D input) {
                if (!(ops instanceof RegistryOps<D> registryOps)) {
                    return elementCodec.decode(ops, input).map(p -> p.mapFirst(value -> value));
                }
                var lookup = registryOps.getter(registry);
                if (lookup.isEmpty()) {
                    return DataResult.error(() -> "Registry does not exist: " + registry);
                }
                var decoded = Identifiers.CODEC.decode(ops, input);
                if (decoded.result().isEmpty()) {
                    return !allowInline
                            ? DataResult.error(() -> "Inline definitions not allowed here")
                            : elementCodec.decode(ops, input).map(p -> p.mapFirst(value -> value));
                }
                var pair = decoded.result().get();
                Key id = pair.getFirst();
                return lookup.get().containsElement(id)
                        ? DataResult.success(Pair.of(RegistryKey.of(id), pair.getSecond()), Lifecycle.stable())
                        : DataResult.error(() -> "Failed to get element " + registry.element(id));
            }

            @Override
            public <D> DataResult<D> encode(Holder<T> input, DynamicOps<D> ops, D prefix) {
                return input.unwrap().unify(
                        key -> Identifiers.CODEC.encode(key.key(), ops, prefix),
                        value -> elementCodec.encode(value, ops, prefix));
            }

            @Override
            public String toString() {
                return "RegistryFileCodec[" + registry + " " + elementCodec + "]";
            }
        };
    }

    /**
     * 原版 {@code RegistryCodecs.homogeneousList(registry)}：标签（{@code #id}）、单个元素 ID 或者元素 ID 列表，
     * 元素按 {@link #fixed} 检查。
     */
    public static <T> Codec<HolderSet<T>> homogeneousList(RegistryId<T> registry) {
        return homogeneousList(registry, fixed(registry), false);
    }

    /**
     * 原版 {@code RegistryCodecs.homogeneousList(registry, alwaysUseList)}：{@code alwaysUseList} 为 {@code true}
     * 时不接受单个元素。
     */
    public static <T> Codec<HolderSet<T>> homogeneousList(RegistryId<T> registry, boolean alwaysUseList) {
        return homogeneousList(registry, fixed(registry), alwaysUseList);
    }

    /**
     * 原版 {@code HolderSetCodec.create(registry, elementCodec, alwaysUseList)}。
     * 标签不存在时为 {@code Missing tag: '<tag>' in '<registry>'}。
     */
    public static <T, E extends RegistryKey<T>> Codec<HolderSet<T>> homogeneousList(RegistryId<T> registry, Codec<E> elementCodec,
                                                                                   boolean alwaysUseList) {
        @SuppressWarnings("unchecked")
        Codec<RegistryKey<T>> element = elementCodec.xmap(e -> e, key -> (E) key);
        Codec<List<RegistryKey<T>>> listCodec = element.listOf();
        Codec<List<RegistryKey<T>>> homogenousList = alwaysUseList ? listCodec : ExtraCodecs.compactListCodec(element, listCodec);
        Codec<Either<TagKey<T>, List<RegistryKey<T>>>> registryAware = Codec.either(hashedTagKey(registry), homogenousList);
        return new Codec<>() {
            @Override
            public <D> DataResult<Pair<HolderSet<T>, D>> decode(DynamicOps<D> ops, D input) {
                if (ops instanceof RegistryOps<D> registryOps) {
                    var lookup = registryOps.getter(registry);
                    if (lookup.isPresent()) {
                        var getter = lookup.get();
                        return registryAware.decode(ops, input).flatMap(p -> {
                            DataResult<HolderSet<T>> result = p.getFirst().map(
                                    tag -> getter.containsTag(tag.key())
                                            ? DataResult.success(new HolderSet.Named<>(tag))
                                            : DataResult.error(() -> "Missing tag: '" + tag.key().asString() + "' in '" + registry.key().asString() + "'"),
                                    values -> DataResult.success(HolderSet.direct(values)));
                            return result.map(holders -> Pair.of(holders, p.getSecond()));
                        });
                    }
                }
                return element.listOf().decode(ops, input).flatMap(p -> {
                    var values = p.getFirst();
                    if (!values.isEmpty()) {
                        var first = values.getFirst();
                        return DataResult.error(() -> "Can't decode element " + registry.element(first.key()) + " without registry");
                    }
                    return DataResult.success(Pair.of((HolderSet<T>) HolderSet.<T>direct(List.of()), p.getSecond()));
                });
            }

            @Override
            public <D> DataResult<D> encode(HolderSet<T> input, DynamicOps<D> ops, D prefix) {
                return switch (input) {
                    case HolderSet.Named<T> named -> registryAware.encode(Either.left(named.tag()), ops, prefix);
                    case HolderSet.Direct<T> direct -> registryAware.encode(Either.right(direct.keys()), ops, prefix);
                };
            }

            @Override
            public String toString() {
                return "HolderSetCodec[" + registry + "]";
            }
        };
    }

    /**
     * 原版内置注册表的 {@code Registry.byNameCodec()}/{@code holderByNameCodec()}：
     * 错误为 {@code Unknown registry key in <registry>: <id>}。
     */
    public static <T> Codec<T> byName(RegistryId<T> registry, Function<Key, @Nullable T> lookup, Function<T, Key> toKey) {
        return Identifiers.CODEC.flatXmap(id -> {
            T value = lookup.apply(id);
            return value != null
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Unknown registry key in " + registry + ": " + id.asString());
        }, value -> DataResult.success(toKey.apply(value)));
    }

    /**
     * 原版 {@code ResourceKey.codec(registry)}：任意 ID，不检查是否存在。
     */
    public static <T> Codec<RegistryKey<T>> resourceKey(RegistryId<T> registry) {
        return Identifiers.CODEC.xmap(RegistryKey::of, RegistryKey::key);
    }

    /**
     * 原版 {@code TagKey.codec(registry)}：不带 {@code #} 的标签 ID，不检查是否存在。
     */
    public static <T> Codec<TagKey<T>> tagKey(RegistryId<T> registry) {
        return Identifiers.CODEC.xmap(TagKey::of, TagKey::key);
    }

    /**
     * 原版 {@code TagKey.hashedCodec(registry)}：{@code #} 开头的标签 ID，否则为 {@code Not a tag id}。
     */
    public static <T> Codec<TagKey<T>> hashedTagKey(RegistryId<T> registry) {
        return Codec.STRING.comapFlatMap(
                name -> name.startsWith("#")
                        ? Identifiers.read(name.substring(1)).map(TagKey::<T>of)
                        : DataResult.error(() -> "Not a tag id"),
                tag -> "#" + tag.key().asString());
    }
}
