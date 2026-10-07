package io.ib67.yuanban.loot.entry;

import java.util.List;

/**
 * 复合条目：展开成若干子条目，对应原版 {@code CompositeEntryBase}。
 */
public sealed interface CompositeEntry extends LootEntry
        permits GroupEntry, AlternativesEntry, SequenceEntry {
    /**
     * 子条目。原版 JSON 字段名 {@code children}。
     */
    List<LootEntry> children();
}
