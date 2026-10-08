package io.ib67.yuanban.loot.function;

/**
 * 列表修改操作：原版 {@code net.minecraft.world.level.storage.loot.functions.ListOperation}。
 * 四种操作与原版 {@code ListOperation$Type} 一一对应：{@code replace_all}、{@code replace_section}、
 * {@code insert}、{@code append}。JSON 中操作内联在所属对象里，由必填的 {@code mode} 字段选择。
 */
public sealed interface ListOperation
        permits AppendOperation, InsertOperation, ReplaceAllOperation, ReplaceSectionOperation {
}
