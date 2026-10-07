package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 数字提供器：战利品表中数值的来源。原版 26.2 注册的 8 种数字提供器对应的 record 在本包下声明。
 */
public sealed interface NumberProvider
        permits ConstantValue, UniformValue, BinomialValue, ScoreboardValue, StorageValue, SumValue,
        EnchantmentLevelValue, EnvironmentAttributeValue {

    /**
     * 求值时要求上下文提供的参数集合，对应原版 {@code LootContextUser#getReferencedContextParams}；
     * 缺省不引用任何参数。
     */
    default Set<ContextKey<?>> referencedParams() {
        return Set.of();
    }
}
