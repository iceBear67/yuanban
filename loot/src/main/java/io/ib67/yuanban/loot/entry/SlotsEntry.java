package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import io.ib67.yuanban.loot.slot.SlotSource;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code slots}：从槽位来源取出物品，每个不为空的物品分别经过条目、池、表的修饰器。
 * JSON 独有字段 {@code slot_source}（原版 {@code SlotSources.CODEC}）。
 */
public record SlotsEntry(List<LootCondition> conditions,
                         List<LootFunction> functions,
                         int weight,
                         int quality,
                         SlotSource slotSource) implements SingletonEntry {
    public SlotsEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
        Objects.requireNonNull(slotSource, "slotSource");
    }
}
