package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.NumberProvider;

import java.util.List;

/**
 * 原版 {@code set_random_dyes}：随机染色若干次。JSON 字段 {@code number_of_dyes}（数字提供器）。
 */
public record SetRandomDyesFunction(List<LootCondition> conditions,
                                    NumberProvider numberOfDyes) implements LootFunction {
    public SetRandomDyesFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
