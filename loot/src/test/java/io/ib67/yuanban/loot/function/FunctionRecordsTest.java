package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.component.DataComponentMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loot.function 包的 44 个修饰器 record 的最小行为与缺省值。
 */
class FunctionRecordsTest {

    private static final LootCondition COND = KilledByPlayerCondition.INSTANCE;
    private static final java.util.List<LootCondition> NO_CONDITIONS = java.util.List.of();

    @Test
    void setCountDefaults() {
        var function = new SetCountFunction(NO_CONDITIONS, new ConstantValue(3));
        assertEquals(new ConstantValue(3), function.count());
        assertFalse(function.add());
    }

    @Test
    void setCountWithConditionsAndAdd() {
        var function = new SetCountFunction(List.of(COND), new ConstantValue(3), true);
        assertEquals(List.of(COND), function.conditions());
        assertTrue(function.add());
    }

    @Test
    void setDamageDefaults() {
        var function = new SetDamageFunction(NO_CONDITIONS, new ConstantValue(0.5f));
        assertEquals(new ConstantValue(0.5f), function.damage());
        assertFalse(function.add());
    }

    @Test
    void setComponents() {
        var patch = DataComponentMap.EMPTY;
        var function = new SetComponentsFunction(NO_CONDITIONS, patch);
        assertEquals(patch, function.components());
    }

    @Test
    void setItem() {
        var function = new SetItemFunction(NO_CONDITIONS, Key.key("minecraft:diamond"));
        assertEquals(Key.key("minecraft:diamond"), function.id());
    }

    @Test
    void setName() {
        var name = Component.text("hello");
        var function = new SetNameFunction(NO_CONDITIONS, name, EntityTarget.THIS, null);
        assertEquals(name, function.name().orElseThrow());
        assertEquals(EntityTarget.THIS, function.resolutionContext().orElseThrow());
        assertEquals(SetNameTarget.CUSTOM_NAME, function.target());
    }

    @Test
    void setNameTargetItemName() {
        var function = new SetNameFunction(NO_CONDITIONS, (Component) null, null, SetNameTarget.ITEM_NAME);
        assertTrue(function.name().isEmpty());
        assertTrue(function.resolutionContext().isEmpty());
        assertEquals(SetNameTarget.ITEM_NAME, function.target());
    }

    @Test
    void setLoreDefaultsMode() {
        List<Component> lore = List.of(Component.text("line"));
        var function = new SetLoreFunction(NO_CONDITIONS, lore);
        assertEquals(lore, function.lore());
        assertSame(AppendOperation.INSTANCE, function.mode());
    }

    @Test
    void applyBonus() {
        var function = new ApplyBonusFunction(NO_CONDITIONS, Key.key("minecraft:looting"),
                new ApplyBonusFormula.UniformBonusCount(2));
        assertEquals(Key.key("minecraft:looting"), function.enchantment());
        var formula = (ApplyBonusFormula.UniformBonusCount) function.formula();
        assertEquals(2, formula.bonusMultiplier());
    }

    @Test
    void applyBonusBinomial() {
        var function = new ApplyBonusFunction(NO_CONDITIONS, Key.key("minecraft:fortune"),
                new ApplyBonusFormula.BinomialWithBonusCount(2, 0.5f));
        var formula = (ApplyBonusFormula.BinomialWithBonusCount) function.formula();
        assertEquals(2, formula.extraRounds());
        assertEquals(0.5f, formula.probability());
    }

    @Test
    void applyBonusOreDrops() {
        var function = new ApplyBonusFunction(NO_CONDITIONS, Key.key("minecraft:fortune"),
                ApplyBonusFormula.OreDrops.INSTANCE);
        assertSame(ApplyBonusFormula.OreDrops.INSTANCE, function.formula());
    }

    @Test
    void enchantedCountIncreaseDefaultLimit() {
        var function = new EnchantedCountIncreaseFunction(NO_CONDITIONS,
                Key.key("minecraft:looting"), new ConstantValue(1));
        assertEquals(0, function.limit());
        assertEqualFive(function.enchantment(), "minecraft:looting");
    }

    private static void assertEqualFive(Object actual, String expected) {
        assertEquals(Key.key(expected), actual);
    }

    @Test
    void enchantRandomly() {
        var options = EnchantedOptions.ofTag(Key.key("minecraft:tradeable"));
        var function = new EnchantRandomlyFunction(NO_CONDITIONS, options);
        assertEquals(options, function.options().orElseThrow());
        assertTrue(function.onlyCompatible());
        assertFalse(function.includeAdditionalCostComponent());
    }

    @Test
    void enchantWithLevels() {
        var function = new EnchantWithLevelsFunction(NO_CONDITIONS, new ConstantValue(30));
        assertEquals(new ConstantValue(30), function.levels());
        assertTrue(function.options().isEmpty());
        assertFalse(function.includeAdditionalCostComponent());
    }

    @Test
    void limitCount() {
        var function = new LimitCountFunction(NO_CONDITIONS,
                new io.ib67.yuanban.loot.number.IntRange(new ConstantValue(1), new ConstantValue(5)));
        assertEquals(1, ((ConstantValue) function.limit().min()).value());
    }

    @Test
    void smeltDefaults() {
        var function = new SmeltFunction();
        assertTrue(function.useInputCount());
    }

    @Test
    void explosionDecayIsEmpty() {
        var function = new ExplosionDecayFunction();
        assertTrue(function.conditions().isEmpty());
    }

    @Test
    void discardIsEmpty() {
        var function = new DiscardFunction();
        assertTrue(function.conditions().isEmpty());
    }

    @Test
    void copyComponentsOfBlockEntity() {
        var function = new CopyComponentsFunction(NO_CONDITIONS, CopyNameSource.BlockEntity.INSTANCE);
        assertSame(CopyNameSource.BlockEntity.INSTANCE, function.source());
        assertTrue(function.include().isEmpty());
        assertTrue(function.exclude().isEmpty());
    }

    @Test
    void copyComponentsWithInclude() {
        var include = List.of(Key.key("minecraft:custom_name"));
        var function = new CopyComponentsFunction(NO_CONDITIONS,
                new CopyNameSource.Entity(EntityTarget.THIS),
                Optional.of(include), Optional.empty());
        assertEquals(include, function.include().orElseThrow());
    }

    @Test
    void copyName() {
        var function = new CopyNameFunction(NO_CONDITIONS, new CopyNameSource.Entity(EntityTarget.ATTACKER));
        var source = (CopyNameSource.Entity) function.source();
        assertEquals(EntityTarget.ATTACKER, source.target());
    }

    @Test
    void copyState() {
        var function = new CopyBlockStateFunction(NO_CONDITIONS, Key.key("minecraft:grass_block"),
                java.util.Set.of("snowy"));
        assertEquals(Key.key("minecraft:grass_block"), function.block());
        assertEquals(java.util.Set.of("snowy"), function.properties());
    }

    @Test
    void setEnchantmentsDefaults() {
        var map = Map.of(Key.key("minecraft:sharpness"), (io.ib67.yuanban.loot.number.NumberProvider) new ConstantValue(5));
        var function = new SetEnchantmentsFunction(NO_CONDITIONS, map);
        assertFalse(function.add());
        assertEquals(map, function.enchantments());
    }

    @Test
    void setPotion() {
        var function = new SetPotionFunction(NO_CONDITIONS, Key.key("minecraft:fire_resistance"));
        assertEquals(Key.key("minecraft:fire_resistance"), function.id());
    }

    @Test
    void setStewEffect() {
        var effects = List.of(new StewEffectEntry(Key.key("minecraft:saturation"), new ConstantValue(7)));
        var function = new SetStewEffectFunction(NO_CONDITIONS, effects);
        assertEquals(effects, function.effects());
    }

    @Test
    void sequence() {
        var a = new SetItemFunction(NO_CONDITIONS, Key.key("minecraft:stone"));
        var b = new SetCountFunction(NO_CONDITIONS, new ConstantValue(3));
        var function = new SequenceFunction(NO_CONDITIONS, List.of(a, b));
        assertEquals(List.of(a, b), function.functions());
        assertEquals(List.of(a, b), function.children());
    }

    @Test
    void functionReference() {
        var function = new FunctionReference(NO_CONDITIONS, Key.key("minecraft:my_modifier"));
        assertEquals(Key.key("minecraft:my_modifier"), function.name());
    }

    @Test
    void filteredFunctionOptionalChildren() {
        var onMatch = new DiscardFunction();
        var onFail = new DiscardFunction();
        var function = new FilteredFunction(NO_CONDITIONS,
                new io.ib67.yuanban.loot.condition.ItemPredicate(
                        List.of(Key.key("minecraft:paper")), null),
                Optional.of(onMatch), Optional.of(onFail));
        assertEquals(onMatch, function.onMatch().orElseThrow());
        assertEquals(onFail, function.onFail().orElseThrow());
        assertEquals(List.of(onMatch, onFail), function.children());
    }

    @Test
    void toggleTooltips() {
        var toggles = Map.of(Key.key("minecraft:enchantments"), true);
        var function = new ToggleTooltipsFunction(NO_CONDITIONS, toggles);
        assertEquals(toggles, function.toggles());
    }

    @Test
    void modifyContents() {
        var modifier = new SetCountFunction(NO_CONDITIONS, new ConstantValue(2));
        var function = new ModifyContentsFunction(NO_CONDITIONS,
                Key.key("minecraft:container"), modifier);
        assertEquals(Key.key("minecraft:container"), function.component());
        assertEquals(List.of(modifier), function.children());
    }

    @Test
    void setContainerContents() {
        var entries = List.of((io.ib67.yuanban.loot.entry.LootEntry) new ItemEntry(Key.key("minecraft:stone")));
        var function = new SetContainerContentsFunction(NO_CONDITIONS,
                Key.key("minecraft:bundle_contents"), entries);
        assertEquals(entries, function.entries());
    }

    @Test
    void setContainerLootTableDefaults() {
        var function = new SetContainerLootTableFunction(NO_CONDITIONS,
                Key.key("minecraft:chest/simple_dungeon"), Key.key("minecraft:chest"));
        assertEquals(0L, function.seed());
        assertEquals(Key.key("minecraft:chest/simple_dungeon"), function.name());
        assertEquals(Key.key("minecraft:chest"), function.type());
    }

    @Test
    void copyCustomData() {
        var source = new NbtSource.Storage(Key.key("minecraft:loot/chest"));
        var ops = List.of(new CopyOperation("items", "Items", MergeStrategy.APPEND));
        var function = new CopyCustomDataFunction(NO_CONDITIONS, source, ops);
        assertEquals(source, function.source());
        assertEquals(ops, function.ops());
    }

    @Test
    void copyCustomDataMergestrategyDefault() {
        var op = new CopyOperation("a", "b");
        assertEquals(MergeStrategy.REPLACE, op.op());
    }

    @Test
    void setCustomData() {
        var tag = net.kyori.adventure.nbt.CompoundBinaryTag.empty();
        var function = new SetCustomDataFunction(NO_CONDITIONS, tag);
        assertEquals(tag, function.tag());
    }

    @Test
    void setCustomModelDataEmpty() {
        var function = SetCustomModelDataFunction.empty(NO_CONDITIONS);
        assertTrue(function.floats().isEmpty());
        assertTrue(function.flags().isEmpty());
        assertTrue(function.strings().isEmpty());
        assertTrue(function.colors().isEmpty());
    }

    @Test
    void setCustomModelDataFloats() {
        StandAloneOperation<io.ib67.yuanban.loot.number.NumberProvider> floats =
                new StandAloneOperation<>(
                        List.<io.ib67.yuanban.loot.number.NumberProvider>of(new ConstantValue(1.0f)),
                        AppendOperation.INSTANCE);
        var function = new SetCustomModelDataFunction(NO_CONDITIONS,
                Optional.of(floats), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(floats, function.floats().orElseThrow());
    }

    @Test
    void setAttributes() {
        var modifier = new AttributeModifierRecord(
                Key.key("minecraft:attack_damage_bonus"), Key.key("minecraft:attack_damage"),
                AttributeOperation.ADD_VALUE, new ConstantValue(2), List.of(EquipmentSlotGroup.MAIN_HAND));
        var function = new SetAttributesFunction(NO_CONDITIONS, List.of(modifier));
        assertEquals(List.of(modifier), function.modifiers());
        assertFalse(function.replace());
    }

    @Test
    void setBannerPatternAppend() {
        var layers = List.of(new BannerLayer(Key.key("minecraft:flower"), "red"));
        var function = new SetBannerPatternFunction(NO_CONDITIONS, layers);
        assertTrue(function.append());
        assertEquals(layers, function.patterns());
    }

    @Test
    void setBookCover() {
        var function = new SetBookCoverFunction(NO_CONDITIONS,
                "Author", new FilteredText("title"), 1);
        assertEquals("Author", function.author().orElseThrow());
        assertEquals("title", function.title().orElseThrow().raw());
        assertEquals(1, function.generation().orElseThrow());
    }

    @Test
    void setBookCoverDefaultsEmpty() {
        var function = new SetBookCoverFunction(NO_CONDITIONS, (String) null, null, null);
        assertTrue(function.author().isEmpty());
        assertTrue(function.title().isEmpty());
        assertTrue(function.generation().isEmpty());
    }

    @Test
    void explorationMapDefaults() {
        var function = new ExplorationMapFunction(NO_CONDITIONS);
        assertEquals(ExplorationMapFunction.DEFAULT_DESTINATION, function.destination());
        assertEquals(ExplorationMapFunction.DEFAULT_DECORATION, function.mapDecoration());
        assertEquals(2, function.zoom());
        assertEquals(50, function.searchRadius());
        assertTrue(function.skipExistingChunks());
    }

    @Test
    void explorationMapCustom() {
        var function = new ExplorationMapFunction(NO_CONDITIONS,
                Key.key("minecraft:on_woodland_explorer_maps"), Key.key("minecraft:woodland_mansion"),
                1, 100, false);
        assertEquals(1, function.zoom());
        assertEquals(100, function.searchRadius());
        assertFalse(function.skipExistingChunks());
    }

    @Test
    void fillPlayerHeadDefaults() {
        var function = new FillPlayerHeadFunction(NO_CONDITIONS);
        assertEquals(EntityTarget.THIS, function.entity());
    }

    @Test
    void setFireworkExplosionOptional() {
        var colors = List.of(0xFF0000);
        var function = new SetFireworkExplosionFunction(NO_CONDITIONS,
                Optional.of(FireworkExplosionShape.STAR),
                Optional.of(colors), Optional.empty(), Optional.of(true), Optional.empty());
        assertEquals(FireworkExplosionShape.STAR, function.shape().orElseThrow());
        assertEquals(colors, function.colors().orElseThrow());
        assertTrue(function.fadeColors().isEmpty());
        assertTrue(function.trail().orElseThrow());
        assertTrue(function.twinkle().isEmpty());
    }

    @Test
    void setFireworks() {
        var explosions = new StandAloneOperation<>(List.of(FireworkExplosionShape.CREEPER),
                AppendOperation.INSTANCE);
        var function = new SetFireworksFunction(NO_CONDITIONS, Optional.of(explosions), Optional.of(2));
        assertEquals(explosions, function.explosions().orElseThrow());
        assertEquals(2, function.flightDuration().orElseThrow());
    }

    @Test
    void setInstrument() {
        var function = new SetInstrumentFunction(NO_CONDITIONS,
                EnchantedOptions.ofList(List.of(Key.key("minecraft:ponder_goat_horn"))));
        assertEquals(List.of(Key.key("minecraft:ponder_goat_horn")),
                function.options().enchantments());
    }

    @Test
    void setOminousBottleAmplifier() {
        var function = new SetOminousBottleAmplifierFunction(NO_CONDITIONS, new ConstantValue(2.0f));
        assertEquals(new ConstantValue(2.0f), function.amplifier());
    }

    @Test
    void setRandomDyes() {
        var function = new SetRandomDyesFunction(NO_CONDITIONS, new ConstantValue(3));
        assertEquals(new ConstantValue(3), function.numberOfDyes());
    }

    @Test
    void setRandomPotionEmpty() {
        var function = new SetRandomPotionFunction(NO_CONDITIONS);
        assertTrue(function.options().isEmpty());
    }

    @Test
    void setWritableBookPagesDefaults() {
        var pages = List.of(new FilteredText("hello"));
        var function = new SetWritableBookPagesFunction(NO_CONDITIONS, pages);
        assertEquals(pages, function.pages());
        assertSame(AppendOperation.INSTANCE, function.mode());
    }

    @Test
    void setWrittenBookPagesDefaults() {
        List<Component> pages = List.of(Component.text("page"));
        var function = new SetWrittenBookPagesFunction(NO_CONDITIONS, pages);
        assertEquals(pages, function.pages());
        assertSame(AppendOperation.INSTANCE, function.mode());
    }

    @Test
    void replaceSectionOperation() {
        var operation = new ReplaceSectionOperation(1, 2);
        assertEquals(1, operation.offset());
        assertEquals(2, operation.size().orElseThrow());
    }

    @Test
    void insertOperation() {
        assertEquals(3, new InsertOperation(3).offset());
    }

    @Test
    void replaceAllIsSingleton() {
        assertEquals(ReplaceAllOperation.INSTANCE, new ReplaceAllOperation());
    }

    @Test
    void enchantedOptionsOfList() {
        var options = EnchantedOptions.ofList(List.of(Key.key("minecraft:sharpness")));
        assertEquals(List.of(Key.key("minecraft:sharpness")), options.enchantments());
        assertNull(options.enchantmentTag());
    }

    @Test
    void enchantedOptionsOfTag() {
        var options = EnchantedOptions.ofTag(Key.key("minecraft:tradeable"));
        assertEquals(Key.key("minecraft:tradeable"), options.enchantmentTag());
        assertNull(options.enchantments());
    }
}
