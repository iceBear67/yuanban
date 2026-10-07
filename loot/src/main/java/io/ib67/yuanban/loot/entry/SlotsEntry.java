package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;

import java.util.List;

/**
 * 原版 {@code slots}：从槽位来源取物品。JSON 独有字段 {@code slot_source}。
 * 原版 26.2 的原版数据没有使用这一条目；求值器遇到时按 DESIGN 第 7 节抛出"当前版本不支持 slots 条目"。
 */
public record SlotsEntry(List<LootCondition> conditions,
                         List<LootFunction> functions,
                         int weight,
                         int quality,
                         SlotSource slotSource) implements SingletonEntry {
    public SlotsEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }
}
