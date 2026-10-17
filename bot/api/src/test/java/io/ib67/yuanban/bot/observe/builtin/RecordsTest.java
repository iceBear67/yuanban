package io.ib67.yuanban.bot.observe.builtin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内置 observer 的记录列表：上限之内保存，超出上限只计数。
 */
class RecordsTest {
    @Test
    void recordsWithinTheLimitAreKept() {
        var records = new Records<Integer>(3);
        records.add(1);
        records.add(2);
        assertEquals(List.of(1, 2), records.items());
        assertFalse(records.truncated());
        assertEquals(2, records.count());
    }

    @Test
    void recordsBeyondTheLimitAreOnlyCounted() {
        var records = new Records<Integer>(2);
        for (int i = 0; i < 5; i++) {
            records.add(i);
        }
        assertEquals(List.of(0, 1), records.items());
        assertTrue(records.truncated());
        // count 包括没有保存的记录
        assertEquals(5, records.count());
    }

    @Test
    void zeroLimitKeepsNothing() {
        var records = new Records<Integer>(0);
        records.add(1);
        assertTrue(records.items().isEmpty());
        assertTrue(records.truncated());
        assertEquals(1, records.count());
    }

    @Test
    void itemsIsACopy() {
        var records = new Records<Integer>(3);
        records.add(1);
        var items = records.items();
        records.add(2);
        assertEquals(1, items.size());
        assertEquals(2, records.items().size());
    }
}
