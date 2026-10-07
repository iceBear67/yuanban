package io.ib67.yuanban.loot.referenced;

import io.ib67.yuanban.loot.function.ApplyBonusFormula;
import io.ib67.yuanban.loot.function.ApplyBonusFunction;
import io.ib67.yuanban.loot.function.AppendOperation;
import io.ib67.yuanban.loot.function.CopyBlockStateFunction;
import io.ib67.yuanban.loot.function.CopyComponentsFunction;
import io.ib67.yuanban.loot.function.CopyNameFunction;
import io.ib67.yuanban.loot.function.CopyNameSource;
import io.ib67.yuanban.loot.function.DiscardFunction;
import io.ib67.yuanban.loot.function.EnchantRandomlyFunction;
import io.ib67.yuanban.loot.function.EnchantWithLevelsFunction;
import io.ib67.yuanban.loot.function.EnchantedCountIncreaseFunction;
import io.ib67.yuanban.loot.function.ExplorationMapFunction;
import io.ib67.yuanban.loot.function.FillPlayerHeadFunction;
import io.ib67.yuanban.loot.function.FilteredFunction;
import io.ib67.yuanban.loot.function.FunctionReference;
import io.ib67.yuanban.loot.function.SequenceFunction;
import io.ib67.yuanban.loot.function.SetCountFunction;
import io.ib67.yuanban.loot.function.SetLoreFunction;
import io.ib67.yuanban.loot.function.SetNameFunction;
import io.ib67.yuanban.loot.function.SetNameTarget;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
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

    @Test
    void applyBonus() {
        var function = new ApplyBonusFunction(List.of(), Key.key("minecraft:fortune"),
                ApplyBonusFormula.OreDrops.INSTANCE);
        assertEquals(Set.of("minecraft:tool"), ids(function.referencedParams()));
    }

    @Test
    void enchantedCountIncrease() {
        var function = new EnchantedCountIncreaseFunction(List.of(), Key.key("minecraft:looting"),
                new ConstantValue(1));
        assertEquals(Set.of("minecraft:attacking_entity"), ids(function.referencedParams()));
    }

    @Test
    void explorationMap() {
        assertEquals(Set.of("minecraft:origin"), ids(new ExplorationMapFunction(List.of()).referencedParams()));
    }

    @Test
    void copyBlockState() {
        var function = new CopyBlockStateFunction(List.of(), Key.key("minecraft:grass_block"),
                Set.of("snowy"));
        assertEquals(Set.of("minecraft:block_state"), ids(function.referencedParams()));
    }

    @Test
    void copyNameEntitySource() {
        var function = new CopyNameFunction(List.of(), new CopyNameSource.Entity(EntityTarget.ATTACKER));
        assertEquals(Set.of("minecraft:attacking_entity"), ids(function.referencedParams()));
    }

    @Test
    void copyNameBlockEntitySource() {
        var function = new CopyNameFunction(List.of(), CopyNameSource.BlockEntity.INSTANCE);
        assertEquals(Set.of("minecraft:block_entity"), ids(function.referencedParams()));
    }

    @Test
    void copyComponentsEntitySource() {
        var function = new CopyComponentsFunction(List.of(),
                new CopyNameSource.Entity(EntityTarget.ATTACKING_PLAYER));
        assertEquals(Set.of("minecraft:last_damage_player"), ids(function.referencedParams()));
    }

    @Test
    void fillPlayerHead() {
        assertEquals(Set.of("minecraft:this_entity"),
                ids(new FillPlayerHeadFunction(List.of()).referencedParams()));
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
        assertEquals(Set.of(), ids(new SetLoreFunction(List.of(), List.of()).referencedParams()));
    }

    @Test
    void enchantRandomlyWithAdditionalCostComponent() {
        var function = new EnchantRandomlyFunction(List.of(), null, true, true);
        assertEquals(Set.of("minecraft:additional_cost_component_allowed"), ids(function.referencedParams()));
    }

    @Test
    void enchantRandomlyPlain() {
        assertEquals(Set.of(), ids(new EnchantRandomlyFunction(List.of(), null).referencedParams()));
    }

    @Test
    void enchantWithLevelsWithAdditionalCostComponent() {
        var function = new EnchantWithLevelsFunction(List.of(), new ConstantValue(30), Optional.empty(), true);
        assertEquals(Set.of("minecraft:additional_cost_component_allowed"), ids(function.referencedParams()));
    }

    @Test
    void filteredMergesBranches() {
        var function = new FilteredFunction(List.of(),
                new io.ib67.yuanban.loot.condition.ItemPredicate(List.of(Key.key("minecraft:diamond")), null),
                Optional.of(new FillPlayerHeadFunction(List.of())),
                Optional.of(new SetNameFunction(List.of(), Component.text("名称"), EntityTarget.ATTACKER, SetNameTarget.CUSTOM_NAME)));
        // 原版 FilteredFunction 返回空集，分支修饰器由外层 validate 递归覆盖。
        assertEquals(Set.of(), ids(function.referencedParams()));
    }

    @Test
    void sequenceMergesChildren() {
        var function = new SequenceFunction(List.of(), List.of(
                new EnchantedCountIncreaseFunction(List.of(), Key.key("minecraft:looting"), new ConstantValue(1)),
                new FunctionReference(List.of(), Key.key("minecraft:some_modifier"))));
        // 原版 SequenceFunction 返回空集，子修饰器由外层 validate 递归覆盖。
        assertEquals(Set.of(), ids(function.referencedParams()));
    }

    @Test
    void sequenceOfEmptyChildren() {
        var function = new SequenceFunction(List.of(), List.of(
                new SetCountFunction(List.of(), new ConstantValue(3)),
                new FunctionReference(List.of(), Key.key("minecraft:some_modifier"))));
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
