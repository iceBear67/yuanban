package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;

import java.util.List;
import java.util.Set;

/**
 * 物品修饰器：原版 JSON 字段名 {@code functions}。原版 26.2 注册的 43 种修饰器对应的 record 在本包下声明。
 * {@link #children()} 暴露修饰器内嵌套的子修饰器（例如 {@code sequence} 的 {@code functions}、
 * {@code filtered} 的 {@code on_pass}、{@code modify_contents} 的 {@code modifier}），
 * 供 {@code LootContents} 收集内联表与标签引用。
 */
public sealed interface LootFunction
        permits ApplyBonusFunction, CopyBlockStateFunction, CopyComponentsFunction, CopyCustomDataFunction,
        CopyNameFunction, DiscardFunction, EnchantRandomlyFunction, EnchantWithLevelsFunction,
        EnchantedCountIncreaseFunction, ExplorationMapFunction, ExplosionDecayFunction, FillPlayerHeadFunction,
        FilteredFunction, FunctionReference, LimitCountFunction, ModifyContentsFunction, SequenceFunction,
        SetAttributesFunction, SetBannerPatternFunction, SetBookCoverFunction, SetComponentsFunction,
        SetContainerContentsFunction, SetContainerLootTableFunction, SetCountFunction, SetCustomDataFunction,
        SetCustomModelDataFunction, SetDamageFunction, SetEnchantmentsFunction, SetFireworkExplosionFunction,
        SetFireworksFunction, SetInstrumentFunction, SetItemFunction, SetLoreFunction, SetNameFunction,
        SetOminousBottleAmplifierFunction, SetPotionFunction, SetRandomDyesFunction, SetRandomPotionFunction,
        SetStewEffectFunction, SetWritableBookPagesFunction, SetWrittenBookPagesFunction, SmeltFunction,
        ToggleTooltipsFunction {

    /**
     * 原版 JSON 字段名 {@code conditions}：修饰器生效要满足的条件列表，缺省为空。
     * 原版除 {@code sequence} 之外的修饰器都有这个字段（{@code LootItemConditionalFunction#commonFields}）。
     */
    List<LootCondition> conditions();

    /**
     * 修饰器内嵌套的子修饰器；默认不存在。
     */
    default List<LootFunction> children() {
        return List.of();
    }

    /**
     * 求值时要求上下文提供的参数集合，对应原版 {@code LootContextUser#getReferencedContextParams}；
     * 缺省不引用任何参数。
     */
    default Set<ContextKey<?>> referencedParams() {
        return Set.of();
    }
}
