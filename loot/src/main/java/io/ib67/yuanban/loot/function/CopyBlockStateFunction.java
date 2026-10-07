package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Set;

/**
 * 原版 {@code copy_state}：把方块状态属性复制到 {@code block_state} 组件。JSON 字段
 * {@code block}、{@code properties}（要复制的属性名字集合）。
 */
public record CopyBlockStateFunction(List<LootCondition> conditions,
                                     Key block,
                                     Set<String> properties) implements LootFunction {
    public CopyBlockStateFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        properties = properties == null ? Set.of() : Set.copyOf(properties);
    }

    /**
     * 对应原版 {@code CopyBlockState#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.BLOCK_STATE);
    }
}
