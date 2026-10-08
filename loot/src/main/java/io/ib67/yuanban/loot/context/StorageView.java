package io.ib67.yuanban.loot.context;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;

/**
 * 命令存储的只读视图，对应原版 {@code CommandStorage}。底层容器由宿主实现。
 */
public interface StorageView {
    /**
     * 整个存储的根标签，对应原版 {@code CommandStorage#get(Identifier)}：存储不存在时返回空的复合标签。
     * {@code storage} 数字提供器和 {@code copy_custom_data} 的 {@code storage} 来源从这里按 NBT 路径读取。
     */
    CompoundBinaryTag root(Key storage);
}
