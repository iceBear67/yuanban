package io.ib67.yuanban.loot.condition;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 原版 {@code weather_check}：按当前天气判定。JSON 字段 {@code raining}、{@code thundering}（均可缺省）。
 */
public record WeatherCheckCondition(Optional<Boolean> isRaining, Optional<Boolean> isThundering) implements LootCondition {
    public WeatherCheckCondition {
        if (isRaining == null) isRaining = Optional.empty();
        if (isThundering == null) isThundering = Optional.empty();
    }

    public WeatherCheckCondition(@Nullable Boolean isRaining, @Nullable Boolean isThundering) {
        this(Optional.ofNullable(isRaining), Optional.ofNullable(isThundering));
    }

    public WeatherCheckCondition() {
        this(Optional.empty(), Optional.empty());
    }
}
