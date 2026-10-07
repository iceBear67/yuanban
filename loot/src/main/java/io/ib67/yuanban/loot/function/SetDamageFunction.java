package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.number.NumberProvider;

import java.util.List;

/**
 * 原版 {@code set_damage}：设定耐久损耗比例。JSON 字段 {@code damage}
 * （{@code [0,1]} 的数字提供器）、{@code add}（默认 {@code false}）。
 */
public record SetDamageFunction(List<LootCondition> conditions, NumberProvider damage, boolean add)
        implements LootFunction {
    public SetDamageFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public SetDamageFunction(List<LootCondition> conditions, NumberProvider damage) {
        this(conditions, damage, false);
    }
}
