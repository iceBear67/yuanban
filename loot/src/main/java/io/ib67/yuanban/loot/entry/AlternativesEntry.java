package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code alternatives}：取第一个能展开的子条目。JSON 独有字段 {@code children}。
 */
public record AlternativesEntry(List<LootCondition> conditions,
                                List<LootEntry> children) implements CompositeEntry {
    public AlternativesEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
