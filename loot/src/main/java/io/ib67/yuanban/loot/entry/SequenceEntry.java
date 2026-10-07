package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;

import java.util.List;

/**
 * 原版 {@code sequence}：逐个展开到首个条件不满足为止。JSON 独有字段 {@code children}。
 */
public record SequenceEntry(List<LootCondition> conditions,
                            List<LootFunction> functions,
                            List<LootEntry> children) implements CompositeEntry {
    public SequenceEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
