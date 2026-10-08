package io.ib67.yuanban.loot.referenced;

import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.context.LootContextArg;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.CopyBlockStateFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyCustomDataFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.FillPlayerHeadFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.NbtSource;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetNameTarget;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.ib67.yuanban.loot.referenced.ReferencedParamsFixture.ids;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 修饰器 record 的 {@code referencedParams} 对照原版 {@code LootContextUser#getReferencedContextParams} 的抽样断言。
 */
class FunctionReferencedParamsTest {
    private static final ItemPredicate ANY_ITEM = new ItemPredicate(Optional.empty(), MinMaxBounds.ANY_INT, ComponentMatchers.ANY);

    @Test
    void applyBonus() {
        var function = new ApplyBonusFunction(List.of(), RegistryKey.of(Key.key("minecraft:fortune")),
                ApplyBonusFormula.OreDrops.INSTANCE);
        assertEquals(Set.of("minecraft:tool"), ids(function.referencedParams()));
    }

    @Test
    void enchantedCountIncrease() {
        var function = new EnchantedCountIncreaseFunction(List.of(), RegistryKey.of(Key.key("minecraft:looting")),
                new ConstantValue(1));
        assertEquals(Set.of("minecraft:attacking_entity"), ids(function.referencedParams()));
    }

    @Test
    void explorationMap() {
        var function = new ExplorationMapFunction(List.of(), ExplorationMapFunction.DEFAULT_DESTINATION,
                ExplorationMapFunction.DEFAULT_DECORATION, (byte) 2, 50, true);
        assertEquals(Set.of("minecraft:origin"), ids(function.referencedParams()));
    }

    @Test
    void copyBlockState() {
        var function = new CopyBlockStateFunction(List.of(), Block.GRASS_BLOCK, List.of("snowy"));
        assertEquals(Set.of("minecraft:block_state"), ids(function.referencedParams()));
    }

    @Test
    void copyNameEntitySource() {
        var function = new CopyNameFunction(List.of(), new LootContextArg.Entity(EntityTarget.ATTACKER));
        assertEquals(Set.of("minecraft:attacking_entity"), ids(function.referencedParams()));
    }

    @Test
    void copyNameBlockEntitySource() {
        var function = new CopyNameFunction(List.of(), LootContextArg.BlockEntity.INSTANCE);
        assertEquals(Set.of("minecraft:block_entity"), ids(function.referencedParams()));
    }

    @Test
    void copyComponentsEntitySource() {
        var function = new CopyComponentsFunction(List.of(), new LootContextArg.Entity(EntityTarget.ATTACKING_PLAYER),
                Optional.empty(), Optional.empty());
        assertEquals(Set.of("minecraft:last_damage_player"), ids(function.referencedParams()));
    }

    @Test
    void copyComponentsToolSource() {
        var function = new CopyComponentsFunction(List.of(), LootContextArg.Tool.INSTANCE, Optional.empty(), Optional.empty());
        assertEquals(Set.of("minecraft:tool"), ids(function.referencedParams()));
    }

    @Test
    void copyCustomDataReferencesNothingItself() {
        var function = new CopyCustomDataFunction(List.of(), new NbtSource.Context(LootContextArg.BlockEntity.INSTANCE), List.of());
        // 原版 CopyCustomDataFunction 不覆盖 getReferencedContextParams，来源的参数由 validate 递归到来源检查
        assertEquals(Set.of(), ids(function.referencedParams()));
        assertEquals(Set.of("minecraft:block_entity"), ids(function.source().referencedParams()));
        assertEquals(Set.of(), ids(new NbtSource.Storage(Key.key("example:storage")).referencedParams()));
    }

    @Test
    void fillPlayerHead() {
        assertEquals(Set.of("minecraft:this_entity"),
                ids(new FillPlayerHeadFunction(List.of(), EntityTarget.THIS).referencedParams()));
    }

    @Test
    void setNameWithResolutionContext() {
        var function = new SetNameFunction(List.of(), Component.text("名称"), EntityTarget.THIS, SetNameTarget.CUSTOM_NAME);
        assertEquals(Set.of("minecraft:this_entity"), ids(function.referencedParams()));
    }

    @Test
    void setNameWithoutResolutionContext() {
        var function = new SetNameFunction(List.of(), Component.text("名称"), null, SetNameTarget.CUSTOM_NAME);
        assertEquals(Set.of(), ids(function.referencedParams()));
    }

    @Test
    void setNameNullNameKeepsParams() {
        var function = new SetNameFunction(List.of(), null, EntityTarget.THIS, SetNameTarget.CUSTOM_NAME);
        assertEquals(Set.of("minecraft:this_entity"), ids(function.referencedParams()));
    }

    @Test
    void setLoreWithResolutionContext() {
        var function = new SetLoreFunction(List.of(), List.of(), AppendOperation.INSTANCE,
                Optional.of(EntityTarget.ATTACKER));
        assertEquals(Set.of("minecraft:attacking_entity"), ids(function.referencedParams()));
    }

    @Test
    void setLoreWithoutResolutionContext() {
        assertEquals(Set.of(), ids(new SetLoreFunction(List.of(), List.of(), AppendOperation.INSTANCE, Optional.empty()).referencedParams()));
    }

    @Test
    void enchantRandomlyWithAdditionalCostComponent() {
        var function = new EnchantRandomlyFunction(List.of(), Optional.empty(), true, true);
        assertEquals(Set.of("minecraft:additional_cost_component_allowed"), ids(function.referencedParams()));
    }

    @Test
    void enchantRandomlyPlain() {
        assertEquals(Set.of(), ids(new EnchantRandomlyFunction(List.of(), Optional.empty(), true, false).referencedParams()));
    }

    @Test
    void enchantWithLevelsWithAdditionalCostComponent() {
        var function = new EnchantWithLevelsFunction(List.of(), new ConstantValue(30), Optional.empty(), true);
        assertEquals(Set.of("minecraft:additional_cost_component_allowed"), ids(function.referencedParams()));
    }

    @Test
    void filteredMergesBranches() {
        var function = new FilteredFunction(List.of(), ANY_ITEM,
                Optional.of(new FillPlayerHeadFunction(List.of(), EntityTarget.THIS)),
                Optional.of(new SetNameFunction(List.of(), Component.text("名称"), EntityTarget.ATTACKER, SetNameTarget.CUSTOM_NAME)));
        // 原版 FilteredFunction 返回空集，分支修饰器由外层 validate 递归覆盖。
        assertEquals(Set.of(), ids(function.referencedParams()));
    }

    @Test
    void sequenceMergesChildren() {
        var function = new SequenceFunction(List.of(
                new EnchantedCountIncreaseFunction(List.of(), RegistryKey.of(Key.key("minecraft:looting")), new ConstantValue(1)),
                new FunctionReference(List.of(), Key.key("minecraft:some_modifier"))));
        // 原版 SequenceFunction 返回空集，子修饰器由外层 validate 递归覆盖。
        assertEquals(Set.of(), ids(function.referencedParams()));
    }

    @Test
    void discard() {
        assertEquals(Set.of(), ids(new DiscardFunction(List.of()).referencedParams()));
    }

    @Test
    void setCount() {
        assertEquals(Set.of(), ids(new SetCountFunction(List.of(), new ConstantValue(3)).referencedParams()));
    }

    @Test
    void functionReference() {
        assertEquals(Set.of(),
                ids(new FunctionReference(List.of(), Key.key("minecraft:some_modifier")).referencedParams()));
    }
}
