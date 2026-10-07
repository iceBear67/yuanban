package io.ib67.yuanban.loot.function;

import io.ib67.yuanban.loot.number.EntityTarget;

/**
 * {@code copy_name} / {@code copy_components} 的来源：原版用 {@code LootContextArg} 承载，
 * 解码为 {@code entity} 或 {@code block_entity}。本模块用两个 record 表达，序列化时按枚举
 * 名称小写回原版的 {@code EntityTarget} 名字；方块实体由 {@link BlockEntity} 承载。
 */
public sealed interface CopyNameSource {

    /**
     * 原版 {@code LootContextArg} 里从上下文实体取值的情况。
     */
    record Entity(EntityTarget target) implements CopyNameSource {
    }

    /**
     * 原版 {@code LootContextArg} 里从上下文方块实体取值的情况（JSON 中常量值
     * {@code "block_entity"}）。
     */
    record BlockEntity() implements CopyNameSource {
        public static final BlockEntity INSTANCE = new BlockEntity();
    }
}
