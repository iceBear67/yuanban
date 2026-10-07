package io.ib67.yuanban.loot.function;

import java.util.Optional;

/**
 * 原版 {@code replace_section}：替换列表中 {@code offset} 起的长为 {@code size} 的区段；
 * {@code size} 缺省时替换到列表末尾。
 */
public record ReplaceSectionOperation(int offset, Optional<Integer> size) implements ListOperation {
    public ReplaceSectionOperation {
        size = size == null ? Optional.empty() : size;
    }

    public ReplaceSectionOperation(int offset, Integer size) {
        this(offset, Optional.ofNullable(size));
    }

    public ReplaceSectionOperation(int offset) {
        this(offset, Optional.empty());
    }
}
