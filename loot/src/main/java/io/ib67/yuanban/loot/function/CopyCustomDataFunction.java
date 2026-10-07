package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;

import java.util.List;

/**
 * 原版 {@code copy_custom_data}：把 NBT 来源的数据按给定路径复制到物品的自定义数据。JSON 字段
 * {@code source}、{@code ops}（{@link CopyOperation} 列表）。
 */
public record CopyCustomDataFunction(List<LootCondition> conditions,
                                     NbtSource source,
                                     List<CopyOperation> ops) implements LootFunction {
    public CopyCustomDataFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        ops = ops == null ? List.of() : List.copyOf(ops);
    }
}
