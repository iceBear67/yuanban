package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.slot.ContainerComponent;

import java.util.List;
import java.util.Objects;

/**
 * 原版 {@code modify_contents}：对容器组件中每个不为空的物品施加修饰器。JSON 字段 {@code component}
 * （{@code container}、{@code bundle_contents} 或 {@code charged_projectiles}）、{@code modifier}。
 */
public record ModifyContentsFunction(List<LootCondition> conditions,
                                     ContainerComponent component,
                                     LootFunction modifier) implements LootFunction {
    public ModifyContentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(modifier, "modifier");
    }

    @Override
    public List<LootFunction> children() {
        return List.of(modifier);
    }
}
