package io.ib67.yuanban.loot.number;

import net.kyori.adventure.key.Key;

/**
 * 命令存储取值：原版 {@code providers.number.StorageValue}，JSON 字段 {@code storage}（命名空间 ID）、
 * {@code path}（NBT 路径字符串）。
 */
public record StorageValue(Key storage, String path) implements NumberProvider {
}
