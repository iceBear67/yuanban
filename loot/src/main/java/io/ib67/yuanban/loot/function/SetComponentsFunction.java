package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.minestom.server.component.DataComponentMap;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_components}：把组件补丁应用到物品堆。JSON 字段 {@code components}（原版
 * {@code DataComponentPatch}）。Minestom 用 {@link DataComponentMap} 表示补丁，值为 {@code null} 的项表示移除该组件。
 */
public record SetComponentsFunction(List<LootCondition> conditions, DataComponentMap components)
        implements LootFunction {
    public SetComponentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(components, "components");
    }
}
