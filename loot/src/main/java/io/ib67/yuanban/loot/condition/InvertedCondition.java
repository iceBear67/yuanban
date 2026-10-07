package io.ib67.yuanban.loot.condition;

/**
 * 原版 {@code inverted}：取反单个条件。JSON 字段 {@code term}。
 */
public record InvertedCondition(LootCondition term) implements LootCondition {
}
