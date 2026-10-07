package io.ib67.yuanban.loot.function;

/**
 * 原版 {@code append}：把值加到列表末尾。原版是单例。
 */
public record AppendOperation() implements ListOperation {
    public static final AppendOperation INSTANCE = new AppendOperation();
}
