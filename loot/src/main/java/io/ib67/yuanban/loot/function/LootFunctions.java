package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.context.ContextKey;

import java.util.Set;

/**
 * 物品修饰器共用的引用参数工具。
 */
final class LootFunctions {

    private LootFunctions() {
    }

    /**
     * {@code copy_name} / {@code copy_components} 的 {@code source} 对应的上下文参数：
     * 实体来源对应原版 {@code LootContextArg} 实体实现的参数，方块实体来源对应 {@code BLOCK_ENTITY}。
     */
    static Set<ContextKey<?>> sourceParam(CopyNameSource source) {
        if (source instanceof CopyNameSource.Entity entity) {
            return Set.of(entity.target().contextParam());
        }
        return Set.of(ContextKey.BLOCK_ENTITY);
    }
}
