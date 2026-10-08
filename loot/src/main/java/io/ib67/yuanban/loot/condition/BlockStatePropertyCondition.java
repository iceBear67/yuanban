package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import io.ib67.yuanban.loot.predicate.StatePropertiesPredicate;
import net.minestom.server.instance.block.Block;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code block_state_property}：被挖掘方块是指定方块且状态属性匹配。JSON 字段 {@code block}
 * （方块 ID，按方块注册表解码）、{@code properties}（可选，原版 {@code StatePropertiesPredicate}）。
 * 解码时检查每个属性名都是该方块的属性。
 *
 * @param block 方块，只比较方块类型，与状态无关
 */
public record BlockStatePropertyCondition(Block block, Optional<StatePropertiesPredicate> properties)
        implements LootCondition {
    public BlockStatePropertyCondition {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(properties, "properties");
    }

    /**
     * 对应原版 {@code LootItemBlockStatePropertyCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.BLOCK_STATE);
    }
}
