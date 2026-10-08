package io.ib67.yuanban.loot.codec;

import com.mojang.datafixers.Products;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.codec.ComponentSerialization;
import io.ib67.yuanban.codec.ExtraCodecs;
import io.ib67.yuanban.codec.Identifiers;
import io.ib67.yuanban.codec.ItemCodecs;
import io.ib67.yuanban.codec.MapDecorationTypes;
import io.ib67.yuanban.codec.MinestomCodecs;
import io.ib67.yuanban.codec.NbtCodecs;
import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.codec.RegistryCodecs;
import io.ib67.yuanban.codec.RegistryId;
import io.ib67.yuanban.codec.RegistryIds;
import io.ib67.yuanban.codec.StringRepresentables;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.AttributeModifierRecord;
import io.ib67.yuanban.loot.function.CopyBlockStateFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyCustomDataFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.CopyOperation;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.ExplosionDecayFunction;
import io.ib67.yuanban.loot.function.FillPlayerHeadFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.LimitCountFunction;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.MergeStrategy;
import io.ib67.yuanban.loot.function.ModifyContentsFunction;
import io.ib67.yuanban.loot.function.NbtSource;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetAttributesFunction;
import io.ib67.yuanban.loot.function.SetBannerPatternFunction;
import io.ib67.yuanban.loot.function.SetBookCoverFunction;
import io.ib67.yuanban.loot.function.SetComponentsFunction;
import io.ib67.yuanban.loot.function.SetContainerContentsFunction;
import io.ib67.yuanban.loot.function.SetContainerLootTableFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetCustomDataFunction;
import io.ib67.yuanban.loot.function.SetCustomModelDataFunction;
import io.ib67.yuanban.loot.function.SetDamageFunction;
import io.ib67.yuanban.loot.function.SetEnchantmentsFunction;
import io.ib67.yuanban.loot.function.SetFireworkExplosionFunction;
import io.ib67.yuanban.loot.function.SetFireworksFunction;
import io.ib67.yuanban.loot.function.SetInstrumentFunction;
import io.ib67.yuanban.loot.function.SetItemFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetNameTarget;
import io.ib67.yuanban.loot.function.SetOminousBottleAmplifierFunction;
import io.ib67.yuanban.loot.function.SetPotionFunction;
import io.ib67.yuanban.loot.function.SetRandomDyesFunction;
import io.ib67.yuanban.loot.function.SetRandomPotionFunction;
import io.ib67.yuanban.loot.function.SetStewEffectFunction;
import io.ib67.yuanban.loot.function.SetWritableBookPagesFunction;
import io.ib67.yuanban.loot.function.SetWrittenBookPagesFunction;
import io.ib67.yuanban.loot.function.SmeltFunction;
import io.ib67.yuanban.loot.function.StewEffectEntry;
import io.ib67.yuanban.loot.function.ToggleTooltipsFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.color.Color;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.potion.PotionEffect;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 原版 {@code LootItemFunctions} 注册的 43 种修饰器的编解码器。除 {@code sequence} 外，字段顺序都是
 * {@code conditions}（默认空列表）在前，然后是修饰器自己的字段；字段名、必填与可选、缺省值与原版相同。
 */
public final class FunctionCodecs {
    /**
     * 原版 {@code NbtProviders.CODEC}：实体目标名或 {@code block_entity}（上下文来源），或按 {@code type}
     * （原版 {@code loot_nbt_provider_type} 注册表）分派的 {@code storage}（{@code source}）、{@code context}（{@code target}）。
     */
    static final Codec<NbtSource> NBT_SOURCE = nbtSource();

    private FunctionCodecs() {
    }

    /**
     * 原版 {@code LootItemConditionalFunction#commonFields}：{@code conditions}，默认空列表。
     */
    private static <T extends LootFunction> Products.P1<RecordCodecBuilder.Mu<T>, List<LootCondition>> commonFields(
            RecordCodecBuilder.Instance<T> i, LootCodecs codecs) {
        return i.group(codecs.condition().listOf().optionalFieldOf("conditions", List.of()).forGetter(LootFunction::conditions));
    }

    /**
     * 原版 {@code SetItemCountFunction.MAP_CODEC}：{@code count}、{@code add}（默认 false）。
     */
    public static MapCodec<SetCountFunction> setCount(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                codecs.number().fieldOf("count").forGetter(SetCountFunction::count),
                Codec.BOOL.optionalFieldOf("add", false).forGetter(SetCountFunction::add)
        )).apply(i, SetCountFunction::new));
    }

    /**
     * 原版 {@code SetItemFunction.MAP_CODEC}：{@code item}（不能是 {@code minecraft:air}）。
     */
    public static MapCodec<SetItemFunction> setItem(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                ItemCodecs.ITEM.fieldOf("item").forGetter(SetItemFunction::item)
        ).apply(i, SetItemFunction::new));
    }

    /**
     * 原版 {@code EnchantWithLevelsFunction.MAP_CODEC}：{@code levels}、{@code options}（可选，附魔集合）、
     * {@code include_additional_cost_component}（默认 false）。
     */
    public static MapCodec<EnchantWithLevelsFunction> enchantWithLevels(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                codecs.number().fieldOf("levels").forGetter(EnchantWithLevelsFunction::levels),
                RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT).optionalFieldOf("options").forGetter(EnchantWithLevelsFunction::options),
                Codec.BOOL.optionalFieldOf("include_additional_cost_component", false)
                        .forGetter(EnchantWithLevelsFunction::includeAdditionalCostComponent)
        )).apply(i, EnchantWithLevelsFunction::new));
    }

    /**
     * 原版 {@code EnchantRandomlyFunction.MAP_CODEC}：{@code options}（可选）、{@code only_compatible}（默认 true）、
     * {@code include_additional_cost_component}（默认 false）。
     */
    public static MapCodec<EnchantRandomlyFunction> enchantRandomly(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                RegistryCodecs.homogeneousList(RegistryIds.ENCHANTMENT).optionalFieldOf("options").forGetter(EnchantRandomlyFunction::options),
                Codec.BOOL.optionalFieldOf("only_compatible", true).forGetter(EnchantRandomlyFunction::onlyCompatible),
                Codec.BOOL.optionalFieldOf("include_additional_cost_component", false)
                        .forGetter(EnchantRandomlyFunction::includeAdditionalCostComponent)
        )).apply(i, EnchantRandomlyFunction::new));
    }

    /**
     * 原版 {@code SetEnchantmentsFunction.MAP_CODEC}：{@code enchantments}（附魔 ID 到数字提供器，默认空）、
     * {@code add}（默认 false）。
     */
    public static MapCodec<SetEnchantmentsFunction> setEnchantments(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                Codec.unboundedMap(RegistryCodecs.fixed(RegistryIds.ENCHANTMENT), codecs.number())
                        .optionalFieldOf("enchantments", Map.of()).forGetter(SetEnchantmentsFunction::enchantments),
                Codec.BOOL.optionalFieldOf("add", false).forGetter(SetEnchantmentsFunction::add)
        )).apply(i, SetEnchantmentsFunction::new));
    }

    /**
     * 原版 {@code SetCustomDataFunction.MAP_CODEC}：{@code tag}（SNBT 字符串或 NBT 对象）。
     */
    public static MapCodec<SetCustomDataFunction> setCustomData(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                NbtCodecs.LENIENT_COMPOUND_TAG.fieldOf("tag").forGetter(SetCustomDataFunction::tag)
        ).apply(i, SetCustomDataFunction::new));
    }

    /**
     * 原版 {@code SetComponentsFunction.MAP_CODEC}：{@code components}（组件补丁，{@code !} 前缀表示移除）。
     */
    public static MapCodec<SetComponentsFunction> setComponents(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                ItemCodecs.DATA_COMPONENT_PATCH.fieldOf("components").forGetter(SetComponentsFunction::components)
        ).apply(i, SetComponentsFunction::new));
    }

    /**
     * 原版 {@code SmeltItemFunction.MAP_CODEC}：{@code use_input_count}（默认 true）。
     */
    public static MapCodec<SmeltFunction> furnaceSmelt(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                Codec.BOOL.optionalFieldOf("use_input_count", true).forGetter(SmeltFunction::useInputCount)
        ).apply(i, SmeltFunction::new));
    }

    /**
     * 原版 {@code EnchantedCountIncreaseFunction.MAP_CODEC}：{@code enchantment}、{@code count}、{@code limit}（默认 0）。
     */
    public static MapCodec<EnchantedCountIncreaseFunction> enchantedCountIncrease(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                RegistryCodecs.fixed(RegistryIds.ENCHANTMENT).fieldOf("enchantment").forGetter(EnchantedCountIncreaseFunction::enchantment),
                codecs.number().fieldOf("count").forGetter(EnchantedCountIncreaseFunction::count),
                Codec.INT.optionalFieldOf("limit", 0).forGetter(EnchantedCountIncreaseFunction::limit)
        )).apply(i, EnchantedCountIncreaseFunction::new));
    }

    /**
     * 原版 {@code SetItemDamageFunction.MAP_CODEC}：{@code damage}、{@code add}（默认 false）。
     */
    public static MapCodec<SetDamageFunction> setDamage(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                codecs.number().fieldOf("damage").forGetter(SetDamageFunction::damage),
                Codec.BOOL.optionalFieldOf("add", false).forGetter(SetDamageFunction::add)
        )).apply(i, SetDamageFunction::new));
    }

    /**
     * 原版 {@code SetAttributesFunction.MAP_CODEC}：{@code modifiers}、{@code replace}（默认 true）。
     * 修饰符的 {@code slot} 是单个槽位组或非空的槽位组列表。
     */
    public static MapCodec<SetAttributesFunction> setAttributes(LootCodecs codecs) {
        Codec<AttributeModifierRecord> modifier = RecordCodecBuilder.create(i -> i.group(
                Identifiers.CODEC.fieldOf("id").forGetter(AttributeModifierRecord::id),
                CommonCodecs.ATTRIBUTE.fieldOf("attribute").forGetter(AttributeModifierRecord::attribute),
                CommonCodecs.ATTRIBUTE_OPERATION.fieldOf("operation").forGetter(AttributeModifierRecord::operation),
                codecs.number().fieldOf("amount").forGetter(AttributeModifierRecord::amount),
                ExtraCodecs.nonEmptyList(ExtraCodecs.compactListCodec(CommonCodecs.EQUIPMENT_SLOT_GROUP))
                        .fieldOf("slot").forGetter(AttributeModifierRecord::slots)
        ).apply(i, AttributeModifierRecord::new));
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                modifier.listOf().fieldOf("modifiers").forGetter(SetAttributesFunction::modifiers),
                Codec.BOOL.optionalFieldOf("replace", true).forGetter(SetAttributesFunction::replace)
        )).apply(i, SetAttributesFunction::new));
    }

    /**
     * 原版 {@code SetNameFunction.MAP_CODEC}：{@code name}（可选，文本组件）、{@code entity}（可选）、
     * {@code target}（{@code custom_name} 或 {@code item_name}，默认 {@code custom_name}）。
     */
    public static MapCodec<SetNameFunction> setName(LootCodecs codecs) {
        Codec<SetNameTarget> target = StringRepresentables.fromEnum(SetNameTarget.values(),
                value -> value.name().toLowerCase(Locale.ROOT));
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(SetNameFunction::name),
                CommonCodecs.ENTITY_TARGET.optionalFieldOf("entity").forGetter(SetNameFunction::resolutionContext),
                target.optionalFieldOf("target", SetNameTarget.CUSTOM_NAME).forGetter(SetNameFunction::target)
        )).apply(i, SetNameFunction::new));
    }

    /**
     * 原版 {@code ExplorationMapFunction.MAP_CODEC}：{@code destination}（结构标签 ID，默认 {@code on_treasure_maps}）、
     * {@code decoration}（地图装饰类型，默认 {@code mansion}）、{@code zoom}（默认 2）、{@code search_radius}
     * （默认 50）、{@code skip_existing_chunks}（默认 true）。
     */
    public static MapCodec<ExplorationMapFunction> explorationMap(LootCodecs codecs) {
        Codec<Key> decoration = Identifiers.CODEC.validate(id -> MapDecorationTypes.IDS.contains(id)
                ? DataResult.success(id)
                : DataResult.error(() -> "Unknown registry key in " + RegistryIds.MAP_DECORATION_TYPE + ": " + id.asString()));
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                RegistryCodecs.tagKey(RegistryIds.STRUCTURE).optionalFieldOf("destination", ExplorationMapFunction.DEFAULT_DESTINATION)
                        .forGetter(ExplorationMapFunction::destination),
                decoration.optionalFieldOf("decoration", ExplorationMapFunction.DEFAULT_DECORATION)
                        .forGetter(ExplorationMapFunction::mapDecoration),
                Codec.BYTE.optionalFieldOf("zoom", (byte) 2).forGetter(ExplorationMapFunction::zoom),
                Codec.INT.optionalFieldOf("search_radius", 50).forGetter(ExplorationMapFunction::searchRadius),
                Codec.BOOL.optionalFieldOf("skip_existing_chunks", true).forGetter(ExplorationMapFunction::skipExistingChunks)
        )).apply(i, ExplorationMapFunction::new));
    }

    /**
     * 原版 {@code SetStewEffectFunction.MAP_CODEC}：{@code effects}（{@code {type, duration}} 列表，默认空）。
     * 同一个效果出现两次为 {@code Encountered duplicate mob effect: '<效果>'}；原版在这里打印效果的注册表引用，
     * 引用中的效果对象没有自己的 {@code toString}，本模块在该位置打印效果 ID。
     */
    public static MapCodec<SetStewEffectFunction> setStewEffect(LootCodecs codecs) {
        Codec<StewEffectEntry> entry = RecordCodecBuilder.create(i -> i.group(
                PredicateCodecs.mobEffect().fieldOf("type").forGetter(StewEffectEntry::effect),
                codecs.number().fieldOf("duration").forGetter(StewEffectEntry::duration)
        ).apply(i, StewEffectEntry::new));
        Codec<List<StewEffectEntry>> effects = entry.listOf().validate(entries -> {
            var seen = new HashSet<PotionEffect>();
            for (var effect : entries) {
                if (!seen.add(effect.effect())) {
                    Key id = effect.effect().key();
                    return DataResult.error(() -> "Encountered duplicate mob effect: '"
                            + PredicateCodecs.holderString(RegistryIds.MOB_EFFECT, id, id.asString()) + "'");
                }
            }
            return DataResult.success(entries);
        });
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                effects.optionalFieldOf("effects", List.of()).forGetter(SetStewEffectFunction::effects)
        ).apply(i, SetStewEffectFunction::new));
    }

    /**
     * 原版 {@code CopyNameFunction.MAP_CODEC}：{@code source}（实体目标名或 {@code block_entity}）。
     */
    public static MapCodec<CopyNameFunction> copyName(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                CommonCodecs.ENTITY_OR_BLOCK.fieldOf("source").forGetter(CopyNameFunction::source)
        ).apply(i, CopyNameFunction::new));
    }

    /**
     * 原版 {@code SetContainerContents.MAP_CODEC}：{@code component}、{@code entries}（条目列表，必填）。
     */
    public static MapCodec<SetContainerContentsFunction> setContents(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.CONTAINER_COMPONENT.fieldOf("component").forGetter(SetContainerContentsFunction::component),
                codecs.entry().listOf().fieldOf("entries").forGetter(SetContainerContentsFunction::entries)
        )).apply(i, SetContainerContentsFunction::new));
    }

    /**
     * 原版 {@code ModifyContainerContents.MAP_CODEC}：{@code component}、{@code modifier}（修饰器或修饰器列表）。
     */
    public static MapCodec<ModifyContentsFunction> modifyContents(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.CONTAINER_COMPONENT.fieldOf("component").forGetter(ModifyContentsFunction::component),
                codecs.function().fieldOf("modifier").forGetter(ModifyContentsFunction::modifier)
        )).apply(i, ModifyContentsFunction::new));
    }

    /**
     * 原版 {@code FilteredFunction.MAP_CODEC}：{@code item_filter}、{@code on_pass}、{@code on_fail}（都可选）。
     */
    public static MapCodec<FilteredFunction> filtered(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                PredicateCodecs.ITEM.fieldOf("item_filter").forGetter(FilteredFunction::itemFilter),
                codecs.function().optionalFieldOf("on_pass").forGetter(FilteredFunction::onPass),
                codecs.function().optionalFieldOf("on_fail").forGetter(FilteredFunction::onFail)
        )).apply(i, FilteredFunction::new));
    }

    /**
     * 原版 {@code LimitCount.MAP_CODEC}：{@code limit}（{@code IntRange}，必填）。
     */
    public static MapCodec<LimitCountFunction> limitCount(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                codecs.intRange().fieldOf("limit").forGetter(LimitCountFunction::limit)
        ).apply(i, LimitCountFunction::new));
    }

    /**
     * 原版 {@code ApplyBonusCount.MAP_CODEC}：{@code enchantment}，公式按 {@code formula} 分派，参数在可选的
     * {@code parameters} 中。未知公式为 {@code No formula type with id: '<id>'}。
     */
    public static MapCodec<ApplyBonusFunction> applyBonus(LootCodecs codecs) {
        var formulas = new LinkedHashMap<Key, Codec<? extends ApplyBonusFormula>>();
        formulas.put(Key.key("binomial_with_bonus_count"), RecordCodecBuilder.<ApplyBonusFormula.BinomialWithBonusCount>create(i -> i.group(
                Codec.INT.fieldOf("extra").forGetter(ApplyBonusFormula.BinomialWithBonusCount::extraRounds),
                Codec.FLOAT.fieldOf("probability").forGetter(ApplyBonusFormula.BinomialWithBonusCount::probability)
        ).apply(i, ApplyBonusFormula.BinomialWithBonusCount::new)));
        formulas.put(Key.key("ore_drops"), MapCodec.unit(ApplyBonusFormula.OreDrops.INSTANCE).codec());
        formulas.put(Key.key("uniform_bonus_count"), RecordCodecBuilder.<ApplyBonusFormula.UniformBonusCount>create(i -> i.group(
                Codec.INT.fieldOf("bonusMultiplier").forGetter(ApplyBonusFormula.UniformBonusCount::bonusMultiplier)
        ).apply(i, ApplyBonusFormula.UniformBonusCount::new)));
        Map<Key, Codec<? extends ApplyBonusFormula>> table = Map.copyOf(formulas);
        Codec<Key> formulaType = Identifiers.CODEC.validate(id -> table.containsKey(id)
                ? DataResult.success(id)
                : DataResult.error(() -> "No formula type with id: '" + id.asString() + "'"));
        MapCodec<ApplyBonusFormula> formula = ExtraCodecs.dispatchOptionalValue("formula", "parameters", formulaType,
                FunctionCodecs::formulaId, table::get);
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                RegistryCodecs.fixed(RegistryIds.ENCHANTMENT).fieldOf("enchantment").forGetter(ApplyBonusFunction::enchantment),
                formula.forGetter(ApplyBonusFunction::formula)
        )).apply(i, ApplyBonusFunction::new));
    }

    private static Key formulaId(ApplyBonusFormula formula) {
        return Key.key(switch (formula) {
            case ApplyBonusFormula.BinomialWithBonusCount ignored -> "binomial_with_bonus_count";
            case ApplyBonusFormula.OreDrops ignored -> "ore_drops";
            case ApplyBonusFormula.UniformBonusCount ignored -> "uniform_bonus_count";
        });
    }

    /**
     * 原版 {@code SetContainerLootTable.MAP_CODEC}：{@code name}（战利品表 ID，不检查是否存在）、{@code seed}
     * （默认 0）、{@code type}（方块实体类型）。
     */
    public static MapCodec<SetContainerLootTableFunction> setLootTable(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                Identifiers.CODEC.fieldOf("name").forGetter(SetContainerLootTableFunction::name),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(SetContainerLootTableFunction::seed),
                CommonCodecs.BLOCK_ENTITY_TYPE.fieldOf("type").forGetter(SetContainerLootTableFunction::type)
        )).apply(i, SetContainerLootTableFunction::new));
    }

    /**
     * 原版 {@code ApplyExplosionDecay.MAP_CODEC}：只有 {@code conditions}。
     */
    public static MapCodec<ExplosionDecayFunction> explosionDecay(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).apply(i, ExplosionDecayFunction::new));
    }

    /**
     * 原版 {@code SetLoreFunction.MAP_CODEC}：{@code lore}（最多 256 行）、内联的列表操作（{@code mode} 必填，
     * 上限 256）、{@code entity}（可选）。
     */
    public static MapCodec<SetLoreFunction> setLore(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                ComponentSerialization.CODEC.sizeLimitedListOf(256).fieldOf("lore").forGetter(SetLoreFunction::lore),
                CommonCodecs.listOperation(256).forGetter(SetLoreFunction::mode),
                CommonCodecs.ENTITY_TARGET.optionalFieldOf("entity").forGetter(SetLoreFunction::resolutionContext)
        )).apply(i, SetLoreFunction::new));
    }

    /**
     * 原版 {@code FillPlayerHead.MAP_CODEC}：{@code entity}（必填）。
     */
    public static MapCodec<FillPlayerHeadFunction> fillPlayerHead(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                CommonCodecs.ENTITY_TARGET.fieldOf("entity").forGetter(FillPlayerHeadFunction::entity)
        ).apply(i, FillPlayerHeadFunction::new));
    }

    /**
     * 原版 {@code CopyCustomDataFunction.MAP_CODEC}：{@code source}（NBT 来源）、{@code ops}（{@code {source, target, op}}
     * 列表，{@code op} 必填）。
     */
    public static MapCodec<CopyCustomDataFunction> copyCustomData(LootCodecs codecs) {
        Codec<MergeStrategy> strategy = StringRepresentables.fromEnum(MergeStrategy.values(),
                value -> value.name().toLowerCase(Locale.ROOT));
        Codec<CopyOperation> operation = RecordCodecBuilder.create(i -> i.group(
                NbtPath.CODEC.fieldOf("source").forGetter(CopyOperation::source),
                NbtPath.CODEC.fieldOf("target").forGetter(CopyOperation::target),
                strategy.fieldOf("op").forGetter(CopyOperation::op)
        ).apply(i, CopyOperation::new));
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                NBT_SOURCE.fieldOf("source").forGetter(CopyCustomDataFunction::source),
                operation.listOf().fieldOf("ops").forGetter(CopyCustomDataFunction::ops)
        )).apply(i, CopyCustomDataFunction::new));
    }

    /**
     * 原版 {@code CopyBlockState.MAP_CODEC}：{@code block}、{@code properties}（属性名列表，必填）。
     */
    public static MapCodec<CopyBlockStateFunction> copyState(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.BLOCK.fieldOf("block").forGetter(CopyBlockStateFunction::block),
                Codec.STRING.listOf().fieldOf("properties").forGetter(CopyBlockStateFunction::properties)
        )).apply(i, CopyBlockStateFunction::new));
    }

    /**
     * 原版 {@code SetBannerPatternFunction.MAP_CODEC}：{@code patterns}（{@code {pattern, color}} 列表，图案是
     * 注册表 ID 或内联定义）、{@code append}（必填）。
     */
    public static MapCodec<SetBannerPatternFunction> setBannerPattern(LootCodecs codecs) {
        Codec<BannerPatterns.Layer> layer = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.file(RegistryIds.BANNER_PATTERN, MinestomCodecs.adapt(BannerPattern.REGISTRY_CODEC), true)
                        .fieldOf("pattern").forGetter(BannerPatterns.Layer::pattern),
                CommonCodecs.DYE_COLOR.fieldOf("color").forGetter(BannerPatterns.Layer::color)
        ).apply(i, BannerPatterns.Layer::new));
        Codec<BannerPatterns> patterns = layer.listOf().xmap(BannerPatterns::new, BannerPatterns::layers);
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                patterns.fieldOf("patterns").forGetter(SetBannerPatternFunction::patterns),
                Codec.BOOL.fieldOf("append").forGetter(SetBannerPatternFunction::append)
        )).apply(i, SetBannerPatternFunction::new));
    }

    /**
     * 原版 {@code SetPotionFunction.MAP_CODEC}：{@code id}（药水 ID）。
     */
    public static MapCodec<SetPotionFunction> setPotion(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                CommonCodecs.POTION.fieldOf("id").forGetter(SetPotionFunction::id)
        ).apply(i, SetPotionFunction::new));
    }

    /**
     * 原版 {@code SetRandomDyesFunction.MAP_CODEC}：{@code number_of_dyes}。
     */
    public static MapCodec<SetRandomDyesFunction> setRandomDyes(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                codecs.number().fieldOf("number_of_dyes").forGetter(SetRandomDyesFunction::numberOfDyes)
        ).apply(i, SetRandomDyesFunction::new));
    }

    /**
     * 原版 {@code SetRandomPotionFunction.MAP_CODEC}：{@code options}（可选，药水集合）。
     */
    public static MapCodec<SetRandomPotionFunction> setRandomPotion(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                RegistryCodecs.homogeneousList(RegistryIds.POTION).optionalFieldOf("options").forGetter(SetRandomPotionFunction::options)
        ).apply(i, SetRandomPotionFunction::new));
    }

    /**
     * 原版 {@code SetInstrumentFunction.MAP_CODEC}：{@code options}（必填，乐器集合）。
     */
    public static MapCodec<SetInstrumentFunction> setInstrument(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                RegistryCodecs.homogeneousList(RegistryIds.INSTRUMENT).fieldOf("options").forGetter(SetInstrumentFunction::options)
        ).apply(i, SetInstrumentFunction::new));
    }

    /**
     * 原版 {@code FunctionReference.MAP_CODEC}：{@code name}（物品修饰器 ID，解码时不检查是否存在）。
     */
    public static MapCodec<FunctionReference> reference(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                Identifiers.CODEC.fieldOf("name").forGetter(FunctionReference::name)
        ).apply(i, FunctionReference::new));
    }

    /**
     * 原版 {@code SequenceFunction.MAP_CODEC}：{@code functions}（带 {@code function} 字段的修饰器对象列表），没有
     * {@code conditions}。
     */
    public static MapCodec<SequenceFunction> sequence(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                codecs.typedFunction().listOf().fieldOf("functions").forGetter(SequenceFunction::functions)
        ).apply(i, SequenceFunction::new));
    }

    /**
     * 原版 {@code CopyComponentsFunction.MAP_CODEC}：{@code source}（实体目标名、{@code block_entity} 或
     * {@code tool}）、{@code include}、{@code exclude}（可选的组件类型列表）。
     */
    public static MapCodec<CopyComponentsFunction> copyComponents(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.ENTITY_BLOCK_OR_TOOL.fieldOf("source").forGetter(CopyComponentsFunction::source),
                CommonCodecs.dataComponentTypes().optionalFieldOf("include").forGetter(CopyComponentsFunction::include),
                CommonCodecs.dataComponentTypes().optionalFieldOf("exclude").forGetter(CopyComponentsFunction::exclude)
        )).apply(i, CopyComponentsFunction::new));
    }

    /**
     * 原版 {@code SetFireworksFunction.MAP_CODEC}：{@code explosions}（可选，最多 256 个爆炸效果加列表操作）、
     * {@code flight_duration}（可选，0 到 255）。
     */
    public static MapCodec<SetFireworksFunction> setFireworks(LootCodecs codecs) {
        Codec<FireworkExplosion> explosion = RecordCodecBuilder.create(i -> i.group(
                CommonCodecs.FIREWORK_SHAPE.fieldOf("shape").forGetter(FireworkExplosion::shape),
                colorList().optionalFieldOf("colors", List.of()).forGetter(FireworkExplosion::colors),
                colorList().optionalFieldOf("fade_colors", List.of()).forGetter(FireworkExplosion::fadeColors),
                Codec.BOOL.optionalFieldOf("has_trail", false).forGetter(FireworkExplosion::hasTrail),
                Codec.BOOL.optionalFieldOf("has_twinkle", false).forGetter(FireworkExplosion::hasTwinkle)
        ).apply(i, FireworkExplosion::new));
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.standAlone(explosion, 256).optionalFieldOf("explosions").forGetter(SetFireworksFunction::explosions),
                ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("flight_duration").forGetter(SetFireworksFunction::flightDuration)
        )).apply(i, SetFireworksFunction::new));
    }

    /**
     * 原版 {@code FireworkExplosion.COLOR_LIST_CODEC}：整数颜色的列表。
     */
    private static Codec<List<RGBLike>> colorList() {
        return Codec.INT.listOf().xmap(
                colors -> colors.stream().<RGBLike>map(Color::new).toList(),
                colors -> colors.stream().map(color -> Color.fromRGBLike(color).asRGB()).toList());
    }

    /**
     * 原版 {@code SetFireworkExplosionFunction.MAP_CODEC}：{@code shape}、{@code colors}、{@code fade_colors}、
     * {@code trail}、{@code twinkle}，都可选。
     */
    public static MapCodec<SetFireworkExplosionFunction> setFireworkExplosion(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.FIREWORK_SHAPE.optionalFieldOf("shape").forGetter(SetFireworkExplosionFunction::shape),
                Codec.INT.listOf().optionalFieldOf("colors").forGetter(SetFireworkExplosionFunction::colors),
                Codec.INT.listOf().optionalFieldOf("fade_colors").forGetter(SetFireworkExplosionFunction::fadeColors),
                Codec.BOOL.optionalFieldOf("trail").forGetter(SetFireworkExplosionFunction::trail),
                Codec.BOOL.optionalFieldOf("twinkle").forGetter(SetFireworkExplosionFunction::twinkle)
        )).apply(i, SetFireworkExplosionFunction::new));
    }

    /**
     * 原版 {@code SetBookCoverFunction.MAP_CODEC}：{@code title}（可过滤文本，最长 32）、{@code author}、
     * {@code generation}（0 到 3），都可选。
     */
    public static MapCodec<SetBookCoverFunction> setBookCover(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.filterable(Codec.string(0, 32)).optionalFieldOf("title").forGetter(SetBookCoverFunction::title),
                Codec.STRING.optionalFieldOf("author").forGetter(SetBookCoverFunction::author),
                ExtraCodecs.intRange(0, 3).optionalFieldOf("generation").forGetter(SetBookCoverFunction::generation)
        )).apply(i, SetBookCoverFunction::new));
    }

    /**
     * 原版 {@code SetWrittenBookPagesFunction.MAP_CODEC}：{@code pages}（可过滤文本组件列表，每页展开后最长 32767）、
     * 内联的列表操作（不限长度）。
     */
    public static MapCodec<SetWrittenBookPagesFunction> setWrittenBookPages(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.filterable(ComponentSerialization.flatRestrictedCodec(32767)).listOf()
                        .fieldOf("pages").forGetter(SetWrittenBookPagesFunction::pages),
                CommonCodecs.listOperation(Integer.MAX_VALUE).forGetter(SetWrittenBookPagesFunction::mode)
        )).apply(i, SetWrittenBookPagesFunction::new));
    }

    /**
     * 原版 {@code SetWritableBookPagesFunction.MAP_CODEC}：{@code pages}（最多 100 页，每页最长 1024）、
     * 内联的列表操作（上限 100）。
     */
    public static MapCodec<SetWritableBookPagesFunction> setWritableBookPages(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.filterable(Codec.string(0, 1024)).sizeLimitedListOf(100)
                        .fieldOf("pages").forGetter(SetWritableBookPagesFunction::pages),
                CommonCodecs.listOperation(100).forGetter(SetWritableBookPagesFunction::mode)
        )).apply(i, SetWritableBookPagesFunction::new));
    }

    /**
     * 原版 {@code ToggleTooltips.MAP_CODEC}：{@code toggles}（组件类型 ID 到布尔值）。
     */
    public static MapCodec<ToggleTooltipsFunction> toggleTooltips(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                Codec.unboundedMap(ItemCodecs.DATA_COMPONENT_TYPE, Codec.BOOL).fieldOf("toggles").forGetter(ToggleTooltipsFunction::toggles)
        ).apply(i, ToggleTooltipsFunction::new));
    }

    /**
     * 原版 {@code SetOminousBottleAmplifierFunction.MAP_CODEC}：{@code amplifier}。
     */
    public static MapCodec<SetOminousBottleAmplifierFunction> setOminousBottleAmplifier(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(
                codecs.number().fieldOf("amplifier").forGetter(SetOminousBottleAmplifierFunction::amplifier)
        ).apply(i, SetOminousBottleAmplifierFunction::new));
    }

    /**
     * 原版 {@code SetCustomModelDataFunction.MAP_CODEC}：{@code floats}、{@code flags}、{@code strings}、
     * {@code colors}，都可选，每个都是值列表加列表操作。颜色可以写数字提供器或 RGB 颜色（整数或三个浮点数），
     * RGB 颜色按原版转成常量。
     */
    public static MapCodec<SetCustomModelDataFunction> setCustomModelData(LootCodecs codecs) {
        Codec<NumberProvider> color = Codec.withAlternative(codecs.number(), ExtraCodecs.RGB_COLOR_CODEC,
                rgb -> new ConstantValue((float) rgb));
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).and(i.group(
                CommonCodecs.standAlone(codecs.number(), Integer.MAX_VALUE).optionalFieldOf("floats").forGetter(SetCustomModelDataFunction::floats),
                CommonCodecs.standAlone(Codec.BOOL, Integer.MAX_VALUE).optionalFieldOf("flags").forGetter(SetCustomModelDataFunction::flags),
                CommonCodecs.standAlone(Codec.STRING, Integer.MAX_VALUE).optionalFieldOf("strings").forGetter(SetCustomModelDataFunction::strings),
                CommonCodecs.standAlone(color, Integer.MAX_VALUE).optionalFieldOf("colors").forGetter(SetCustomModelDataFunction::colors)
        )).apply(i, SetCustomModelDataFunction::new));
    }

    /**
     * 原版 {@code DiscardItem.MAP_CODEC}：只有 {@code conditions}。
     */
    public static MapCodec<DiscardFunction> discard(LootCodecs codecs) {
        return RecordCodecBuilder.mapCodec(i -> commonFields(i, codecs).apply(i, DiscardFunction::new));
    }

    // ------------------------------------------------------------------
    // NBT 来源
    // ------------------------------------------------------------------

    private static Codec<NbtSource> nbtSource() {
        var types = new LinkedHashMap<Key, MapCodec<? extends NbtSource>>();
        types.put(Key.key("storage"), RecordCodecBuilder.<NbtSource.Storage>mapCodec(i -> i.group(
                Identifiers.CODEC.fieldOf("source").forGetter(NbtSource.Storage::storage)
        ).apply(i, NbtSource.Storage::new)));
        types.put(Key.key("context"), RecordCodecBuilder.<NbtSource.Context>mapCodec(i -> i.group(
                CommonCodecs.ENTITY_OR_BLOCK.fieldOf("target").forGetter(NbtSource.Context::target)
        ).apply(i, NbtSource.Context::new)));
        Map<Key, MapCodec<? extends NbtSource>> table = Map.copyOf(types);
        Codec<Key> typeCodec = RegistryCodecs.byName(RegistryId.of("loot_nbt_provider_type"),
                id -> table.containsKey(id) ? id : null, id -> id);
        Codec<NbtSource> typed = typeCodec.dispatch(source -> Key.key(switch (source) {
            case NbtSource.Storage ignored -> "storage";
            case NbtSource.Context ignored -> "context";
        }), table::get);
        Codec<NbtSource.Context> inline = CommonCodecs.ENTITY_OR_BLOCK.xmap(NbtSource.Context::new, NbtSource.Context::target);
        return Codec.either(inline, typed).xmap(either -> either.map(source -> source, source -> source),
                source -> source instanceof NbtSource.Context context ? Either.left(context) : Either.right(source));
    }
}
