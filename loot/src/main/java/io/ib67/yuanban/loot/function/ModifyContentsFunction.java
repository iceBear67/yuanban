package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * 原版 {@code modify_contents}：对容器组件内容遍历施加修饰器。JSON 字段 {@code component}
 * （容器组件 ID，例如 {@code minecraft:container}）、{@code modifier}。
 */
public record ModifyContentsFunction(List<LootCondition> conditions,
                                     Key component,
                                     LootFunction modifier) implements LootFunction {
    public ModifyContentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    @Override
    public List<LootFunction> children() {
        return List.of(modifier);
    }
}
