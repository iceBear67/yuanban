package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.number.IntRange;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.clock.WorldClock;

import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code time_check}：时钟的总刻数（给出 {@code period} 时取余数）落在区间内。JSON 字段 {@code clock}
 * （必填，世界时钟 ID，按世界时钟注册表检查）、{@code period}（可选）、{@code value}（原版 {@code IntRange}）。
 */
public record TimeCheckCondition(RegistryKey<WorldClock> clock, Optional<Long> period, IntRange value)
        implements LootCondition {
    public TimeCheckCondition {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(value, "value");
    }
}
