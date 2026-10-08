package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code sequence}：逐个展开子条目，遇到第一个不能展开的子条目为止。JSON 独有字段 {@code children}。
 */
public record SequenceEntry(List<LootCondition> conditions,
                            List<LootEntry> children) implements CompositeEntry {
    public SequenceEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
