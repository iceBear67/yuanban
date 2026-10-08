package io.ib67.yuanban.enchant;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.BiasedToBottomInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.ClampedInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.ClampedNormalInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.ConstantInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.TrapezoidInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.UniformInt;
import io.ib67.yuanban.enchant.ProviderEntry.IntProvider.WeightedListInt;
import io.ib67.yuanban.enchant.internal.EnchantmentCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原版 {@code IntProviders.CODEC} 的 7 种整数提供器：接受的输入、原版的校验文本和取样范围。
 */
class IntProviderTest {
    private static final int SAMPLES = 2000;

    @Test
    void constant() {
        assertEquals(new ConstantInt(4), decode("4"));
        var provider = decode("""
                {"type": "minecraft:constant", "value": -3}
                """);
        assertEquals(new ConstantInt(-3), provider);
        assertSamples(provider, Set.of(-3));
        assertEquals("Failed to parse either. First: Not a number: \"4\"; Second: Not a JSON object: \"4\"", error("\"4\""));
    }

    @Test
    void uniform() {
        var provider = decode("""
                {"type": "uniform", "min_inclusive": 2, "max_inclusive": 5}
                """);
        assertEquals(new UniformInt(2, 5), provider);
        assertSamples(provider, Set.of(2, 3, 4, 5));
        assertTrue(error("""
                {"type": "minecraft:uniform", "min_inclusive": 5, "max_inclusive": 1}
                """).endsWith("Second: Max must be at least min, min_inclusive: 5, max_inclusive: 1"));
    }

    @Test
    void biasedToBottom() {
        var provider = decode("""
                {"type": "minecraft:biased_to_bottom", "min_inclusive": 1, "max_inclusive": 4}
                """);
        assertEquals(new BiasedToBottomInt(1, 4), provider);
        assertSamples(provider, Set.of(1, 2, 3, 4));
        assertTrue(error("""
                {"type": "minecraft:biased_to_bottom", "min_inclusive": 4, "max_inclusive": 3}
                """).endsWith("Second: Max must be at least min, min_inclusive: 4, max_inclusive: 3"));
    }

    @Test
    void clamped() {
        var provider = decode("""
                {"type": "minecraft:clamped", "source": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 10},
                 "min_inclusive": 3, "max_inclusive": 5}
                """);
        assertEquals(new ClampedInt(new UniformInt(0, 10), 3, 5), provider);
        assertSamples(provider, Set.of(3, 4, 5));
        // 原版的 minInclusive()/maxInclusive() 是和 source 求交之后的范围
        var narrowed = decode("""
                {"type": "minecraft:clamped", "source": 20, "min_inclusive": 0, "max_inclusive": 30}
                """);
        assertEquals(20, narrowed.minInclusive());
        assertEquals(20, narrowed.maxInclusive());
        // 原版只按 JSON 中的两个字段校验，求交之后为空的范围仍然接受
        var disjoint = decode("""
                {"type": "minecraft:clamped", "source": 20, "min_inclusive": 0, "max_inclusive": 10}
                """);
        assertEquals(new ClampedInt(new ConstantInt(20), 0, 10), disjoint);
        assertEquals(10, disjoint.sample());
        assertTrue(error("""
                {"type": "minecraft:clamped", "source": 1, "min_inclusive": 5, "max_inclusive": 4}
                """).endsWith("Second: Max must be at least min, min_inclusive: 5, max_inclusive: 4"));
    }

    @Test
    void weightedList() {
        var provider = decode("""
                {"type": "minecraft:weighted_list", "distribution": [
                  {"data": 1, "weight": 1},
                  {"data": {"type": "minecraft:uniform", "min_inclusive": 7, "max_inclusive": 8}, "weight": 3},
                  {"data": 100, "weight": 0}
                ]}
                """);
        assertEquals(new WeightedListInt(List.of(
                new WeightedListInt.Weighted(new ConstantInt(1), 1),
                new WeightedListInt.Weighted(new UniformInt(7, 8), 3),
                new WeightedListInt.Weighted(new ConstantInt(100), 0))), provider);
        // 权重为 0 的条目不会被选中，但计入 minInclusive()/maxInclusive()
        assertSamples(provider, Set.of(1, 7, 8));
        assertEquals(1, provider.minInclusive());
        assertEquals(100, provider.maxInclusive());

        assertTrue(error("""
                {"type": "minecraft:weighted_list", "distribution": []}
                """).endsWith("Second: Weighted list must contain at least one entry with non-zero weight"));
        assertTrue(error("""
                {"type": "minecraft:weighted_list", "distribution": [{"data": 1, "weight": 0}]}
                """).endsWith("Second: Weighted list must contain at least one entry with non-zero weight"));
        assertTrue(error("""
                {"type": "minecraft:weighted_list", "distribution": [{"data": 1, "weight": -1}]}
                """).contains("Value must be non-negative: -1"));
        // 原版 WeightedList 在权重之和溢出时直接抛出异常
        var overflow = assertThrows(IllegalArgumentException.class, () -> decode("""
                {"type": "minecraft:weighted_list", "distribution": [{"data": 1, "weight": 2147483647}, {"data": 2, "weight": 1}]}
                """));
        assertEquals("Sum of weights must be <= 2147483647", overflow.getMessage());
    }

    @Test
    void clampedNormal() {
        var provider = decode("""
                {"type": "minecraft:clamped_normal", "mean": 5.0, "deviation": 3.0, "min_inclusive": 2, "max_inclusive": 8}
                """);
        assertEquals(new ClampedNormalInt(5.0F, 3.0F, 2, 8), provider);
        assertSamples(provider, Set.of(2, 3, 4, 5, 6, 7, 8));
        assertTrue(error("""
                {"type": "minecraft:clamped_normal", "mean": 0, "deviation": 1, "min_inclusive": 3, "max_inclusive": 2}
                """).endsWith("Second: Max must be larger than min: [3, 2]"));
    }

    @Test
    void trapezoid() {
        var symmetric = decode("""
                {"type": "minecraft:trapezoid", "min": -2, "max": 2, "plateau": 0}
                """);
        assertEquals(new TrapezoidInt(-2, 2, 0), symmetric);
        assertSamples(symmetric, Set.of(-2, -1, 0, 1, 2));
        var flat = decode("""
                {"type": "minecraft:trapezoid", "min": 1, "max": 4, "plateau": 3}
                """);
        assertSamples(flat, Set.of(1, 2, 3, 4));
        var general = decode("""
                {"type": "minecraft:trapezoid", "min": 0, "max": 6, "plateau": 2}
                """);
        assertSamples(general, Set.of(0, 1, 2, 3, 4, 5, 6));

        assertTrue(error("""
                {"type": "minecraft:trapezoid", "min": 3, "max": 1, "plateau": 0}
                """).endsWith("Second: Max must be larger than min: [3, 1]"));
        assertTrue(error("""
                {"type": "minecraft:trapezoid", "min": 0, "max": 4, "plateau": 5}
                """).endsWith("Second: Plateau can at most be the full span: [0, 4]"));
    }

    @Test
    void unknownAndMissingType() {
        assertEquals("Failed to parse either. First: Not a number: {\"type\":\"example:uniform\"}; "
                + "Second: Unknown registry key in ResourceKey[minecraft:root / minecraft:int_provider_type]: example:uniform", error("""
                {"type": "example:uniform"}
                """));
        assertTrue(error("""
                {"min_inclusive": 1, "max_inclusive": 2}
                """).contains("Second: No key type in MapLike["));
    }

    private static IntProvider decode(String json) {
        return EnchantmentCodecs.INT_PROVIDER.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static String error(String json) {
        return EnchantmentCodecs.INT_PROVIDER.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().orElseThrow().message();
    }

    /**
     * 取样 {@link #SAMPLES} 次：每个结果都在 {@code expected} 中，并且 {@code expected} 的每个值都出现过。
     */
    private static void assertSamples(IntProvider provider, Set<Integer> expected) {
        var seen = new HashSet<Integer>();
        for (var i = 0; i < SAMPLES; i++) {
            var value = provider.sample();
            assertTrue(expected.contains(value), provider + " sampled " + value);
            assertTrue(value >= provider.minInclusive() && value <= provider.maxInclusive(), provider + " sampled " + value);
            seen.add(value);
        }
        assertEquals(expected, seen, provider.toString());
    }
}
