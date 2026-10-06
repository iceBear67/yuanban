package io.ib67.yuanban.datapack.tag;

import io.ib67.yuanban.datapack.pack.PackInfo;

import java.util.Objects;

/**
 * 合并之后的标签条目，带有所属数据包。
 */
public record TagValue(TagEntry entry, PackInfo pack) {
    public TagValue {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(pack, "pack");
    }
}
