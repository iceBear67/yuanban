package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.codec.HolderSet;
import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.potion.PotionType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 原版 {@code set_random_potion}：随机选一种药水。JSON 字段 {@code options}
 * （可选，药水集合；缺省时为药水注册表中的全部药水）。
 */
public record SetRandomPotionFunction(List<LootCondition> conditions,
                                      Optional<HolderSet<PotionType>> options) implements LootFunction {
    public SetRandomPotionFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(options, "options");
    }

    public SetRandomPotionFunction(List<LootCondition> conditions) {
        this(conditions, Optional.empty());
    }
}
