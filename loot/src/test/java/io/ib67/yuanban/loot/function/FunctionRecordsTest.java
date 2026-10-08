package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.entry.ItemEntry;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.NumberProvider;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.slot.ContainerComponent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlotGroup;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.Material;
import net.minestom.server.item.book.FilteredText;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loot.function 包的 43 个修饰器 record 的最小行为与缺省值。
 */
class FunctionRecordsTest {

    private static final LootCondition COND = KilledByPlayerCondition.INSTANCE;
    private static final List<LootCondition> NO_CONDITIONS = List.of();
    private static final ItemPredicate ANY_ITEM = new ItemPredicate(Optional.empty(), MinMaxBounds.ANY_INT, ComponentMatchers.ANY);

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
        var function = new SetItemFunction(NO_CONDITIONS, Material.DIAMOND);
        assertEquals(Material.DIAMOND, function.item());
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
    void setLoreRequiresMode() {
        List<Component> lore = List.of(Component.text("line"));
        var function = new SetLoreFunction(NO_CONDITIONS, lore, new InsertOperation(1), Optional.empty());
        assertEquals(lore, function.lore());
        assertEquals(new InsertOperation(1), function.mode());
        assertThrows(NullPointerException.class, () -> new SetLoreFunction(NO_CONDITIONS, lore, null, Optional.empty()));
    }

    @Test
    void applyBonus() {
        var function = new ApplyBonusFunction(NO_CONDITIONS, RegistryKey.of(Key.key("minecraft:looting")),
                new ApplyBonusFormula.UniformBonusCount(2));
        assertEquals(Key.key("minecraft:looting"), function.enchantment().key());
        assertEquals(new ApplyBonusFormula.UniformBonusCount(2), function.formula());
        assertSame(ApplyBonusFormula.OreDrops.INSTANCE, new ApplyBonusFunction(NO_CONDITIONS,
                RegistryKey.of(Key.key("minecraft:fortune")), ApplyBonusFormula.OreDrops.INSTANCE).formula());
    }

    @Test
    void enchantedCountIncreaseDefaultLimit() {
        var function = new EnchantedCountIncreaseFunction(NO_CONDITIONS,
                RegistryKey.of(Key.key("minecraft:looting")), new ConstantValue(1));
        assertEquals(0, function.limit());
    }

    @Test
    void enchantRandomly() {
        var options = HolderSet.<net.minestom.server.item.enchant.Enchantment>named(Key.key("on_random_loot"));
        var function = new EnchantRandomlyFunction(NO_CONDITIONS, Optional.of(options), true, false);
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
        var function = new LimitCountFunction(NO_CONDITIONS, new IntRange(new ConstantValue(1), new ConstantValue(5)));
        assertEquals(new ConstantValue(1), function.limit().min());
    }

    @Test
    void smeltDefaults() {
        assertTrue(new SmeltFunction().useInputCount());
        assertFalse(new SmeltFunction(NO_CONDITIONS, false).useInputCount());
    }

    @Test
    void explosionDecayAndDiscardHaveOnlyConditions() {
        assertTrue(new ExplosionDecayFunction().conditions().isEmpty());
        assertTrue(new DiscardFunction().conditions().isEmpty());
    }

    @Test
    void copyComponentsWithInclude() {
        var include = List.<net.minestom.server.component.DataComponent<?>>of(DataComponents.CUSTOM_NAME);
        var function = new CopyComponentsFunction(NO_CONDITIONS, LootContextArg.Tool.INSTANCE, Optional.of(include), Optional.empty());
        assertEquals(include, function.include().orElseThrow());
        assertTrue(function.exclude().isEmpty());
    }

    @Test
    void copyName() {
        var function = new CopyNameFunction(NO_CONDITIONS, new LootContextArg.Entity(EntityTarget.ATTACKER));
        assertEquals(new LootContextArg.Entity(EntityTarget.ATTACKER), function.source());
    }

    @Test
    void copyStateKeepsNames() {
        var function = new CopyBlockStateFunction(NO_CONDITIONS, Block.GRASS_BLOCK, List.of("snowy", "unknown"));
        assertEquals(Block.GRASS_BLOCK, function.block());
        assertEquals(List.of("snowy", "unknown"), function.properties());
    }

    @Test
    void setEnchantmentsKeepsOrder() {
        var map = new LinkedHashMap<RegistryKey<net.minestom.server.item.enchant.Enchantment>, NumberProvider>();
        map.put(RegistryKey.of(Key.key("sharpness")), new ConstantValue(5));
        map.put(RegistryKey.of(Key.key("looting")), new ConstantValue(1));
        var function = new SetEnchantmentsFunction(NO_CONDITIONS, map);
        assertFalse(function.add());
        assertEquals(List.copyOf(map.keySet()), List.copyOf(function.enchantments().keySet()));
    }

    @Test
    void setPotion() {
        var function = new SetPotionFunction(NO_CONDITIONS, PotionType.FIRE_RESISTANCE);
        assertEquals(PotionType.FIRE_RESISTANCE, function.id());
    }

    @Test
    void setStewEffect() {
        var effects = List.of(new StewEffectEntry(PotionEffect.SATURATION, new ConstantValue(7)));
        var function = new SetStewEffectFunction(NO_CONDITIONS, effects);
        assertEquals(effects, function.effects());
    }

    @Test
    void sequenceHasNoConditions() {
        var a = new SetItemFunction(NO_CONDITIONS, Material.STONE);
        var b = new SetCountFunction(NO_CONDITIONS, new ConstantValue(3));
        var function = new SequenceFunction(List.of(a, b));
        assertEquals(List.of(a, b), function.functions());
        assertEquals(List.of(a, b), function.children());
        assertTrue(function.conditions().isEmpty());
    }

    @Test
    void functionReference() {
        var function = new FunctionReference(NO_CONDITIONS, Key.key("example:my_modifier"));
        assertEquals(Key.key("example:my_modifier"), function.name());
    }

    @Test
    void filteredFunctionOptionalChildren() {
        var onPass = new DiscardFunction();
        var onFail = new ExplosionDecayFunction();
        var function = new FilteredFunction(NO_CONDITIONS, ANY_ITEM, Optional.of(onPass), Optional.of(onFail));
        assertEquals(onPass, function.onPass().orElseThrow());
        assertEquals(onFail, function.onFail().orElseThrow());
        assertEquals(List.of(onPass, onFail), function.children());
    }

    @Test
    void toggleTooltips() {
        var toggles = Map.<net.minestom.server.component.DataComponent<?>, Boolean>of(DataComponents.ENCHANTMENTS, true);
        var function = new ToggleTooltipsFunction(NO_CONDITIONS, toggles);
        assertEquals(toggles, function.toggles());
    }

    @Test
    void modifyContents() {
        var modifier = new SetCountFunction(NO_CONDITIONS, new ConstantValue(2));
        var function = new ModifyContentsFunction(NO_CONDITIONS, ContainerComponent.CONTAINER, modifier);
        assertEquals(ContainerComponent.CONTAINER, function.component());
        assertEquals(DataComponents.CONTAINER, function.component().component());
        assertEquals(List.of(modifier), function.children());
    }

    @Test
    void setContainerContents() {
        var entries = List.<LootEntry>of(new ItemEntry(Material.STONE));
        var function = new SetContainerContentsFunction(NO_CONDITIONS, ContainerComponent.BUNDLE_CONTENTS, entries);
        assertEquals(entries, function.entries());
    }

    @Test
    void setContainerLootTableDefaults() {
        var function = new SetContainerLootTableFunction(NO_CONDITIONS, Key.key("minecraft:chests/simple_dungeon"), BlockEntityType.CHEST);
        assertEquals(0L, function.seed());
        assertEquals(BlockEntityType.CHEST, function.type());
    }

    @Test
    void copyCustomData() throws Exception {
        var source = new NbtSource.Storage(Key.key("minecraft:loot/chest"));
        var ops = List.of(new CopyOperation(NbtPath.of("items"), NbtPath.of("Items"), MergeStrategy.APPEND));
        var function = new CopyCustomDataFunction(NO_CONDITIONS, source, ops);
        assertEquals(source, function.source());
        assertEquals(ops, function.ops());
        assertThrows(NullPointerException.class, () -> new CopyOperation(NbtPath.of("a"), NbtPath.of("b"), null));
    }

    @Test
    void setCustomData() {
        var tag = CompoundBinaryTag.builder().putInt("a", 1).build();
        assertEquals(tag, new SetCustomDataFunction(NO_CONDITIONS, tag).tag());
    }

    @Test
    void setCustomModelData() {
        var empty = SetCustomModelDataFunction.empty(NO_CONDITIONS);
        assertTrue(empty.floats().isEmpty() && empty.flags().isEmpty() && empty.strings().isEmpty() && empty.colors().isEmpty());
        var floats = new StandAloneOperation<NumberProvider>(List.of(new ConstantValue(1.0f)), AppendOperation.INSTANCE);
        var function = new SetCustomModelDataFunction(NO_CONDITIONS, Optional.of(floats), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(floats, function.floats().orElseThrow());
    }

    @Test
    void setAttributesReplaceDefaultsToTrue() {
        var modifier = new AttributeModifierRecord(Key.key("example:bonus"), Attribute.ATTACK_DAMAGE,
                AttributeOperation.ADD_VALUE, new ConstantValue(2), List.of(EquipmentSlotGroup.MAIN_HAND));
        var function = new SetAttributesFunction(NO_CONDITIONS, List.of(modifier));
        assertEquals(List.of(modifier), function.modifiers());
        assertTrue(function.replace());
        assertThrows(IllegalArgumentException.class, () -> new AttributeModifierRecord(Key.key("example:bonus"),
                Attribute.ATTACK_DAMAGE, AttributeOperation.ADD_VALUE, new ConstantValue(2), List.of()));
    }

    @Test
    void setBannerPattern() {
        var patterns = new BannerPatterns(List.of(new BannerPatterns.Layer(BannerPattern.FLOWER, DyeColor.RED)));
        var function = new SetBannerPatternFunction(NO_CONDITIONS, patterns, false);
        assertFalse(function.append());
        assertEquals(patterns, function.patterns());
    }

    @Test
    void setBookCover() {
        var function = new SetBookCoverFunction(NO_CONDITIONS, Optional.of(new FilteredText<>("title", null)),
                Optional.of("Author"), Optional.of(1));
        assertEquals("Author", function.author().orElseThrow());
        assertEquals("title", function.title().orElseThrow().text());
        assertEquals(1, function.generation().orElseThrow());
    }

    @Test
    void explorationMap() {
        var function = new ExplorationMapFunction(NO_CONDITIONS, ExplorationMapFunction.DEFAULT_DESTINATION,
                ExplorationMapFunction.DEFAULT_DECORATION, (byte) 1, 100, false);
        assertEquals(Key.key("minecraft:on_treasure_maps"), function.destination().key());
        assertEquals(Key.key("minecraft:mansion"), function.mapDecoration());
        assertEquals(1, function.zoom());
        assertEquals(100, function.searchRadius());
        assertFalse(function.skipExistingChunks());
    }

    @Test
    void fillPlayerHeadRequiresEntity() {
        assertEquals(EntityTarget.THIS, new FillPlayerHeadFunction(NO_CONDITIONS, EntityTarget.THIS).entity());
        assertThrows(NullPointerException.class, () -> new FillPlayerHeadFunction(NO_CONDITIONS, null));
    }

    @Test
    void setFireworkExplosion() {
        var function = new SetFireworkExplosionFunction(NO_CONDITIONS, Optional.of(FireworkExplosion.Shape.STAR),
                Optional.of(List.of(0xFF0000)), Optional.empty(), Optional.of(true), Optional.empty());
        assertEquals(FireworkExplosion.Shape.STAR, function.shape().orElseThrow());
        assertEquals(List.of(0xFF0000), function.colors().orElseThrow());
        assertTrue(new SetFireworkExplosionFunction(NO_CONDITIONS).shape().isEmpty());
    }

    @Test
    void setFireworks() {
        var explosions = new StandAloneOperation<>(List.of(new FireworkExplosion(FireworkExplosion.Shape.CREEPER,
                List.of(), List.of(), false, false)), AppendOperation.INSTANCE);
        var function = new SetFireworksFunction(NO_CONDITIONS, Optional.of(explosions), Optional.of(2));
        assertEquals(explosions, function.explosions().orElseThrow());
        assertEquals(2, function.flightDuration().orElseThrow());
    }

    @Test
    void setInstrument() {
        var options = HolderSet.<net.minestom.server.item.instrument.Instrument>named(Key.key("goat_horns"));
        assertEquals(options, new SetInstrumentFunction(NO_CONDITIONS, options).options());
    }

    @Test
    void setOminousBottleAmplifierAndRandomDyes() {
        assertEquals(new ConstantValue(2.0f), new SetOminousBottleAmplifierFunction(NO_CONDITIONS, new ConstantValue(2.0f)).amplifier());
        assertEquals(new ConstantValue(3), new SetRandomDyesFunction(NO_CONDITIONS, new ConstantValue(3)).numberOfDyes());
    }

    @Test
    void setRandomPotionEmpty() {
        assertTrue(new SetRandomPotionFunction(NO_CONDITIONS).options().isEmpty());
    }

    @Test
    void bookPages() {
        var writable = List.of(new FilteredText<>("hello", null));
        var writableFunction = new SetWritableBookPagesFunction(NO_CONDITIONS, writable, ReplaceAllOperation.INSTANCE);
        assertEquals(writable, writableFunction.pages());
        var written = List.of(new FilteredText<Component>(Component.text("page"), null));
        var writtenFunction = new SetWrittenBookPagesFunction(NO_CONDITIONS, written, AppendOperation.INSTANCE);
        assertEquals(written, writtenFunction.pages());
        assertSame(AppendOperation.INSTANCE, writtenFunction.mode());
    }

    @Test
    void listOperations() {
        var section = new ReplaceSectionOperation(1, 2);
        assertEquals(1, section.offset());
        assertEquals(2, section.size().orElseThrow());
        assertTrue(new ReplaceSectionOperation(1).size().isEmpty());
        assertEquals(3, new InsertOperation(3).offset());
        assertEquals(ReplaceAllOperation.INSTANCE, new ReplaceAllOperation());
    }
}
