package io.ib67.yuanban.enchant;

import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一次加载解码得到的全部附魔定义，按附魔 ID 的自然顺序排列。
 */
public record EnchantmentData(NavigableMap<Key, EnchantmentMut> enchantments) {
    public static final EnchantmentData EMPTY = new EnchantmentData(new TreeMap<>());

    public EnchantmentData {
        Objects.requireNonNull(enchantments, "enchantments");
        enchantments = Collections.unmodifiableNavigableMap(new TreeMap<>(enchantments));
    }
}
