package io.ib67.yuanban.loot.condition;

import java.util.List;

/**
 * 原版 {@code all_of}：全部子条件通过才算通过。JSON 字段 {@code terms}。
 */
public record AllOfCondition(List<LootCondition> terms) implements LootCondition {
    public AllOfCondition {
        terms = List.copyOf(terms);
    }
}
