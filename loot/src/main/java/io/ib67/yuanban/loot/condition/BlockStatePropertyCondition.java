package io.ib67.yuanban.loot.condition;

import io.ib67.yuanban.loot.context.ContextKey;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 原版 {@code block_state_property}：被挖掘方块的状态与属性匹配。JSON 字段 {@code block}、
 * {@code properties}（原版 {@code StatePropertiesPredicate}）。
 */
public record BlockStatePropertyCondition(Key block, Optional<BlockPredicate> properties) implements LootCondition {
    public BlockStatePropertyCondition {
        properties = properties == null ? Optional.empty() : properties;
    }

    public BlockStatePropertyCondition(Key block, @Nullable BlockPredicate properties) {
        this(block, Optional.ofNullable(properties));
    }

    /**
     * 对应原版 {@code LootItemBlockStatePropertyCondition#getReferencedContextParams}。
     */
    @Override
    public Set<ContextKey<?>> referencedParams() {
        return Set.of(ContextKey.BLOCK_STATE);
    }
}
