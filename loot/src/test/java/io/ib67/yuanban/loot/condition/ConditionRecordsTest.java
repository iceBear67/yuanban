package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.number.ScoreboardName;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loot.condition 包的 20 个条件 record 的最小行为。
 */
class ConditionRecordsTest {

    @Test
    void allOf() {
        var condition = new AllOfCondition(List.of(KilledByPlayerCondition.INSTANCE));
        assertEquals(List.of(KilledByPlayerCondition.INSTANCE), condition.terms());
    }

    @Test
    void anyOf() {
        var condition = new AnyOfCondition(List.of(KilledByPlayerCondition.INSTANCE, SurvivesExplosionCondition.INSTANCE));
        assertEquals(2, condition.terms().size());
    }

    @Test
    void blockStateProperty() {
        var predicate = new BlockPredicate(Key.key("minecraft:grass_block"),
                Optional.of(Map.of("snowy", "true")));
        var condition = new BlockStatePropertyCondition(Key.key("minecraft:grass_block"), predicate);
        assertEquals(Key.key("minecraft:grass_block"), condition.block());
        assertEquals(predicate, condition.properties().orElseThrow());
    }

    @Test
    void blockStatePropertyNoProperties() {
        var condition = new BlockStatePropertyCondition(Key.key("minecraft:stone"), (BlockPredicate) null);
        assertTrue(condition.properties().isEmpty());
    }

    @Test
    void damageSourceProperties() {
        var predicate = new DamageSourcePredicate(Key.key("minecraft:is_projectile"), null, null);
        var condition = new DamageSourcePropertiesCondition(predicate);
        assertEquals(predicate, condition.predicate().orElseThrow());
    }

    @Test
    void enchantmentActiveCheck() {
        assertTrue(new EnchantmentActiveCheckCondition(true).active());
        assertFalse(new EnchantmentActiveCheckCondition(false).active());
    }

    @Test
    void entityProperties() {
        var predicate = new EntityPredicate(List.of(Key.key("minecraft:zombie")), null);
        var condition = new EntityPropertiesCondition(predicate, EntityTarget.ATTACKER);
        assertEquals(EntityTarget.ATTACKER, condition.entity());
        assertEquals(predicate, condition.predicate().orElseThrow());
    }

    @Test
    void entityPropertiesDefaultsToThis() {
        var condition = new EntityPropertiesCondition((EntityPredicate) null, null);
        assertEquals(EntityTarget.THIS, condition.entity());
        assertTrue(condition.predicate().isEmpty());
    }

    @Test
    void entityScores() {
        var scores = Map.<String, IntRange>of("kills", new IntRange(ConstantValue.ZERO, null));
        var condition = new EntityScoresCondition(scores, EntityTarget.ATTACKING_PLAYER);
        assertEquals(scores, condition.scores());
        assertEquals(EntityTarget.ATTACKING_PLAYER, condition.entity());
    }

    @Test
    void environmentAttributeCheck() {
        var condition = new EnvironmentAttributeCheckCondition(
                Key.key("minecraft:daytime"), "true");
        assertEquals(Key.key("minecraft:daytime"), condition.attribute());
        assertEquals("true", condition.value());
    }

    @Test
    void inverted() {
        var condition = new InvertedCondition(KilledByPlayerCondition.INSTANCE);
        assertEquals(KilledByPlayerCondition.INSTANCE, condition.term());
    }

    @Test
    void killedByPlayer() {
        assertEquals(KilledByPlayerCondition.INSTANCE, new KilledByPlayerCondition());
    }

    @Test
    void locationCheck() {
        var predicate = new LocationPredicate(Key.key("minecraft:village"), null, null, null, null);
        var condition = new LocationCheckCondition(predicate, 1, 2, 3);
        assertEquals(predicate, condition.predicate().orElseThrow());
        assertEquals(1, condition.offsetX());
        assertEquals(2, condition.offsetY());
        assertEquals(3, condition.offsetZ());
    }

    @Test
    void locationCheckDefaultOffsets() {
        var condition = new LocationCheckCondition((LocationPredicate) null);
        assertEquals(0, condition.offsetX());
        assertEquals(0, condition.offsetY());
        assertEquals(0, condition.offsetZ());
        assertTrue(condition.predicate().isEmpty());
    }

    @Test
    void matchTool() {
        var predicate = new ItemPredicate(List.of(Key.key("minecraft:shears")), null);
        var condition = new MatchToolCondition(predicate);
        assertEquals(predicate, condition.predicate().orElseThrow());
        assertTrue(condition.referencedItemTags().isEmpty());
    }

    @Test
    void matchToolWithTagReference() {
        var predicate = new ItemPredicate(null, Key.key("minecraft:swords"));
        var condition = new MatchToolCondition(predicate);
        assertEquals(java.util.Set.of(Key.key("minecraft:swords")), condition.referencedItemTags());
    }

    @Test
    void randomChance() {
        var condition = new RandomChanceCondition(new ConstantValue(0.5f));
        assertEquals(new ConstantValue(0.5f), condition.chance());
    }

    @Test
    void randomChanceWithEnchantedBonus() {
        var chance = new RandomChanceWithEnchantedBonusCondition(
                0.05f,
                new LevelBasedValue.Linear(0.1f, 0.05f),
                Key.key("minecraft:looting"));
        assertEquals(0.05f, chance.unenchantedChance());
        assertEquals(Key.key("minecraft:looting"), chance.enchantment());
    }

    @Test
    void reference() {
        var condition = new ReferenceCondition(Key.key("minecraft:some_predicate"));
        assertEquals(Key.key("minecraft:some_predicate"), condition.name());
    }

    @Test
    void survivesExplosion() {
        assertEquals(SurvivesExplosionCondition.INSTANCE, new SurvivesExplosionCondition());
    }

    @Test
    void tableBonus() {
        var condition = new TableBonusCondition(
                Key.key("minecraft:fortune"), List.of(0.1f, 0.2f, 0.3f));
        assertEquals(Key.key("minecraft:fortune"), condition.enchantment());
        assertEquals(List.of(0.1f, 0.2f, 0.3f), condition.values());
    }

    @Test
    void timeCheck() {
        var condition = new TimeCheckCondition(
                Key.key("minecraft:overworld"),
                Optional.of(24000L),
                new IntRange(ConstantValue.ZERO, new ConstantValue(13000)));
        assertEquals(24000L, condition.period().orElseThrow());
        assertEquals(13000, condition.value().max() == null ? -1 : 13000);
    }

    @Test
    void valueCheck() {
        var condition = new ValueCheckCondition(
                new ConstantValue(5),
                new IntRange(new ConstantValue(1), new ConstantValue(10)));
        assertEquals(new ConstantValue(5), condition.value());
    }

    @Test
    void weatherCheck() {
        var condition = new WeatherCheckCondition(true, null);
        assertTrue(condition.isRaining().orElseThrow());
        assertTrue(condition.isThundering().isEmpty());
    }

    @Test
    void weatherCheckDefaults() {
        var condition = new WeatherCheckCondition();
        assertTrue(condition.isRaining().isEmpty());
        assertTrue(condition.isThundering().isEmpty());
    }
}
