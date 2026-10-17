package io.ib67.yuanban.bot.state;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CookieStoreTest {
    private static final Key KEY = Key.key("test:cookie");

    @Test
    void valuesAreStoredByKey() {
        var cookies = new CookieStore();
        assertNull(cookies.get(KEY));
        cookies.store(KEY, new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, cookies.get(KEY));
        // 新的数据覆盖旧的
        cookies.store(KEY, new byte[]{4});
        assertArrayEquals(new byte[]{4}, cookies.get(KEY));
        // 不同的键互不影响
        assertNull(cookies.get(Key.key("test:other")));
    }

    @Test
    void storedAndReturnedArraysAreCopies() {
        var cookies = new CookieStore();
        var stored = new byte[]{1, 2};
        cookies.store(KEY, stored);
        // 写入之后修改原数组不影响保存的数据
        stored[0] = 9;
        assertArrayEquals(new byte[]{1, 2}, cookies.get(KEY));
        // 读出的数组修改之后不影响保存的数据
        var read = cookies.get(KEY);
        assertNotSame(stored, read);
        read[1] = 9;
        assertArrayEquals(new byte[]{1, 2}, cookies.get(KEY));
    }

    @Test
    void fiveKibibytesIsTheLimit() {
        var cookies = new CookieStore();
        assertEquals(5120, CookieStore.MAX_SIZE);
        cookies.store(KEY, new byte[CookieStore.MAX_SIZE]);
        assertEquals(CookieStore.MAX_SIZE, cookies.get(KEY).length);
        assertThrows(IllegalArgumentException.class, () -> cookies.store(KEY, new byte[CookieStore.MAX_SIZE + 1]));
        // 拒绝之后旧的仍然保留
        assertEquals(CookieStore.MAX_SIZE, cookies.get(KEY).length);
    }
}
