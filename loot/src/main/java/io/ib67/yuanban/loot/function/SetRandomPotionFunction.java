package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code set_random_potion}：随机选一种药水。JSON 字段 {@code options}
 * （可选；缺省时所有原版药水）。
 */
public record SetRandomPotionFunction(List<LootCondition> conditions,
                                      Optional<EnchantedOptions> options) implements LootFunction {
    public SetRandomPotionFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        options = options == null ? Optional.empty() : options;
    }

    public SetRandomPotionFunction(List<LootCondition> conditions) {
        this(conditions, Optional.empty());
    }
}
