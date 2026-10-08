package io.ib67.yuanban.loot.codec;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.RegistryLookup;
import io.ib67.yuanban.codec.RegistryOps;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.BlockStatePropertyCondition;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.ReferenceCondition;
import io.ib67.yuanban.loot.condition.TimeCheckCondition;
import io.ib67.yuanban.loot.entry.AlternativesEntry;
import io.ib67.yuanban.loot.entry.DynamicEntry;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.entry.NestedTableEntry;
import io.ib67.yuanban.loot.entry.TagEntry;
import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.InsertOperation;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.function.ReplaceSectionOperation;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetAttributesFunction;
import io.ib67.yuanban.loot.function.SetCustomModelDataFunction;
import io.ib67.yuanban.loot.function.SetEnchantmentsFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetStewEffectFunction;
import io.ib67.yuanban.loot.function.SetWritableBookPagesFunction;
import io.ib67.yuanban.loot.function.SmeltFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.SumValue;
import io.ib67.yuanban.loot.number.UniformValue;
import io.ib67.yuanban.loot.context.LootContextArg;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.item.book.FilteredText;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LootCodecs} 按原版 26.2 的 {@code MAP_CODEC} 解码：接受原版接受的写法，拒绝原版拒绝的写法，错误文本与原版相同。
 * 注册表引用按 Minestom 进程的注册表检查。
 */
@EnvTest
class LootCodecsTest {
    private final LootCodecs codecs = new LootCodecs(LootTypeRegistry.vanilla());

    private static RegistryOps<com.google.gson.JsonElement> ops(Env env) {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryLookup.builder(env.process().registries()).build());
    }

    private static <T> T accept(Env env, Codec<T> codec, String json) {
        var result = codec.parse(ops(env), JsonParser.parseString(json));
        result.error().ifPresent(error -> {
            throw new AssertionError("expected success for " + json + ": " + error.message());
        });
        return result.getOrThrow();
    }

    private static <T> String reject(Env env, Codec<T> codec, String json) {
        DataResult<T> result = codec.parse(ops(env), JsonParser.parseString(json));
        assertTrue(result.error().isPresent(), "expected failure for " + json);
        return result.error().get().message();
    }

    private static void assertContains(String expected, String message) {
        assertTrue(message.contains(expected), "expected [" + expected + "] in [" + message + "]");
    }

    // ------------------------------------------------------------------
    // 表与池
    // ------------------------------------------------------------------

    @Test
    void tableUnknownTypeFallsBackToGeneric(Env env) {
        var table = accept(env, codecs.table(), "{\"type\":\"minecraft:bogus\",\"pools\":[]}");
        assertEquals(LootTable.DEFAULT_TYPE, table.type());
        assertEquals(Key.key("chest"), accept(env, codecs.table(), "{\"type\":\"chest\"}").type());
    }

    @Test
    void poolEntriesMayBeEmptyButRequired(Env env) {
        var pool = accept(env, codecs.pool(), "{\"rolls\":1,\"entries\":[]}");
        assertTrue(pool.entries().isEmpty());
        assertEquals(ConstantValue.ZERO, pool.bonusRolls());
        assertContains("No key entries", reject(env, codecs.pool(), "{\"rolls\":1}"));
    }

    // ------------------------------------------------------------------
    // 条目
    // ------------------------------------------------------------------

    @Test
    void unknownEntryType(Env env) {
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_pool_entry_type]: minecraft:bogus",
                reject(env, codecs.entry(), "{\"type\":\"bogus\"}"));
    }

    @Test
    void itemEntry(Env env) {
        var entry = assertInstanceOf(ItemEntry.class, accept(env, codecs.entry(),
                "{\"type\":\"item\",\"name\":\"stone\",\"weight\":3,\"functions\":[{\"function\":\"set_count\",\"count\":2}]}"));
        assertEquals(Material.STONE, entry.name());
        assertEquals(3, entry.weight());
        assertContains("Item must not be minecraft:air", reject(env, codecs.entry(), "{\"type\":\"item\",\"name\":\"air\"}"));
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minecraft:nope",
                reject(env, codecs.entry(), "{\"type\":\"item\",\"name\":\"nope\"}"));
    }

    @Test
    void tagEntryExpandRequired(Env env) {
        var entry = assertInstanceOf(TagEntry.class, accept(env, codecs.entry(), "{\"type\":\"tag\",\"name\":\"example:missing\",\"expand\":true}"));
        assertEquals(Key.key("example:missing"), entry.name());
        assertContains("No key expand", reject(env, codecs.entry(), "{\"type\":\"tag\",\"name\":\"planks\"}"));
    }

    @Test
    void dynamicEntryAnyName(Env env) {
        var entry = assertInstanceOf(DynamicEntry.class, accept(env, codecs.entry(), "{\"type\":\"dynamic\",\"name\":\"example:anything\"}"));
        assertEquals(Key.key("example:anything"), entry.name());
    }

    @Test
    void compositeEntryChildrenOptional(Env env) {
        var entry = assertInstanceOf(AlternativesEntry.class, accept(env, codecs.entry(),
                "{\"type\":\"alternatives\",\"conditions\":[{\"condition\":\"killed_by_player\"}]}"));
        assertTrue(entry.children().isEmpty());
        assertEquals(1, entry.conditions().size());
    }

    @Test
    void nestedTableIdOrInline(Env env) {
        var byId = assertInstanceOf(NestedTableEntry.class, accept(env, codecs.entry(), "{\"type\":\"loot_table\",\"value\":\"example:any\"}"));
        assertEquals(Key.key("example:any"), byId.value().left().orElseThrow());
        var inline = assertInstanceOf(NestedTableEntry.class, accept(env, codecs.entry(),
                "{\"type\":\"loot_table\",\"value\":{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"empty\"}]}]}}"));
        assertEquals(1, inline.value().right().orElseThrow().pools().size());
    }

    // ------------------------------------------------------------------
    // 条件
    // ------------------------------------------------------------------

    @Test
    void conditionListIsAllOf(Env env) {
        var condition = accept(env, codecs.condition(), "[{\"condition\":\"killed_by_player\"},{\"condition\":\"survives_explosion\"}]");
        assertEquals(2, assertInstanceOf(AllOfCondition.class, condition).terms().size());
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_condition_type]: minecraft:bogus",
                reject(env, codecs.condition(), "{\"condition\":\"bogus\"}"));
    }

    @Test
    void entityPropertiesRequiresEntity(Env env) {
        var condition = assertInstanceOf(EntityPropertiesCondition.class, accept(env, codecs.condition(),
                "{\"condition\":\"entity_properties\",\"entity\":\"attacker\",\"predicate\":{\"flags\":{\"is_on_fire\":true}}}"));
        assertEquals(EntityTarget.ATTACKER, condition.entity());
        assertContains("No key entity", reject(env, codecs.condition(), "{\"condition\":\"entity_properties\"}"));
        assertContains("No key entity", reject(env, codecs.condition(), "{\"condition\":\"entity_scores\",\"scores\":{}}"));
    }

    @Test
    void timeCheckClockRequiredAndChecked(Env env) {
        var clock = env.process().worldClock().getKey(0).key().asString();
        var condition = assertInstanceOf(TimeCheckCondition.class, accept(env, codecs.condition(),
                "{\"condition\":\"time_check\",\"clock\":\"" + clock + "\",\"value\":{\"min\":1}}"));
        assertTrue(condition.period().isEmpty());
        assertContains("No key clock", reject(env, codecs.condition(), "{\"condition\":\"time_check\",\"value\":1}"));
        assertContains("Failed to get element example:nope",
                reject(env, codecs.condition(), "{\"condition\":\"time_check\",\"clock\":\"example:nope\",\"value\":1}"));
    }

    @Test
    void referenceAnyNamespace(Env env) {
        assertEquals(Key.key("example:x"), assertInstanceOf(ReferenceCondition.class,
                accept(env, codecs.condition(), "{\"condition\":\"reference\",\"name\":\"example:x\"}")).name());
    }

    @Test
    void tableBonusAndEnchantedBonusChecks(Env env) {
        assertContains("List must have contents", reject(env, codecs.condition(),
                "{\"condition\":\"table_bonus\",\"enchantment\":\"fortune\",\"chances\":[]}"));
        assertContains("Failed to get element example:nope", reject(env, codecs.condition(),
                "{\"condition\":\"table_bonus\",\"enchantment\":\"example:nope\",\"chances\":[0.1]}"));
        assertContains("Value 1.5 outside of range [0.0:1.0]", reject(env, codecs.condition(),
                "{\"condition\":\"random_chance_with_enchanted_bonus\",\"unenchanted_chance\":1.5,\"enchanted_chance\":0.1,\"enchantment\":\"looting\"}"));
    }

    @Test
    void blockStatePropertyChecksNames(Env env) {
        var condition = assertInstanceOf(BlockStatePropertyCondition.class, accept(env, codecs.condition(),
                "{\"condition\":\"block_state_property\",\"block\":\"grass_block\",\"properties\":{\"snowy\":\"true\"}}"));
        assertEquals(Block.GRASS_BLOCK, condition.block());
        assertContains("Block Reference{ResourceKey[minecraft:block / minecraft:stone]=Block{minecraft:stone}} has no propertysnowy",
                reject(env, codecs.condition(), "{\"condition\":\"block_state_property\",\"block\":\"stone\",\"properties\":{\"snowy\":\"true\"}}"));
    }

    @Test
    void tagsInPredicatesAreChecked(Env env) {
        assertContains("Missing tag: 'example:none' in 'minecraft:item'", reject(env, codecs.condition(),
                "{\"condition\":\"match_tool\",\"predicate\":{\"items\":\"#example:none\"}}"));
        var condition = accept(env, codecs.condition(), "{\"condition\":\"match_tool\",\"predicate\":{\"items\":\"#minecraft:planks\"}}");
        assertInstanceOf(LootCondition.class, condition);
    }

    // ------------------------------------------------------------------
    // 数字提供器
    // ------------------------------------------------------------------

    @Test
    void numberForms(Env env) {
        assertEquals(new ConstantValue(2.5f), accept(env, codecs.number(), "2.5"));
        assertInstanceOf(UniformValue.class, accept(env, codecs.number(), "{\"min\":1,\"max\":2}"));
        var score = assertInstanceOf(ScoreboardValue.class, accept(env, codecs.number(),
                "{\"type\":\"score\",\"target\":\"this\",\"score\":\"kills\"}"));
        assertEquals(new ScoreboardName.Context(EntityTarget.THIS), score.target());
        assertEquals(new ScoreboardName.Fixed("steve"), assertInstanceOf(ScoreboardValue.class, accept(env, codecs.number(),
                "{\"type\":\"score\",\"target\":{\"type\":\"fixed\",\"name\":\"steve\"},\"score\":\"kills\"}")).target());
    }

    @Test
    void sumReadsSummands(Env env) {
        assertEquals(2, assertInstanceOf(SumValue.class, accept(env, codecs.number(), "{\"type\":\"sum\",\"summands\":[1,2]}")).summands().size());
        assertContains("No key summands", reject(env, codecs.number(), "{\"type\":\"sum\",\"terms\":[1,2]}"));
    }

    @Test
    void levelBasedValueClamped(Env env) {
        assertInstanceOf(LevelBasedValue.Clamped.class, accept(env, NumberCodecs.LEVEL_BASED_VALUE,
                "{\"type\":\"clamped\",\"value\":1,\"min\":0,\"max\":2}"));
        assertContains("Max must be larger than min, min: 2.0, max: 1.0", reject(env, NumberCodecs.LEVEL_BASED_VALUE,
                "{\"type\":\"clamped\",\"value\":1,\"min\":2,\"max\":1}"));
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_level_based_value_type]: minecraft:constant",
                reject(env, NumberCodecs.LEVEL_BASED_VALUE, "{\"type\":\"constant\",\"value\":1}"));
    }

    @Test
    void environmentAttributeMustBeNumeric(Env env) {
        assertContains("minecraft:visual/fog_color cannot be converted to a number", reject(env, codecs.number(),
                "{\"type\":\"environment_attribute\",\"attribute\":\"visual/fog_color\"}"));
    }

    // ------------------------------------------------------------------
    // 修饰器
    // ------------------------------------------------------------------

    @Test
    void functionListIsSequenceButSequenceNeedsTypedFunctions(Env env) {
        var function = accept(env, codecs.function(), "[{\"function\":\"set_count\",\"count\":1},{\"function\":\"discard\"}]");
        assertEquals(2, assertInstanceOf(SequenceFunction.class, function).functions().size());
        assertTrue(reject(env, codecs.function(), "{\"function\":\"sequence\",\"functions\":[[{\"function\":\"discard\"}]]}").length() > 0);
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_function_type]: minecraft:function",
                reject(env, codecs.function(), "{\"function\":\"function\"}"));
    }

    @Test
    void furnaceSmeltUseInputCount(Env env) {
        assertTrue(assertInstanceOf(SmeltFunction.class, accept(env, codecs.function(), "{\"function\":\"furnace_smelt\"}")).useInputCount());
        assertFalse(assertInstanceOf(SmeltFunction.class, accept(env, codecs.function(),
                "{\"function\":\"furnace_smelt\",\"use_input_count\":false}")).useInputCount());
    }

    @Test
    void setAttributesReadsIdAndSlots(Env env) {
        var function = assertInstanceOf(SetAttributesFunction.class, accept(env, codecs.function(),
                "{\"function\":\"set_attributes\",\"modifiers\":[{\"id\":\"example:a\",\"attribute\":\"attack_damage\",\"operation\":\"add_value\",\"amount\":1,\"slot\":\"mainhand\"}]}"));
        assertTrue(function.replace());
        assertEquals(Key.key("example:a"), function.modifiers().getFirst().id());
        assertEquals(List.of(EquipmentSlotGroup.MAIN_HAND), function.modifiers().getFirst().slots());
        assertTrue(reject(env, codecs.function(),
                "{\"function\":\"set_attributes\",\"modifiers\":[{\"id\":\"example:a\",\"attribute\":\"attack_damage\",\"operation\":\"add_value\",\"amount\":1,\"slot\":[]}]}")
                .length() > 0);
        assertContains("No key id", reject(env, codecs.function(),
                "{\"function\":\"set_attributes\",\"modifiers\":[{\"name\":\"a\",\"attribute\":\"attack_damage\",\"operation\":\"add_value\",\"amount\":1,\"slot\":\"mainhand\"}]}"));
    }

    @Test
    void explorationMapDefaults(Env env) {
        var function = assertInstanceOf(ExplorationMapFunction.class, accept(env, codecs.function(), "{\"function\":\"exploration_map\"}"));
        assertEquals(Key.key("mansion"), function.mapDecoration());
        assertEquals((byte) 2, function.zoom());
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:map_decoration_type]: minecraft:bogus",
                reject(env, codecs.function(), "{\"function\":\"exploration_map\",\"decoration\":\"bogus\"}"));
    }

    @Test
    void requiredFields(Env env) {
        assertContains("No key limit", reject(env, codecs.function(), "{\"function\":\"limit_count\"}"));
        assertContains("No key entity", reject(env, codecs.function(), "{\"function\":\"fill_player_head\"}"));
        assertContains("No key append", reject(env, codecs.function(), "{\"function\":\"set_banner_pattern\",\"patterns\":[]}"));
        assertContains("No key op", reject(env, codecs.function(),
                "{\"function\":\"copy_custom_data\",\"source\":\"block_entity\",\"ops\":[{\"source\":\"a\",\"target\":\"b\"}]}"));
        assertContains("No key mode", reject(env, codecs.function(), "{\"function\":\"set_lore\",\"lore\":[]}"));
    }

    @Test
    void optionalMaps(Env env) {
        assertTrue(assertInstanceOf(SetEnchantmentsFunction.class, accept(env, codecs.function(), "{\"function\":\"set_enchantments\"}"))
                .enchantments().isEmpty());
        assertTrue(assertInstanceOf(SetStewEffectFunction.class, accept(env, codecs.function(), "{\"function\":\"set_stew_effect\"}"))
                .effects().isEmpty());
        assertContains("Encountered duplicate mob effect", reject(env, codecs.function(),
                "{\"function\":\"set_stew_effect\",\"effects\":[{\"type\":\"speed\",\"duration\":1},{\"type\":\"speed\",\"duration\":2}]}"));
    }

    @Test
    void listOperationsAreInline(Env env) {
        var lore = assertInstanceOf(SetLoreFunction.class, accept(env, codecs.function(),
                "{\"function\":\"set_lore\",\"lore\":[\"a\"],\"mode\":\"insert\",\"offset\":2}"));
        assertEquals(new InsertOperation(2), lore.mode());
        var section = assertInstanceOf(SetLoreFunction.class, accept(env, codecs.function(),
                "{\"function\":\"set_lore\",\"lore\":[],\"mode\":\"replace_section\",\"size\":3}"));
        assertEquals(new ReplaceSectionOperation(0, 3), section.mode());
        assertContains("Size value too large: 300, max size is 256", reject(env, codecs.function(),
                "{\"function\":\"set_lore\",\"lore\":[],\"mode\":\"replace_section\",\"size\":300}"));
    }

    @Test
    void filterablePagesAndLengthLimits(Env env) {
        var pages = assertInstanceOf(SetWritableBookPagesFunction.class, accept(env, codecs.function(),
                "{\"function\":\"set_writable_book_pages\",\"pages\":[{\"raw\":\"a\",\"filtered\":\"b\"},\"c\"],\"mode\":\"append\"}"));
        assertEquals(List.of(new FilteredText<>("a", "b"), new FilteredText<>("c", null)), pages.pages());
        var tooLong = "\"" + "x".repeat(1025) + "\"";
        assertTrue(reject(env, codecs.function(),
                "{\"function\":\"set_writable_book_pages\",\"pages\":[" + tooLong + "],\"mode\":\"append\"}").length() > 0);
        var lines = String.join(",", java.util.Collections.nCopies(257, "\"a\""));
        assertTrue(reject(env, codecs.function(), "{\"function\":\"set_lore\",\"lore\":[" + lines + "],\"mode\":\"append\"}").length() > 0);
    }

    @Test
    void customModelDataColorsAcceptRgb(Env env) {
        var function = assertInstanceOf(SetCustomModelDataFunction.class, accept(env, codecs.function(),
                "{\"function\":\"set_custom_model_data\",\"colors\":{\"values\":[[1.0,0.0,0.0],16],\"mode\":\"append\"}}"));
        List<NumberProvider> colors = function.colors().orElseThrow().values();
        // 原版 ExtraCodecs.RGB_COLOR_CODEC 把三个浮点数按不透明度 1 转成 ARGB 整数
        assertEquals(List.of(new ConstantValue((float) 0xFFFF0000), new ConstantValue(16.0f)), colors);
    }

    @Test
    void applyBonusFormula(Env env) {
        assertEquals(ApplyBonusFormula.OreDrops.INSTANCE, assertInstanceOf(ApplyBonusFunction.class, accept(env, codecs.function(),
                "{\"function\":\"apply_bonus\",\"enchantment\":\"fortune\",\"formula\":\"ore_drops\"}")).formula());
        assertEquals(new ApplyBonusFormula.UniformBonusCount(2), assertInstanceOf(ApplyBonusFunction.class, accept(env, codecs.function(),
                "{\"function\":\"apply_bonus\",\"enchantment\":\"fortune\",\"formula\":\"uniform_bonus_count\",\"parameters\":{\"bonusMultiplier\":2}}"))
                .formula());
        assertContains("No formula type with id: 'minecraft:bogus'", reject(env, codecs.function(),
                "{\"function\":\"apply_bonus\",\"enchantment\":\"fortune\",\"formula\":\"bogus\"}"));
    }

    @Test
    void containerComponents(Env env) {
        assertTrue(accept(env, codecs.function(), "{\"function\":\"set_contents\",\"component\":\"bundle_contents\",\"entries\":[]}")
                instanceof LootFunction);
        assertContains("No items in component", reject(env, codecs.function(),
                "{\"function\":\"set_contents\",\"component\":\"lore\",\"entries\":[]}"));
    }

    @Test
    void copyComponentsAcceptsTool(Env env) {
        var function = assertInstanceOf(CopyComponentsFunction.class, accept(env, codecs.function(),
                "{\"function\":\"copy_components\",\"source\":\"tool\",\"include\":[\"custom_name\"]}"));
        assertEquals(LootContextArg.Tool.INSTANCE, function.source());
        assertContains("Unknown element id: tool", reject(env, codecs.function(), "{\"function\":\"copy_name\",\"source\":\"tool\"}"));
    }

    @Test
    void enchantRandomlyOptionsChecked(Env env) {
        assertContains("Failed to get element example:nope", reject(env, codecs.function(),
                "{\"function\":\"enchant_randomly\",\"options\":[\"example:nope\"]}"));
        var options = accept(env, codecs.function(), "{\"function\":\"enchant_randomly\",\"options\":\"#minecraft:on_random_loot\"}");
        assertInstanceOf(HolderSet.Named.class, ((io.ib67.yuanban.loot.function.EnchantRandomlyFunction) options).options().orElseThrow());
    }

    @Test
    void decodedEntriesAreFullRecords(Env env) {
        LootEntry entry = accept(env, codecs.entry(), "{\"type\":\"empty\",\"weight\":0}");
        assertEquals(0, ((io.ib67.yuanban.loot.entry.EmptyEntry) entry).weight());
        LevelBasedValue value = accept(env, NumberCodecs.LEVEL_BASED_VALUE, "{\"type\":\"lookup\",\"values\":[1,2],\"fallback\":3}");
        assertInstanceOf(LevelBasedValue.Lookup.class, value);
    }
}
