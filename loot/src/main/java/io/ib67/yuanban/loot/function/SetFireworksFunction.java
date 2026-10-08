package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.item.component.FireworkExplosion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code set_fireworks}：修改烟花火箭。JSON 字段 {@code explosions}（可选，爆炸效果的
 * {@code ListOperation$StandAlone}，最多 256 个）、{@code flight_duration}（可选，无符号字节）。
 * 缺省的项保持物品原有的值。
 */
public record SetFireworksFunction(List<LootCondition> conditions,
                                   Optional<StandAloneOperation<FireworkExplosion>> explosions,
                                   Optional<Integer> flightDuration) implements LootFunction {
    public SetFireworksFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(explosions, "explosions");
        Objects.requireNonNull(flightDuration, "flightDuration");
    }

    public SetFireworksFunction(List<LootCondition> conditions) {
        this(conditions, Optional.empty(), Optional.empty());
    }
}
