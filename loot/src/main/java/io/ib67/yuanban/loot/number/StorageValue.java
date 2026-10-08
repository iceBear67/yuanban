package io.ib67.yuanban.loot.number;

import io.ib67.yuanban.codec.NbtPath;
import net.kyori.adventure.key.Key;

import java.util.Objects;

/**
 * 命令存储取值：原版 {@code providers.number.StorageValue}，JSON 字段 {@code storage}（命名空间 ID）、
 * {@code path}（NBT 路径，按原版 {@code NbtPathArgument} 的语法解析）。路径恰好选中一个数值标签时取它的值，否则为 0。
 */
public record StorageValue(Key storage, NbtPath path) implements NumberProvider {
    public StorageValue {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(path, "path");
    }
}
