package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.codec.MinMaxBounds;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.predicate.ComponentMatchers;
import io.ib67.yuanban.loot.predicate.DamageSourcePredicate;
import io.ib67.yuanban.loot.predicate.ItemPredicate;
import io.ib67.yuanban.loot.predicate.LocationPredicate;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntitySubPredicate;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loot.condition 包的 20 个条件 record 的最小行为。
 */
class ConditionRecordsTest {
    private static final LocationPredicate ANY_LOCATION = new LocationPredicate(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

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
        var predicate = new StatePropertiesPredicate(List.of(
                new StatePropertiesPredicate.PropertyMatcher("snowy", new StatePropertiesPredicate.Exact("true"))));
        var condition = new BlockStatePropertyCondition(Block.GRASS_BLOCK, Optional.of(predicate));
        assertEquals(Block.GRASS_BLOCK, condition.block());
        assertEquals(predicate, condition.properties().orElseThrow());
        assertEquals(Set.of(ContextKey.BLOCK_STATE), condition.referencedParams());
    }

    @Test
    void blockStatePropertyNoProperties() {
        var condition = new BlockStatePropertyCondition(Block.STONE, Optional.empty());
        assertTrue(condition.properties().isEmpty());
    }

    @Test
    void damageSourceProperties() {
        var predicate = new DamageSourcePredicate(
                List.of(new DamageSourcePredicate.TagPredicate<>(TagKey.of(Key.key("is_projectile")), true)),
                Optional.empty(), Optional.empty(), Optional.empty());
        var condition = new DamageSourcePropertiesCondition(Optional.of(predicate));
        assertEquals(predicate, condition.predicate().orElseThrow());
        assertEquals(Set.of(ContextKey.ORIGIN, ContextKey.DAMAGE_SOURCE), condition.referencedParams());
    }

    @Test
    void enchantmentActiveCheck() {
        assertTrue(new EnchantmentActiveCheckCondition(true).active());
        assertFalse(new EnchantmentActiveCheckCondition(false).active());
    }

    @Test
    void entityProperties() {
        var predicate = new EntityPredicate(List.of(new EntitySubPredicate.EntityTypes(
                HolderSet.direct(List.of(RegistryKey.of(Key.key("zombie")))))));
        var condition = new EntityPropertiesCondition(Optional.of(predicate), EntityTarget.ATTACKER);
        assertEquals(EntityTarget.ATTACKER, condition.entity());
        assertEquals(predicate, condition.predicate().orElseThrow());
        assertEquals(Set.of(ContextKey.ORIGIN, ContextKey.ATTACKING_ENTITY), condition.referencedParams());
    }

    @Test
    void entityPropertiesRequiresEntity() {
        assertThrows(NullPointerException.class, () -> new EntityPropertiesCondition(Optional.empty(), null));
    }

    @Test
    void entityScoresKeepsOrder() {
        var scores = new LinkedHashMap<String, IntRange>();
        scores.put("kills", new IntRange(ConstantValue.ZERO, null));
        scores.put("deaths", new IntRange(null, ConstantValue.ZERO));
        var condition = new EntityScoresCondition(scores, EntityTarget.ATTACKING_PLAYER);
        assertEquals(List.of("kills", "deaths"), List.copyOf(condition.scores().keySet()));
        assertEquals(EntityTarget.ATTACKING_PLAYER, condition.entity());
    }

    @Test
    void environmentAttributeCheck() {
        var condition = new EnvironmentAttributeCheckCondition<>(EnvironmentAttribute.FAST_LAVA, true);
        assertEquals(EnvironmentAttribute.FAST_LAVA, condition.attribute());
        assertEquals(true, condition.value());
        assertEquals(Set.of(), condition.referencedParams());
        assertEquals(Set.of(ContextKey.ORIGIN),
                new EnvironmentAttributeCheckCondition<>(EnvironmentAttribute.WATER_EVAPORATES, true).referencedParams());
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
        var condition = new LocationCheckCondition(Optional.of(ANY_LOCATION), 1, 2, 3);
        assertEquals(ANY_LOCATION, condition.predicate().orElseThrow());
        assertEquals(1, condition.offsetX());
        assertEquals(2, condition.offsetY());
        assertEquals(3, condition.offsetZ());
        assertEquals(Set.of(ContextKey.ORIGIN), condition.referencedParams());
    }

    @Test
    void matchTool() {
        var predicate = new ItemPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Material>of(Key.key("shears"))))),
                MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
        var condition = new MatchToolCondition(Optional.of(predicate));
        assertEquals(predicate, condition.predicate().orElseThrow());
        assertTrue(condition.referencedItemTags().isEmpty());
    }

    @Test
    void matchToolWithTagReference() {
        var predicate = new ItemPredicate(Optional.of(HolderSet.named(Key.key("swords"))), MinMaxBounds.ANY_INT, ComponentMatchers.ANY);
        var condition = new MatchToolCondition(Optional.of(predicate));
        assertEquals(Set.of(Key.key("minecraft:swords")), condition.referencedItemTags());
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
                RegistryKey.of(Key.key("minecraft:looting")));
        assertEquals(0.05f, chance.unenchantedChance());
        assertEquals(Key.key("minecraft:looting"), chance.enchantment().key());
        assertEquals(Set.of(ContextKey.ATTACKING_ENTITY), chance.referencedParams());
    }

    @Test
    void reference() {
        var condition = new ReferenceCondition(Key.key("example:some_predicate"));
        assertEquals(Key.key("example:some_predicate"), condition.name());
    }

    @Test
    void survivesExplosion() {
        assertEquals(SurvivesExplosionCondition.INSTANCE, new SurvivesExplosionCondition());
    }

    @Test
    void tableBonus() {
        var condition = new TableBonusCondition(RegistryKey.of(Key.key("minecraft:fortune")), List.of(0.1f, 0.2f, 0.3f));
        assertEquals(Key.key("minecraft:fortune"), condition.enchantment().key());
        assertEquals(List.of(0.1f, 0.2f, 0.3f), condition.values());
    }

    @Test
    void tableBonusRequiresChances() {
        assertThrows(IllegalArgumentException.class,
                () -> new TableBonusCondition(RegistryKey.of(Key.key("minecraft:fortune")), List.of()));
    }

    @Test
    void timeCheck() {
        var condition = new TimeCheckCondition(
                RegistryKey.of(Key.key("minecraft:overworld")),
                Optional.of(24000L),
                new IntRange(ConstantValue.ZERO, new ConstantValue(13000)));
        assertEquals(24000L, condition.period().orElseThrow());
        assertEquals(new ConstantValue(13000), condition.value().max());
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
