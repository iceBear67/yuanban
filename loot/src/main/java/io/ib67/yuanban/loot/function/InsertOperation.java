package io.ib67.yuanban.loot.function;

/**
 * 原版 {@code insert}：把新值插入到列表的 {@code offset}（默认 0，非负整数）处。
 */
public record InsertOperation(int offset) implements ListOperation {
}
