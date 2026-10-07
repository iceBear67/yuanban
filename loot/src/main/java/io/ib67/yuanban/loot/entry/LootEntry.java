package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;

import java.util.List;

/**
 * 战利品条目：战利品池中的元素。原版 26.2 注册的 10 种条目对应的 record 在本包下声明。
 */
public sealed interface LootEntry permits SingletonEntry, CompositeEntry {
    /**
     * 条目生效要满足的条件列表。原版 JSON 字段名 {@code conditions}，缺省为空。
     */
    List<LootCondition> conditions();

    /**
     * 条目产出之后追加应用的修饰器。原版 JSON 字段名 {@code functions}，缺省为空。
     */
    List<LootFunction> functions();
}
