package io.ib67.yuanban.loot.function;

/**
 * 列表修改操作：原版 {@code net.minecraft.world.level.storage.loot.functions.ListOperation}。
 * 四种操作与原版 sealed 集合一一对应：{@code append}、{@code insert}、{@code replace_all}、
 * {@code replace_section}。
 */
public sealed interface ListOperation
        permits AppendOperation, InsertOperation, ReplaceAllOperation, ReplaceSectionOperation {
}
