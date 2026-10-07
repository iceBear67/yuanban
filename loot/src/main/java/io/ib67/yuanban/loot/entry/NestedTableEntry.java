package io.ib67.yuanban.loot.entry;

import io.ib67.yuanban.loot.EitherId;
import io.ib67.yuanban.loot.LootTable;
import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.function.LootFunction;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code loot_table}：嵌套取另一张战利品表的产物。JSON 独有字段 {@code value}，可以是表 ID
 * 或一张内联战利品表（由 {@link EitherId} 表达；原版用 {@code Either}）。
 */
public record NestedTableEntry(List<LootCondition> conditions,
                               List<LootFunction> functions,
                               int weight,
                               int quality,
                               EitherId<LootTable> value) implements SingletonEntry {
    public NestedTableEntry {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        functions = functions == null ? List.of() : List.copyOf(functions);
    }

    public NestedTableEntry(List<LootCondition> conditions, List<LootFunction> functions,
                            int weight, int quality, Key tableId) {
        this(conditions, functions, weight, quality, EitherId.ofLeft(tableId));
    }

    public NestedTableEntry(List<LootCondition> conditions, List<LootFunction> functions,
                            int weight, int quality, LootTable inline) {
        this(conditions, functions, weight, quality, EitherId.ofRight(inline));
    }
}
