package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code set_fireworks}：写入烟花火箭的发射配置。JSON 字段 {@code explosions}
 * （可选）、{@code flight_duration}（可选）。
 */
public record SetFireworksFunction(List<LootCondition> conditions,
                                   Optional<StandAloneOperation<FireworkExplosionShape>> explosions,
                                   Optional<Integer> flightDuration) implements LootFunction {
    public SetFireworksFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        explosions = explosions == null ? Optional.empty() : explosions;
        flightDuration = flightDuration == null ? Optional.empty() : flightDuration;
    }

    public SetFireworksFunction(List<LootCondition> conditions) {
        this(conditions, Optional.empty(), Optional.empty());
    }
}
