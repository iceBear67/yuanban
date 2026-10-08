package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContextArg;
import net.minestom.server.component.DataComponent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code copy_components}：从上下文实体、方块实体或工具复制组件到物品。JSON 字段
 * {@code source}（实体目标名、{@code block_entity} 或 {@code tool}）、{@code include}（可选，组件类型列表）与
 * {@code exclude}（可选，组件类型列表）。
 */
public record CopyComponentsFunction(List<LootCondition> conditions,
                                     LootContextArg source,
                                     Optional<List<DataComponent<?>>> include,
                                     Optional<List<DataComponent<?>>> exclude) implements LootFunction {
    public CopyComponentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(source, "source");
        include = include.map(List::copyOf);
        exclude = exclude.map(List::copyOf);
    }

    /**
     * 对应原版 {@code CopyComponentsFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(source.contextParam());
    }
}
