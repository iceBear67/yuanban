package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.datapack.LoadFailedException;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.*;
import io.ib67.yuanban.loot.entry.*;
import io.ib67.yuanban.loot.function.*;
import io.ib67.yuanban.loot.number.*;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 经过数据包加载解码战利品表：原版 9 种条目、20 种条件、43 种修饰器、8 种数字提供器各至少一个用例，
 * 以及本次修正的原版行为的接受与拒绝用例；错误文本与原版相同。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class LootDecodingTest {
    @TempDir
    Path tempDir;

    private LootTable load(Env env, String json) {
        return load(env, world -> world, json);
    }

    /**
     * 加载 {@code loot_table/t.json}；{@code setup} 可以向原版数据中加入标签、附魔或其他战利品表。
     */
    private LootTable load(Env env, UnaryOperator<LootTestWorld> setup, String json) {
        var world = setup.apply(new LootTestWorld(tempDir)).vanilla("loot_table/t.json", json);
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var session = world.loader(env.process(), new LootTableSubscriber(env.process(), manager)).start(LootTestWorld.createWorld());
        try {
            return manager.get(Key.key("t")).orElseThrow(() -> new AssertionError("loot table not decoded"));
        } finally {
            session.close();
        }
    }

    /**
     * 加载失败时 {@code loot_table/t.json} 的错误信息（去掉文件位置的前缀）。
     */
    private String failure(Env env, UnaryOperator<LootTestWorld> setup, String json) {
        var world = setup.apply(new LootTestWorld(tempDir)).vanilla("loot_table/t.json", json);
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var loader = world.loader(env.process(), new LootTableSubscriber(env.process(), manager));
        var thrown = assertThrows(LoadFailedException.class, () -> loader.start(LootTestWorld.createWorld()));
        var exception = assertInstanceOf(LootException.class, thrown.getCause());
        var prefix = "Couldn't parse data file 'minecraft:t' from [vanilla] data/minecraft/loot_table/t.json: ";
        assertTrue(exception.getMessage().startsWith(prefix), exception.getMessage());
        return exception.getMessage().substring(prefix.length());
    }

    private String failure(Env env, String json) {
        return failure(env, world -> world, json);
    }

    private static void assertContains(String expected, String message) {
        assertTrue(message.contains(expected), "expected [" + expected + "] in [" + message + "]");
    }

    private static UnaryOperator<LootTestWorld> enchantments() {
        return world -> world.enchantment("fortune", 3).enchantment("looting", 3).enchantment("sharpness", 5);
    }

    private static String entries(String... entries) {
        return "{\"pools\":[{\"rolls\":1,\"entries\":[" + String.join(",", entries) + "]}]}";
    }

    private static String withConditions(String... conditions) {
        return entries("{\"type\":\"item\",\"name\":\"stone\",\"conditions\":[" + String.join(",", conditions) + "]}");
    }

    private static String withFunctions(String... functions) {
        return entries("{\"type\":\"item\",\"name\":\"stone\",\"functions\":[" + String.join(",", functions) + "]}");
    }

    private static List<LootCondition> conditions(LootTable table) {
        return table.pools().getFirst().entries().getFirst().conditions();
    }

    private static List<LootFunction> functions(LootTable table) {
        return ((SingletonEntry) table.pools().getFirst().entries().getFirst()).functions();
    }

    // ------------------------------------------------------------------
    // 表与池
    // ------------------------------------------------------------------

    @Test
    void tableDefaults(Env env) {
        var table = load(env, "{\"pools\":[]}");
        assertEquals(Key.key("minecraft:generic"), table.type());
        assertTrue(table.randomSequence().isEmpty());
        assertTrue(table.functions().isEmpty());
        assertEquals(Key.key("chest"), load(env, "{\"type\":\"minecraft:chest\"}").type());
    }

    @Test
    void tableUnknownTypeFallsBackToGeneric(Env env) {
        // 原版 LootTable.DIRECT_CODEC 用 lenientOptionalFieldOf 读 type
        assertEquals(LootTable.DEFAULT_TYPE, load(env, "{\"type\":\"minecraft:bogus\",\"pools\":[]}").type());
    }

    @Test
    void poolEntriesMayBeEmpty(Env env) {
        var pool = load(env, "{\"pools\":[{\"rolls\":3,\"entries\":[]}]}").pools().getFirst();
        assertTrue(pool.entries().isEmpty());
        assertEquals(new ConstantValue(3.0f), pool.rolls());
        assertEquals(ConstantValue.ZERO, pool.bonusRolls());
        assertContains("No key entries", failure(env, "{\"pools\":[{\"rolls\":1}]}"));
    }

    // ------------------------------------------------------------------
    // 条目
    // ------------------------------------------------------------------

    @Test
    void allEntryTypes(Env env) {
        var table = load(env, world -> world.vanilla("tags/item/planks.json", "{\"values\":[\"minecraft:oak_planks\"]}")
                .vanilla("loot_table/other.json", "{\"pools\":[]}"), entries(
                "{\"type\":\"empty\"}",
                "{\"type\":\"item\",\"name\":\"stone\",\"weight\":5,\"quality\":3}",
                "{\"type\":\"loot_table\",\"value\":\"minecraft:other\"}",
                "{\"type\":\"dynamic\",\"name\":\"example:anything\"}",
                "{\"type\":\"tag\",\"name\":\"minecraft:planks\",\"expand\":true}",
                "{\"type\":\"slots\",\"slot_source\":{\"type\":\"slot_range\",\"source\":\"this\",\"slots\":\"hotbar.*\"}}",
                "{\"type\":\"alternatives\",\"children\":[{\"type\":\"empty\"}]}",
                "{\"type\":\"sequence\"}",
                "{\"type\":\"group\",\"children\":[],\"conditions\":[{\"condition\":\"killed_by_player\"}]}"));
        var decoded = table.pools().getFirst().entries();
        assertInstanceOf(EmptyEntry.class, decoded.get(0));
        var item = assertInstanceOf(ItemEntry.class, decoded.get(1));
        assertEquals(Material.STONE, item.name());
        assertEquals(5, item.weight());
        assertEquals(3, item.quality());
        assertEquals(Key.key("other"), assertInstanceOf(NestedTableEntry.class, decoded.get(2)).value().left().orElseThrow());
        assertEquals(Key.key("example:anything"), assertInstanceOf(DynamicEntry.class, decoded.get(3)).name());
        assertTrue(assertInstanceOf(TagEntry.class, decoded.get(4)).expand());
        assertInstanceOf(SlotsEntry.class, decoded.get(5));
        assertEquals(1, assertInstanceOf(AlternativesEntry.class, decoded.get(6)).children().size());
        assertTrue(assertInstanceOf(SequenceEntry.class, decoded.get(7)).children().isEmpty());
        var group = assertInstanceOf(GroupEntry.class, decoded.get(8));
        assertTrue(group.children().isEmpty());
        assertEquals(1, group.conditions().size());
    }

    @Test
    void tagEntryDoesNotCheckTheTag(Env env) {
        // 原版 TagEntry 的 name 是 TagKey.codec，解码时不检查标签是否存在
        var entry = assertInstanceOf(TagEntry.class, load(env, entries("{\"type\":\"tag\",\"name\":\"minecraft:no_such_tag\",\"expand\":false}"))
                .pools().getFirst().entries().getFirst());
        assertEquals(Key.key("no_such_tag"), entry.name());
    }

    @Test
    void tagEntryExpandIsRequired(Env env) {
        assertContains("No key expand", failure(env, entries("{\"type\":\"tag\",\"name\":\"minecraft:planks\"}")));
    }

    @Test
    void unknownEntryType(Env env) {
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_pool_entry_type]: minecraft:bogus_entry",
                failure(env, entries("{\"type\":\"minecraft:bogus_entry\",\"name\":\"minecraft:stone\"}")));
    }

    @Test
    void itemEntryRejectsAirAndUnknownItems(Env env) {
        assertContains("Item must not be minecraft:air", failure(env, entries("{\"type\":\"item\",\"name\":\"air\"}")));
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: minecraft:nope",
                failure(env, entries("{\"type\":\"item\",\"name\":\"nope\"}")));
    }

    // ------------------------------------------------------------------
    // 条件
    // ------------------------------------------------------------------

    @Test
    void allConditionTypes(Env env) {
        var clock = env.process().worldClock().getKey(0).key().asString();
        var table = load(env, enchantments(), withConditions(
                "{\"condition\":\"inverted\",\"term\":{\"condition\":\"killed_by_player\"}}",
                "{\"condition\":\"any_of\",\"terms\":[{\"condition\":\"survives_explosion\"}]}",
                "{\"condition\":\"all_of\",\"terms\":[]}",
                "{\"condition\":\"random_chance\",\"chance\":0.5}",
                "{\"condition\":\"random_chance_with_enchanted_bonus\",\"unenchanted_chance\":0.1,\"enchanted_chance\":{\"type\":\"linear\",\"base\":0.5,\"per_level_above_first\":0.5},\"enchantment\":\"looting\"}",
                "{\"condition\":\"entity_properties\",\"entity\":\"this\",\"predicate\":{\"minecraft:entity_type\":\"minecraft:pig\"}}",
                "{\"condition\":\"killed_by_player\"}",
                "{\"condition\":\"entity_scores\",\"entity\":\"this\",\"scores\":{\"kills\":{\"min\":1}}}",
                "{\"condition\":\"block_state_property\",\"block\":\"wheat\",\"properties\":{\"age\":{\"min\":\"3\"}}}",
                "{\"condition\":\"match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}}",
                "{\"condition\":\"table_bonus\",\"enchantment\":\"fortune\",\"chances\":[0.5,0.6]}",
                "{\"condition\":\"survives_explosion\"}",
                "{\"condition\":\"damage_source_properties\",\"predicate\":{\"tags\":[{\"id\":\"minecraft:is_fire\",\"expected\":true}]}}",
                "{\"condition\":\"location_check\",\"offsetY\":1,\"predicate\":{\"block\":{\"blocks\":\"minecraft:stone\"}}}",
                "{\"condition\":\"weather_check\",\"raining\":true}",
                "{\"condition\":\"reference\",\"name\":\"example:any\"}",
                "{\"condition\":\"time_check\",\"clock\":\"" + clock + "\",\"value\":1000}",
                "{\"condition\":\"value_check\",\"value\":1,\"range\":5}",
                "{\"condition\":\"enchantment_active_check\",\"active\":true}",
                "{\"condition\":\"environment_attribute_check\",\"attribute\":\"gameplay/fast_lava\",\"value\":true}"));
        var decoded = conditions(table);
        assertEquals(20, decoded.size());
        assertInstanceOf(InvertedCondition.class, decoded.get(0));
        assertInstanceOf(AnyOfCondition.class, decoded.get(1));
        assertInstanceOf(AllOfCondition.class, decoded.get(2));
        assertInstanceOf(RandomChanceCondition.class, decoded.get(3));
        assertEquals(Key.key("looting"), assertInstanceOf(RandomChanceWithEnchantedBonusCondition.class, decoded.get(4)).enchantment().key());
        assertInstanceOf(EntityPropertiesCondition.class, decoded.get(5));
        assertInstanceOf(KilledByPlayerCondition.class, decoded.get(6));
        assertInstanceOf(EntityScoresCondition.class, decoded.get(7));
        assertInstanceOf(BlockStatePropertyCondition.class, decoded.get(8));
        assertInstanceOf(MatchToolCondition.class, decoded.get(9));
        assertEquals(2, assertInstanceOf(TableBonusCondition.class, decoded.get(10)).values().size());
        assertInstanceOf(SurvivesExplosionCondition.class, decoded.get(11));
        assertInstanceOf(DamageSourcePropertiesCondition.class, decoded.get(12));
        assertEquals(1, assertInstanceOf(LocationCheckCondition.class, decoded.get(13)).offsetY());
        assertTrue(assertInstanceOf(WeatherCheckCondition.class, decoded.get(14)).isRaining().orElseThrow());
        assertEquals(Key.key("example:any"), assertInstanceOf(ReferenceCondition.class, decoded.get(15)).name());
        assertTrue(assertInstanceOf(TimeCheckCondition.class, decoded.get(16)).period().isEmpty());
        assertInstanceOf(ValueCheckCondition.class, decoded.get(17));
        assertInstanceOf(EnchantmentActiveCheckCondition.class, decoded.get(18));
        assertInstanceOf(EnvironmentAttributeCheckCondition.class, decoded.get(19));
    }

    @Test
    void conditionListIsAllOf(Env env) {
        var table = load(env, withConditions("[{\"condition\":\"killed_by_player\"},{\"condition\":\"survives_explosion\"}]"));
        assertEquals(2, assertInstanceOf(AllOfCondition.class, conditions(table).getFirst()).terms().size());
    }

    @Test
    void conditionMatchToolItemTag(Env env) {
        var table = load(env, world -> world.vanilla("tags/item/planks.json", "{\"values\":[\"minecraft:oak_planks\"]}"),
                withConditions("{\"condition\":\"match_tool\",\"predicate\":{\"items\":\"#minecraft:planks\"}}"));
        var predicate = assertInstanceOf(MatchToolCondition.class, conditions(table).getFirst()).predicate().orElseThrow();
        assertEquals(HolderSet.named(Key.key("planks")), predicate.items().orElseThrow());
    }

    @Test
    void conditionMatchToolUnknownTagFails(Env env) {
        assertContains("Missing tag: 'minecraft:does_not_exist' in 'minecraft:item'", failure(env,
                withConditions("{\"condition\":\"match_tool\",\"predicate\":{\"items\":\"#minecraft:does_not_exist\"}}")));
    }

    @Test
    void conditionEntityRequired(Env env) {
        assertContains("No key entity", failure(env, withConditions("{\"condition\":\"entity_properties\"}")));
        assertContains("No key entity", failure(env, withConditions("{\"condition\":\"entity_scores\",\"scores\":{}}")));
    }

    @Test
    void conditionTimeCheckClockRequired(Env env) {
        assertContains("No key clock", failure(env, withConditions("{\"condition\":\"time_check\",\"value\":1000}")));
        assertContains("Failed to get element example:nope", failure(env,
                withConditions("{\"condition\":\"time_check\",\"clock\":\"example:nope\",\"value\":1000}")));
    }

    @Test
    void conditionEnchantmentsAreCheckedAgainstThisLoad(Env env) {
        // 附魔来自同一次加载的附魔文件
        assertContains("Failed to get element minecraft:fortune", failure(env,
                withConditions("{\"condition\":\"table_bonus\",\"enchantment\":\"fortune\",\"chances\":[0.5]}")));
        assertContains("List must have contents", failure(env, enchantments(),
                withConditions("{\"condition\":\"table_bonus\",\"enchantment\":\"fortune\",\"chances\":[]}")));
    }

    @Test
    void conditionBlockStatePropertyChecksNames(Env env) {
        assertContains("Block Reference{ResourceKey[minecraft:block / minecraft:stone]=Block{minecraft:stone}} has no propertysnowy",
                failure(env, withConditions("{\"condition\":\"block_state_property\",\"block\":\"stone\",\"properties\":{\"snowy\":\"true\"}}")));
    }

    @Test
    void unknownConditionType(Env env) {
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_condition_type]: minecraft:bogus_condition",
                failure(env, withConditions("{\"condition\":\"minecraft:bogus_condition\"}")));
    }

    // ------------------------------------------------------------------
    // 修饰器
    // ------------------------------------------------------------------

    @Test
    void allFunctionTypes(Env env) {
        // set_loot_table 引用的表由 LootManager 检查存在
        UnaryOperator<LootTestWorld> setup = world -> enchantments().apply(world).vanilla("loot_table/any.json", "{\"pools\":[]}");
        var table = load(env, setup, withFunctions(
                "{\"function\":\"set_count\",\"count\":5}",
                "{\"function\":\"set_item\",\"item\":\"dirt\"}",
                "{\"function\":\"enchant_with_levels\",\"levels\":30}",
                "{\"function\":\"enchant_randomly\",\"options\":[\"sharpness\"]}",
                "{\"function\":\"set_enchantments\",\"enchantments\":{\"looting\":2}}",
                "{\"function\":\"set_custom_data\",\"tag\":\"{foo:1}\"}",
                "{\"function\":\"set_components\",\"components\":{\"!minecraft:damage\":{},\"minecraft:max_stack_size\":1}}",
                "{\"function\":\"furnace_smelt\"}",
                "{\"function\":\"enchanted_count_increase\",\"enchantment\":\"looting\",\"count\":1}",
                "{\"function\":\"set_damage\",\"damage\":0.5}",
                "{\"function\":\"set_attributes\",\"modifiers\":[{\"id\":\"example:a\",\"attribute\":\"attack_damage\",\"operation\":\"add_value\",\"amount\":1,\"slot\":[\"mainhand\",\"offhand\"]}]}",
                "{\"function\":\"set_name\",\"name\":\"Stone\",\"target\":\"item_name\"}",
                "{\"function\":\"exploration_map\"}",
                "{\"function\":\"set_stew_effect\",\"effects\":[{\"type\":\"saturation\",\"duration\":1}]}",
                "{\"function\":\"copy_name\",\"source\":\"block_entity\"}",
                "{\"function\":\"set_contents\",\"component\":\"container\",\"entries\":[{\"type\":\"item\",\"name\":\"stone\"}]}",
                "{\"function\":\"modify_contents\",\"component\":\"bundle_contents\",\"modifier\":{\"function\":\"discard\"}}",
                "{\"function\":\"filtered\",\"item_filter\":{\"items\":\"stone\"},\"on_pass\":{\"function\":\"discard\"}}",
                "{\"function\":\"limit_count\",\"limit\":{\"min\":1,\"max\":3}}",
                "{\"function\":\"apply_bonus\",\"enchantment\":\"fortune\",\"formula\":\"ore_drops\"}",
                "{\"function\":\"set_loot_table\",\"name\":\"minecraft:any\",\"seed\":42,\"type\":\"chest\"}",
                "{\"function\":\"explosion_decay\"}",
                "{\"function\":\"set_lore\",\"lore\":[\"line1\"],\"mode\":\"append\"}",
                "{\"function\":\"fill_player_head\",\"entity\":\"this\"}",
                "{\"function\":\"copy_custom_data\",\"source\":{\"type\":\"storage\",\"source\":\"example:chest\"},\"ops\":[{\"source\":\"a\",\"target\":\"b\",\"op\":\"merge\"}]}",
                "{\"function\":\"copy_state\",\"block\":\"oak_log\",\"properties\":[\"axis\"]}",
                "{\"function\":\"set_banner_pattern\",\"patterns\":[{\"pattern\":\"minecraft:creeper\",\"color\":\"red\"}],\"append\":true}",
                "{\"function\":\"set_potion\",\"id\":\"fire_resistance\"}",
                "{\"function\":\"set_random_dyes\",\"number_of_dyes\":3}",
                "{\"function\":\"set_random_potion\"}",
                "{\"function\":\"set_instrument\",\"options\":\"#minecraft:goat_horns\"}",
                "{\"function\":\"reference\",\"name\":\"example:modifier\"}",
                "{\"function\":\"sequence\",\"functions\":[{\"function\":\"discard\"}]}",
                "{\"function\":\"copy_components\",\"source\":\"tool\",\"include\":[\"custom_name\"]}",
                "{\"function\":\"set_fireworks\",\"explosions\":{\"values\":[{\"shape\":\"small_ball\"}],\"mode\":\"append\"},\"flight_duration\":2}",
                "{\"function\":\"set_firework_explosion\",\"shape\":\"large_ball\",\"colors\":[255,0]}",
                "{\"function\":\"set_book_cover\",\"title\":{\"raw\":\"Title\"},\"generation\":1}",
                "{\"function\":\"set_written_book_pages\",\"pages\":[\"page1\"],\"mode\":\"replace_all\"}",
                "{\"function\":\"set_writable_book_pages\",\"pages\":[\"p1\"],\"mode\":\"insert\",\"offset\":0}",
                "{\"function\":\"toggle_tooltips\",\"toggles\":{\"minecraft:enchantments\":false}}",
                "{\"function\":\"set_ominous_bottle_amplifier\",\"amplifier\":4}",
                "{\"function\":\"set_custom_model_data\",\"floats\":{\"values\":[1.0],\"mode\":\"append\"}}",
                "{\"function\":\"discard\"}"));
        var decoded = functions(table);
        assertEquals(43, decoded.size());
        assertInstanceOf(SetCountFunction.class, decoded.get(0));
        assertEquals(Material.DIRT, assertInstanceOf(SetItemFunction.class, decoded.get(1)).item());
        assertInstanceOf(EnchantWithLevelsFunction.class, decoded.get(2));
        assertTrue(assertInstanceOf(EnchantRandomlyFunction.class, decoded.get(3)).onlyCompatible());
        assertFalse(assertInstanceOf(SetEnchantmentsFunction.class, decoded.get(4)).add());
        assertNotNull(assertInstanceOf(SetCustomDataFunction.class, decoded.get(5)).tag().get("foo"));
        assertInstanceOf(SetComponentsFunction.class, decoded.get(6));
        assertTrue(assertInstanceOf(SmeltFunction.class, decoded.get(7)).useInputCount());
        assertEquals(0, assertInstanceOf(EnchantedCountIncreaseFunction.class, decoded.get(8)).limit());
        assertInstanceOf(SetDamageFunction.class, decoded.get(9));
        var attributes = assertInstanceOf(SetAttributesFunction.class, decoded.get(10));
        assertTrue(attributes.replace());
        assertEquals(2, attributes.modifiers().getFirst().slots().size());
        assertEquals(SetNameTarget.ITEM_NAME, assertInstanceOf(SetNameFunction.class, decoded.get(11)).target());
        var map = assertInstanceOf(ExplorationMapFunction.class, decoded.get(12));
        assertEquals(Key.key("mansion"), map.mapDecoration());
        assertEquals((byte) 2, map.zoom());
        assertInstanceOf(SetStewEffectFunction.class, decoded.get(13));
        assertInstanceOf(CopyNameFunction.class, decoded.get(14));
        assertInstanceOf(SetContainerContentsFunction.class, decoded.get(15));
        assertInstanceOf(ModifyContentsFunction.class, decoded.get(16));
        assertInstanceOf(FilteredFunction.class, decoded.get(17));
        assertInstanceOf(LimitCountFunction.class, decoded.get(18));
        assertEquals(ApplyBonusFormula.OreDrops.INSTANCE, assertInstanceOf(ApplyBonusFunction.class, decoded.get(19)).formula());
        assertEquals(42L, assertInstanceOf(SetContainerLootTableFunction.class, decoded.get(20)).seed());
        assertInstanceOf(ExplosionDecayFunction.class, decoded.get(21));
        assertSame(AppendOperation.INSTANCE, assertInstanceOf(SetLoreFunction.class, decoded.get(22)).mode());
        assertInstanceOf(FillPlayerHeadFunction.class, decoded.get(23));
        assertEquals(MergeStrategy.MERGE, assertInstanceOf(CopyCustomDataFunction.class, decoded.get(24)).ops().getFirst().op());
        assertEquals(List.of("axis"), assertInstanceOf(CopyBlockStateFunction.class, decoded.get(25)).properties());
        assertTrue(assertInstanceOf(SetBannerPatternFunction.class, decoded.get(26)).append());
        assertInstanceOf(SetPotionFunction.class, decoded.get(27));
        assertInstanceOf(SetRandomDyesFunction.class, decoded.get(28));
        assertTrue(assertInstanceOf(SetRandomPotionFunction.class, decoded.get(29)).options().isEmpty());
        assertInstanceOf(SetInstrumentFunction.class, decoded.get(30));
        assertInstanceOf(FunctionReference.class, decoded.get(31));
        assertEquals(1, assertInstanceOf(SequenceFunction.class, decoded.get(32)).functions().size());
        assertInstanceOf(CopyComponentsFunction.class, decoded.get(33));
        assertEquals(2, assertInstanceOf(SetFireworksFunction.class, decoded.get(34)).flightDuration().orElseThrow());
        assertEquals(List.of(255, 0), assertInstanceOf(SetFireworkExplosionFunction.class, decoded.get(35)).colors().orElseThrow());
        assertEquals("Title", assertInstanceOf(SetBookCoverFunction.class, decoded.get(36)).title().orElseThrow().text());
        assertSame(ReplaceAllOperation.INSTANCE, assertInstanceOf(SetWrittenBookPagesFunction.class, decoded.get(37)).mode());
        assertEquals(new InsertOperation(0), assertInstanceOf(SetWritableBookPagesFunction.class, decoded.get(38)).mode());
        assertInstanceOf(ToggleTooltipsFunction.class, decoded.get(39));
        assertInstanceOf(SetOminousBottleAmplifierFunction.class, decoded.get(40));
        assertTrue(assertInstanceOf(SetCustomModelDataFunction.class, decoded.get(41)).floats().isPresent());
        assertInstanceOf(DiscardFunction.class, decoded.get(42));
    }

    @Test
    void functionListIsSequence(Env env) {
        var table = load(env, withFunctions("[{\"function\":\"set_count\",\"count\":1},{\"function\":\"discard\"}]"));
        assertEquals(2, assertInstanceOf(SequenceFunction.class, functions(table).getFirst()).functions().size());
    }

    @Test
    void functionRequiredFields(Env env) {
        assertContains("No key limit", failure(env, withFunctions("{\"function\":\"limit_count\"}")));
        assertContains("No key entity", failure(env, withFunctions("{\"function\":\"fill_player_head\"}")));
        assertContains("No key append", failure(env, withFunctions("{\"function\":\"set_banner_pattern\",\"patterns\":[]}")));
        assertContains("No key mode", failure(env, withFunctions("{\"function\":\"set_lore\",\"lore\":[\"line\"]}")));
        assertContains("No key op", failure(env, withFunctions(
                "{\"function\":\"copy_custom_data\",\"source\":\"block_entity\",\"ops\":[{\"source\":\"a\",\"target\":\"b\"}]}")));
        assertContains("No key id", failure(env, withFunctions(
                "{\"function\":\"set_attributes\",\"modifiers\":[{\"attribute\":\"attack_damage\",\"operation\":\"add_value\",\"amount\":1,\"slot\":\"mainhand\"}]}")));
    }

    @Test
    void functionSetCustomModelDataColorsAcceptRgb(Env env) {
        var function = assertInstanceOf(SetCustomModelDataFunction.class, functions(load(env, withFunctions(
                "{\"function\":\"set_custom_model_data\",\"colors\":{\"values\":[[0.0,0.0,1.0]],\"mode\":\"replace_all\"}}"))).getFirst());
        assertEquals(List.of(new ConstantValue((float) 0xFF0000FF)), function.colors().orElseThrow().values());
    }

    @Test
    void functionUnknownTypeIncludesTheRemovedFunctionType(Env env) {
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_function_type]: minecraft:function",
                failure(env, withFunctions("{\"function\":\"minecraft:function\"}")));
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_function_type]: bogus:x",
                failure(env, withFunctions("{\"function\":\"bogus:x\"}")));
    }

    @Test
    void functionExplorationMapDecorationChecked(Env env) {
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:map_decoration_type]: minecraft:bogus",
                failure(env, withFunctions("{\"function\":\"exploration_map\",\"decoration\":\"bogus\"}")));
    }

    // ------------------------------------------------------------------
    // 数字提供器
    // ------------------------------------------------------------------

    private NumberProvider number(Env env, String json) {
        var table = load(env, withFunctions("{\"function\":\"set_count\",\"count\":" + json + "}"));
        return assertInstanceOf(SetCountFunction.class, functions(table).getFirst()).count();
    }

    @Test
    void allNumberProviderTypes(Env env) {
        assertEquals(new ConstantValue(3.0f), number(env, "{\"type\":\"constant\",\"value\":3}"));
        assertEquals(new ConstantValue(2.0f), number(env, "2"));
        assertInstanceOf(UniformValue.class, number(env, "{\"type\":\"uniform\",\"min\":0,\"max\":2}"));
        assertInstanceOf(UniformValue.class, number(env, "{\"min\":0,\"max\":2}"));
        assertInstanceOf(BinomialValue.class, number(env, "{\"type\":\"binomial\",\"n\":5,\"p\":0.5}"));
        assertEquals(new ScoreboardName.Fixed("player"), assertInstanceOf(ScoreboardValue.class,
                number(env, "{\"type\":\"score\",\"target\":{\"type\":\"fixed\",\"name\":\"player\"},\"score\":\"kills\"}")).target());
        assertEquals(new ScoreboardName.Context(EntityTarget.THIS), assertInstanceOf(ScoreboardValue.class,
                number(env, "{\"type\":\"score\",\"target\":\"this\",\"score\":\"x\"}")).target());
        assertInstanceOf(StorageValue.class, number(env, "{\"type\":\"storage\",\"storage\":\"example:chest\",\"path\":\"Items[0]\"}"));
        assertEquals(3, assertInstanceOf(SumValue.class, number(env, "{\"type\":\"sum\",\"summands\":[1,2,3]}")).summands().size());
        assertInstanceOf(EnchantmentLevelValue.class, number(env,
                "{\"type\":\"enchantment_level\",\"amount\":{\"type\":\"linear\",\"base\":1,\"per_level_above_first\":1}}"));
        assertInstanceOf(EnvironmentAttributeValue.class, number(env, "{\"type\":\"environment_attribute\",\"attribute\":\"gameplay/sky_light_level\"}"));
    }

    @Test
    void numberSumReadsSummands(Env env) {
        assertContains("No key summands", failure(env, withFunctions("{\"function\":\"set_count\",\"count\":{\"type\":\"sum\",\"terms\":[1]}}")));
    }

    @Test
    void numberStoragePathErrors(Env env) {
        assertContains("Failed to parse path a..b", failure(env, withFunctions(
                "{\"function\":\"set_count\",\"count\":{\"type\":\"storage\",\"storage\":\"example:chest\",\"path\":\"a..b\"}}")));
    }

    @Test
    void unknownNumberProviderType(Env env) {
        assertContains("Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_number_provider_type]: minecraft:not_a_provider",
                failure(env, withFunctions("{\"function\":\"set_count\",\"count\":{\"type\":\"not_a_provider\",\"x\":1}}")));
    }

    @Test
    void itemPredicateRecordsAreDecoded(Env env) {
        var filtered = assertInstanceOf(FilteredFunction.class, functions(load(env, withFunctions(
                "{\"function\":\"filtered\",\"item_filter\":{\"items\":[\"stone\",\"dirt\"],\"count\":{\"min\":2},\"predicates\":{\"minecraft:damage\":{}}}}")))
                .getFirst());
        ItemPredicate predicate = filtered.itemFilter();
        assertEquals(2, ((HolderSet.Direct<Material>) predicate.items().orElseThrow()).keys().size());
        assertEquals(2, predicate.count().min());
        assertEquals(1, predicate.components().partial().size());
    }
}
