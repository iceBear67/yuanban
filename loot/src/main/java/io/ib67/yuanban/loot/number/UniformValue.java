package io.ib67.yuanban.loot.number;

/**
 * 均匀分布：原版 {@code providers.number.UniformGenerator}，JSON 字段 {@code min}、{@code max}。
 */
public record UniformValue(NumberProvider min, NumberProvider max) implements NumberProvider {
}
