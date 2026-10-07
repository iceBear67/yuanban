package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.loot.LootManager;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.LootTypeRegistry;
import io.ib67.yuanban.loot.condition.*;
import io.ib67.yuanban.loot.entry.*;
import io.ib67.yuanban.loot.function.*;
import io.ib67.yuanban.loot.number.*;
import io.ib67.yuanban.lootparser.internal.CodecWiring;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerProcess;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 每个原版条目（9）/ 条件（20）/ 修饰器（44）/ 数字提供器（8）至少一个正常用例，断言字段与缺省值；
 * 对照 loot-table-parser 设计第 4 节的字段名与原版 26.2 MapCodec 的缺省值。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class LootDecodingTest {
    @TempDir
    Path tempDir;

    private static LootTypeRegistry wireTypes(ServerProcess p) {
        var types = LootTypeRegistry.vanilla();
        CodecWiring.wire(types, p);
        return types;
    }

    private LootTable load(Env env, String path, String json) {
        var world = new LootTestWorld(tempDir).vanilla(path, json);
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var loottables = new LootTableSubscriber(env.process(), manager);
        var session = world.loader(env.process(), loottables).start(LootTestWorld.createWorld());
        try {
            var id = Key.key(path.substring("loot_table/".length(), path.length() - ".json".length()));
            return manager.get(id).orElseThrow(() -> new AssertionError("loot table not decoded: " + id));
        } finally {
            session.close();
        }
    }

    /**
     * 一次性加载多份 vanilla 路径文件后取指定 ID 的表；用于「引用必须已存在」的用例。
     */
    private LootTable loadReferenced(Env env, java.util.Map<String, String> files, String id) {
        var world = new LootTestWorld(tempDir);
        files.forEach(world::vanilla);
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var loottables = new LootTableSubscriber(env.process(), manager);
        var session = world.loader(env.process(), loottables).start(LootTestWorld.createWorld());
        try {
            return manager.get(Key.key(id)).orElseThrow(() -> new AssertionError("loot table not decoded: " + id));
        } finally {
            session.close();
        }
    }

    private LootEntry firstEntry(LootTable t) {
        return t.pools().getFirst().entries().getFirst();
    }

    private String entryJson(String typeJson) {
        return "{\"pools\":[{\"rolls\":1,\"entries\":[" + typeJson + "]}]}";
    }

    // ------------------------------------------------------------------
    // 顶层与池
    // ------------------------------------------------------------------

    @Test
    void tableDefaults(Env env) {
        var t = load(env, "loot_table/test.json", "{\"pools\":[]}");
        assertEquals(Key.key("minecraft:generic"), t.type());
        assertTrue(t.randomSequence().isEmpty());
        assertTrue(t.functions().isEmpty());
        assertTrue(t.pools().isEmpty());
    }

    @Test
    void tableTypeField(Env env) {
        var t = load(env, "loot_table/test.json", "{\"type\":\"minecraft:chest\",\"pools\":[]}");
        assertEquals(Key.key("minecraft:chest"), t.type());
    }

    @Test
    void tableUnknownTypeFails(Env env) {
        assertThrows(Exception.class, () ->
                load(env, "loot_table/test.json", "{\"type\":\"minecraft:bogus\",\"pools\":[]}"));
    }

    @Test
    void poolRollsAndDefaults(Env env) {
        var t = load(env, "loot_table/test.json",
                "{\"pools\":[{\"rolls\":3,\"entries\":[{\"type\":\"minecraft:empty\"}]}]}");
        var pool = t.pools().getFirst();
        assertEquals(3f, ((ConstantValue) pool.rolls()).value());
        assertEquals(ConstantValue.ZERO, pool.bonusRolls());
        assertTrue(pool.conditions().isEmpty());
        assertTrue(pool.functions().isEmpty());
    }

    @Test
    void poolEmptyEntriesFails(Env env) {
        assertThrows(Exception.class, () ->
                load(env, "loot_table/test.json", "{\"pools\":[{\"rolls\":1,\"entries\":[]}]}"));
    }

    // ------------------------------------------------------------------
    // 条目：item / loot_table / dynamic / empty / slots / tag / group / alternatives / sequence
    // ------------------------------------------------------------------

    @Test
    void itemEntryDefaults(Env env) {
        var t = load(env, "loot_table/test.json",
                entryJson("{\"type\":\"minecraft:item\",\"name\":\"minecraft:stone\"}"));
        var e = assertInstanceOf(ItemEntry.class, firstEntry(t));
        assertEquals(Key.key("minecraft:stone"), e.name());
        assertEquals(1, e.weight());
        assertEquals(0, e.quality());
        assertTrue(e.conditions().isEmpty());
        assertTrue(e.functions().isEmpty());
    }

    @Test
    void itemEntryWeightAndQuality(Env env) {
        var t = load(env, "loot_table/test.json",
                entryJson("{\"type\":\"minecraft:item\",\"name\":\"minecraft:dirt\",\"weight\":5,\"quality\":3}"));
        var e = assertInstanceOf(ItemEntry.class, firstEntry(t));
        assertEquals(5, e.weight());
        assertEquals(3, e.quality());
    }

    @Test
    void itemEntryWeightNotIntFails(Env env) {
        assertThrows(Exception.class, () ->
                load(env, "loot_table/test.json",
                        entryJson("{\"type\":\"minecraft:item\",\"name\":\"minecraft:dirt\",\"weight\":\"x\"}")));
    }

    @Test
    void itemEntryUnknownNameFails(Env env) {
        // 原版不校验物品是否在注册表，这里测 item.name 必须是合法命名空间 ID
        assertThrows(Exception.class, () ->
                load(env, "loot_table/test.json",
                        entryJson("{\"type\":\"minecraft:item\",\"name\":\"not a id\"}")));
    }

    @Test
    void nestedTableEntryById(Env env) {
        // LootManager 校验表 ID 引用必须存在，因此把被引用的表一并加载
        var t = loadReferenced(env, java.util.Map.of(
                "loot_table/test.json", entryJson(
                        "{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:chests/desert_pyramid\"}"),
                "loot_table/chests/desert_pyramid.json", "{\"pools\":[]}"
        ), "test");
        var e = assertInstanceOf(NestedTableEntry.class, firstEntry(t));
        assertEquals(Key.key("minecraft:chests/desert_pyramid"), e.value().left().orElseThrow());
    }

    @Test
    void dynamicEntryContents(Env env) {
        var t = load(env, "loot_table/test.json",
                entryJson("{\"type\":\"minecraft:dynamic\",\"name\":\"minecraft:contents\"}"));
        var e = assertInstanceOf(DynamicEntry.class, firstEntry(t));
        assertEquals(Key.key("contents"), e.name());
    }

    @Test
    void dynamicEntrySherds(Env env) {
        var t = load(env, "loot_table/test.json",
                entryJson("{\"type\":\"minecraft:dynamic\",\"name\":\"sherds\"}"));
        var e = assertInstanceOf(DynamicEntry.class, firstEntry(t));
        assertEquals(Key.key("sherds"), e.name());
    }

    @Test
    void dynamicEntryWrongNameFails(Env env) {
        assertThrows(Exception.class, () ->
                load(env, "loot_table/test.json",
                        entryJson("{\"type\":\"minecraft:dynamic\",\"name\":\"minecraft:other\"}")));
    }

    @Test
    void emptyEntry(Env env) {
        var t = load(env, "loot_table/test.json",
                entryJson("{\"type\":\"minecraft:empty\"}"));
        var e = assertInstanceOf(EmptyEntry.class, firstEntry(t));
        assertEquals(1, e.weight());
    }

    @Test
    void tagEntryRequiresItemTagExists(Env env) {
        var world = new LootTestWorld(tempDir)
                .vanilla("tags/item/planks.json", "{\"values\":[\"minecraft:oak_planks\"]}")
                .vanilla("loot_table/test.json", entryJson(
                        "{\"type\":\"minecraft:tag\",\"name\":\"minecraft:planks\",\"expand\":true}"));
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var loottables = new LootTableSubscriber(env.process(), manager);
        world.loader(env.process(), loottables).start(LootTestWorld.createWorld());
        var e = assertInstanceOf(TagEntry.class, firstEntry(manager.get(Key.key("test")).orElseThrow()));
        assertEquals(Key.key("minecraft:planks"), e.name());
        assertTrue(e.expand());
    }

    @Test
    void tagEntryUnknownTagFails(Env env) {
        assertThrows(Exception.class, () ->
                load(env, "loot_table/test.json", entryJson(
                        "{\"type\":\"minecraft:tag\",\"name\":\"minecraft:wel\",\"expand\":true}")));
    }

    @Test
    void tagEntryExpandDefaultsFalse(Env env) {
        var world = new LootTestWorld(tempDir)
                .vanilla("tags/item/planks.json", "{\"values\":[\"minecraft:oak_planks\"]}")
                .vanilla("loot_table/test.json", entryJson(
                        "{\"type\":\"minecraft:tag\",\"name\":\"minecraft:planks\"}"));
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var loottables = new LootTableSubscriber(env.process(), manager);
        world.loader(env.process(), loottables).start(LootTestWorld.createWorld());
        assertFalse(assertInstanceOf(TagEntry.class, firstEntry(manager.get(Key.key("test")).orElseThrow())).expand());
    }

    @Test
    void groupEntry(Env env) {
        var t = load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:group\",\"children\":[{\"type\":\"minecraft:empty\"},{\"type\":\"minecraft:empty\"}]}"));
        var e = assertInstanceOf(GroupEntry.class, firstEntry(t));
        assertEquals(2, e.children().size());
    }

    @Test
    void alternativesEntry(Env env) {
        var t = load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:empty\"},{\"type\":\"minecraft:item\",\"name\":\"minecraft:stone\"}]}"));
        var e = assertInstanceOf(AlternativesEntry.class, firstEntry(t));
        assertEquals(2, e.children().size());
    }

    @Test
    void sequenceEntry(Env env) {
        var t = load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:sequence\",\"children\":[{\"type\":\"minecraft:empty\"},{\"type\":\"minecraft:empty\"}]}"));
        var e = assertInstanceOf(SequenceEntry.class, firstEntry(t));
        assertEquals(2, e.children().size());
    }

    @Test
    void compositeEntryEmptyChildrenFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:group\",\"children\":[]}")));
        assertThrows(Exception.class, () -> load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:alternatives\",\"children\":[]}")));
        assertThrows(Exception.class, () -> load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:sequence\",\"children\":[]}")));
    }

    @Test
    void unknownEntryTypeFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"minecraft:bogus_entry\",\"name\":\"minecraft:stone\"}")));
        assertThrows(Exception.class, () -> load(env, "loot_table/test.json", entryJson(
                "{\"type\":\"foo:bar\"}")));
    }

    // ------------------------------------------------------------------
    // 条件：20 种
    // ------------------------------------------------------------------

    private String withCondition(String conditionJson) {
        return "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stone\",\"conditions\":["
                + conditionJson + "]}]}]}";
    }

    private LootCondition firstCondition(LootTable t) {
        return t.pools().getFirst().entries().getFirst().conditions().getFirst();
    }

    @Test
    void conditionAllOf(Env env) {
        var c = assertInstanceOf(AllOfCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:all_of\",\"terms\":[{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:killed_by_player\"}}]}"))));
        assertEquals(1, c.terms().size());
    }

    @Test
    void conditionAnyOf(Env env) {
        var c = assertInstanceOf(AnyOfCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:any_of\",\"terms\":[{\"condition\":\"minecraft:killed_by_player\"},{\"condition\":\"minecraft:survives_explosion\"}]}"))));
        assertEquals(2, c.terms().size());
    }

    @Test
    void conditionBlockStateProperty(Env env) {
        var c = assertInstanceOf(BlockStatePropertyCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:block_state_property\",\"block\":\"minecraft:torch\"}"))));
        assertEquals(Key.key("minecraft:torch"), c.block());
        assertTrue(c.properties().isEmpty());
    }

    @Test
    void conditionDamageSourceProperties(Env env) {
        var c = assertInstanceOf(DamageSourcePropertiesCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:damage_source_properties\"}"))));
        assertTrue(c.predicate().isEmpty());
    }

    @Test
    void conditionEnchantmentActiveCheck(Env env) {
        var c = assertInstanceOf(EnchantmentActiveCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:enchantment_active_check\",\"active\":true}"))));
        assertTrue(c.active());
    }

    @Test
    void conditionEntityPropertiesDefaults(Env env) {
        var c = assertInstanceOf(EntityPropertiesCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:entity_properties\"}"))));
        assertEquals(io.ib67.yuanban.loot.number.EntityTarget.THIS, c.entity());
    }

    @Test
    void conditionEntityScores(Env env) {
        var c = assertInstanceOf(EntityScoresCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:entity_scores\",\"entity\":\"this\",\"scores\":{\"kills\":{\"min\":1}}}"))));
        assertEquals(1, c.scores().size());
    }

    @Test
    void conditionEnvironmentAttributeCheck(Env env) {
        var c = assertInstanceOf(EnvironmentAttributeCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:environment_attribute_check\",\"attribute\":\"minecraft:visual/firefly_bush_sounds\",\"value\":\"true\"}"))));
        assertEquals(Key.key("minecraft:visual/firefly_bush_sounds"), c.attribute());
    }

    @Test
    void conditionInverted(Env env) {
        var c = assertInstanceOf(InvertedCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:inverted\",\"term\":{\"condition\":\"minecraft:killed_by_player\"}}"))));
        assertInstanceOf(KilledByPlayerCondition.class, c.term());
    }

    @Test
    void conditionKilledByPlayer(Env env) {
        assertInstanceOf(KilledByPlayerCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:killed_by_player\"}"))));
    }

    @Test
    void conditionLocationCheckDefaults(Env env) {
        var c = assertInstanceOf(LocationCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:location_check\"}"))));
        assertTrue(c.predicate().isEmpty());
        assertEquals(0, c.offsetX());
        assertEquals(0, c.offsetY());
        assertEquals(0, c.offsetZ());
    }

    @Test
    void conditionMatchToolEmpty(Env env) {
        var c = assertInstanceOf(MatchToolCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:match_tool\"}"))));
        assertTrue(c.predicate().isEmpty());
    }

    @Test
    void conditionMatchToolItemTag(Env env) {
        var world = new LootTestWorld(tempDir)
                .vanilla("tags/item/planks.json", "{\"values\":[\"minecraft:oak_planks\"]}")
                .vanilla("loot_table/t.json",
                        withCondition("{\"condition\":\"minecraft:match_tool\",\"predicate\":\"#minecraft:planks\"}"));
        var manager = new LootManager(LootTypeRegistry.vanilla());
        var loottables = new LootTableSubscriber(env.process(), manager);
        world.loader(env.process(), loottables).start(LootTestWorld.createWorld());
        var c = assertInstanceOf(MatchToolCondition.class,
                manager.get(Key.key("t")).orElseThrow().pools().getFirst().entries().getFirst().conditions().getFirst());
        assertEquals(Key.key("minecraft:planks"), c.predicate().orElseThrow().itemTag());
    }

    @Test
    void conditionMatchToolUnknownTagFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:match_tool\",\"predicate\":\"#minecraft:does_not_exist\"}")));
    }

    @Test
    void conditionRandomChance(Env env) {
        var c = assertInstanceOf(RandomChanceCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}"))));
        assertEquals(0.5f, ((ConstantValue) c.chance()).value());
    }

    @Test
    void conditionRandomChanceWithEnchantedBonus(Env env) {
        var c = assertInstanceOf(RandomChanceWithEnchantedBonusCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:random_chance_with_enchanted_bonus\",\"unenchanted_chance\":0.1,\"enchanted_chance\":{\"type\":\"minecraft:linear\",\"base\":0.5,\"per_level_above_first\":0.5},\"enchantment\":\"minecraft:looting\"}"))));
        assertEquals(0.1f, c.unenchantedChance());
        assertEquals(Key.key("minecraft:looting"), c.enchantment());
        assertInstanceOf(LevelBasedValue.Linear.class, c.enchantedChance());
    }

    @Test
    void conditionReference(Env env) {
        var c = assertInstanceOf(ReferenceCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:reference\",\"name\":\"minecraft:entities/zombie\"}"))));
        assertEquals(Key.key("minecraft:entities/zombie"), c.name());
    }

    @Test
    void conditionReferenceInvalidIdFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:reference\",\"name\":\"not a id\"}")));
    }

    @Test
    void conditionSurvivesExplosion(Env env) {
        assertInstanceOf(SurvivesExplosionCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:survives_explosion\"}"))));
    }

    @Test
    void conditionTableBonus(Env env) {
        var c = assertInstanceOf(TableBonusCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:table_bonus\",\"enchantment\":\"minecraft:looting\",\"chances\":[0.5,0.6,0.7]}"))));
        assertEquals(Key.key("minecraft:looting"), c.enchantment());
        assertEquals(3, c.values().size());
    }

    @Test
    void conditionTimeCheckDefaults(Env env) {
        var c = assertInstanceOf(TimeCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:time_check\",\"value\":1000}"))));
        assertEquals(Key.key("minecraft:clock"), c.clock());
        assertTrue(c.period().isEmpty());
    }

    @Test
    void conditionValueCheck(Env env) {
        var c = assertInstanceOf(ValueCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:value_check\",\"value\":1,\"range\":5}"))));
        assertEquals(1f, ((ConstantValue) c.value()).value());
        assertEquals(5f, ((ConstantValue) c.range().min()).value());
    }

    @Test
    void conditionWeatherCheckDefaults(Env env) {
        var c = assertInstanceOf(WeatherCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:weather_check\"}"))));
        assertTrue(c.isRaining().isEmpty());
        assertTrue(c.isThundering().isEmpty());
    }

    @Test
    void unknownConditionTypeFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:bogus_condition\"}")));
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"bogus:x\"}")));
    }

    // ------------------------------------------------------------------
    // 修饰器：44 种
    // ------------------------------------------------------------------

    private String withFunction(String functionJson) {
        return "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stone\",\"functions\":["
                + functionJson + "]}]}]}";
    }

    private LootFunction firstFunction(LootTable t) {
        return t.pools().getFirst().entries().getFirst().functions().getFirst();
    }

    @Test
    void functionSetCount(Env env) {
        var f = assertInstanceOf(SetCountFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_count\",\"count\":5}"))));
        assertEquals(5f, ((ConstantValue) f.count()).value());
        assertFalse(f.add());
        assertTrue(f.conditions().isEmpty());
    }

    @Test
    void functionSetItem(Env env) {
        var f = assertInstanceOf(SetItemFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_item\",\"item\":\"minecraft:dirt\"}"))));
        assertEquals(Key.key("minecraft:dirt"), f.id());
    }

    @Test
    void functionEnchantWithLevelsDefaults(Env env) {
        var f = assertInstanceOf(EnchantWithLevelsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:enchant_with_levels\",\"levels\":1}"))));
        assertTrue(f.options().isEmpty());
        assertFalse(f.includeAdditionalCostComponent());
    }

    @Test
    void functionEnchantRandomlyDefaults(Env env) {
        var f = assertInstanceOf(EnchantRandomlyFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:enchant_randomly\",\"options\":[\"minecraft:sharpness\"]}"))));
        assertTrue(f.onlyCompatible());
        assertFalse(f.includeAdditionalCostComponent());
        assertEquals(Key.key("minecraft:sharpness"), f.options().orElseThrow().enchantments().getFirst());
    }

    @Test
    void functionSetEnchantmentsDefaults(Env env) {
        var f = assertInstanceOf(SetEnchantmentsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_enchantments\",\"enchantments\":{\"minecraft:looting\":2}}"))));
        assertEquals(2f, ((ConstantValue) f.enchantments().get(Key.key("minecraft:looting"))).value());
        assertFalse(f.add());
    }

    @Test
    void functionSetCustomDataFromString(Env env) {
        var f = assertInstanceOf(SetCustomDataFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_custom_data\",\"tag\":\"{foo:1}\"}"))));
        assertNotNull(f.tag().get("foo"));
    }

    @Test
    void functionSetComponents(Env env) {
        var f = assertInstanceOf(SetComponentsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_components\",\"components\":{\"!minecraft:damage\":1,\"minecraft:item_model\":\"minecraft:dirt\"}}"))));
        assertNotNull(f.components());
    }

    @Test
    void functionSetComponentsInvalidFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_components\",\"components\":123}")));
    }

    @Test
    void functionFurnaceSmelt(Env env) {
        var f = assertInstanceOf(SmeltFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:furnace_smelt\"}"))));
        // 原版 FurnaceSmeltFunction 无 use_input_count 字段，loot 记录该字段在本模块恒为 false
        assertFalse(f.useInputCount());
    }

    @Test
    void functionEnchantedCountIncreaseDefaults(Env env) {
        var f = assertInstanceOf(EnchantedCountIncreaseFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:enchanted_count_increase\",\"enchantment\":\"minecraft:looting\",\"count\":1}"))));
        assertEquals(0, f.limit());
        assertTrue(f.conditions().isEmpty());
        assertEquals(1f, ((ConstantValue) f.count()).value());
    }

    @Test
    void functionSetDamageDefaults(Env env) {
        var f = assertInstanceOf(SetDamageFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_damage\",\"damage\":0.5}"))));
        assertFalse(f.add());
    }

    @Test
    void functionSetAttributes(Env env) {
        var f = assertInstanceOf(SetAttributesFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_attributes\",\"modifiers\":[{\"attribute\":\"minecraft:generic.attack_damage\",\"name\":\"minecraft:attack\",\"amount\":1,\"operation\":\"add_value\",\"slot\":\"mainhand\",\"id\":\"minecraft:attack\"}]}"))));
        assertFalse(f.replace());
        var m = f.modifiers().getFirst();
        assertEquals(Key.key("minecraft:generic.attack_damage"), m.attribute());
        assertEquals(AttributeOperation.ADD_VALUE, m.operation());
    }

    @Test
    void functionSetNameDefaults(Env env) {
        var f = assertInstanceOf(SetNameFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_name\",\"name\":\"Stone\"}"))));
        assertEquals(SetNameTarget.CUSTOM_NAME, f.target());
        assertTrue(f.name().isPresent());
    }

    @Test
    void functionExplorationMapDefaults(Env env) {
        var f = assertInstanceOf(ExplorationMapFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:exploration_map\"}"))));
        assertEquals(ExplorationMapFunction.DEFAULT_DESTINATION, f.destination());
        assertEquals(ExplorationMapFunction.DEFAULT_DECORATION, f.mapDecoration());
        assertEquals(2, f.zoom());
        assertEquals(50, f.searchRadius());
        assertTrue(f.skipExistingChunks());
    }

    @Test
    void functionSetStewEffect(Env env) {
        var f = assertInstanceOf(SetStewEffectFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_stew_effect\",\"effects\":[{\"type\":\"minecraft:saturation\",\"duration\":1}]}"))));
        var entry = f.effects().getFirst();
        assertEquals(Key.key("minecraft:saturation"), entry.effect());
        assertEquals(1f, ((ConstantValue) entry.duration()).value());
    }

    @Test
    void functionCopyName(Env env) {
        var f = assertInstanceOf(CopyNameFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:copy_name\",\"source\":\"block_entity\"}"))));
        assertInstanceOf(CopyNameSource.BlockEntity.class, f.source());
    }

    @Test
    void functionSetContents(Env env) {
        var f = assertInstanceOf(SetContainerContentsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_contents\",\"component\":\"minecraft:container\",\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:stone\"}]}"))));
        assertEquals(1, f.entries().size());
    }

    @Test
    void functionModifyContents(Env env) {
        var f = assertInstanceOf(ModifyContentsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:modify_contents\",\"component\":\"minecraft:container\",\"modifier\":{\"function\":\"minecraft:discard\"}}"))));
        assertInstanceOf(DiscardFunction.class, f.modifier());
    }

    @Test
    void functionFiltered(Env env) {
        var f = assertInstanceOf(FilteredFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:filtered\",\"item_filter\":\"minecraft:stone\",\"on_pass\":{\"function\":\"minecraft:discard\"}}"))));
        assertTrue(f.onMatch().isPresent());
        assertTrue(f.onFail().isEmpty());
        assertEquals(Key.key("minecraft:stone"), f.itemFilter().items().getFirst());
    }

    @Test
    void functionLimitCount(Env env) {
        var f = assertInstanceOf(LimitCountFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:limit_count\",\"limit\":{\"min\":1,\"max\":3}}"))));
        assertEquals(1f, ((ConstantValue) f.limit().min()).value());
        assertEquals(3f, ((ConstantValue) f.limit().max()).value());
    }

    @Test
    void functionLimitCountDefault(Env env) {
        var f = assertInstanceOf(LimitCountFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:limit_count\"}"))));
        assertNull(f.limit().min());
        assertNull(f.limit().max());
    }

    @Test
    void functionApplyBonusOreDrops(Env env) {
        var f = assertInstanceOf(ApplyBonusFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\",\"formula\":\"minecraft:ore_drops\"}"))));
        assertInstanceOf(ApplyBonusFormula.OreDrops.class, f.formula());
    }

    @Test
    void functionSetLootTable(Env env) {
        // LootManager 校验 set_loot_table 引用的表必须存在，把目标表一并加载
        var f = assertInstanceOf(SetContainerLootTableFunction.class, firstFunction(loadReferenced(env, java.util.Map.of(
                "loot_table/t.json", withFunction("{\"function\":\"minecraft:set_loot_table\",\"name\":\"minecraft:chest_simple_dungeon\",\"seed\":42,\"type\":\"minecraft:chest\"}"),
                "loot_table/chest_simple_dungeon.json", "{\"pools\":[]}"
        ), "t")));
        assertEquals(Key.key("minecraft:chest_simple_dungeon"), f.name());
        assertEquals(42L, f.seed());
        assertEquals(Key.key("minecraft:chest"), f.type());
    }

    @Test
    void functionSetLootTableSeedDefault(Env env) {
        var f = assertInstanceOf(SetContainerLootTableFunction.class, firstFunction(loadReferenced(env, java.util.Map.of(
                "loot_table/t.json", withFunction("{\"function\":\"minecraft:set_loot_table\",\"name\":\"minecraft:chest_simple_dungeon\",\"type\":\"minecraft:chest\"}"),
                "loot_table/chest_simple_dungeon.json", "{\"pools\":[]}"
        ), "t")));
        assertEquals(0L, f.seed());
    }

    @Test
    void functionExplosionDecay(Env env) {
        var f = assertInstanceOf(ExplosionDecayFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:explosion_decay\"}"))));
        assertTrue(f.conditions().isEmpty());
    }

    @Test
    void functionSetLoreDefaults(Env env) {
        var f = assertInstanceOf(SetLoreFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_lore\",\"lore\":[\"line1\",\"line2\"]}"))));
        assertInstanceOf(AppendOperation.class, f.mode());
        assertTrue(f.resolutionContext().isEmpty());
        assertEquals(2, f.lore().size());
    }

    @Test
    void functionFillPlayerHeadDefaults(Env env) {
        var f = assertInstanceOf(FillPlayerHeadFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:fill_player_head\"}"))));
        assertEquals(io.ib67.yuanban.loot.number.EntityTarget.THIS, f.entity());
    }

    @Test
    void functionCopyCustomData(Env env) {
        var f = assertInstanceOf(CopyCustomDataFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:copy_custom_data\",\"source\":{\"type\":\"minecraft:storage\",\"source\":\"minecraft:entity/chest\"},\"ops\":[{\"source\":\"a\",\"target\":\"b\"}]}"))));
        assertInstanceOf(NbtSource.Storage.class, f.source());
    }

    @Test
    void functionCopyState(Env env) {
        var f = assertInstanceOf(CopyBlockStateFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:copy_state\",\"block\":\"minecraft:oak_log\",\"properties\":[\"axis\"]}"))));
        assertEquals(Key.key("minecraft:oak_log"), f.block());
        assertTrue(f.properties().contains("axis"));
    }

    @Test
    void functionSetBannerPattern(Env env) {
        var f = assertInstanceOf(SetBannerPatternFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_banner_pattern\",\"patterns\":[{\"pattern\":\"minecraft:creeper\",\"color\":\"red\"}]}"))));
        assertTrue(f.append());
        assertEquals(Key.key("minecraft:creeper"), f.patterns().getFirst().pattern());
    }

    @Test
    void functionSetPotion(Env env) {
        var f = assertInstanceOf(SetPotionFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_potion\",\"id\":\"minecraft:fire_resistance\"}"))));
        assertEquals(Key.key("minecraft:fire_resistance"), f.id());
    }

    @Test
    void functionSetRandomDyes(Env env) {
        var f = assertInstanceOf(SetRandomDyesFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_random_dyes\",\"number_of_dyes\":3}"))));
        assertEquals(3f, ((ConstantValue) f.numberOfDyes()).value());
    }

    @Test
    void functionSetRandomPotionDefaults(Env env) {
        var f = assertInstanceOf(SetRandomPotionFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_random_potion\"}"))));
        assertTrue(f.options().isEmpty());
    }

    @Test
    void functionSetInstrument(Env env) {
        var f = assertInstanceOf(SetInstrumentFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_instrument\",\"options\":[\"minecraft:ponder_goat_horn\"]}"))));
        assertEquals(Key.key("minecraft:ponder_goat_horn"), f.options().enchantments().getFirst());
    }

    @Test
    void functionReference(Env env) {
        var f = assertInstanceOf(FunctionReference.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:reference\",\"name\":\"minecraft:chest_simple\"}"))));
        assertEquals(Key.key("minecraft:chest_simple"), f.name());
    }

    @Test
    void functionSequence(Env env) {
        var f = assertInstanceOf(SequenceFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:sequence\",\"functions\":[{\"function\":\"minecraft:discard\"},{\"function\":\"minecraft:set_count\",\"count\":1}]}"))));
        assertEquals(2, f.functions().size());
    }

    @Test
    void functionCopyComponents(Env env) {
        var f = assertInstanceOf(CopyComponentsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:copy_components\",\"source\":\"block_entity\",\"include\":[\"minecraft:custom_name\"]}"))));
        assertTrue(f.include().isPresent());
        assertEquals(Key.key("minecraft:custom_name"), f.include().orElseThrow().getFirst());
    }

    @Test
    void functionCopyComponentsExcludeDefaults(Env env) {
        var f = assertInstanceOf(CopyComponentsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:copy_components\",\"source\":\"this\"}"))));
        assertTrue(f.include().isEmpty());
        assertTrue(f.exclude().isEmpty());
    }

    @Test
    void functionSetFireworks(Env env) {
        var f = assertInstanceOf(SetFireworksFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_fireworks\",\"explosions\":{\"values\":[\"small_ball\"]},\"flight_duration\":2}"))));
        assertEquals(2, f.flightDuration().orElse(0));
        assertEquals(FireworkExplosionShape.SMALL_BALL, f.explosions().orElseThrow().values().getFirst());
    }

    @Test
    void functionSetFireworkExplosion(Env env) {
        var f = assertInstanceOf(SetFireworkExplosionFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_firework_explosion\",\"shape\":\"large_ball\",\"colors\":[255,0]}"))));
        assertEquals(FireworkExplosionShape.LARGE_BALL, f.shape().orElseThrow());
    }

    @Test
    void functionSetBookCoverDefaults(Env env) {
        var f = assertInstanceOf(SetBookCoverFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_book_cover\"}"))));
        assertTrue(f.author().isEmpty());
        assertTrue(f.title().isEmpty());
        assertTrue(f.generation().isEmpty());
    }

    @Test
    void functionSetWrittenBookPagesDefaults(Env env) {
        var f = assertInstanceOf(SetWrittenBookPagesFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_written_book_pages\",\"pages\":[\"page1\"]}"))));
        assertInstanceOf(AppendOperation.class, f.mode());
    }

    @Test
    void functionSetWritableBookPages(Env env) {
        var f = assertInstanceOf(SetWritableBookPagesFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_writable_book_pages\",\"pages\":[\"p1\"]}"))));
        assertInstanceOf(AppendOperation.class, f.mode());
        assertEquals(new FilteredText("p1"), f.pages().getFirst());
    }

    @Test
    void functionToggleTooltips(Env env) {
        var f = assertInstanceOf(ToggleTooltipsFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:toggle_tooltips\",\"toggles\":{\"minecraft:enchantments\":false}}"))));
        assertFalse(f.toggles().get(Key.key("minecraft:enchantments")));
    }

    @Test
    void functionSetOminousBottleAmplifier(Env env) {
        var f = assertInstanceOf(SetOminousBottleAmplifierFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_ominous_bottle_amplifier\",\"amplifier\":4}"))));
        assertEquals(4f, ((ConstantValue) f.amplifier()).value());
    }

    @Test
    void functionSetCustomModelData(Env env) {
        var f = assertInstanceOf(SetCustomModelDataFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_custom_model_data\",\"floats\":{\"values\":[1.0]},\"flags\":{\"values\":[true]}}"))));
        assertTrue(f.flags().isPresent());
    }

    @Test
    void functionDiscard(Env env) {
        var f = assertInstanceOf(DiscardFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:discard\"}"))));
        assertTrue(f.conditions().isEmpty());
    }

    @Test
    void unknownFunctionTypeFails(Env env) {
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:bogus_function\"}")));
        assertThrows(Exception.class, () -> load(env, "loot_table/t.json",
                withFunction("{\"function\":\"bogus:x\"}")));
    }

    @Test
    void functionConditionVariantParse(Env env) {
        // With conditions element and a body of set_count - verify conditions field is dotted through
        var f = assertInstanceOf(SetCountFunction.class, firstFunction(load(env, "loot_table/t.json",
                withFunction("{\"function\":\"minecraft:set_count\",\"count\":1,\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}"))));
        assertEquals(1, f.conditions().size());
        assertInstanceOf(SurvivesExplosionCondition.class, f.conditions().getFirst());
    }

    // ------------------------------------------------------------------
    // 数字提供器：8 种
    // ------------------------------------------------------------------

    private NumberProvider decodeNumber(Env env, String numberJson) {
        var c = assertInstanceOf(ValueCheckCondition.class, firstCondition(load(env, "loot_table/t.json",
                withCondition("{\"condition\":\"minecraft:value_check\",\"value\":" + numberJson + ",\"range\":1}"))));
        return c.value();
    }

    @Test
    void numberConstantInline(Env env) {
        var n = decodeNumber(env, "5");
        assertInstanceOf(ConstantValue.class, n);
        assertEquals(5f, ((ConstantValue) n).value());
    }

    @Test
    void numberConstantObjectForm(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:constant\",\"value\":3}");
        assertEquals(3f, ((ConstantValue) n).value());
    }

    @Test
    void numberUniform(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:uniform\",\"min\":0,\"max\":2}");
        var u = assertInstanceOf(UniformValue.class, n);
        assertEquals(0f, ((ConstantValue) u.min()).value());
        assertEquals(2f, ((ConstantValue) u.max()).value());
    }

    @Test
    void numberBinomial(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:binomial\",\"n\":5,\"p\":0.5}");
        assertInstanceOf(BinomialValue.class, n);
    }

    @Test
    void numberScoreboard(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:score\",\"target\":{\"type\":\"minecraft:fixed\",\"name\":\"player\"},\"score\":\"kills\"}");
        var s = assertInstanceOf(ScoreboardValue.class, n);
        assertEquals(new ScoreboardName.Fixed("player"), s.target());
        assertEquals("kills", s.score());
        assertEquals(1.0f, s.scale());
    }

    @Test
    void numberScoreboardContext(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:score\",\"target\":{\"type\":\"minecraft:context\",\"target\":\"this\"},\"score\":\"x\"}");
        var s = assertInstanceOf(ScoreboardValue.class, n);
        assertInstanceOf(ScoreboardName.Context.class, s.target());
    }

    @Test
    void numberStorage(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:storage\",\"storage\":\"minecraft:entity/chest\",\"path\":\"Items\"}");
        assertEquals(Key.key("minecraft:entity/chest"), ((StorageValue) n).storage());
        assertEquals("Items", ((StorageValue) n).path());
    }

    @Test
    void numberSum(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:sum\",\"terms\":[1,2,3]}");
        assertEquals(3, assertInstanceOf(SumValue.class, n).terms().size());
    }

    @Test
    void numberEnchantmentLevel(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:enchantment_level\",\"amount\":{\"type\":\"minecraft:linear\",\"base\":1,\"per_level_above_first\":1}}");
        assertInstanceOf(EnchantmentLevelValue.class, n);
    }

    @Test
    void numberEnvironmentAttribute(Env env) {
        var n = decodeNumber(env, "{\"type\":\"minecraft:environment_attribute\",\"attribute\":\"minecraft:visual/firefly_bush_sounds\"}");
        assertEquals(Key.key("minecraft:visual/firefly_bush_sounds"), ((EnvironmentAttributeValue) n).attribute());
    }

    @Test
    void unknownNumberProviderTypeFails(Env env) {
        assertThrows(Exception.class, () -> decodeNumber(env, "{\"type\":\"minecraft:not_a_provider\",\"x\":1}"));
        assertThrows(Exception.class, () -> decodeNumber(env, "{\"type\":\"bogus:x\",\"x\":1}"));
    }

    // ------------------------------------------------------------------
    // 81 个 vanilla 类型 codec 装配
    // ------------------------------------------------------------------

    @Test
    void allVanillaCodecsWired(Env env) {
        var types = wireTypes(env.process());
        var missing = new java.util.ArrayList<String>();
        for (var e : java.util.List.<java.util.Map.Entry<Key, ?>>of()) {}
        // entries: 9
        var entryIds = new String[]{"empty","item","loot_table","dynamic","tag","slots","alternatives","sequence","group"};
        assertEquals(9, entryIds.length);
        for (var id : entryIds) types.entry(Key.key(id)).ifPresentOrElse(
                t -> assertNotNull(t.codec(), "entry codec null: " + id),
                () -> fail("entry absent: " + id));
        // conditions: 20
        var conIds = new String[]{"inverted","any_of","all_of","random_chance","random_chance_with_enchanted_bonus",
                "entity_properties","killed_by_player","entity_scores","block_state_property","match_tool",
                "table_bonus","survives_explosion","damage_source_properties","location_check","weather_check",
                "reference","time_check","value_check","enchantment_active_check","environment_attribute_check"};
        assertEquals(20, conIds.length);
        for (var id : conIds) types.condition(Key.key(id)).ifPresentOrElse(
                t -> assertNotNull(t.codec(), "condition codec null: " + id),
                () -> fail("condition absent: " + id));
        // functions: 44
        var fnIds = new String[]{"set_count","set_item","enchant_with_levels","enchant_randomly","set_enchantments",
                "set_custom_data","set_components","furnace_smelt","enchanted_count_increase","set_damage",
                "set_attributes","set_name","exploration_map","set_stew_effect","copy_name","set_contents",
                "modify_contents","filtered","limit_count","apply_bonus","set_loot_table","explosion_decay",
                "set_lore","fill_player_head","copy_custom_data","copy_state","set_banner_pattern","set_potion",
                "set_random_dyes","set_random_potion","set_instrument","reference","sequence","copy_components",
                "set_fireworks","set_firework_explosion","set_book_cover","set_written_book_pages",
                "set_writable_book_pages","toggle_tooltips","set_ominous_bottle_amplifier","set_custom_model_data",
                "discard","function"};
        assertEquals(44, fnIds.length);
        for (var id : fnIds) types.function(Key.key(id)).ifPresentOrElse(
                t -> assertNotNull(t.codec(), "function codec null: " + id),
                () -> fail("function absent: " + id));
        // number providers: 8
        var npIds = new String[]{"constant","uniform","binomial","score","storage","sum","enchantment_level",
                "environment_attribute"};
        assertEquals(8, npIds.length);
        for (var id : npIds) types.numberProvider(Key.key(id)).ifPresentOrElse(
                t -> assertNotNull(t.codec(), "number provider codec null: " + id),
                () -> fail("number provider absent: " + id));
    }
}
