package io.ib67.yuanban.enchant.internal;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ComponentSerialization;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinestomCodecs;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.StringRepresentables;
import io.ib67.yuanban.enchant.EnchantmentMut;
import io.ib67.yuanban.enchant.ProviderEntry;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.BiasedToBottomInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.ClampedInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.ClampedNormalInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.ConstantInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.TrapezoidInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.UniformInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.WeightedListInt;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 附魔定义和附魔提供器的编解码器，逐个移植原版 26.2 的编解码器：字段顺序、必需或可选、缺省值、校验和错误文本都与原版相同，
 * {@link RecordCodecBuilder} 把全部字段的错误合并成一条信息。
 * <p>
 * 注册表引用由调用者提供的 {@link io.ib67.yuanban.codec.RegistryOps} 检查。效果组件的值是 Minestom 的类型，
 * 通过 {@link MinestomCodecs#adapt} 使用 {@link EffectComponent} 注册的 Minestom 编解码器解码。
 */
@ApiStatus.Internal
public final class EnchantmentCodecs {
    /**
     * 原版内置注册表 {@code enchantment_effect_component_type}，元素是 {@link EffectComponent} 中的组件。
     */
    public static final RegistryId<DataComponent<?>> ENCHANTMENT_EFFECT_COMPONENT_TYPE = RegistryId.of("enchantment_effect_component_type");
    /**
     * 原版内置注册表 {@code enchantment_provider_type}。
     */
    public static final RegistryId<MapCodec<? extends ProviderEntry>> ENCHANTMENT_PROVIDER_TYPE = RegistryId.of("enchantment_provider_type");
    /**
     * 原版内置注册表 {@code int_provider_type}。
     */
    public static final RegistryId<MapCodec<? extends IntProvider>> INT_PROVIDER_TYPE = RegistryId.of("int_provider_type");

    /**
     * 原版 {@code IntProviders.CODEC}：整数字面量解码为 {@link ConstantInt}，否则按 {@code type} 分派到 7 种类型。
     */
    public static final Codec<IntProvider> INT_PROVIDER = Codec.recursive("IntProvider", EnchantmentCodecs::intProvider);

    /**
     * 原版 {@code Enchantment.Cost.CODEC}。
     */
    private static final Codec<Enchantment.Cost> COST = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("base").forGetter(Enchantment.Cost::base),
            Codec.INT.fieldOf("per_level_above_first").forGetter(Enchantment.Cost::perLevelAboveFirst)
    ).apply(i, Enchantment.Cost::new));

    /**
     * 原版 {@code EquipmentSlotGroup.CODEC}（{@code StringRepresentable.fromEnum}）。Minestom 的枚举与原版的取值、
     * 名字和顺序相同，包括 {@code saddle}。不使用 Minestom 的 {@code EquipmentSlotGroup.CODEC}：
     * 它遇到未知名字时得到 {@code null}，在 {@code List.copyOf} 处抛出 {@link NullPointerException}。
     */
    private static final Codec<EquipmentSlotGroup> EQUIPMENT_SLOT_GROUP = StringRepresentables.fromEnum(
            EquipmentSlotGroup.values(), EquipmentSlotGroup::nbtName);

    /**
     * 原版 {@code EnchantmentEffectComponents.COMPONENT_CODEC}：{@code enchantment_effect_component_type} 的
     * {@code byNameCodec()}。
     */
    private static final Codec<DataComponent<?>> EFFECT_COMPONENT_TYPE = RegistryCodecs.byName(
            ENCHANTMENT_EFFECT_COMPONENT_TYPE, EffectComponent::fromKey, DataComponent::key);

    /**
     * 原版 {@code EnchantmentEffectComponents.CODEC}，即 {@code DataComponentMap.makeCodec}：
     * {@link Codec#dispatchedMap} 逐个条目解码，未知的键和解码失败的值都报告错误，其余条目照常解码。
     * 结果保持 JSON 中键的顺序。
     */
    private static final Codec<List<Map.Entry<DataComponent<?>, Object>>> EFFECTS = Codec
            .<DataComponent<?>, Object>dispatchedMap(EFFECT_COMPONENT_TYPE, EnchantmentCodecs::effectValue)
            .xmap(map -> List.copyOf(map.entrySet()), EnchantmentCodecs::effectMap);

    /**
     * 原版 {@code Enchantment.EnchantmentDefinition.CODEC}。
     */
    private static final MapCodec<Definition> DEFINITION = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(RegistryIds.ITEM).fieldOf("supported_items").forGetter(Definition::supportedItems),
            RegistryCodecs.homogeneousList(RegistryIds.ITEM).optionalFieldOf("primary_items").forGetter(Definition::primaryItems),
            ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Definition::weight),
            ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Definition::maxLevel),
            COST.fieldOf("min_cost").forGetter(Definition::minCost),
            COST.fieldOf("max_cost").forGetter(Definition::maxCost),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Definition::anvilCost),
            EQUIPMENT_SLOT_GROUP.listOf().fieldOf("slots").forGetter(Definition::slots)
    ).apply(i, Definition::new));

    /**
     * 原版 {@code Enchantment.DIRECT_CODEC}。{@code exclusive_set} 缺失时为空的元素列表，与原版 {@code HolderSet.empty()} 相同。
     */
    public static final Codec<EnchantmentMut> ENCHANTMENT = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("description").forGetter(EnchantmentMut::description),
            DEFINITION.forGetter(Definition::of),
            RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT)
                    .optionalFieldOf("exclusive_set", HolderSet.direct(List.of()))
                    .forGetter(EnchantmentMut::exclusiveSet),
            EFFECTS.optionalFieldOf("effects", List.of()).forGetter(EnchantmentMut::effects)
    ).apply(i, EnchantmentCodecs::enchantment));

    /**
     * 原版 {@code Enchantment.CODEC}（{@code RegistryFixedCodec}）：附魔注册表中已有元素的 ID。
     */
    private static final Codec<Key> ENCHANTMENT_ID = RegistryCodecs.fixed(RegistryIds.ENCHANTMENT)
            .xmap(RegistryKey::key, RegistryKey::of);

    /**
     * 原版 {@code SingleEnchantment.CODEC}。
     */
    private static final MapCodec<ProviderEntry.Single> SINGLE = RecordCodecBuilder.mapCodec(i -> i.group(
            ENCHANTMENT_ID.fieldOf("enchantment").forGetter(ProviderEntry.Single::enchantment),
            INT_PROVIDER.fieldOf("level").forGetter(ProviderEntry.Single::level)
    ).apply(i, ProviderEntry.Single::new));

    /**
     * 原版 {@code EnchantmentsByCost.CODEC}。
     */
    private static final MapCodec<ProviderEntry.ByCost> BY_COST = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT).fieldOf("enchantments").forGetter(ProviderEntry.ByCost::enchantments),
            INT_PROVIDER.fieldOf("cost").forGetter(ProviderEntry.ByCost::cost)
    ).apply(i, ProviderEntry.ByCost::new));

    /**
     * 原版 {@code EnchantmentsByCostWithDifficulty.CODEC}。
     */
    private static final MapCodec<ProviderEntry.ByCostWithDifficulty> BY_COST_WITH_DIFFICULTY = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT).fieldOf("enchantments").forGetter(ProviderEntry.ByCostWithDifficulty::enchantments),
            ExtraCodecs.intRange(1, 10000).fieldOf("min_cost").forGetter(ProviderEntry.ByCostWithDifficulty::minCost),
            ExtraCodecs.intRange(0, 10000).fieldOf("max_cost_span").forGetter(ProviderEntry.ByCostWithDifficulty::maxCostSpan)
    ).apply(i, ProviderEntry.ByCostWithDifficulty::new));

    /**
     * 原版 {@code EnchantmentProvider.DIRECT_CODEC}：按 {@code type} 的完整 ID 在 {@code enchantment_provider_type}
     * 中分派，类型与原版 {@code EnchantmentProviderTypes.bootstrap} 登记的相同。
     */
    public static final Codec<ProviderEntry> PROVIDER = typeCodec(ENCHANTMENT_PROVIDER_TYPE, Map.of(
            Key.key("by_cost"), BY_COST,
            Key.key("by_cost_with_difficulty"), BY_COST_WITH_DIFFICULTY,
            Key.key("single"), SINGLE
    )).dispatch(EnchantmentCodecs::providerType, Function.identity());

    private EnchantmentCodecs() {
    }

    private static EnchantmentMut enchantment(net.kyori.adventure.text.Component description, Definition definition,
                                              HolderSet<Enchantment> exclusiveSet, List<Map.Entry<DataComponent<?>, Object>> effects) {
        return new EnchantmentMut(description, definition.supportedItems(), definition.primaryItems().orElse(null), exclusiveSet,
                definition.weight(), definition.maxLevel(), definition.minCost(), definition.maxCost(), definition.anvilCost(),
                definition.slots(), effects);
    }

    private static MapCodec<? extends ProviderEntry> providerType(ProviderEntry provider) {
        return switch (provider) {
            case ProviderEntry.Single _ -> SINGLE;
            case ProviderEntry.ByCost _ -> BY_COST;
            case ProviderEntry.ByCostWithDifficulty _ -> BY_COST_WITH_DIFFICULTY;
        };
    }

    /**
     * 构造 {@link #INT_PROVIDER}。{@code clamped} 和 {@code weighted_list} 递归使用 {@code self}。
     * 类型与原版 {@code IntProviders.bootstrap} 登记的相同。
     */
    private static Codec<IntProvider> intProvider(Codec<IntProvider> self) {
        // 原版 ConstantInt.MAP_CODEC
        MapCodec<ConstantInt> constant = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("value").forGetter(ConstantInt::value)
        ).apply(i, ConstantInt::new));
        // 原版 UniformInt.MAP_CODEC
        MapCodec<UniformInt> uniform = RecordCodecBuilder.<UniformInt>mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_inclusive").forGetter(UniformInt::minInclusive),
                Codec.INT.fieldOf("max_inclusive").forGetter(UniformInt::maxInclusive)
        ).apply(i, UniformInt::new)).validate(u -> maxAtLeastMin(u, u.minInclusive(), u.maxInclusive()));
        // 原版 BiasedToBottomInt.MAP_CODEC
        MapCodec<BiasedToBottomInt> biasedToBottom = RecordCodecBuilder.<BiasedToBottomInt>mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_inclusive").forGetter(BiasedToBottomInt::minInclusive),
                Codec.INT.fieldOf("max_inclusive").forGetter(BiasedToBottomInt::maxInclusive)
        ).apply(i, BiasedToBottomInt::new)).validate(u -> maxAtLeastMin(u, u.minInclusive(), u.maxInclusive()));
        // 原版 ClampedInt.MAP_CODEC。ClampedInt 的 minInclusive()/maxInclusive() 返回和 source 求交之后的范围，
        // 原版的校验读取 JSON 中的两个字段，所以先解码成 ClampedFields 校验，再构造 ClampedInt
        MapCodec<ClampedInt> clamped = RecordCodecBuilder.<ClampedFields>mapCodec(i -> i.group(
                self.fieldOf("source").forGetter(ClampedFields::source),
                Codec.INT.fieldOf("min_inclusive").forGetter(ClampedFields::minInclusive),
                Codec.INT.fieldOf("max_inclusive").forGetter(ClampedFields::maxInclusive)
        ).apply(i, ClampedFields::new)).validate(c -> maxAtLeastMin(c, c.minInclusive(), c.maxInclusive())).xmap(
                c -> new ClampedInt(c.source(), c.minInclusive(), c.maxInclusive()),
                c -> new ClampedFields(c.source(), c.minInclusive(), c.maxInclusive()));
        // 原版 WeightedListInt.MAP_CODEC，distribution 是 WeightedList.nonEmptyCodec(IntProviders.CODEC)
        Codec<WeightedListInt.Weighted> weighted = RecordCodecBuilder.create(i -> i.group(
                self.fieldOf("data").forGetter(WeightedListInt.Weighted::data),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("weight").forGetter(WeightedListInt.Weighted::weight)
        ).apply(i, WeightedListInt.Weighted::new));
        MapCodec<WeightedListInt> weightedList = weighted.listOf()
                .xmap(WeightedListInt::new, WeightedListInt::distribution)
                .validate(list -> list.totalWeight() == 0
                        ? DataResult.error(() -> "Weighted list must contain at least one entry with non-zero weight")
                        : DataResult.success(list))
                .fieldOf("distribution");
        // 原版 ClampedNormalInt.MAP_CODEC
        MapCodec<ClampedNormalInt> clampedNormal = RecordCodecBuilder.<ClampedNormalInt>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("mean").forGetter(ClampedNormalInt::mean),
                Codec.FLOAT.fieldOf("deviation").forGetter(ClampedNormalInt::deviation),
                Codec.INT.fieldOf("min_inclusive").forGetter(ClampedNormalInt::minInclusive),
                Codec.INT.fieldOf("max_inclusive").forGetter(ClampedNormalInt::maxInclusive)
        ).apply(i, ClampedNormalInt::new)).validate(c -> c.maxInclusive() < c.minInclusive()
                ? DataResult.error(() -> "Max must be larger than min: [" + c.minInclusive() + ", " + c.maxInclusive() + "]")
                : DataResult.success(c));
        // 原版 TrapezoidInt.MAP_CODEC
        MapCodec<TrapezoidInt> trapezoid = RecordCodecBuilder.<TrapezoidInt>mapCodec(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(TrapezoidInt::minInclusive),
                Codec.INT.fieldOf("max").forGetter(TrapezoidInt::maxInclusive),
                Codec.INT.fieldOf("plateau").forGetter(TrapezoidInt::plateau)
        ).apply(i, TrapezoidInt::new)).validate(c -> {
            if (c.maxInclusive() < c.minInclusive()) {
                return DataResult.error(() -> "Max must be larger than min: [" + c.minInclusive() + ", " + c.maxInclusive() + "]");
            }
            return c.plateau() > c.maxInclusive() - c.minInclusive()
                    ? DataResult.error(() -> "Plateau can at most be the full span: [" + c.minInclusive() + ", " + c.maxInclusive() + "]")
                    : DataResult.success(c);
        });

        Function<IntProvider, MapCodec<? extends IntProvider>> typeOf = provider -> switch (provider) {
            case ConstantInt _ -> constant;
            case UniformInt _ -> uniform;
            case BiasedToBottomInt _ -> biasedToBottom;
            case ClampedInt _ -> clamped;
            case WeightedListInt _ -> weightedList;
            case ClampedNormalInt _ -> clampedNormal;
            case TrapezoidInt _ -> trapezoid;
        };
        Codec<IntProvider> dispatch = typeCodec(INT_PROVIDER_TYPE, Map.of(
                Key.key("constant"), constant,
                Key.key("uniform"), uniform,
                Key.key("biased_to_bottom"), biasedToBottom,
                Key.key("clamped"), clamped,
                Key.key("weighted_list"), weightedList,
                Key.key("clamped_normal"), clampedNormal,
                Key.key("trapezoid"), trapezoid
        )).dispatch(typeOf, Function.identity());
        return Codec.either(Codec.INT, dispatch).xmap(
                either -> either.map(ConstantInt::new, provider -> provider),
                provider -> provider instanceof ConstantInt(var value)
                        ? Either.left(value)
                        : Either.right(provider));
    }

    /**
     * 原版 {@code UniformInt}、{@code BiasedToBottomInt}、{@code ClampedInt} 共用的校验文本。
     */
    private static <T> DataResult<T> maxAtLeastMin(T value, int minInclusive, int maxInclusive) {
        return maxInclusive < minInclusive
                ? DataResult.error(() -> "Max must be at least min, min_inclusive: " + minInclusive + ", max_inclusive: " + maxInclusive)
                : DataResult.success(value);
    }

    /**
     * 原版内置类型注册表的 {@code byNameCodec()}：未知 ID 的错误为
     * {@code Unknown registry key in ResourceKey[minecraft:root / <registry>]: <id>}。
     */
    private static <T> Codec<MapCodec<? extends T>> typeCodec(RegistryId<MapCodec<? extends T>> registry,
                                                              Map<Key, MapCodec<? extends T>> types) {
        var ids = new IdentityHashMap<MapCodec<? extends T>, Key>();
        types.forEach((id, codec) -> ids.put(codec, id));
        return RegistryCodecs.byName(registry, types::get, ids::get);
    }

    /**
     * 效果组件的值通过 {@link MinestomCodecs#adapt} 使用该组件在 {@link EffectComponent} 中注册的 Minestom 编解码器。
     */
    private static Codec<?> effectValue(DataComponent<?> component) {
        return MinestomCodecs.adapt(minestomCodec(component));
    }

    /**
     * {@link DataComponent} 同时是 Minestom 的编码器和解码器，这里组合成 {@link MinestomCodecs#adapt} 需要的编解码器。
     */
    private static <T> net.minestom.server.codec.Codec<T> minestomCodec(DataComponent<T> component) {
        return new net.minestom.server.codec.Codec<>() {
            @Override
            public <D> Result<T> decode(Transcoder<D> coder, D value) {
                return component.decode(coder, value);
            }

            @Override
            public <D> Result<D> encode(Transcoder<D> coder, @Nullable T value) {
                return component.encode(coder, value);
            }
        };
    }

    private static Map<DataComponent<?>, Object> effectMap(List<Map.Entry<DataComponent<?>, Object>> effects) {
        var map = new LinkedHashMap<DataComponent<?>, Object>();
        for (var effect : effects) {
            map.put(effect.getKey(), effect.getValue());
        }
        return map;
    }

    /**
     * 原版 {@code Enchantment.EnchantmentDefinition} 的字段，{@link #ENCHANTMENT} 把它们平铺到 {@link EnchantmentMut}。
     */
    private record Definition(HolderSet<Material> supportedItems, Optional<HolderSet<Material>> primaryItems,
                              int weight, int maxLevel, Enchantment.Cost minCost, Enchantment.Cost maxCost,
                              int anvilCost, List<EquipmentSlotGroup> slots) {
        static Definition of(EnchantmentMut enchantment) {
            return new Definition(enchantment.supportedItems(), Optional.ofNullable(enchantment.primaryItems()),
                    enchantment.weight(), enchantment.maxLevel(), enchantment.minCost(), enchantment.maxCost(),
                    enchantment.anvilCost(), enchantment.slots());
        }
    }

    /**
     * {@code clamped} 在 JSON 中的三个字段，校验之后构造 {@link ClampedInt}。
     */
    private record ClampedFields(IntProvider source, int minInclusive, int maxInclusive) {
    }
}
