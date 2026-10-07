package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;

import java.util.List;
import java.util.Set;

/**
 * 原版 {@code copy_name}：把来源实体/方块实体的名字写到物品的 custom name。JSON 字段 {@code source}。
 */
public record CopyNameFunction(List<LootCondition> conditions, CopyNameSource source) implements LootFunction {
    public CopyNameFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    /**
     * 对应原版 {@code CopyNameFunction#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return LootFunctions.sourceParam(source);
    }
}
