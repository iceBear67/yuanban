package io.ib67.yuanban.loot.number;

/**
 * 二项分布：原版 {@code providers.number.BinomialDistributionGenerator}，JSON 字段 {@code n}、{@code p}。
 */
public record BinomialValue(NumberProvider n, NumberProvider p) implements NumberProvider {
}
