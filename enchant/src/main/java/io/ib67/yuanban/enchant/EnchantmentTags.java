package io.ib67.yuanban.enchant;

import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一次加载解码得到的全部附魔标签，按标签 ID 的自然顺序排列。每条标签列出其中的附魔 ID。
 */
public record EnchantmentTags(NavigableMap<Key, List<Key>> tags) {
    public static final EnchantmentTags EMPTY = new EnchantmentTags(new TreeMap<>());

    public EnchantmentTags {
        Objects.requireNonNull(tags, "tags");
        var copy = new TreeMap<Key, List<Key>>();
        tags.forEach((id, values) -> copy.put(id, List.copyOf(values)));
        tags = Collections.unmodifiableNavigableMap(copy);
    }
}
