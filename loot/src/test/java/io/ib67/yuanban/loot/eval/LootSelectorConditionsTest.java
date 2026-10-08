package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.LootException;
import io.ib67.yuanban.loot.LootPool;
import io.ib67.yuanban.loot.LootTable;
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
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.condition.RandomChanceCondition;
import io.ib67.yuanban.loot.condition.RandomChanceWithEnchantedBonusCondition;
import io.ib67.yuanban.loot.condition.ReferenceCondition;
import io.ib67.yuanban.loot.condition.SurvivesExplosionCondition;
import io.ib67.yuanban.loot.condition.TableBonusCondition;
import io.ib67.yuanban.loot.condition.TimeCheckCondition;
import io.ib67.yuanban.loot.condition.ValueCheckCondition;
import io.ib67.yuanban.loot.condition.WeatherCheckCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContexts;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.predicate.DamageSourcePredicate;
import io.ib67.yuanban.loot.predicate.LocationPredicate;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntityPredicate;
import io.ib67.yuanban.loot.predicate.entity.EntitySubPredicate;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.Weather;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.TagKey;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static io.ib67.yuanban.loot.eval.EvalSupport.context;
import static io.ib67.yuanban.loot.eval.EvalSupport.item;
import static io.ib67.yuanban.loot.eval.EvalSupport.selector;
import static io.ib67.yuanban.loot.eval.EvalSupport.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 条件：原版 {@code net.minecraft.world.level.storage.loot.predicates} 同名类的求值。
 */
@EnvTest
class LootSelectorConditionsTest {
    private static final LocationPredicate ANY_LOCATION = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static LootTable tableWith(LootCondition... poolConditions) {
        return table(new LootPool(List.of(poolConditions), List.of(), new ConstantValue(1.0f), List.of(item(Material.STONE))));
    }

    private static boolean passes(Env env, InMemoryLootEnvironment environment, io.ib67.yuanban.loot.context.LootContext context,
                                  LootCondition condition) {
        return !selector(env, environment).roll(tableWith(condition), context, new Random(0L)).isEmpty();
    }

    @Test
    void killedByPlayerWithoutParamReportsAtRollEntry(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () -> selector(env, environment)
                .roll(tableWith(KilledByPlayerCondition.INSTANCE), LootContexts.empty(null, environment), new Random(0L)));
        assertEquals("minecraft:last_damage_player is not provided in this context", exception.getMessage());
    }

    @Test
    void killedByPlayerWithPresentParam(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));
        // 原版 killed_by_player 只判断 LAST_DAMAGE_PLAYER 参数是否存在
        assertTrue(passes(env, environment, context(environment, instance, ContextKey.LAST_DAMAGE_PLAYER, player),
                KilledByPlayerCondition.INSTANCE));
    }

    @Test
    void referenceConditionResolves(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putCondition(Key.key("test", "always_true"), new RandomChanceCondition(new ConstantValue(1.0f)));
        assertTrue(passes(env, environment, LootContexts.empty(null, environment), new ReferenceCondition(Key.key("test", "always_true"))));
    }

    @Test
    void referenceConditionMissingFailsFast(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () -> passes(env, environment, LootContexts.empty(null, environment),
                new ReferenceCondition(Key.key("test", "missing"))));
        assertEquals("Unknown condition: test:missing", exception.getMessage());
    }

    @Test
    void compositeConditions(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = LootContexts.empty(null, environment);
        var yes = new RandomChanceCondition(new ConstantValue(1.0f));
        var no = new RandomChanceCondition(new ConstantValue(0.0f));
        assertTrue(passes(env, environment, context, new InvertedCondition(no)));
        assertFalse(passes(env, environment, context, new AllOfCondition(List.of(yes, no))));
        assertTrue(passes(env, environment, context, new AllOfCondition(List.of(yes, yes))));
        assertTrue(passes(env, environment, context, new AnyOfCondition(List.of(no, yes))));
        assertFalse(passes(env, environment, context, new AnyOfCondition(List.of())));
        assertTrue(passes(env, environment, context, new AllOfCondition(List.of())));
    }

    @Test
    void valueCheckUsesIntValue(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = LootContexts.empty(null, environment);
        // 原版 ValueCheckCondition：getInt 的结果在范围内；2.5 四舍五入为 3
        assertTrue(passes(env, environment, context, new ValueCheckCondition(new ConstantValue(2.5f),
                new IntRange(new ConstantValue(3), new ConstantValue(3)))));
        assertFalse(passes(env, environment, context, new ValueCheckCondition(new ConstantValue(2.4f),
                new IntRange(new ConstantValue(3), null))));
    }

    @Test
    void survivesExplosion(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 半径为 1 时概率为 1；半径很大时几乎都不通过
        assertTrue(passes(env, environment, context(environment, null, ContextKey.EXPLOSION_RADIUS, 1.0f),
                SurvivesExplosionCondition.INSTANCE));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.EXPLOSION_RADIUS, 1.0e9f),
                SurvivesExplosionCondition.INSTANCE));
    }

    @Test
    void tableBonusBinnedByLevel(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var bonus = new TableBonusCondition(Enchantment.FORTUNE, List.of(0.0f, 1.0f));
        var plain = ItemStack.of(Material.DIAMOND_PICKAXE);
        var enchanted = plain.with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.FORTUNE, 3));
        // 等级 0 取第一项 0.0；等级 3 超出列表时取最后一项 1.0
        assertFalse(passes(env, environment, context(environment, null, ContextKey.TOOL, plain), bonus));
        assertTrue(passes(env, environment, context(environment, null, ContextKey.TOOL, enchanted), bonus));
    }

    @Test
    void tableBonusReadsOnlyEnchantmentsComponent(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var bonus = new TableBonusCondition(Enchantment.FORTUNE, List.of(0.0f, 1.0f));
        // 原版 EnchantmentHelper.getItemEnchantmentLevel 只读 enchantments，附魔书的 stored_enchantments 不算
        var book = ItemStack.of(Material.ENCHANTED_BOOK).with(DataComponents.STORED_ENCHANTMENTS, new EnchantmentList(Enchantment.FORTUNE, 3));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.TOOL, book), bonus));
    }

    @Test
    void randomChanceWithEnchantedBonusReadsAttackerEquipment(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var condition = new RandomChanceWithEnchantedBonusCondition(0.0f, new LevelBasedValue.Constant(1.0f), Enchantment.LOOTING);
        var attacker = new LivingEntity(EntityType.ZOMBIE);
        // 没有附魔时用 unenchanted_chance
        assertFalse(passes(env, environment, context(environment, null, ContextKey.ATTACKING_ENTITY, attacker), condition));
        attacker.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SWORD)
                .with(DataComponents.ENCHANTMENTS, new EnchantmentList(Enchantment.LOOTING, 1)));
        assertTrue(passes(env, environment, context(environment, null, ContextKey.ATTACKING_ENTITY, attacker), condition));
    }

    @Test
    void blockStateProperty(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var snowy = new StatePropertiesPredicate(List.of(
                new StatePropertiesPredicate.PropertyMatcher("snowy", new StatePropertiesPredicate.Exact("true"))));
        var condition = new BlockStatePropertyCondition(Block.GRASS_BLOCK, Optional.of(snowy));
        assertTrue(passes(env, environment, context(environment, null, ContextKey.BLOCK_STATE,
                Block.GRASS_BLOCK.withProperty("snowy", "true")), condition));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.BLOCK_STATE,
                Block.GRASS_BLOCK.withProperty("snowy", "false")), condition));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.BLOCK_STATE, Block.STONE), condition));
    }

    @Test
    void blockStateRangedOnIntegerProperty(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var ripe = new StatePropertiesPredicate(List.of(new StatePropertiesPredicate.PropertyMatcher("age",
                new StatePropertiesPredicate.Ranged(Optional.of("3"), Optional.empty()))));
        var condition = new BlockStatePropertyCondition(Block.WHEAT, Optional.of(ripe));
        assertTrue(passes(env, environment, context(environment, null, ContextKey.BLOCK_STATE, Block.WHEAT.withProperty("age", "7")), condition));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.BLOCK_STATE, Block.WHEAT.withProperty("age", "2")), condition));
    }

    @Test
    void contextValueOfWrongTypeFails(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () -> passes(env, environment,
                context(environment, null, ContextKey.BLOCK_STATE, "stone"),
                new BlockStatePropertyCondition(Block.STONE, Optional.empty())));
        assertTrue(exception.getMessage().startsWith("minecraft:block_state must be a Block"), exception.getMessage());
    }

    @Test
    void enchantmentActiveCheck(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertTrue(passes(env, environment, context(environment, null, ContextKey.ENCHANTMENT_ACTIVE, true),
                new EnchantmentActiveCheckCondition(true)));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.ENCHANTMENT_ACTIVE, false),
                new EnchantmentActiveCheckCondition(true)));
    }

    @Test
    void entityScores(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var entity = new Entity(EntityType.PIG);
        environment.putScore(entity.getUuid().toString(), "kills", 5);
        var scores = new LinkedHashMap<String, IntRange>();
        scores.put("kills", new IntRange(new ConstantValue(3), null));
        var condition = new EntityScoresCondition(scores, EntityTarget.THIS);
        assertTrue(passes(env, environment, context(environment, null, ContextKey.THIS_ENTITY, entity), condition));
        scores.put("deaths", new IntRange(null, null));
        // 没有分数的计分项不通过
        assertFalse(passes(env, environment, context(environment, null, ContextKey.THIS_ENTITY, entity),
                new EntityScoresCondition(scores, EntityTarget.THIS)));
    }

    @Test
    void entityPropertiesChecksEntityType(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var pigs = new EntityPredicate(List.of(new EntitySubPredicate.EntityTypes(
                HolderSet.direct(List.of(RegistryKey.<EntityType>of(Key.key("pig")))))));
        var condition = new EntityPropertiesCondition(Optional.of(pigs), EntityTarget.THIS);
        var origin = new Vec(0, 0, 0);
        assertTrue(passes(env, environment, context(environment, null, ContextKey.THIS_ENTITY, new Entity(EntityType.PIG),
                ContextKey.ORIGIN, origin), condition));
        assertFalse(passes(env, environment, context(environment, null, ContextKey.THIS_ENTITY, new Entity(EntityType.COW),
                ContextKey.ORIGIN, origin), condition));
    }

    @Test
    void locationCheckReadsWorldBlocks(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(1, 50, 1, Block.DIAMOND_BLOCK);
        var diamond = new LocationPredicate.BlockPredicate(Optional.of(HolderSet.direct(List.of(RegistryKey.<Block>of(Key.key("diamond_block"))))),
                Optional.empty(), Optional.empty(), io.ib67.yuanban.loot.predicate.ComponentMatchers.ANY);
        var predicate = new LocationPredicate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(diamond), Optional.empty(), Optional.empty());
        // 偏移量加到 origin 上
        assertTrue(passes(env, environment, context(environment, instance, ContextKey.ORIGIN, new Vec(0.5, 49.5, 0.5)),
                new LocationCheckCondition(Optional.of(predicate), 1, 1, 1)));
        assertFalse(passes(env, environment, context(environment, instance, ContextKey.ORIGIN, new Vec(0.5, 49.5, 0.5)),
                new LocationCheckCondition(Optional.of(predicate), 0, 0, 0)));
        assertTrue(passes(env, environment, context(environment, instance, ContextKey.ORIGIN, new Vec(0.5, 49.5, 0.5)),
                new LocationCheckCondition(Optional.of(ANY_LOCATION), 0, 0, 0)));
    }

    @Test
    void locationCheckWithoutLevelFails(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () -> passes(env, environment,
                context(environment, null, ContextKey.ORIGIN, Vec.ZERO), new LocationCheckCondition(Optional.of(ANY_LOCATION), 0, 0, 0)));
        assertEquals("The loot context has no level, but the evaluation needs one", exception.getMessage());
    }

    @Test
    void weatherCheckReadsInstanceWeather(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var instance = env.createFlatInstance();
        var rain = new WeatherCheckCondition(true, null);
        assertFalse(passes(env, environment, LootContexts.empty(instance, environment), rain));
        instance.setWeather(Weather.RAIN);
        assertTrue(passes(env, environment, LootContexts.empty(instance, environment), rain));
        assertFalse(passes(env, environment, LootContexts.empty(instance, environment), new WeatherCheckCondition(null, true)));
    }

    @Test
    void timeCheckReadsWorldClock(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var instance = env.createFlatInstance();
        var clock = env.process().worldClock().getKey(0);
        instance.setTime(clock, 25000L);
        // period 24000 时取余为 1000
        var day = new TimeCheckCondition(clock, Optional.of(24000L), new IntRange(new ConstantValue(0), new ConstantValue(12000)));
        assertTrue(passes(env, environment, LootContexts.empty(instance, environment), day));
        var noPeriod = new TimeCheckCondition(clock, Optional.empty(), new IntRange(new ConstantValue(0), new ConstantValue(12000)));
        assertFalse(passes(env, environment, LootContexts.empty(instance, environment), noPeriod));
    }

    @Test
    void damageSourceProperties(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var attacker = new LivingEntity(EntityType.SKELETON);
        var arrow = new Entity(EntityType.ARROW);
        var damage = new Damage(DamageType.ARROW, arrow, attacker, null, 1.0f);
        var indirect = new DamageSourcePredicate(List.of(), Optional.empty(), Optional.empty(), Optional.of(false));
        var projectile = new DamageSourcePredicate(List.of(new DamageSourcePredicate.TagPredicate<>(
                TagKey.of(Key.key("is_projectile")), true)), Optional.empty(), Optional.empty(), Optional.empty());
        var params = new Object[]{ContextKey.DAMAGE_SOURCE, damage, ContextKey.ORIGIN, Vec.ZERO};
        assertTrue(passes(env, environment, context(environment, null, params), new DamageSourcePropertiesCondition(Optional.of(indirect))));
        assertTrue(passes(env, environment, context(environment, null, params), new DamageSourcePropertiesCondition(Optional.of(projectile))));
        var direct = new Damage(DamageType.MOB_ATTACK, attacker, attacker, null, 1.0f);
        assertFalse(passes(env, environment, context(environment, null, ContextKey.DAMAGE_SOURCE, direct, ContextKey.ORIGIN, Vec.ZERO),
                new DamageSourcePropertiesCondition(Optional.of(indirect))));
    }

    @Test
    void environmentAttributeCheckIsUnsupported(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var exception = assertThrows(LootException.class, () -> passes(env, environment, LootContexts.empty(null, environment),
                new EnvironmentAttributeCheckCondition<>(EnvironmentAttribute.FAST_LAVA, true)));
        assertEquals("Not supported on Minestom: value of the environment attribute minecraft:gameplay/fast_lava", exception.getMessage());
    }
}
