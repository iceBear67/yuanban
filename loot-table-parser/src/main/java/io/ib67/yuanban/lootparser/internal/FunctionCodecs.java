package io.ib67.yuanban.lootparser.internal;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.ib67.yuanban.loot.condition.ItemPredicate;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.AttributeModifierRecord;
import io.ib67.yuanban.loot.function.AttributeOperation;
import io.ib67.yuanban.loot.function.BannerLayer;
import io.ib67.yuanban.loot.function.CopyBlockStateFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyCustomDataFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.CopyNameSource;
import io.ib67.yuanban.loot.function.CopyOperation;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.EnchantedOptions;
import io.ib67.yuanban.loot.function.EquipmentSlotGroup;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.ExplosionDecayFunction;
import io.ib67.yuanban.loot.function.FillPlayerHeadFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FilteredText;
import io.ib67.yuanban.loot.function.FireworkExplosionShape;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.InsertOperation;
import io.ib67.yuanban.loot.function.LimitCountFunction;
import io.ib67.yuanban.loot.function.ListOperation;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.MergeStrategy;
import io.ib67.yuanban.loot.function.ModifyContentsFunction;
import io.ib67.yuanban.loot.function.NbtSource;
import io.ib67.yuanban.loot.function.ReplaceAllOperation;
import io.ib67.yuanban.loot.function.ReplaceSectionOperation;
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
import io.ib67.yuanban.loot.function.StandAloneOperation;
import io.ib67.yuanban.loot.function.StewEffectEntry;
import io.ib67.yuanban.loot.function.ToggleTooltipsFunction;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.NumberProvider;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.minestom.server.component.DataComponent;
import net.minestom.server.codec.Result;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.ib67.yuanban.lootparser.internal.LootCodecs.ID;
import static io.ib67.yuanban.lootparser.internal.LootCodecs.compactList;
import static io.ib67.yuanban.lootparser.internal.LootCodecs.entityTargetCodec;
import static io.ib67.yuanban.lootparser.internal.LootCodecs.tagReference;

/**
 * 原版 26.2 {@code LootItemFunctions} 注册的 44 种物品修饰器的编解码器装配。字段名与缺省值逐一对照原版类；
 * 每个修饰器的 {@code conditions}（原版 {@code LootItemConditionalFunction#commonFields}，缺省空列表）按 Vanilla 的位置排在最后。
 * 编码端（写 JSON 方向）不需要完整实现，getter 均抛 {@link UnsupportedOperationException}。
 */
@ApiStatus.Internal
final class FunctionCodecs {
    private final LootCodecs c;
    /** 修饰器的相互引用（sequence.functions / modify_contents.modifier / filtered.on_pass 等）由这里回指。 */
    private final Supplier<Codec<LootFunction>> root;

    private FunctionCodecs(LootCodecs c, Supplier<Codec<LootFunction>> root) {
        this.c = c;
        this.root = root;
    }

    /** 装配 44 种修饰器的具体 codec，供注册表回写和 root dispatch 共用一份。 */
    static java.util.Map<Key, MapCodec<? extends LootFunction>> assemble(LootCodecs c, Supplier<Codec<LootFunction>> root) {
        return new FunctionCodecs(c, root).assembleAll();
    }

    /** 原版 {@code LootItemFunctions.ROOT_CODEC}：按 {@code function} 字段分发。 */
    static Codec<LootFunction> rootCodec(LootCodecs c, Supplier<Codec<LootFunction>> root,
                                         java.util.Map<Key, MapCodec<? extends LootFunction>> specifics) {
        return ID.comapFlatMap(id -> specifics.containsKey(id)
                        ? DataResult.success(id)
                        : DataResult.error(() -> LootCodecs.unknownKey("loot_function_type", id)),
                Function.<Key>identity())
                .dispatch("function", FunctionCodecs::functionTypeOf, specifics::get);
    }

    // ------------------------------------------------------------------
    // 装配模板：Schema + Finisher（45 个调用点逐一对应原版注册表里的顺序）
    // ------------------------------------------------------------------

    /** 单一字段解码 + 修饰器无条件构造。groups 后跟 conds，最后一个参数是 conds。 */
    private interface Finisher<T> {
        T make(List<LootCondition> conditions, Object... fields);
    }

    /** 解码出所有字段并打包成 Object[]，由 finisher 收尾。 */
    private MapCodec<Object[]> schemaCodec(MapCodec<?>[] fields) {
        var conditions = LootCodecs.compactList(Codec.lazyInitialized(() -> c.condition())).optionalFieldOf("conditions", List.of());
        var all = new MapCodec[fields.length + 1];
        System.arraycopy(fields, 0, all, 0, fields.length);
        all[fields.length] = conditions;
        return new MapCodec<>() {
            @Override
            public <S> DataResult<Object[]> decode(DynamicOps<S> ops, com.mojang.serialization.MapLike<S> input) {
                var values = new Object[all.length];
                for (int idx = 0; idx < all.length; idx++) {
                    DataResult<?> decoded = all[idx].decode(ops, input);
                    var error = decoded.error();
                    if (error.isPresent()) {
                        return DataResult.error(() -> error.get().message());
                    }
                    values[idx] = decoded.result().orElseThrow();
                }
                return DataResult.success(values);
            }

            @Override
            public <S> com.mojang.serialization.RecordBuilder<S> encode(Object[] input, DynamicOps<S> ops, com.mojang.serialization.RecordBuilder<S> prefix) {
                throw new UnsupportedOperationException("Loot function 编码不支持");
            }

            @Override
            public <S> java.util.stream.Stream<S> keys(DynamicOps<S> ops) {
                return java.util.stream.Stream.empty();
            }
        };
    }

    /** 等价于 vanilla 的 commonFields：自有字段 + conditions，构造时把 conditions 放在构造器第一个位置。 */
    @SuppressWarnings("unchecked")
    private <T extends LootFunction> MapCodec<T> withConditions(MapCodec<?>[] fields, Finisher<T> finisher) {
        return schemaCodec(fields).xmap(values -> {
            var conds = (List<LootCondition>) values[values.length - 1];
            var own = java.util.Arrays.copyOf(values, values.length - 1);
            return finisher.make(conds, own);
        }, f -> {
            throw new UnsupportedOperationException("Loot function 编码不支持");
        });
    }

    /** 无自有字段的修饰器：原版的 {@code MapCodec.unit} 加 conditions。 */
    private <T extends LootFunction> MapCodec<T> unit(Supplier<T> make, Function<T, T> rebuild) {
        var condition = Codec.lazyInitialized(() -> c.condition());
        return LootCodecs.compactList(condition).optionalFieldOf("conditions", List.of())
                .xmap(conds -> rebuild.apply(make.get()), LootFunction::conditions);
    }

    // ------------------------------------------------------------------
    // 44 种修饰器
    // ------------------------------------------------------------------

    private Map<Key, MapCodec<? extends LootFunction>> assembleAll() {
        var out = new TreeMap<Key, MapCodec<? extends LootFunction>>();
        var itemPredicate = LootCodecs.Specifics.itemPredicateCodec();
        var number = Codec.lazyInitialized(c::number);
        var function = Codec.lazyInitialized(root::get);

        out.put(key("set_count"), withConditions(new MapCodec<?>[]{
                number.fieldOf("count"),
                Codec.BOOL.optionalFieldOf("add", false)
        }, (conds, fields) -> new SetCountFunction(conds, (NumberProvider) fields[0], (Boolean) fields[1])));

        out.put(key("set_item"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("item")
        }, (conds, fields) -> new SetItemFunction(conds, (Key) fields[0])));

        out.put(key("enchant_with_levels"), withConditions(new MapCodec<?>[]{
                number.fieldOf("levels"),
                enchantedOptionsCodec().optionalFieldOf("options"),
                Codec.BOOL.optionalFieldOf("include_additional_cost_component", false)
        }, (conds, fields) -> new EnchantWithLevelsFunction(conds, (NumberProvider) fields[0], (Optional<EnchantedOptions>) fields[1], (Boolean) fields[2])));

        out.put(key("enchant_randomly"), withConditions(new MapCodec<?>[]{
                enchantedOptionsCodec().optionalFieldOf("options"),
                Codec.BOOL.optionalFieldOf("only_compatible", true),
                Codec.BOOL.optionalFieldOf("include_additional_cost_component", false)
        }, (conds, fields) -> new EnchantRandomlyFunction(conds, (Optional<EnchantedOptions>) fields[0], (Boolean) fields[1], (Boolean) fields[2])));

        out.put(key("set_enchantments"), withConditions(new MapCodec<?>[]{
                Codec.unboundedMap(ID, number).fieldOf("enchantments"),
                Codec.BOOL.optionalFieldOf("add", false)
        }, (conds, fields) -> new SetEnchantmentsFunction(conds, (Map<Key, NumberProvider>) fields[0], (Boolean) fields[1])));

        out.put(key("set_custom_data"), withConditions(new MapCodec<?>[]{
                Codec.either(
                                Codec.STRING.flatXmap(FunctionCodecs::readNbtString,
                                        tag -> DataResult.error(() -> "encode is not supported")),
                                minestom(net.minestom.server.codec.Codec.NBT).flatXmap(
                                        tag -> tag instanceof CompoundBinaryTag compound
                                                ? DataResult.success(compound)
                                                : DataResult.error(() -> "Expected a compound tag for set_custom_data"),
                                        tag -> DataResult.error(() -> "encode is not supported")))
                        .xmap(e -> e.map(tag -> tag, tag -> tag), Either::left)
                        .fieldOf("tag")
        }, (conds, fields) -> new SetCustomDataFunction(conds, (CompoundBinaryTag) fields[0])));

        out.put(key("set_components"), withConditions(new MapCodec<?>[]{
                minestom(DataComponent.PATCH_CODEC).fieldOf("components")
        }, (conds, fields) -> new SetComponentsFunction(conds, (net.minestom.server.component.DataComponentMap) fields[0])));

        // 原版 FurnaceSmeltFunction 无 use_input_count 字段；loot 记录的 useInputCount 是
        // eval 阶段语义，本模块按原版恒为 false。
        out.put(key("furnace_smelt"), unit(
                () -> new SmeltFunction(List.of(), false),
                f -> new SmeltFunction(f.conditions(), f.useInputCount())));

        out.put(key("enchanted_count_increase"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("enchantment"),
                number.fieldOf("count"),
                Codec.INT.optionalFieldOf("limit", 0)
        }, (conds, fields) -> new EnchantedCountIncreaseFunction(conds, (Key) fields[0], (NumberProvider) fields[1], (Integer) fields[2])));

        out.put(key("set_damage"), withConditions(new MapCodec<?>[]{
                number.fieldOf("damage"),
                Codec.BOOL.optionalFieldOf("add", false)
        }, (conds, fields) -> new SetDamageFunction(conds, (NumberProvider) fields[0], (Boolean) fields[1])));

        out.put(key("set_attributes"), withConditions(new MapCodec<?>[]{
                attributeModifierCodec().listOf().fieldOf("modifiers"),
                Codec.BOOL.optionalFieldOf("replace", false)
        }, (conds, fields) -> new SetAttributesFunction(conds, (List<AttributeModifierRecord>) fields[0], (Boolean) fields[1])));

        out.put(key("set_name"), withConditions(new MapCodec<?>[]{
                minestom(net.minestom.server.codec.Codec.COMPONENT).optionalFieldOf("name"),
                entityTargetCodec().optionalFieldOf("entity"),
                enumCodec(SetNameTarget.values()).optionalFieldOf("target", SetNameTarget.CUSTOM_NAME)
        }, (conds, fields) -> new SetNameFunction(conds, (Optional<net.kyori.adventure.text.Component>) fields[0], (Optional<EntityTarget>) fields[1], (SetNameTarget) fields[2])));

        out.put(key("exploration_map"), withConditions(new MapCodec<?>[]{
                ID.optionalFieldOf("destination", ExplorationMapFunction.DEFAULT_DESTINATION),
                ID.optionalFieldOf("decoration", ExplorationMapFunction.DEFAULT_DECORATION),
                Codec.INT.optionalFieldOf("zoom", 2),
                Codec.INT.optionalFieldOf("search_radius", 50),
                Codec.BOOL.optionalFieldOf("skip_existing_chunks", true)
        }, (conds, fields) -> new ExplorationMapFunction(conds, (Key) fields[0], (Key) fields[1], (Integer) fields[2], (Integer) fields[3], (Boolean) fields[4])));

        out.put(key("set_stew_effect"), withConditions(new MapCodec<?>[]{
                stewEffectEntryCodec().listOf().fieldOf("effects")
        }, (conds, fields) -> new SetStewEffectFunction(conds, (List<StewEffectEntry>) fields[0])));

        out.put(key("copy_name"), withConditions(new MapCodec<?>[]{
                copyNameSourceCodec().fieldOf("source")
        }, (conds, fields) -> new CopyNameFunction(conds, (CopyNameSource) fields[0])));

        out.put(key("set_contents"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("component"),
                Codec.lazyInitialized(c::entry).listOf().fieldOf("entries")
        }, (conds, fields) -> new SetContainerContentsFunction(conds, (Key) fields[0], (List<io.ib67.yuanban.loot.entry.LootEntry>) fields[1])));

        out.put(key("modify_contents"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("component"),
                Codec.lazyInitialized(root::get).fieldOf("modifier")
        }, (conds, fields) -> new ModifyContentsFunction(conds, (Key) fields[0], (LootFunction) fields[1])));

        out.put(key("filtered"), withConditions(new MapCodec<?>[]{
                itemPredicate.fieldOf("item_filter"),
                Codec.lazyInitialized(root::get).optionalFieldOf("on_pass"),
                Codec.lazyInitialized(root::get).optionalFieldOf("on_fail")
        }, (conds, fields) -> new FilteredFunction(conds, (ItemPredicate) fields[0], (Optional<LootFunction>) fields[1], (Optional<LootFunction>) fields[2])));

        out.put(key("limit_count"), withConditions(new MapCodec<?>[]{
                intRangeCodec().optionalFieldOf("limit", new io.ib67.yuanban.loot.number.IntRange(null, null))
        }, (conds, fields) -> new LimitCountFunction(conds, (io.ib67.yuanban.loot.number.IntRange) fields[0])));

        out.put(key("apply_bonus"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("enchantment"),
                applyBonusFormulaCodec()
        }, (conds, fields) -> new ApplyBonusFunction(conds, (Key) fields[0], (ApplyBonusFormula) fields[1])));

        out.put(key("set_loot_table"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("name"),
                Codec.LONG.optionalFieldOf("seed", 0L),
                ID.fieldOf("type")
        }, (conds, fields) -> new SetContainerLootTableFunction(conds, (Key) fields[0], (Long) fields[1], (Key) fields[2])));

        out.put(key("explosion_decay"), unit(
                ExplosionDecayFunction::new,
                f -> new ExplosionDecayFunction(f.conditions())));

        out.put(key("set_lore"), withConditions(new MapCodec<?>[]{
                minestom(net.minestom.server.codec.Codec.COMPONENT).listOf().fieldOf("lore"),
                listOperationCodec().optionalFieldOf("mode", AppendOperation.INSTANCE),
                entityTargetCodec().optionalFieldOf("entity")
        }, (conds, fields) -> new SetLoreFunction(conds, (List<net.kyori.adventure.text.Component>) fields[0], (ListOperation) fields[1], (Optional<EntityTarget>) fields[2])));

        out.put(key("fill_player_head"), withConditions(new MapCodec<?>[]{
                entityTargetCodec().optionalFieldOf("entity", EntityTarget.THIS)
        }, (conds, fields) -> new FillPlayerHeadFunction(conds, (EntityTarget) fields[0])));

        out.put(key("copy_custom_data"), withConditions(new MapCodec<?>[]{
                nbtSourceCodec().fieldOf("source"),
                copyOperationCodec().listOf().fieldOf("ops")
        }, (conds, fields) -> new CopyCustomDataFunction(conds, (NbtSource) fields[0], (List<CopyOperation>) fields[1])));

        out.put(key("copy_state"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("block"),
                Codec.STRING.listOf().fieldOf("properties").xmap(java.util.Set::copyOf, java.util.List::copyOf)
        }, (conds, fields) -> new CopyBlockStateFunction(conds, (Key) fields[0], (java.util.Set<String>) fields[1])));

        out.put(key("set_banner_pattern"), withConditions(new MapCodec<?>[]{
                bannerLayerCodec().listOf().fieldOf("patterns"),
                Codec.BOOL.optionalFieldOf("append", true)
        }, (conds, fields) -> new SetBannerPatternFunction(conds, (List<BannerLayer>) fields[0], (Boolean) fields[1])));

        out.put(key("set_potion"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("id")
        }, (conds, fields) -> new SetPotionFunction(conds, (Key) fields[0])));

        out.put(key("set_random_dyes"), withConditions(new MapCodec<?>[]{
                number.fieldOf("number_of_dyes")
        }, (conds, fields) -> new SetRandomDyesFunction(conds, (NumberProvider) fields[0])));

        out.put(key("set_random_potion"), withConditions(new MapCodec<?>[]{
                enchantedOptionsCodec().optionalFieldOf("options")
        }, (conds, fields) -> new SetRandomPotionFunction(conds, (Optional<EnchantedOptions>) fields[0])));

        out.put(key("set_instrument"), withConditions(new MapCodec<?>[]{
                enchantedOptionsCodec().fieldOf("options")
        }, (conds, fields) -> new SetInstrumentFunction(conds, (EnchantedOptions) fields[0])));

        out.put(key("reference"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("name")
        }, (conds, fields) -> new FunctionReference(conds, (Key) fields[0])));

        out.put(key("sequence"), withConditions(new MapCodec<?>[]{
                Codec.lazyInitialized(root::get).listOf().fieldOf("functions")
        }, (conds, fields) -> new SequenceFunction(conds, (List<LootFunction>) fields[0])));

        out.put(key("copy_components"), withConditions(new MapCodec<?>[]{
                copyNameSourceCodec().fieldOf("source"),
                ID.listOf().optionalFieldOf("include"),
                ID.listOf().optionalFieldOf("exclude")
        }, (conds, fields) -> new CopyComponentsFunction(conds, (CopyNameSource) fields[0], (Optional<List<Key>>) fields[1], (Optional<List<Key>>) fields[2])));

        out.put(key("set_fireworks"), withConditions(new MapCodec<?>[]{
                standAloneCodec(enumCodec(FireworkExplosionShape.values())).optionalFieldOf("explosions"),
                Codec.INT.optionalFieldOf("flight_duration")
        }, (conds, fields) -> new SetFireworksFunction(conds, (Optional<StandAloneOperation<FireworkExplosionShape>>) fields[0], (Optional<Integer>) fields[1])));

        out.put(key("set_firework_explosion"), withConditions(new MapCodec<?>[]{
                enumCodec(FireworkExplosionShape.values()).optionalFieldOf("shape"),
                Codec.INT.listOf().optionalFieldOf("colors"),
                Codec.INT.listOf().optionalFieldOf("fade_colors"),
                Codec.BOOL.optionalFieldOf("trail"),
                Codec.BOOL.optionalFieldOf("twinkle")
        }, (conds, fields) -> new SetFireworkExplosionFunction(conds, (Optional<FireworkExplosionShape>) fields[0], (Optional<List<Integer>>) fields[1], (Optional<List<Integer>>) fields[2], (Optional<Boolean>) fields[3], (Optional<Boolean>) fields[4])));

        out.put(key("set_book_cover"), withConditions(new MapCodec<?>[]{
                Codec.STRING.optionalFieldOf("author"),
                filteredTextCodec().optionalFieldOf("title"),
                intRange(0, 3).optionalFieldOf("generation")
        }, (conds, fields) -> new SetBookCoverFunction(conds, (Optional<String>) fields[0], (Optional<FilteredText>) fields[1], (Optional<Integer>) fields[2])));

        out.put(key("set_written_book_pages"), withConditions(new MapCodec<?>[]{
                minestom(net.minestom.server.codec.Codec.COMPONENT).listOf().fieldOf("pages"),
                listOperationCodec().optionalFieldOf("mode", AppendOperation.INSTANCE)
        }, (conds, fields) -> new SetWrittenBookPagesFunction(conds, (List<net.kyori.adventure.text.Component>) fields[0], (ListOperation) fields[1])));

        out.put(key("set_writable_book_pages"), withConditions(new MapCodec<?>[]{
                filteredTextCodec().listOf().fieldOf("pages"),
                listOperationCodec().optionalFieldOf("mode", AppendOperation.INSTANCE)
        }, (conds, fields) -> new SetWritableBookPagesFunction(conds, (List<FilteredText>) fields[0], (ListOperation) fields[1])));

        out.put(key("toggle_tooltips"), withConditions(new MapCodec<?>[]{
                Codec.unboundedMap(ID, Codec.BOOL).fieldOf("toggles")
        }, (conds, fields) -> new ToggleTooltipsFunction(conds, (Map<Key, Boolean>) fields[0])));

        out.put(key("set_ominous_bottle_amplifier"), withConditions(new MapCodec<?>[]{
                number.fieldOf("amplifier")
        }, (conds, fields) -> new SetOminousBottleAmplifierFunction(conds, (NumberProvider) fields[0])));

        out.put(key("set_custom_model_data"), withConditions(new MapCodec<?>[]{
                standAloneCodec(number).optionalFieldOf("floats"),
                standAloneCodec(Codec.BOOL).optionalFieldOf("flags"),
                standAloneCodec(Codec.STRING).optionalFieldOf("strings"),
                standAloneCodec(number).optionalFieldOf("colors")
        }, (conds, fields) -> new SetCustomModelDataFunction(conds,
                (Optional<StandAloneOperation<NumberProvider>>) fields[0],
                (Optional<StandAloneOperation<Boolean>>) fields[1],
                (Optional<StandAloneOperation<String>>) fields[2],
                (Optional<StandAloneOperation<NumberProvider>>) fields[3])));

        out.put(key("discard"), unit(
                DiscardFunction::new,
                f -> new DiscardFunction(f.conditions())));

        out.put(key("function"), withConditions(new MapCodec<?>[]{
                ID.fieldOf("name")
        }, (conds, fields) -> new FunctionReference(conds, (Key) fields[0])));

        return out;
    }

    // ------------------------------------------------------------------
    // 嵌套 record 的编解码器
    // ------------------------------------------------------------------

    /** 原版 {@code HolderSet}：{@code #tag}、单 ID 或 ID 列表。 */
    static Codec<EnchantedOptions> enchantedOptionsCodec() {
        var single = Codec.either(tagReference(), ID).xmap(
                e -> e.map(EnchantedOptions::ofTag, id -> EnchantedOptions.ofList(List.of(id))),
                LootCodecs::enchantedOptionsToEither);
        var listForm = ID.listOf().xmap(EnchantedOptions::ofList,
                o -> o.enchantments() == null ? List.of() : o.enchantments());
        return Codec.either(single, listForm).xmap(
                e -> e.map(Function.identity(), Function.identity()),
                o -> o.enchantments() != null && o.enchantments().size() > 1 ? Either.right(o) : Either.left(o));
    }

    /** 原版 {@code ListOperation.CODEC}：mode 缺省 append。 */
    private Codec<ListOperation> listOperationCodec() {
        MapCodec<ListOperation> insert = RecordCodecBuilder.<ListOperation>mapCodec(i -> i.group(
                Codec.INT.fieldOf("offset").forGetter(o -> ((InsertOperation) o).offset())
        ).apply(i, (Integer offset) -> (ListOperation) new InsertOperation(offset)));
        MapCodec<ListOperation> replaceSection = RecordCodecBuilder.<ListOperation>mapCodec(i -> i.group(
                Codec.INT.fieldOf("offset").forGetter(o -> ((ReplaceSectionOperation) o).offset()),
                Codec.INT.optionalFieldOf("size").forGetter(o -> ((ReplaceSectionOperation) o).size())
        ).apply(i, (Integer offset, Optional<Integer> size) -> (ListOperation) new ReplaceSectionOperation(offset, size)));
        MapCodec<ListOperation> append = MapCodec.unit((ListOperation) AppendOperation.INSTANCE);
        MapCodec<ListOperation> replaceAll = MapCodec.unit((ListOperation) ReplaceAllOperation.INSTANCE);
        return ID.comapFlatMap(id -> switch (id.value()) {
                    case "append", "replace_all", "insert", "replace_section" -> DataResult.success(id);
                    default -> DataResult.error(() -> LootCodecs.unknownKey("list_operation_type", id));
                }, Function.<Key>identity())
                .fieldOf("mode")
                .dispatch(FunctionCodecs::listOperationTypeOf,
                        id -> switch (id.value()) {
                            case "append" -> append;
                            case "replace_all" -> replaceAll;
                            case "insert" -> insert;
                            default -> replaceSection;
                        });
    }

    private static Key listOperationTypeOf(ListOperation o) {
        return switch (o) {
            case AppendOperation _ -> key("append");
            case ReplaceAllOperation _ -> key("replace_all");
            case InsertOperation _ -> key("insert");
            case ReplaceSectionOperation _ -> key("replace_section");
        };
    }

    /** 原版 {@code ListOperation.StandAlone.CODEC}：{@code values} + {@code mode}（缺省 append）。 */
    private <T> Codec<StandAloneOperation<T>> standAloneCodec(Codec<T> element) {
        return RecordCodecBuilder.create(i -> i.group(
                LootCodecs.compactList(element).fieldOf("values").forGetter(StandAloneOperation::values),
                listOperationCodec().optionalFieldOf("mode", AppendOperation.INSTANCE).forGetter(StandAloneOperation::operation)
        ).apply(i, StandAloneOperation::new));
    }

    /** 原版 {@code SetAttributesFunction.Modifier}：{@code slot} 单值或列表。 */
    private Codec<AttributeModifierRecord> attributeModifierCodec() {
        return RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("name").forGetter(AttributeModifierRecord::id),
                ID.fieldOf("attribute").forGetter(AttributeModifierRecord::attribute),
                enumCodec(AttributeOperation.values()).fieldOf("operation").forGetter(AttributeModifierRecord::operation),
                number().fieldOf("amount").forGetter(AttributeModifierRecord::amount),
                LootCodecs.compactList(equipmentSlotGroupCodec())
                        .optionalFieldOf("slot", List.of(EquipmentSlotGroup.ANY)).forGetter(AttributeModifierRecord::slots)
        ).apply(i, AttributeModifierRecord::new));
    }

    /** 原版 {@code BannerPatternLayers.Layer}：{@code pattern} 与 {@code color}（DyeColor 名）。 */
    private static Codec<BannerLayer> bannerLayerCodec() {
        return RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("pattern").forGetter(BannerLayer::pattern),
                Codec.STRING.fieldOf("color").forGetter(BannerLayer::color)
        ).apply(i, BannerLayer::new));
    }

    /** 原版 {@code SetStewEffectFunction.EffectEntry}：{@code type} 与 {@code duration}。 */
    private Codec<StewEffectEntry> stewEffectEntryCodec() {
        return RecordCodecBuilder.create(i -> i.group(
                ID.fieldOf("type").forGetter(StewEffectEntry::effect),
                number().fieldOf("duration").forGetter(StewEffectEntry::duration)
        ).apply(i, StewEffectEntry::new));
    }

    /** 原版 {@code CopyCustomDataFunction.CopyOperation}：{@code source}、{@code target}、{@code op}（默认 replace）。 */
    private static Codec<CopyOperation> copyOperationCodec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("source").forGetter(CopyOperation::source),
                Codec.STRING.fieldOf("target").forGetter(CopyOperation::target),
                enumCodec(MergeStrategy.values()).optionalFieldOf("op", MergeStrategy.REPLACE).forGetter(CopyOperation::op)
        ).apply(i, CopyOperation::new));
    }

    /** 原版 {@code FilteredText.CODEC}：{@code text} 必填、{@code filtered} 可选；String 形态折叠。 */
    private static Codec<FilteredText> filteredTextCodec() {
        var objectForm = RecordCodecBuilder.<FilteredText>mapCodec(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(FilteredText::raw),
                Codec.STRING.optionalFieldOf("filtered").forGetter(FilteredText::filtered)
        ).apply(i, FilteredText::new)).codec();
        var inline = Codec.STRING.xmap(s -> new FilteredText(s, Optional.empty()), FilteredText::raw);
        return Codec.either(objectForm, inline).xmap(
                e -> e.map(Function.identity(), Function.identity()),
                t -> t.filtered().isEmpty() ? Either.right(t) : Either.left(t));
    }

    /** 原版 {@code ApplyBonusCount} 的公式：原版的 JSON 形态是
     * {@code {"formula": "minecraft:<id>", "parameters": {…}}}。参数型公式（binomial_with_bonus_count、
     * uniform_bonus_count）需要从 {@code parameters} 解码子对象；单例公式（ore_drops）的
     * {@code parameters} 可缺省。
     */
    private MapCodec<ApplyBonusFormula> applyBonusFormulaCodec() {
        MapCodec<ApplyBonusFormula> binomial = RecordCodecBuilder.<ApplyBonusFormula>mapCodec(i -> i.group(
                Codec.INT.fieldOf("extra").forGetter(f -> ((ApplyBonusFormula.BinomialWithBonusCount) f).extraRounds()),
                Codec.FLOAT.fieldOf("probability").forGetter(f -> ((ApplyBonusFormula.BinomialWithBonusCount) f).probability())
        ).apply(i, (Integer extra, Float probability) ->
                (ApplyBonusFormula) new ApplyBonusFormula.BinomialWithBonusCount(extra, probability)));
        MapCodec<ApplyBonusFormula> uniform = RecordCodecBuilder.<ApplyBonusFormula>mapCodec(i -> i.group(
                Codec.INT.fieldOf("bonusMultiplier").forGetter(f -> ((ApplyBonusFormula.UniformBonusCount) f).bonusMultiplier())
        ).apply(i, (Integer multiplier) -> (ApplyBonusFormula) new ApplyBonusFormula.UniformBonusCount(multiplier)));
        var formulaId = ID.comapFlatMap(id -> switch (id.value()) {
            case "binomial_with_bonus_count", "uniform_bonus_count", "ore_drops" -> DataResult.success(id);
            default -> DataResult.error(() -> LootCodecs.unknownKey("apply_bonus_formula", id));
        }, Function.<Key>identity());
        return new MapCodec<ApplyBonusFormula>() {
            @Override
            public <S> DataResult<ApplyBonusFormula> decode(DynamicOps<S> ops, com.mojang.serialization.MapLike<S> input) {
                var raw = input.get("formula");
                if (raw == null) {
                    return DataResult.error(() -> "apply_bonus requires 'formula'");
                }
                var idResult = formulaId.decode(ops, raw);
                var err = idResult.error();
                if (err.isPresent()) return DataResult.error(() -> err.get().message());
                var id = idResult.result().orElseThrow().getFirst();
                var sub = switch (id.value()) {
                    case "binomial_with_bonus_count" -> (MapCodec<ApplyBonusFormula>) binomial;
                    case "uniform_bonus_count" -> (MapCodec<ApplyBonusFormula>) uniform;
                    default -> null;
                };
                if (sub == null) {
                    // ore_drops：parameters 可缺省，原版忽略其内容
                    return DataResult.success((ApplyBonusFormula) ApplyBonusFormula.OreDrops.INSTANCE);
                }
                var params = input.get("parameters");
                if (params == null) {
                    return DataResult.error(() -> "apply_bonus '" + id + "' requires 'parameters'");
                }
                return sub.codec().parse(ops, params);
            }

            @Override
            public <S> com.mojang.serialization.RecordBuilder<S> encode(ApplyBonusFormula input, DynamicOps<S> ops, com.mojang.serialization.RecordBuilder<S> prefix) {
                throw new UnsupportedOperationException("encode not supported");
            }

            @Override
            public <S> java.util.stream.Stream<S> keys(DynamicOps<S> ops) {
                return java.util.stream.Stream.of(ops.createString("formula"), ops.createString("parameters"));
            }
        };
    }

    private static Key applyBonusFormulaTypeOf(ApplyBonusFormula f) {
        return switch (f) {
            case ApplyBonusFormula.BinomialWithBonusCount _ -> key("binomial_with_bonus_count");
            case ApplyBonusFormula.UniformBonusCount _ -> key("uniform_bonus_count");
            case ApplyBonusFormula.OreDrops _ -> key("ore_drops");
        };
    }

    /** 原版 {@code LootContextArg}：上下文实体目标或字面量 {@code block_entity}。 */
    private static Codec<CopyNameSource> copyNameSourceCodec() {
        return Codec.STRING.comapFlatMap(s -> {
            if ("block_entity".equals(s)) {
                return DataResult.success((CopyNameSource) CopyNameSource.BlockEntity.INSTANCE);
            }
            return entityTargetCodec().parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(s))
                    .map(CopyNameSource.Entity::new).map(Function.identity());
        }, FunctionCodecs::copyNameSourceName);
    }

    private static String copyNameSourceName(CopyNameSource source) {
        if (source instanceof CopyNameSource.BlockEntity) return "block_entity";
        return ((CopyNameSource.Entity) source).target().name().toLowerCase(Locale.ROOT);
    }

    /** 原版 {@code LootContext<NbtProviders>}：{@code type: context, target} 或 {@code type: storage, source}。 */
    private static Codec<NbtSource> nbtSourceCodec() {
        var targetCodec = Codec.STRING.comapFlatMap(s -> "block_entity".equals(s)
                        ? DataResult.success(Optional.<EntityTarget>empty())
                        : entityTargetCodec().parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(s)).map(Optional::of),
                t -> t.map(e -> e.name().toLowerCase(Locale.ROOT)).orElse("block_entity"));
        MapCodec<NbtSource> contextCodec = RecordCodecBuilder.<NbtSource>mapCodec(i -> i.group(
                targetCodec.fieldOf("target").forGetter(v -> {
                    var context = (NbtSource.Context) v;
                    return context.blockEntity() ? Optional.<EntityTarget>empty() : Optional.ofNullable(context.target());
                })
        ).apply(i, target -> (NbtSource) (target.isEmpty()
                ? new NbtSource.Context(null, true)
                : new NbtSource.Context(target.get(), false))));
        MapCodec<NbtSource> storageCodec = RecordCodecBuilder.<NbtSource>mapCodec(i -> i.group(
                ID.fieldOf("source").forGetter(v -> ((NbtSource.Storage) v).storage())
        ).apply(i, id -> (NbtSource) new NbtSource.Storage(id)));
        return ID.fieldOf("type")
                .dispatch(v -> key(v instanceof NbtSource.Storage ? "storage" : "context"),
                        id -> "storage".equals(id.value()) ? storageCodec : contextCodec);
    }

    // ------------------------------------------------------------------
    // 基础工具
    // ------------------------------------------------------------------

    private Codec<NumberProvider> number() {
        return Codec.lazyInitialized(c::number);
    }

    private static Codec<io.ib67.yuanban.loot.number.IntRange> intRangeCodec() {
        return LootCodecs.Specifics.intRangeCodec();
    }

    private static Codec<Integer> intRange(int min, int max) {
        return Codec.INT.validate(v -> v < min || v > max
                ? DataResult.error(() -> "Value must be within range [" + min + ";" + max + "]: " + v)
                : DataResult.success(v));
    }

    static DataResult<CompoundBinaryTag> readNbtString(String raw) {
        try {
            var tag = TagStringIO.tagStringIO().asCompound(raw);
            return DataResult.success(tag);
        } catch (Exception e) {
            return DataResult.error(() -> "Failed to parse NBT tag: " + e.getMessage());
        }
    }

    /** 把 Minestom 的编解码器包装成 DataFixerUpper 的解码器，用 {@code RegistryTranscoder} 提供注册表。 */
    private <A> Codec<A> minestom(net.minestom.server.codec.Codec<A> codec) {
        return Codec.of(Encoder.error("Cannot encode " + codec), new Decoder<>() {
            @Override
            public <S> DataResult<Pair<A, S>> decode(DynamicOps<S> ops, S input) {
                var json = ops.convertTo(JsonOps.INSTANCE, input);
                return switch (codec.decode(c.transcoder(), json)) {
                    case Result.Ok<A>(var value) -> DataResult.success(Pair.of(value, input));
                    case Result.Error<A>(var message) -> DataResult.error(() -> message);
                };
            }
        });
    }

    static <E extends Enum<E>> Codec<E> enumCodec(E[] values) {
        return Codec.STRING.comapFlatMap(name -> {
            for (var value : values) {
                if (value.name().toLowerCase(Locale.ROOT).equals(name)) {
                    return DataResult.success(value);
                }
            }
            return DataResult.error(() -> "Unknown element name: " + name);
        }, v -> v.name().toLowerCase(Locale.ROOT));
    }

    /**
     * 原版 {@code EquipmentSlotGroup.SERIALIZABLE_CODEC}：JSON 值是原版字符串 id（{@code mainhand}、
     * {@code offhand} 不带下划线，其余与枚举名小写一致）。loot 的记录枚举采用原版字段名，下划线仅出现在
     * {@code MAIN_HAND} / {@code OFF_HAND}，因此按原版 id 显式映射。
     */
    private static Codec<EquipmentSlotGroup> equipmentSlotGroupCodec() {
        var byName = new java.util.HashMap<String, EquipmentSlotGroup>();
        byName.put("any", EquipmentSlotGroup.ANY);
        byName.put("hand", EquipmentSlotGroup.HAND);
        byName.put("mainhand", EquipmentSlotGroup.MAIN_HAND);
        byName.put("offhand", EquipmentSlotGroup.OFF_HAND);
        byName.put("feet", EquipmentSlotGroup.FEET);
        byName.put("legs", EquipmentSlotGroup.LEGS);
        byName.put("chest", EquipmentSlotGroup.CHEST);
        byName.put("head", EquipmentSlotGroup.HEAD);
        byName.put("armor", EquipmentSlotGroup.ARMOR);
        byName.put("body", EquipmentSlotGroup.BODY);
        var toName = byName.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getValue, java.util.Map.Entry::getKey));
        return Codec.STRING.comapFlatMap(name -> {
            var value = byName.get(name);
            return value != null ? DataResult.success(value)
                    : DataResult.error(() -> "Unknown equipment slot group: " + name);
        }, toName::get);
    }

    private static Key key(String path) {
        return Key.key(path);
    }

    static Key functionTypeOf(LootFunction f) {
        return switch (f) {
            case SetCountFunction _ -> key("set_count");
            case SetItemFunction _ -> key("set_item");
            case EnchantWithLevelsFunction _ -> key("enchant_with_levels");
            case EnchantRandomlyFunction _ -> key("enchant_randomly");
            case SetEnchantmentsFunction _ -> key("set_enchantments");
            case SetCustomDataFunction _ -> key("set_custom_data");
            case SetComponentsFunction _ -> key("set_components");
            case SmeltFunction _ -> key("furnace_smelt");
            case EnchantedCountIncreaseFunction _ -> key("enchanted_count_increase");
            case SetDamageFunction _ -> key("set_damage");
            case SetAttributesFunction _ -> key("set_attributes");
            case SetNameFunction _ -> key("set_name");
            case ExplorationMapFunction _ -> key("exploration_map");
            case SetStewEffectFunction _ -> key("set_stew_effect");
            case CopyNameFunction _ -> key("copy_name");
            case SetContainerContentsFunction _ -> key("set_contents");
            case ModifyContentsFunction _ -> key("modify_contents");
            case FilteredFunction _ -> key("filtered");
            case LimitCountFunction _ -> key("limit_count");
            case ApplyBonusFunction _ -> key("apply_bonus");
            case SetContainerLootTableFunction _ -> key("set_loot_table");
            case ExplosionDecayFunction _ -> key("explosion_decay");
            case SetLoreFunction _ -> key("set_lore");
            case FillPlayerHeadFunction _ -> key("fill_player_head");
            case CopyCustomDataFunction _ -> key("copy_custom_data");
            case CopyBlockStateFunction _ -> key("copy_state");
            case SetBannerPatternFunction _ -> key("set_banner_pattern");
            case SetPotionFunction _ -> key("set_potion");
            case SetRandomDyesFunction _ -> key("set_random_dyes");
            case SetRandomPotionFunction _ -> key("set_random_potion");
            case SetInstrumentFunction _ -> key("set_instrument");
            case FunctionReference _ -> key("function");
            case SequenceFunction _ -> key("sequence");
            case CopyComponentsFunction _ -> key("copy_components");
            case SetFireworksFunction _ -> key("set_fireworks");
            case SetFireworkExplosionFunction _ -> key("set_firework_explosion");
            case SetBookCoverFunction _ -> key("set_book_cover");
            case SetWrittenBookPagesFunction _ -> key("set_written_book_pages");
            case SetWritableBookPagesFunction _ -> key("set_writable_book_pages");
            case ToggleTooltipsFunction _ -> key("toggle_tooltips");
            case SetOminousBottleAmplifierFunction _ -> key("set_ominous_bottle_amplifier");
            case SetCustomModelDataFunction _ -> key("set_custom_model_data");
            case DiscardFunction _ -> key("discard");
        };
    }
}
