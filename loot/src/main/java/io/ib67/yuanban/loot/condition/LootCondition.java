package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 战利品条件：原版 JSON 字段名 {@code conditions}。原版 26.2 注册的 20 种条件对应的 record 在本包下声明。
 */
public sealed interface LootCondition
        permits AllOfCondition, AnyOfCondition, BlockStatePropertyCondition, DamageSourcePropertiesCondition,
        EnchantmentActiveCheckCondition, EntityPropertiesCondition, EntityScoresCondition,
        EnvironmentAttributeCheckCondition, InvertedCondition, KilledByPlayerCondition, LocationCheckCondition,
        MatchToolCondition, RandomChanceCondition, RandomChanceWithEnchantedBonusCondition, ReferenceCondition,
        SurvivesExplosionCondition, TableBonusCondition, TimeCheckCondition, ValueCheckCondition, WeatherCheckCondition {

    /**
     * 求值时要求上下文提供的参数集合，对应原版 {@code LootContextUser#getReferencedContextParams}；
     * 缺省不引用任何参数。
     */
    default Set<ContextKey<?>> referencedParams() {
        return Set.of();
    }
}
