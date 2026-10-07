package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Set;

/**
 * 原版 {@code apply_bonus}：按魔咒等级加成物品数量。JSON 字段 {@code enchantment}、
 * {@code formula}（三选一），其余参数按公式拆开嵌在原版 {@code parameters} 里，本模块由
 * {@link ApplyBonusFormula} 承载。
 */
public record ApplyBonusFunction(List<LootCondition> conditions,
                                 Key enchantment,
                                 ApplyBonusFormula formula) implements LootFunction {
    public ApplyBonusFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    /**
     * 对应原版 {@code ApplyBonusCount#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.TOOL);
    }
}
