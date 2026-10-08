package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.NumberProvider;

import java.util.List;

/**
 * 原版 {@code set_ominous_bottle_amplifier}：写入不祥之瓶的效果等级，结果限制在 0 到 4。JSON 字段
 * {@code amplifier}（数字提供器）。
 */
public record SetOminousBottleAmplifierFunction(List<LootCondition> conditions,
                                                NumberProvider amplifier) implements LootFunction {
    public SetOminousBottleAmplifierFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
