package io.ib67.yuanban.datapack.pack;

import java.util.Objects;

/**
 * 包含两端的格式版本范围。
 */
public record FormatRange(PackFormat min, PackFormat max) {
    public FormatRange {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("min (" + min + ") is greater than max (" + max + ")");
        }
    }

    public boolean contains(PackFormat format) {
        return format.compareTo(min) >= 0 && format.compareTo(max) <= 0;
    }

    @Override
    public String toString() {
        return "[" + min + ", " + max + "]";
    }
}
