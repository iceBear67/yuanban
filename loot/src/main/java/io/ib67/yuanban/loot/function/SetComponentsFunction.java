package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.component.DataComponentMap;

import java.util.List;

/**
 * 原版 {@code set_components}：把组件补丁应用到物品堆。JSON 字段 {@code components}。
 * 承载 Minestom 的 {@code DataComponentMap}（minestom 没有独立 patch 类型，凸包内放置即可表示一个补丁），
 * 原版的 {@code DataComponentPatch} 编解码在 loot-table-parser 完成。
 */
public record SetComponentsFunction(List<LootCondition> conditions, DataComponentMap components)
        implements LootFunction {
    public SetComponentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}
