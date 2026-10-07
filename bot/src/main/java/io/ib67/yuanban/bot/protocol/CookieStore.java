package io.ib67.yuanban.bot.protocol;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Store Cookie 保存的数据。每个 bot 一个，跨 Transfer 保留。
 */
public final class CookieStore {
    /**
     * wiki Store Cookie：数据最多 5 KiB
     */
    public static final int MAX_SIZE = 5120;

    private final Map<Key, byte[]> cookies = new HashMap<>();

    /**
     * @throws IllegalArgumentException 数据超过 5 KiB
     */
    public void store(String key, byte[] value) {
        if (value.length > MAX_SIZE) {
            throw new IllegalArgumentException("cookie " + key + " is " + value.length + " bytes, the limit is " + MAX_SIZE);
        }
        cookies.put(Key.key(key), value.clone());
    }

    /**
     * @return 保存的数据；没有时为 null
     */
    public byte @Nullable [] get(String key) {
        var value = cookies.get(Key.key(key));
        return value == null ? null : value.clone();
    }
}
