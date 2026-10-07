package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.number.IntRange;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 原版 {@code time_check}：时钟读数落在区间内。JSON 字段 {@code clock}、{@code period}、{@code value}。
 */
public record TimeCheckCondition(Key clock, Optional<Long> period, IntRange value) implements LootCondition {
    public TimeCheckCondition {
        period = period == null ? Optional.empty() : period;
    }

    public TimeCheckCondition(Key clock, @Nullable Long period, IntRange value) {
        this(clock, Optional.ofNullable(period), value);
    }

    public TimeCheckCondition(Key clock, IntRange value) {
        this(clock, Optional.empty(), value);
    }
}
