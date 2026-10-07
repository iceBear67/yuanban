package io.ib67.yuanban.loot.number;

import java.util.List;

/**
 * 求和：原版 {@code providers.number.Sum}，JSON 字段 {@code terms}（数字提供器列表）。
 */
public record SumValue(List<NumberProvider> terms) implements NumberProvider {
    public SumValue {
        terms = List.copyOf(terms);
    }
}
