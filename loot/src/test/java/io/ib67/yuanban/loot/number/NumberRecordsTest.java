package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.codec.NbtPath;
import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loot.number 包内 8 个数字提供器 record 与 LevelBasedValue 家族、IntRange、ScoreboardName 的最小行为。
 */
class NumberRecordsTest {

    @Test
    void constantValue() {
        assertEquals(2.5f, new ConstantValue(2.5f).value());
        assertSame(ConstantValue.ZERO, ConstantValue.of(0.0f));
        assertEquals(0.0f, ConstantValue.ZERO.value());
        assertEquals(3.0f, ConstantValue.of(3.0f).value());
    }

    @Test
    void uniformValue() {
        var min = new ConstantValue(1.0f);
        var max = new ConstantValue(5.0f);
        var uniform = new UniformValue(min, max);
        assertEquals(min, uniform.min());
        assertEquals(max, uniform.max());
    }

    @Test
    void binomialValue() {
        var n = new ConstantValue(5.0f);
        var p = new ConstantValue(0.5f);
        var binomial = new BinomialValue(n, p);
        assertEquals(n, binomial.n());
        assertEquals(p, binomial.p());
    }

    @Test
    void scoreboardValueDefaultScale() {
        var name = new ScoreboardName.Fixed("steve");
        var score = new ScoreboardValue(name, "kills");
        assertEquals(1.0f, score.scale());
        assertEquals(name, score.target());
        assertEquals("kills", score.score());
    }

    @Test
    void scoreboardValueExplicitScale() {
        var name = new ScoreboardName.Context(EntityTarget.THIS);
        var score = new ScoreboardValue(name, "deaths", 2.0f);
        assertEquals(2.0f, score.scale());
    }

    @Test
    void scoreboardNameContext() {
        assertEquals(EntityTarget.ATTACKER, new ScoreboardName.Context(EntityTarget.ATTACKER).target());
    }

    @Test
    void storageValue() throws Exception {
        var storage = Key.key("minecraft:test");
        var path = NbtPath.of("Items[0].Count");
        var value = new StorageValue(storage, path);
        assertEquals(storage, value.storage());
        assertEquals(path, value.path());
    }

    @Test
    void sumValue() {
        var a = new ConstantValue(1.0f);
        var b = new ConstantValue(2.0f);
        var sum = new SumValue(List.of(a, b));
        assertEquals(List.of(a, b), sum.summands());
    }

    @Test
    void enchantmentLevelValue() {
        LevelBasedValue amount = new LevelBasedValue.Linear(1.0f, 2.0f);
        var value = new EnchantmentLevelValue(amount);
        assertEquals(amount, value.amount());
    }

    @Test
    void environmentAttributeValue() {
        var value = new EnvironmentAttributeValue(EnvironmentAttribute.SKY_LIGHT_LEVEL);
        assertEquals(EnvironmentAttribute.SKY_LIGHT_LEVEL, value.attribute());
    }

    @Test
    void environmentAttributeValue与位置有关时引用origin() {
        assertEquals(Set.of(ContextKey.ORIGIN), new EnvironmentAttributeValue(EnvironmentAttribute.MUSIC_VOLUME).referencedParams());
    }

    @Test
    void environmentAttributeValue与位置无关时不引用参数() {
        // 原版 EnvironmentAttributes 中 gameplay/sky_light_level 与 gameplay/fast_lava 的取值与位置无关
        assertEquals(Set.of(), new EnvironmentAttributeValue(EnvironmentAttribute.SKY_LIGHT_LEVEL).referencedParams());
    }

    @Test
    void intRangeBounds() {
        var min = new ConstantValue(1.0f);
        var max = new ConstantValue(3.0f);
        var range = new IntRange(min, max);
        assertEquals(min, range.min());
        assertEquals(max, range.max());
        var lowerOnly = new IntRange(min, null);
        assertNull(lowerOnly.max());
    }

    @Test
    void levelBasedValueVariants() {
        var constant = new LevelBasedValue.Constant(3.0f);
        assertEquals(3.0f, constant.value());
        var linear = new LevelBasedValue.Linear(1.0f, 2.0f);
        assertEquals(1.0f, linear.base());
        assertEquals(2.0f, linear.perLevelAboveFirst());
        var clamped = new LevelBasedValue.Clamped(linear, 0.0f, 10.0f);
        assertEquals(linear, clamped.value());
        assertEquals(0.0f, clamped.min());
        assertEquals(10.0f, clamped.max());
        var fraction = new LevelBasedValue.Fraction(linear, constant);
        assertEquals(linear, fraction.numerator());
        assertEquals(constant, fraction.denominator());
        var levelsSquared = new LevelBasedValue.LevelsSquared(5.0f);
        assertEquals(5.0f, levelsSquared.added());
        var lookup = new LevelBasedValue.Lookup(List.of(1.0f, 2.0f), constant);
        assertEquals(List.of(1.0f, 2.0f), lookup.values());
        assertEquals(constant, lookup.fallback());
        var exponent = new LevelBasedValue.Exponent(linear, constant);
        assertEquals(linear, exponent.base());
        assertEquals(constant, exponent.power());
    }
}
