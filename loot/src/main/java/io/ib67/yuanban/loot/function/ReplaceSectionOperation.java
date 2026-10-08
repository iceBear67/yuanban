package io.ib67.yuanban.loot.function;

import java.util.Optional;

/**
 * 原版 {@code replace_section}：从 {@code offset}（默认 0）起移除 {@code size} 个元素并在原处放入新值；
 * {@code size} 缺省时等于新值的个数。两项都是非负整数，解码时检查 {@code size} 不超过所属字段允许的最大长度。
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
