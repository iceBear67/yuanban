package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.context.LootContextArg;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code copy_name}：把上下文实体或方块实体的自定义名称写到物品的 {@code custom_name}。
 * JSON 字段 {@code source}（实体目标名或 {@code block_entity}）。
 */
public record CopyNameFunction(List<LootCondition> conditions, LootContextArg source) implements LootFunction {
    public CopyNameFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(source, "source");
    }

    /**
     * 对应原版 {@code CopyNameFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(source.contextParam());
    }
}
