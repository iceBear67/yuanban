package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.world.SectionRef;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.util.Map;

/**
 * 入站线程解码出的区块：区块坐标、从维度最低 y 开始的区段、方块实体的 NBT。
 *
 * @param blockEntities 以 {@code CoordConversion.chunkBlockIndex} 为键、发给客户端的方块实体 NBT
 */
public record DecodedChunk(int x, int z, SectionRef[] sections, Map<Integer, CompoundBinaryTag> blockEntities) {
}
