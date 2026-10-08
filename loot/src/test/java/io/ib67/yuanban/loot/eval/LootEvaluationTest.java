package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContextImpl;
import io.ib67.yuanban.loot.number.BinomialValue;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.EnchantmentLevelValue;
import io.ib67.yuanban.loot.number.EntityTarget;
import io.ib67.yuanban.loot.number.IntRange;
import io.ib67.yuanban.loot.number.LevelBasedValue;
import io.ib67.yuanban.loot.number.ScoreboardName;
import io.ib67.yuanban.loot.number.ScoreboardValue;
import io.ib67.yuanban.loot.number.StorageValue;
import io.ib67.yuanban.loot.number.SumValue;
import io.ib67.yuanban.loot.number.UniformValue;
import io.ib67.yuanban.loot.testenv.InMemoryLootEnvironment;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 数字提供器、{@code LevelBasedValue} 与 {@code IntRange} 的取值规则，对照原版 {@code providers.number} 包。
 */
@EnvTest
class LootEvaluationTest {

    private static LootContextImpl context(InMemoryLootEnvironment environment, Map<ContextKey<?>, Object> params, long seed) {
        return new LootContextImpl(environment, null, Key.key("generic"), params, null, null, new Random(seed));
    }

    @Test
    void constantRoundsForInt(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, Map.of(), 0L);
        assertEquals(3, LootEvaluation.getInt(new ConstantValue(2.5f), context, environment));
        assertEquals(2, LootEvaluation.getInt(new ConstantValue(2.4f), context, environment));
    }

    @Test
    void sumFloorsForInt(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, Map.of(), 0L);
        // 原版 Sum#getInt 对浮点数之和向下取整
        var sum = new SumValue(List.of(new ConstantValue(1.4f), new ConstantValue(1.4f)));
        assertEquals(2, LootEvaluation.getInt(sum, context, environment));
        assertEquals(2.8f, LootEvaluation.getFloat(sum, context, environment), 1.0e-6f);
    }

    @Test
    void uniformIntUsesIntegerBounds(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        // 原版 UniformGenerator#getInt：Mth.nextInt(random, min.getInt, max.getInt)
        var uniform = new UniformValue(new ConstantValue(1.0f), new ConstantValue(3.0f));
        var expected = new Random(5L).nextInt(3) + 1;
        assertEquals(expected, LootEvaluation.getInt(uniform, context(environment, Map.of(), 5L), environment));
        // 下界不小于上界时返回下界，不消耗随机数
        var degenerate = new UniformValue(new ConstantValue(4.0f), new ConstantValue(2.0f));
        assertEquals(4, LootEvaluation.getInt(degenerate, context(environment, Map.of(), 5L), environment));
        assertEquals(4.0f, LootEvaluation.getFloat(degenerate, context(environment, Map.of(), 5L), environment));
    }

    @Test
    void binomialCountsSuccesses(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        assertEquals(5, LootEvaluation.getInt(new BinomialValue(new ConstantValue(5.0f), new ConstantValue(1.0f)),
                context(environment, Map.of(), 0L), environment));
        assertEquals(0, LootEvaluation.getInt(new BinomialValue(new ConstantValue(5.0f), new ConstantValue(0.0f)),
                context(environment, Map.of(), 0L), environment));
    }

    @Test
    void scoreboard(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putScore("steve", "kills", 4);
        var context = context(environment, Map.of(), 0L);
        assertEquals(8.0f, LootEvaluation.getFloat(new ScoreboardValue(new ScoreboardName.Fixed("steve"), "kills", 2.0f), context, environment));
        // 没有分数时为 0
        assertEquals(0.0f, LootEvaluation.getFloat(new ScoreboardValue(new ScoreboardName.Fixed("alex"), "kills"), context, environment));
        var pig = new Entity(EntityType.PIG);
        environment.putScore(pig.getUuid().toString(), "kills", 3);
        var withPig = context(environment, Map.of(ContextKey.THIS_ENTITY, pig), 0L);
        assertEquals(3.0f, LootEvaluation.getFloat(new ScoreboardValue(new ScoreboardName.Context(EntityTarget.THIS), "kills"), withPig, environment));
    }

    @Test
    void storageReadsSingleNumericTag(Env env) throws Exception {
        var environment = new InMemoryLootEnvironment(env.process());
        environment.putStorage(Key.key("example:data"), CompoundBinaryTag.builder()
                .put("value", DoubleBinaryTag.doubleBinaryTag(2.7))
                .put("list", ListBinaryTag.builder().add(DoubleBinaryTag.doubleBinaryTag(1.0)).add(DoubleBinaryTag.doubleBinaryTag(2.0)).build())
                .build());
        var context = context(environment, Map.of(), 0L);
        // 原版 StorageValue：整数取数值标签的 intValue，浮点数取 floatValue
        assertEquals(2, LootEvaluation.getInt(new StorageValue(Key.key("example:data"), NbtPath.of("value")), context, environment));
        assertEquals(2.7f, LootEvaluation.getFloat(new StorageValue(Key.key("example:data"), NbtPath.of("value")), context, environment));
        // 匹配多个标签、路径不存在或存储不存在时为 0
        assertEquals(0, LootEvaluation.getInt(new StorageValue(Key.key("example:data"), NbtPath.of("list[]")), context, environment));
        assertEquals(0, LootEvaluation.getInt(new StorageValue(Key.key("example:data"), NbtPath.of("missing")), context, environment));
        assertEquals(0.0f, LootEvaluation.getFloat(new StorageValue(Key.key("example:none"), NbtPath.of("value")), context, environment));
    }

    @Test
    void enchantmentLevelRequiresParam(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var amount = new EnchantmentLevelValue(new LevelBasedValue.Linear(1.0f, 2.0f));
        assertEquals(5.0f, LootEvaluation.getFloat(amount, context(environment, Map.of(ContextKey.ENCHANTMENT_LEVEL, 3), 0L), environment));
        // 原版 getParameter：参数缺失时抛出 NoSuchElementException
        assertThrows(NoSuchElementException.class, () -> LootEvaluation.getFloat(amount, context(environment, Map.of(), 0L), environment));
    }

    @Test
    void levelBasedValues() {
        assertEquals(10.0f, LootEvaluation.levelBased(new LevelBasedValue.LevelsSquared(1.0f), 3));
        assertEquals(1.0f, LootEvaluation.levelBased(new LevelBasedValue.Clamped(new LevelBasedValue.Constant(5.0f), 0.0f, 1.0f), 1));
        assertEquals(0.0f, LootEvaluation.levelBased(new LevelBasedValue.Fraction(new LevelBasedValue.Constant(5.0f),
                new LevelBasedValue.Constant(0.0f)), 1));
        assertEquals(8.0f, LootEvaluation.levelBased(new LevelBasedValue.Exponent(new LevelBasedValue.Constant(2.0f),
                new LevelBasedValue.Constant(3.0f)), 1));
        // 原版 Lookup：等级 n 取第 n 个值，超出列表时取 fallback
        var lookup = new LevelBasedValue.Lookup(List.of(10.0f, 20.0f), new LevelBasedValue.Constant(-1.0f));
        assertEquals(10.0f, LootEvaluation.levelBased(lookup, 1));
        assertEquals(20.0f, LootEvaluation.levelBased(lookup, 2));
        assertEquals(-1.0f, LootEvaluation.levelBased(lookup, 3));
    }

    @Test
    void intRangeTestAndClamp(Env env) {
        var environment = new InMemoryLootEnvironment(env.process());
        var context = context(environment, Map.of(), 0L);
        var range = new IntRange(new ConstantValue(2.0f), new ConstantValue(4.0f));
        assertEquals(true, LootEvaluation.test(range, 3, context, environment));
        assertEquals(false, LootEvaluation.test(range, 5, context, environment));
        assertEquals(4, LootEvaluation.clamp(range, 9, context, environment));
        assertEquals(7, LootEvaluation.clamp(new IntRange(null, null), 7, context, environment));
    }
}
