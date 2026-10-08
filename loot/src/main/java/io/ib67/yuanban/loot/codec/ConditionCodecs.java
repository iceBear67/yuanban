package io.ib67.yuanban.loot.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.MinestomCodecs;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.AnyOfCondition;
import io.ib67.yuanban.loot.condition.BlockStatePropertyCondition;
import io.ib67.yuanban.loot.condition.DamageSourcePropertiesCondition;
import io.ib67.yuanban.loot.condition.EnchantmentActiveCheckCondition;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.EntityScoresCondition;
import io.ib67.yuanban.loot.condition.EnvironmentAttributeCheckCondition;
import io.ib67.yuanban.loot.condition.InvertedCondition;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.condition.RandomChanceWithEnchantedBonusCondition;
import io.ib67.yuanban.loot.condition.ReferenceCondition;
import io.ib67.yuanban.loot.condition.SurvivesExplosionCondition;
import io.ib67.yuanban.loot.condition.TableBonusCondition;
import io.ib67.yuanban.loot.condition.TimeCheckCondition;
import io.ib67.yuanban.loot.condition.ValueCheckCondition;
import io.ib67.yuanban.loot.condition.WeatherCheckCondition;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.attribute.EnvironmentAttribute;

import java.util.Optional;

/**
 * 原版 {@code LootItemConditions} 注册的 20 种条件的编解码器。字段顺序、必填与可选、缺省值与原版相同。
 */
public final class ConditionCodecs {
    private ConditionCodecs() {
    }

    /**
     * 原版 {@code InvertedLootItemCondition.MAP_CODEC}：{@code term}。
     */
    public static MapCodec<InvertedCondition> inverted(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.condition().fieldOf("term").forGetter(InvertedCondition::term)
        ).apply(i, InvertedCondition::new));
    }

    /**
     * 原版 {@code AnyOfCondition.MAP_CODEC}：{@code terms}（条件列表）。
     */
    public static MapCodec<AnyOfCondition> anyOf(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.condition().listOf().fieldOf("terms").forGetter(AnyOfCondition::terms)
        ).apply(i, AnyOfCondition::new));
    }

    /**
     * 原版 {@code AllOfCondition.MAP_CODEC}：{@code terms}（条件列表）。
     */
    public static MapCodec<AllOfCondition> allOf(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.condition().listOf().fieldOf("terms").forGetter(AllOfCondition::terms)
        ).apply(i, AllOfCondition::new));
    }

    /**
     * 原版 {@code LootItemRandomChanceCondition.MAP_CODEC}：{@code chance}（数字提供器）。
     */
    public static MapCodec<RandomChanceCondition> randomChance(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.number().fieldOf("chance").forGetter(RandomChanceCondition::chance)
        ).apply(i, RandomChanceCondition::new));
    }

    /**
     * 原版 {@code LootItemRandomChanceWithEnchantedBonusCondition.MAP_CODEC}：{@code unenchanted_chance}
     * （[0, 1] 的浮点数）、{@code enchanted_chance}、{@code enchantment}。
     */
    public static MapCodec<RandomChanceWithEnchantedBonusCondition> randomChanceWithEnchantedBonus(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("unenchanted_chance").forGetter(RandomChanceWithEnchantedBonusCondition::unenchantedChance),
                NumberCodecs.LEVEL_BASED_VALUE.fieldOf("enchanted_chance").forGetter(RandomChanceWithEnchantedBonusCondition::enchantedChance),
                RegistryCodecs.fixed(RegistryIds.ENCHANTMENT).fieldOf("enchantment").forGetter(RandomChanceWithEnchantedBonusCondition::enchantment)
        ).apply(i, RandomChanceWithEnchantedBonusCondition::new));
    }

    /**
     * 原版 {@code LootItemEntityPropertyCondition.MAP_CODEC}：{@code predicate}（可选）、{@code entity}（必填）。
     */
    public static MapCodec<EntityPropertiesCondition> entityProperties(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                PredicateCodecs.ENTITY.optionalFieldOf("predicate").forGetter(EntityPropertiesCondition::predicate),
                CommonCodecs.ENTITY_TARGET.fieldOf("entity").forGetter(EntityPropertiesCondition::entity)
        ).apply(i, EntityPropertiesCondition::new));
    }

    /**
     * 原版 {@code LootItemKilledByPlayerCondition.MAP_CODEC}：没有字段。
     */
    public static MapCodec<KilledByPlayerCondition> killedByPlayer(LootCodecs codecs) {
        return MapCodec.unit(KilledByPlayerCondition.INSTANCE);
    }

    /**
     * 原版 {@code EntityHasScoreCondition.MAP_CODEC}：{@code scores}（计分项到 {@code IntRange}）、{@code entity}（必填）。
     */
    public static MapCodec<EntityScoresCondition> entityScores(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.unboundedMap(Codec.STRING, codecs.intRange()).fieldOf("scores").forGetter(EntityScoresCondition::scores),
                CommonCodecs.ENTITY_TARGET.fieldOf("entity").forGetter(EntityScoresCondition::entity)
        ).apply(i, EntityScoresCondition::new));
    }

    /**
     * 原版 {@code LootItemBlockStatePropertyCondition.MAP_CODEC}：{@code block}、{@code properties}（可选）。
     * 解码之后检查每个属性名都是该方块的属性，否则为 {@code Block <方块> has no property<属性名>}；
     * 原版在这里打印方块的注册表引用，例如 {@code Reference{ResourceKey[minecraft:block / minecraft:stone]=Block{minecraft:stone}}}。
     */
    public static MapCodec<BlockStatePropertyCondition> blockStateProperty(LootCodecs codecs) {
        return RecordCodecBuilder.<BlockStatePropertyCondition>mapCodec(i -> i.group(
                CommonCodecs.BLOCK.fieldOf("block").forGetter(BlockStatePropertyCondition::block),
                PredicateCodecs.STATE_PROPERTIES.optionalFieldOf("properties").forGetter(BlockStatePropertyCondition::properties)
        ).apply(i, BlockStatePropertyCondition::new)).validate(ConditionCodecs::validateBlockProperties);
    }

    private static DataResult<BlockStatePropertyCondition> validateBlockProperties(BlockStatePropertyCondition condition) {
        Optional<String> unknown = condition.properties().flatMap(properties -> unknownProperty(condition.block(), properties));
        return unknown.<DataResult<BlockStatePropertyCondition>>map(name -> DataResult.error(() -> "Block "
                        + PredicateCodecs.holderString(RegistryIds.BLOCK, condition.block().key(),
                        "Block{" + condition.block().key().asString() + "}")
                        + " has no property" + name))
                .orElseGet(() -> DataResult.success(condition));
    }

    /**
     * 原版 {@code StatePropertiesPredicate#checkState}：第一个方块没有的属性名。
     */
    private static Optional<String> unknownProperty(Block block, StatePropertiesPredicate properties) {
        var known = block.properties().keySet();
        for (var property : properties.properties()) {
            if (!known.contains(property.name())) {
                return Optional.of(property.name());
            }
        }
        return Optional.empty();
    }

    /**
     * 原版 {@code MatchTool.MAP_CODEC}：{@code predicate}（可选，物品谓词）。
     */
    public static MapCodec<MatchToolCondition> matchTool(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                PredicateCodecs.ITEM.optionalFieldOf("predicate").forGetter(MatchToolCondition::predicate)
        ).apply(i, MatchToolCondition::new));
    }

    /**
     * 原版 {@code BonusLevelTableCondition.MAP_CODEC}：{@code enchantment}、{@code chances}（非空的浮点数列表）。
     */
    public static MapCodec<TableBonusCondition> tableBonus(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryCodecs.fixed(RegistryIds.ENCHANTMENT).fieldOf("enchantment").forGetter(TableBonusCondition::enchantment),
                ExtraCodecs.nonEmptyList(Codec.FLOAT.listOf()).fieldOf("chances").forGetter(TableBonusCondition::values)
        ).apply(i, TableBonusCondition::new));
    }

    /**
     * 原版 {@code ExplosionCondition.MAP_CODEC}：没有字段。
     */
    public static MapCodec<SurvivesExplosionCondition> survivesExplosion(LootCodecs codecs) {
        return MapCodec.unit(SurvivesExplosionCondition.INSTANCE);
    }

    /**
     * 原版 {@code DamageSourceCondition.MAP_CODEC}：{@code predicate}（可选）。
     */
    public static MapCodec<DamageSourcePropertiesCondition> damageSourceProperties(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                PredicateCodecs.DAMAGE_SOURCE.optionalFieldOf("predicate").forGetter(DamageSourcePropertiesCondition::predicate)
        ).apply(i, DamageSourcePropertiesCondition::new));
    }

    /**
     * 原版 {@code LocationCheck.MAP_CODEC}：{@code predicate}（可选）、{@code offsetX} / {@code offsetY} /
     * {@code offsetZ}（默认 0）。
     */
    public static MapCodec<LocationCheckCondition> locationCheck(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                PredicateCodecs.LOCATION.optionalFieldOf("predicate").forGetter(LocationCheckCondition::predicate),
                Codec.INT.optionalFieldOf("offsetX", 0).forGetter(LocationCheckCondition::offsetX),
                Codec.INT.optionalFieldOf("offsetY", 0).forGetter(LocationCheckCondition::offsetY),
                Codec.INT.optionalFieldOf("offsetZ", 0).forGetter(LocationCheckCondition::offsetZ)
        ).apply(i, LocationCheckCondition::new));
    }

    /**
     * 原版 {@code WeatherCheck.MAP_CODEC}：{@code raining}、{@code thundering}，都可选。
     */
    public static MapCodec<WeatherCheckCondition> weatherCheck(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("raining").forGetter(WeatherCheckCondition::isRaining),
                Codec.BOOL.optionalFieldOf("thundering").forGetter(WeatherCheckCondition::isThundering)
        ).apply(i, WeatherCheckCondition::new));
    }

    /**
     * 原版 {@code ConditionReference.MAP_CODEC}：{@code name}（任意命名空间 ID）。
     */
    public static MapCodec<ReferenceCondition> reference(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Identifiers.CODEC.fieldOf("name").forGetter(ReferenceCondition::name)
        ).apply(i, ReferenceCondition::new));
    }

    /**
     * 原版 {@code TimeCheck.MAP_CODEC}：{@code clock}（必填，世界时钟）、{@code period}（可选）、{@code value}。
     */
    public static MapCodec<TimeCheckCondition> timeCheck(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryCodecs.fixed(RegistryIds.WORLD_CLOCK).fieldOf("clock").forGetter(TimeCheckCondition::clock),
                Codec.LONG.optionalFieldOf("period").forGetter(TimeCheckCondition::period),
                codecs.intRange().fieldOf("value").forGetter(TimeCheckCondition::value)
        ).apply(i, TimeCheckCondition::new));
    }

    /**
     * 原版 {@code ValueCheckCondition.MAP_CODEC}：{@code value}（数字提供器）、{@code range}（{@code IntRange}）。
     */
    public static MapCodec<ValueCheckCondition> valueCheck(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.number().fieldOf("value").forGetter(ValueCheckCondition::value),
                codecs.intRange().fieldOf("range").forGetter(ValueCheckCondition::range)
        ).apply(i, ValueCheckCondition::new));
    }

    /**
     * 原版 {@code EnchantmentActiveCheck.MAP_CODEC}：{@code active}。
     */
    public static MapCodec<EnchantmentActiveCheckCondition> enchantmentActiveCheck(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("active").forGetter(EnchantmentActiveCheckCondition::active)
        ).apply(i, EnchantmentActiveCheckCondition::new));
    }

    /**
     * 原版 {@code EnvironmentAttributeCheck.MAP_CODEC}：按 {@code attribute} 分派，{@code value} 用该属性的取值编解码器解码。
     * 取值的编解码器是 Minestom 的（{@link MinestomCodecs#adapt}），原版额外检查的取值范围不在其中。
     */
    public static MapCodec<EnvironmentAttributeCheckCondition<?>> environmentAttributeCheck(LootCodecs codecs) {
        return NumberCodecs.ENVIRONMENT_ATTRIBUTE.dispatchMap("attribute", EnvironmentAttributeCheckCondition::attribute,
                ConditionCodecs::environmentAttributeValue);
    }

    private static <V> MapCodec<EnvironmentAttributeCheckCondition<?>> environmentAttributeValue(EnvironmentAttribute<V> attribute) {
        return MinestomCodecs.adapt(attribute.valueCodec()).fieldOf("value").xmap(
                value -> new EnvironmentAttributeCheckCondition<>(attribute, value),
                ConditionCodecs::checkedValue);
    }

    /**
     * 分派编解码器只把属性相同的条件交给这里，取值的类型就是该属性的取值类型。
     */
    @SuppressWarnings("unchecked")
    private static <V> V checkedValue(EnvironmentAttributeCheckCondition<?> condition) {
        return (V) condition.value();
    }
}
