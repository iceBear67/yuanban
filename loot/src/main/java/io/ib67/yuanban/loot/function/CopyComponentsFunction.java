package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code copy_components}：从来源实体/方块实体复制选中的组件到物品。JSON 字段
 * {@code source}、{@code include}（可选；名单里的组件 ID）与 {@code exclude}
 * （可选；不可同时给 {@code include}）。
 */
public record CopyComponentsFunction(List<LootCondition> conditions,
                                     CopyNameSource source,
                                     Optional<List<Key>> include,
                                     Optional<List<Key>> exclude) implements LootFunction {
    public CopyComponentsFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        include = include == null ? Optional.empty() : include.map(List::copyOf);
        exclude = exclude == null ? Optional.empty() : exclude.map(List::copyOf);
    }

    public CopyComponentsFunction(List<LootCondition> conditions, CopyNameSource source) {
        this(conditions, source, Optional.empty(), Optional.empty());
    }

    /**
     * 对应原版 {@code CopyComponentsFunction#getReferencedContextParams}
     * （实体来源对应原版 {@code LootContextArg} 实体实现）。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return LootFunctions.sourceParam(source);
    }
}
