package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.key.Key;

/**
 * bot 进入的一个世界。
 *
 * @param epoch           每个 bot 从 1 开始，每进入一个世界加一
 * @param dimension       维度名称
 * @param dimensionTypeId 维度类型在服务端注册表中的编号
 * @param cause           进入的原因
 */
public record WorldInfo(long epoch, Key dimension, int dimensionTypeId, WorldEntryCause cause) {
}
