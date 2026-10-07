package io.ib67.yuanban.loot.function;

/**
 * 原版 {@code insert}：把值插入到列表 {@code offset} 处。
 */
public record InsertOperation(int offset) implements ListOperation {
}
