package io.ib67.yuanban.loot.entry;

import java.util.List;

/**
 * 复合条目：展开成若干子条目，对应原版 {@code CompositeEntryBase}。原版的复合条目只有
 * {@code children}（缺省为空列表）和 {@code conditions} 两个字段，没有修饰器。
 */
public sealed interface CompositeEntry extends LootEntry
        permits GroupEntry, AlternativesEntry, SequenceEntry {
    /**
     * 子条目。原版 JSON 字段名 {@code children}。
     */
    List<LootEntry> children();
}
