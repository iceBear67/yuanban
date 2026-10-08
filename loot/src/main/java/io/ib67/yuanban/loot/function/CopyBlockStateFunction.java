package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.condition.LootCondition;
import io.ib67.yuanban.loot.context.ContextKey;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 原版 {@code copy_state}：把方块状态的属性复制到物品的 {@code block_state} 组件。JSON 字段
 * {@code block}（方块 ID，按方块注册表解码）、{@code properties}（属性名列表）。
 * 原版构造时丢掉 {@code block} 没有的属性名，求值时只复制上下文方块状态拥有的属性。
 */
public record CopyBlockStateFunction(List<LootCondition> conditions,
                                     Block block,
                                     List<String> properties) implements LootFunction {
    public CopyBlockStateFunction {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        Objects.requireNonNull(block, "block");
        properties = List.copyOf(properties);
    }

    /**
     * 对应原版 {@code CopyBlockState#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.BLOCK_STATE);
    }
}
