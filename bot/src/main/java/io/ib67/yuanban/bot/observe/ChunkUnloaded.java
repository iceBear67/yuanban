package io.ib67.yuanban.bot.observe;

/**
 * 区块从世界模型中卸载：Unload Chunk，或者落到加载区域之外。
 */
public record ChunkUnloaded(int x, int z) implements Observation {
}
