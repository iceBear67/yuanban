package io.ib67.yuanban.enchant;

import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一次加载解码得到的全部附魔提供器，按提供器 ID 的自然顺序排列。
 */
public record EnchantmentProviderData(NavigableMap<Key, ProviderEntry> providers) {
    public static final EnchantmentProviderData EMPTY = new EnchantmentProviderData(new TreeMap<>());

    public EnchantmentProviderData {
        Objects.requireNonNull(providers, "providers");
        providers = Collections.unmodifiableNavigableMap(new TreeMap<>(providers));
    }
}
