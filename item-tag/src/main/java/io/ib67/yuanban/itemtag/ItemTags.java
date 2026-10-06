package io.ib67.yuanban.itemtag;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;

import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 一次加载展开得到的全部物品标签，按 {@link Key} 的自然顺序排列。
 * 每个列表保持原版展开得到的顺序，不含重复的物品。
 */
public record ItemTags(NavigableMap<Key, List<Material>> tags) {
    public ItemTags {
        Objects.requireNonNull(tags, "tags");
        var copy = new TreeMap<Key, List<Material>>();
        tags.forEach((id, items) -> copy.put(id, List.copyOf(items)));
        tags = Collections.unmodifiableNavigableMap(copy);
    }

    public Optional<List<Material>> find(Key id) {
        return Optional.ofNullable(tags.get(id));
    }
}
