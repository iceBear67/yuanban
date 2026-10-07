package io.ib67.yuanban.lootparser;

import io.ib67.yuanban.loot.LootTable;
import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一次加载得到的战利品表集合，按 {@link Key} 的自然顺序排列；不可变。
 */
public record LootTableSet(NavigableMap<Key, LootTable> tables) {
    public static final LootTableSet EMPTY = new LootTableSet(new TreeMap<>());

    public LootTableSet {
        Objects.requireNonNull(tables, "tables");
        tables = Collections.unmodifiableNavigableMap(new TreeMap<>(tables));
    }
}
