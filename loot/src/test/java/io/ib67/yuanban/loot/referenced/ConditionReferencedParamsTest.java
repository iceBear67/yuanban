package io.ib67.yuanban.loot.referenced;

import io.ib67.yuanban.loot.condition.AllOfCondition;
import io.ib67.yuanban.loot.condition.BlockStatePropertyCondition;
import io.ib67.yuanban.loot.condition.DamageSourcePredicate;
import io.ib67.yuanban.loot.condition.DamageSourcePropertiesCondition;
import io.ib67.yuanban.loot.condition.EnchantmentActiveCheckCondition;
import io.ib67.yuanban.loot.condition.EntityPredicate;
import io.ib67.yuanban.loot.condition.EntityPropertiesCondition;
import io.ib67.yuanban.loot.condition.EntityScoresCondition;
import io.ib67.yuanban.loot.condition.EnvironmentAttributeCheckCondition;
import io.ib67.yuanban.loot.condition.ItemPredicate;
import io.ib67.yuanban.loot.condition.KilledByPlayerCondition;
import io.ib67.yuanban.loot.condition.LocationCheckCondition;
import io.ib67.yuanban.loot.condition.MatchToolCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.condition.RandomChanceWithEnchantedBonusCondition;
import io.ib67.yuanban.loot.condition.SurvivesExplosionCondition;
import io.ib67.yuanban.loot.condition.TableBonusCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 条件 record 的 {@code referencedParams} 对照原版 {@code LootContextUser#getReferencedContextParams} 的抽样断言。
 */
class ConditionReferencedParamsTest {

    private static Set<String> ids(Set<ContextKey<?>> params) {
        return params.stream().map(contextKey -> contextKey.id().toString()).collect(Collectors.toSet());
    }

    @Test
    void killedByPlayer() {
        assertEquals(Set.of("minecraft:last_damage_player"), ids(KilledByPlayerCondition.INSTANCE.referencedParams()));
    }

    @Test
    void randomChanceWithEnchantedBonus() {
        var condition = new RandomChanceWithEnchantedBonusCondition(
                0.05f, new io.ib67.yuanban.loot.number.LevelBasedValue.Linear(0.1f, 0.05f),
                Key.key("minecraft:looting"));
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
        var predicate = new EntityPredicate(List.of(Key.key("minecraft:zombie")), null);
        assertEquals(Set.of("minecraft:origin", "minecraft:attacking_entity"),
                ids(new EntityPropertiesCondition(predicate, EntityTarget.ATTACKER).referencedParams()));
    }

    @Test
    void locationCheck() {
        var condition = new LocationCheckCondition(
                new io.ib67.yuanban.loot.condition.LocationPredicate(Key.key("minecraft:village"), null, null, null, null));
        assertEquals(Set.of("minecraft:origin"), ids(condition.referencedParams()));
    }

    @Test
    void blockStateProperty() {
        var condition = new BlockStatePropertyCondition(Key.key("minecraft:stone"), (io.ib67.yuanban.loot.condition.BlockPredicate) null);
        assertEquals(Set.of("minecraft:block_state"), ids(condition.referencedParams()));
    }

    @Test
    void damageSourceProperties() {
        var condition = new DamageSourcePropertiesCondition(
                new DamageSourcePredicate(Key.key("minecraft:is_projectile"), null, null));
        assertEquals(Set.of("minecraft:origin", "minecraft:damage_source"), ids(condition.referencedParams()));
    }

    @Test
    void enchantmentActiveCheck() {
        assertEquals(Set.of("minecraft:enchantment_active"),
                ids(new EnchantmentActiveCheckCondition(true).referencedParams()));
    }

    @Test
    void environmentAttributeCheck() {
        assertEquals(Set.of("minecraft:origin"),
                ids(new EnvironmentAttributeCheckCondition(Key.key("minecraft:daytime"), "true").referencedParams()));
    }

    @Test
    void matchTool() {
        assertEquals(Set.of("minecraft:tool"),
                ids(new MatchToolCondition(new ItemPredicate(List.of(Key.key("minecraft:shears")), null)).referencedParams()));
    }

    @Test
    void tableBonus() {
        assertEquals(Set.of("minecraft:tool"),
                ids(new TableBonusCondition(Key.key("minecraft:fortune"), List.of(0.1f)).referencedParams()));
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
        assertEquals(Set.of(), ids(new io.ib67.yuanban.loot.condition.AnyOfCondition(
                List.of(KilledByPlayerCondition.INSTANCE)).referencedParams()));
        assertEquals(Set.of(), ids(new io.ib67.yuanban.loot.condition.InvertedCondition(
                KilledByPlayerCondition.INSTANCE).referencedParams()));
    }
}
