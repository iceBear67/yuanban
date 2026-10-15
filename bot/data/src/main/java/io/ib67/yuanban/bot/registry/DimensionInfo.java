package io.ib67.yuanban.bot.registry;

import net.kyori.adventure.key.Key;

/**
 * 维度类型中本库使用的内容
 *
 * @param name   注册表中的名称
 * @param minY   最低 y
 * @param height 高度，区段数量 = 高度 / 16（wiki Chunk format）
 */
public record DimensionInfo(Key name, int minY, int height) {
}
