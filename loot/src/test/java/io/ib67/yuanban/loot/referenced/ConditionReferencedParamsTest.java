package io.ib67.yuanban.loot.referenced;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
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
import io.ib67.yuanban.loot.condition.SurvivesExplosionCondition;
import io.ib67.yuanban.loot.condition.TableBonusCondition;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.ib67.yuanban.loot.referenced.ReferencedParamsFixture.ids;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 条件 record 的 {@code referencedParams} 对照原版 {@code LootContextUser#getReferencedContextParams} 的抽样断言。
 */
class ConditionReferencedParamsTest {

    @Test
    void killedByPlayer() {
        assertEquals(Set.of("minecraft:last_damage_player"), ids(KilledByPlayerCondition.INSTANCE.referencedParams()));
    }

    @Test
    void randomChanceWithEnchantedBonus() {
        var condition = new RandomChanceWithEnchantedBonusCondition(
                0.05f, new LevelBasedValue.Linear(0.1f, 0.05f), RegistryKey.of(Key.key("minecraft:looting")));
        assertEquals(Set.of("minecraft:attacking_entity"), ids(condition.referencedParams()));
    }

    @Test
    void entityScores() {
        var scores = Map.<String, IntRange>of("kills", new IntRange(ConstantValue.ZERO, null));
        assertEquals(Set.of("minecraft:this_entity"),
                ids(new EntityScoresCondition(scores, EntityTarget.THIS).referencedParams()));
        assertEquals(Set.of("minecraft:attacking_entity"),
                ids(new EntityScoresCondition(scores, EntityTarget.ATTACKER).referencedParams()));
        assertEquals(Set.of("minecraft:last_damage_player"),
                ids(new EntityScoresCondition(scores, EntityTarget.ATTACKING_PLAYER).referencedParams()));
    }

    @Test
    void entityProperties() {
        assertEquals(Set.of("minecraft:origin", "minecraft:attacking_entity"),
                ids(new EntityPropertiesCondition(Optional.of(new EntityPredicate(List.of())), EntityTarget.ATTACKER).referencedParams()));
    }

    @Test
    void locationCheck() {
        assertEquals(Set.of("minecraft:origin"), ids(new LocationCheckCondition(Optional.empty(), 0, 0, 0).referencedParams()));
    }

    @Test
    void blockStateProperty() {
        assertEquals(Set.of("minecraft:block_state"),
                ids(new BlockStatePropertyCondition(Block.STONE, Optional.empty()).referencedParams()));
    }

    @Test
    void damageSourceProperties() {
        assertEquals(Set.of("minecraft:origin", "minecraft:damage_source"),
                ids(new DamageSourcePropertiesCondition(Optional.empty()).referencedParams()));
    }

    @Test
    void enchantmentActiveCheck() {
        assertEquals(Set.of("minecraft:enchantment_active"),
                ids(new EnchantmentActiveCheckCondition(true).referencedParams()));
    }

    @Test
    void environmentAttributeCheck() {
        assertEquals(Set.of("minecraft:origin"),
                ids(new EnvironmentAttributeCheckCondition<>(EnvironmentAttribute.MUSIC_VOLUME, 1.0F).referencedParams()));
    }

    @Test
    void matchTool() {
        var predicate = new ItemPredicate(Optional.of(HolderSet.named(Key.key("swords"))), MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
        assertEquals(Set.of("minecraft:tool"), ids(new MatchToolCondition(Optional.of(predicate)).referencedParams()));
    }

    @Test
    void tableBonus() {
        assertEquals(Set.of("minecraft:tool"),
                ids(new TableBonusCondition(RegistryKey.of(Key.key("minecraft:fortune")), List.of(0.1f)).referencedParams()));
    }

    @Test
    void survivesExplosion() {
        assertEquals(Set.of("minecraft:explosion_radius"), ids(SurvivesExplosionCondition.INSTANCE.referencedParams()));
    }

    @Test
    void randomChanceEmpty() {
        assertEquals(Set.of(), ids(new RandomChanceCondition(new ConstantValue(0.5f)).referencedParams()));
    }

    @Test
    void allOfEmptyTerms() {
        assertEquals(Set.of(), ids(new AllOfCondition(List.of()).referencedParams()));
    }

    @Test
    void compositeMergesTerms() {
        var scores = Map.<String, IntRange>of("kills", new IntRange(ConstantValue.ZERO, null));
        var condition = new AllOfCondition(List.of(
                KilledByPlayerCondition.INSTANCE,
                new EntityScoresCondition(scores, EntityTarget.THIS)));
        // 原版 AllOfCondition 的 getReferencedContextParams 返回空集：复合节点只校验自身直接引用的参数，子条件由外层 validate 递归覆盖。
        assertEquals(Set.of(), ids(condition.referencedParams()));
    }

    @Test
    void compositeConditionsReturnEmptySet() {
        // 原版 AnyOfCondition、InvertedLootItemCondition 均继承/直接返回空集，不合并子条件。
        assertEquals(Set.of(), ids(new AnyOfCondition(List.of(KilledByPlayerCondition.INSTANCE)).referencedParams()));
        assertEquals(Set.of(), ids(new InvertedCondition(KilledByPlayerCondition.INSTANCE).referencedParams()));
    }
}
