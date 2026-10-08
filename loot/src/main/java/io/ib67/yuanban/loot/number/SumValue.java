package io.ib67.yuanban.loot.number;

import java.util.List;

/**
 * 求和：原版 {@code providers.number.Sum}，JSON 字段 {@code summands}（数字提供器列表）。
 */
public record SumValue(List<NumberProvider> summands) implements NumberProvider {
    public SumValue {
        summands = List.copyOf(summands);
    }
}
