package io.ib67.yuanban.datapack.tag;

import net.kyori.adventure.key.Key;

import java.util.NavigableMap;

/**
 * 一个注册表的全部标签，按 {@link Key} 的自然顺序排列。
 */
public interface TagDeclarations {
    NavigableMap<Key, TagDeclaration> tags();
}
