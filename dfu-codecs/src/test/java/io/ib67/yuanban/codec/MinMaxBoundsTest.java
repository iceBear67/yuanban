package io.ib67.yuanban.codec;

import com.mojang.serialization.Codec;
import net.minestom.server.utils.Range;
import org.junit.jupiter.api.Test;

import static io.ib67.yuanban.codec.CodecTestSupport.decode;
import static io.ib67.yuanban.codec.CodecTestSupport.encode;
import static io.ib67.yuanban.codec.CodecTestSupport.error;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MinMaxBounds}：错误文本取自原版 26.2 服务端。
 */
class MinMaxBoundsTest {
    @Test
    void ints() {
        assertEquals(new Range.Int(5, 5), decode(MinMaxBounds.INTS, "5"));
        assertEquals(new Range.Int(1, null), decode(MinMaxBounds.INTS, "{\"min\": 1}"));
        assertEquals(MinMaxBounds.ANY_INT, decode(MinMaxBounds.INTS, "{}"));
        assertEquals("5", encode(MinMaxBounds.INTS, new Range.Int(5, 5)));
        assertEquals("{\"min\":1}", encode(MinMaxBounds.INTS, new Range.Int(1, null)));
        assertEquals("Swapped bounds in range: Optional[3] is higher than Optional[2]", error(MinMaxBounds.INTS, "{\"min\": 3, \"max\": 2}"));
    }

    @Test
    void doubles() {
        assertEquals(new Range.Double(0.5, 1.5), decode(MinMaxBounds.DOUBLES, "{\"min\": 0.5, \"max\": 1.5}"));
        assertEquals("Swapped bounds in range: Optional[1.5] is higher than Optional[0.5]",
                error(MinMaxBounds.DOUBLES, "{\"min\": 1.5, \"max\": 0.5}"));
    }

    @Test
    void containedInRange() {
        Codec<Range.Int> between = MinMaxBounds.INTS.validate(MinMaxBounds.validateContainedInRange(new Range.Int(1, 8)));
        assertEquals(new Range.Int(2, 3), decode(between, "{\"min\": 2, \"max\": 3}"));
        assertEquals("Range must be within [1..8], but was [9..9]", error(between, "9"));
        assertEquals("Range must be within [1..8], but was [2..+∞)", error(between, "{\"min\": 2}"));
        Codec<Range.Int> atLeast = MinMaxBounds.INTS.validate(MinMaxBounds.validateContainedInRange(new Range.Int(2, null)));
        assertEquals("Range must be within [2..+∞), but was (-∞..5]", error(atLeast, "{\"max\": 5}"));
    }

    @Test
    void matchesSqr() {
        // 原版先把上下界平方，负数的下界同样平方
        assertTrue(MinMaxBounds.matchesSqr(new Range.Int(2, 3), 9));
        assertFalse(MinMaxBounds.matchesSqr(new Range.Int(2, 3), 10));
        assertFalse(MinMaxBounds.matchesSqr(new Range.Int(-3, null), 4));
        assertTrue(MinMaxBounds.matchesSqr(new Range.Double(null, 1.5), 2.25));
    }
}
