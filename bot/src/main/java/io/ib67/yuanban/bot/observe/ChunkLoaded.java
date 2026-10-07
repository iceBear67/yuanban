package io.ib67.yuanban.bot.observe;

/**
 * 区块安装到世界模型中。
 */
public record ChunkLoaded(int x, int z) implements Observation {
}
