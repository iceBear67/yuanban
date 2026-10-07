package io.ib67.yuanban.loot.condition;

import java.util.List;

/**
 * 原版 {@code any_of}：任一子条件通过即通过。JSON 字段 {@code terms}。
 */
public record AnyOfCondition(List<LootCondition> terms) implements LootCondition {
    public AnyOfCondition {
        terms = List.copyOf(terms);
    }
}
