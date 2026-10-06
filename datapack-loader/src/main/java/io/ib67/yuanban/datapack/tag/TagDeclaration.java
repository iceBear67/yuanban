package io.ib67.yuanban.datapack.tag;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Objects;

/**
 * 一个标签在全部数据包中合并之后的条目，顺序是数据包优先级从低到高、文件内从前到后。
 */
public record TagDeclaration(Key id, List<TagValue> values) {
    public TagDeclaration {
        Objects.requireNonNull(id, "id");
        values = List.copyOf(values);
    }
}
