package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code group}：子条目全部展开并合为一次抽取的候选。JSON 独有字段 {@code children}。
 */
public record GroupEntry(List<LootCondition> conditions,
                         List<LootEntry> children) implements CompositeEntry {
    public GroupEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
