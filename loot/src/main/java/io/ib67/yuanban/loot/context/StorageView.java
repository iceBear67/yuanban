package io.ib67.yuanban.loot.context;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 命令存储的只读视图：loot 求值期 {@code storage} 数字提供器读取的最小面，对应原版
 * {@code CommandStorage#getTag(String)}。底层容器由宿主实现。
 * <p>
 * Minestom 26.2 已经直接携带 adventure-nbt（{@code ItemStack#fromItemNBT} 使用
 * {@code CompoundBinaryTag}），因此元素类型直接是它，不再需要占位类型。
 */
public interface StorageView {
    /**
     * 读取 {@code storage} 下 {@code path}（原版 SNBT 路径语法）指向的 NBT 值；
     * 只有读到的值是复合标签时才返回它，其余类型（数值、字符串）与路径缺失一样按空处理，
     * 对应原版 {@code NumberProvider} 的 storage 读取只对复合值生效的行为。
     */
    Optional<@Nullable CompoundBinaryTag> read(Key storage, String path);
}
