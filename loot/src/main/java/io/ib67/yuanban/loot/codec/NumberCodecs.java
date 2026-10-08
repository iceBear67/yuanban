package io.ib67.yuanban.loot.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.loot.number.BinomialValue;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EnchantmentLevelValue;
import io.ib67.yuanban.loot.number.EnvironmentAttributeValue;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.StorageValue;
import io.ib67.yuanban.loot.number.SumValue;
import io.ib67.yuanban.loot.number.UniformValue;
import net.kyori.adventure.key.Key;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 原版 {@code NumberProviders} 注册的 8 种数字提供器的编解码器，以及 {@code LevelBasedValue}、计分板名称来源。
 */
public final class NumberCodecs {
    /**
     * 原版 {@code LevelBasedValue.CODEC}：裸数字（{@code Constant}），或按 {@code type} 分派的对象，
     * {@code type} 取原版 {@code enchantment_level_based_value_type} 注册表的 6 种。
     */
    public static final Codec<LevelBasedValue> LEVEL_BASED_VALUE = Codec.recursive("LevelBasedValue", NumberCodecs::levelBasedValue);
    /**
     * 原版 {@code EnvironmentAttributes.CODEC}：环境属性注册表的 ID。
     */
    static final Codec<EnvironmentAttribute<?>> ENVIRONMENT_ATTRIBUTE = RegistryCodecs.byName(RegistryIds.ENVIRONMENT_ATTRIBUTE,
            NumberCodecs::findEnvironmentAttribute, EnvironmentAttribute::key);
    /**
     * 原版 {@code ScoreboardNameProviders.CODEC}：实体目标名（上下文实体），或按 {@code type} 分派的
     * {@code fixed}（{@code name}）、{@code context}（{@code target}）。
     */
    static final Codec<ScoreboardName> SCOREBOARD_NAME = scoreboardName();

    private NumberCodecs() {
    }

    /**
     * 原版 {@code ConstantValue.MAP_CODEC}：{@code value}。
     */
    public static MapCodec<ConstantValue> constant(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("value").forGetter(ConstantValue::value)
        ).apply(i, ConstantValue::new));
    }

    /**
     * 原版 {@code UniformGenerator.MAP_CODEC}：{@code min}、{@code max}。
     */
    public static MapCodec<UniformValue> uniform(LootCodecs codecs) {
        return uniform(codecs.number());
    }

    /**
     * 不经过注册表的 {@code uniform}，供数字提供器在没有 {@code type} 时退回使用（原版 {@code NumberProviders.CODEC}）。
     */
    static MapCodec<UniformValue> uniform(Codec<NumberProvider> number) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                number.fieldOf("min").forGetter(UniformValue::min),
                number.fieldOf("max").forGetter(UniformValue::max)
        ).apply(i, UniformValue::new));
    }

    /**
     * 原版 {@code BinomialDistributionGenerator.MAP_CODEC}：{@code n}、{@code p}。
     */
    public static MapCodec<BinomialValue> binomial(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.number().fieldOf("n").forGetter(BinomialValue::n),
                codecs.number().fieldOf("p").forGetter(BinomialValue::p)
        ).apply(i, BinomialValue::new));
    }

    /**
     * 原版 {@code ScoreboardValue.MAP_CODEC}：{@code target}、{@code score}、{@code scale}（默认 1）。
     */
    public static MapCodec<ScoreboardValue> score(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SCOREBOARD_NAME.fieldOf("target").forGetter(ScoreboardValue::target),
                Codec.STRING.fieldOf("score").forGetter(ScoreboardValue::score),
                Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(ScoreboardValue::scale)
        ).apply(i, ScoreboardValue::new));
    }

    /**
     * 原版 {@code StorageValue.MAP_CODEC}：{@code storage}（命名空间 ID）、{@code path}（NBT 路径）。
     */
    public static MapCodec<StorageValue> storage(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Identifiers.CODEC.fieldOf("storage").forGetter(StorageValue::storage),
                NbtPath.CODEC.fieldOf("path").forGetter(StorageValue::path)
        ).apply(i, StorageValue::new));
    }

    /**
     * 原版 {@code Sum.MAP_CODEC}：{@code summands}（数字提供器列表）。
     */
    public static MapCodec<SumValue> sum(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.number().listOf().fieldOf("summands").forGetter(SumValue::summands)
        ).apply(i, SumValue::new));
    }

    /**
     * 原版 {@code EnchantmentLevelProvider.MAP_CODEC}：{@code amount}（{@code LevelBasedValue}）。
     */
    public static MapCodec<EnchantmentLevelValue> enchantmentLevel(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                LEVEL_BASED_VALUE.fieldOf("amount").forGetter(EnchantmentLevelValue::amount)
        ).apply(i, EnchantmentLevelValue::new));
    }

    /**
     * 原版 {@code EnvironmentAttributeValue.MAP_CODEC}：{@code attribute}，取值不能转换成数值的属性为
     * {@code <属性 ID> cannot be converted to a number}。原版中能转换成数值的属性类型是 {@code float}、
     * {@code angle_degrees}、{@code integer}；Minestom 没有 {@code integer} 类型的属性。
     */
    public static MapCodec<EnvironmentAttributeValue> environmentAttribute(LootCodecs codecs) {
        Codec<EnvironmentAttribute<?>> numeric = ENVIRONMENT_ATTRIBUTE.validate(attribute -> isNumeric(attribute)
                ? DataResult.success(attribute)
                : DataResult.error(() -> attribute.key().asString() + " cannot be converted to a number"));
        return RecordCodecBuilder.mapCodec(i -> i.group(
                numeric.fieldOf("attribute").forGetter(EnvironmentAttributeValue::attribute)
        ).apply(i, EnvironmentAttributeValue::new));
    }

    /**
     * 原版 {@code AttributeType#toFloat} 不为空的属性类型。
     */
    static boolean isNumeric(EnvironmentAttribute<?> attribute) {
        return attribute.type() == EnvironmentAttribute.Type.FLOAT || attribute.type() == EnvironmentAttribute.Type.ANGLE_DEGREES;
    }

    private static @Nullable EnvironmentAttribute<?> findEnvironmentAttribute(Key id) {
        for (var attribute : EnvironmentAttribute.values()) {
            if (attribute.key().equals(id)) {
                return attribute;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // LevelBasedValue
    // ------------------------------------------------------------------

    private static Codec<LevelBasedValue> levelBasedValue(Codec<LevelBasedValue> self) {
        var types = new LinkedHashMap<Key, MapCodec<? extends LevelBasedValue>>();
        types.put(Key.key("clamped"), RecordCodecBuilder.<LevelBasedValue.Clamped>mapCodec(i -> i.group(
                self.fieldOf("value").forGetter(LevelBasedValue.Clamped::value),
                Codec.FLOAT.fieldOf("min").forGetter(LevelBasedValue.Clamped::min),
                Codec.FLOAT.fieldOf("max").forGetter(LevelBasedValue.Clamped::max)
        ).apply(i, LevelBasedValue.Clamped::new)).validate(clamped -> clamped.max() <= clamped.min()
                ? DataResult.error(() -> "Max must be larger than min, min: " + clamped.min() + ", max: " + clamped.max())
                : DataResult.success(clamped)));
        types.put(Key.key("fraction"), RecordCodecBuilder.<LevelBasedValue.Fraction>mapCodec(i -> i.group(
                self.fieldOf("numerator").forGetter(LevelBasedValue.Fraction::numerator),
                self.fieldOf("denominator").forGetter(LevelBasedValue.Fraction::denominator)
        ).apply(i, LevelBasedValue.Fraction::new)));
        types.put(Key.key("levels_squared"), RecordCodecBuilder.<LevelBasedValue.LevelsSquared>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("added").forGetter(LevelBasedValue.LevelsSquared::added)
        ).apply(i, LevelBasedValue.LevelsSquared::new)));
        types.put(Key.key("linear"), RecordCodecBuilder.<LevelBasedValue.Linear>mapCodec(i -> i.group(
                Codec.FLOAT.fieldOf("base").forGetter(LevelBasedValue.Linear::base),
                Codec.FLOAT.fieldOf("per_level_above_first").forGetter(LevelBasedValue.Linear::perLevelAboveFirst)
        ).apply(i, LevelBasedValue.Linear::new)));
        types.put(Key.key("exponent"), RecordCodecBuilder.<LevelBasedValue.Exponent>mapCodec(i -> i.group(
                self.fieldOf("base").forGetter(LevelBasedValue.Exponent::base),
                self.fieldOf("power").forGetter(LevelBasedValue.Exponent::power)
        ).apply(i, LevelBasedValue.Exponent::new)));
        types.put(Key.key("lookup"), RecordCodecBuilder.<LevelBasedValue.Lookup>mapCodec(i -> i.group(
                Codec.FLOAT.listOf().fieldOf("values").forGetter(LevelBasedValue.Lookup::values),
                self.fieldOf("fallback").forGetter(LevelBasedValue.Lookup::fallback)
        ).apply(i, LevelBasedValue.Lookup::new)));
        Map<Key, MapCodec<? extends LevelBasedValue>> table = Map.copyOf(types);

        Codec<Key> typeCodec = RegistryCodecs.byName(RegistryId.of("enchantment_level_based_value_type"),
                id -> table.containsKey(id) ? id : null, id -> id);
        Codec<LevelBasedValue> dispatch = typeCodec.partialDispatch("type",
                value -> levelBasedValueType(value).map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Constant level based values are written as numbers")),
                id -> DataResult.success(table.get(id)));
        Codec<LevelBasedValue.Constant> constant = Codec.FLOAT.xmap(LevelBasedValue.Constant::new, LevelBasedValue.Constant::value);
        return Codec.either(constant, dispatch).xmap(either -> either.map(value -> value, value -> value),
                value -> value instanceof LevelBasedValue.Constant c ? Either.left(c) : Either.right(value));
    }

    private static Optional<Key> levelBasedValueType(LevelBasedValue value) {
        return Optional.ofNullable(switch (value) {
            case LevelBasedValue.Constant ignored -> null;
            case LevelBasedValue.Clamped ignored -> Key.key("clamped");
            case LevelBasedValue.Fraction ignored -> Key.key("fraction");
            case LevelBasedValue.LevelsSquared ignored -> Key.key("levels_squared");
            case LevelBasedValue.Linear ignored -> Key.key("linear");
            case LevelBasedValue.Exponent ignored -> Key.key("exponent");
            case LevelBasedValue.Lookup ignored -> Key.key("lookup");
        });
    }

    // ------------------------------------------------------------------
    // 计分板名称
    // ------------------------------------------------------------------

    private static Codec<ScoreboardName> scoreboardName() {
        var types = new LinkedHashMap<Key, MapCodec<? extends ScoreboardName>>();
        types.put(Key.key("fixed"), RecordCodecBuilder.<ScoreboardName.Fixed>mapCodec(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(ScoreboardName.Fixed::name)
        ).apply(i, ScoreboardName.Fixed::new)));
        types.put(Key.key("context"), RecordCodecBuilder.<ScoreboardName.Context>mapCodec(i -> i.group(
                CommonCodecs.ENTITY_TARGET.fieldOf("target").forGetter(ScoreboardName.Context::target)
        ).apply(i, ScoreboardName.Context::new)));
        Map<Key, MapCodec<? extends ScoreboardName>> table = Map.copyOf(types);
        Codec<Key> typeCodec = RegistryCodecs.byName(RegistryId.of("loot_score_provider_type"),
                id -> table.containsKey(id) ? id : null, id -> id);
        Codec<ScoreboardName> typed = typeCodec.dispatch(name -> switch (name) {
            case ScoreboardName.Fixed ignored -> Key.key("fixed");
            case ScoreboardName.Context ignored -> Key.key("context");
        }, table::get);
        Codec<ScoreboardName.Context> inline = CommonCodecs.ENTITY_TARGET.xmap(ScoreboardName.Context::new,
                ScoreboardName.Context::target);
        return Codec.either(inline, typed).xmap(either -> either.map(name -> name, name -> name),
                name -> name instanceof ScoreboardName.Context context ? Either.left(context) : Either.right(name));
    }
}
