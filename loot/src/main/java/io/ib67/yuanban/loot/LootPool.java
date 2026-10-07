package io.ib67.yuanban.loot;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.number.ConstantValue;
import io.ib67.yuanban.loot.number.NumberProvider;

import java.util.List;
import java.util.Objects;

/**
 * 战利品池：战利品表中的一个列表项，对应原版 {@code net.minecraft.world.level.storage.loot.LootPool}。
 * 每次取用先按 {@link #rolls()} 确定抽取次数，每次从池内加权随机选一个条目。
 *
 * @param bonusRolls 额外抽取次数的来源，默认 {@code constant(0)}；实际抽取次数是 {@code rolls + floor(bonusRolls * luck)}
 */
public record LootPool(List<LootCondition> conditions,
                       List<LootFunction> functions,
                       NumberProvider rolls,
                       NumberProvider bonusRolls,
                       List<LootEntry> entries) {
    public LootPool {
        Objects.requireNonNull(rolls, "rolls");
        Objects.requireNonNull(bonusRolls, "bonusRolls");
        conditions = List.copyOf(conditions);
        functions = List.copyOf(functions);
        entries = List.copyOf(entries);
    }

    /**
     * 不带额外抽取次数的池，{@code bonusRolls} 取默认的 {@code constant(0)}。
     */
    public LootPool(List<LootCondition> conditions, List<LootFunction> functions,
                    NumberProvider rolls, List<LootEntry> entries) {
        this(conditions, functions, rolls, ConstantValue.ZERO, entries);
    }
}
