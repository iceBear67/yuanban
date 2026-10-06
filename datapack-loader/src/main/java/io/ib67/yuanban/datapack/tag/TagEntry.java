package io.ib67.yuanban.datapack.tag;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/**
 * 标签文件 {@code values} 中的一项。
 *
 * @param id       元素 ID，或者 {@code tag} 为真的时候被引用标签的 ID
 * @param tag      是否以 {@code #} 引用另一个标签
 * @param required 找不到的时候是否算作错误
 */
public record TagEntry(Key id, boolean tag, boolean required) {
    public TagEntry {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public String toString() {
        return (tag ? "#" : "") + id.asString() + (required ? "" : "?");
    }
}
