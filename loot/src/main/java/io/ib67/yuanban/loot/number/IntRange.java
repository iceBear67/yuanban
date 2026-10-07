package io.ib67.yuanban.loot.number;

import org.jetbrains.annotations.Nullable;

/**
 * 整数区间：原版 {@code net.minecraft.world.level.storage.loot.IntRange}。
 * JSON 形态是 {@code {min?, max?}} 或裸整数；本模块只承载 {@code min} / {@code max} 两个边界，
 * 裸整数形态的展开在 loot-table-parser 中完成。
 */
public record IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
}
