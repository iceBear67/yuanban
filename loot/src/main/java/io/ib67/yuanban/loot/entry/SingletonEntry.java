package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;

/**
 * 单例条目：一次抽取中作为独立候选项产生物品的条目，对应原版
 * {@code LootPoolSingletonContainer}。
 */
public sealed interface SingletonEntry extends LootEntry
        permits ItemEntry, NestedTableEntry, DynamicEntry, EmptyEntry, SlotsEntry, TagEntry {
    /**
     * 加权抽取的权重。原版 JSON 字段名 {@code weight}，缺省 {@code 1}。
     */
    int weight();

    /**
     * 受运气影响的品质。原版 JSON 字段名 {@code quality}，缺省 {@code 0}。
     */
    int quality();
}
