package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.entry.LootEntry;
import io.ib67.yuanban.loot.slot.ContainerComponent;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code set_contents}：用战利品条目产出的物品替换容器组件的内容。JSON 字段 {@code component}
 * （{@code container}、{@code bundle_contents} 或 {@code charged_projectiles}）、{@code entries}（战利品条目列表）。
 */
public record SetContainerContentsFunction(List<LootCondition> conditions,
                                           ContainerComponent component,
                                           List<LootEntry> entries) implements LootFunction {
    public SetContainerContentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(component, "component");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
