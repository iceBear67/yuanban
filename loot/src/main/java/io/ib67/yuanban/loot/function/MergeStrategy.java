package io.ib67.yuanban.loot.function;

/**
 * {@code copy_custom_data} 的合并策略：原版 {@code CopyCustomDataFunction$MergeStrategy}。
 * JSON 取值 {@code replace}、{@code append}、{@code merge}。
 */
public enum MergeStrategy {
    REPLACE,
    APPEND,
    MERGE
}
