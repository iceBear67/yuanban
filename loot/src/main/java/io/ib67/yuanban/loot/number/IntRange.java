package io.ib67.yuanban.loot.number;

import org.jetbrains.annotations.Nullable;

/**
 * 整数区间：原版 {@code net.minecraft.world.level.storage.loot.IntRange}。
 * JSON 形态是裸整数（两端都是这个常数）或 {@code {min?, max?}}，两端都是数字提供器，求值时取整数值。
 * 原版先尝试裸整数形态。
 */
public record IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
}
