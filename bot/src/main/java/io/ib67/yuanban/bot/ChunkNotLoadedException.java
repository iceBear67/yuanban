package io.ib67.yuanban.bot;

/**
 * 读取了 bot 的世界模型中没有加载的区块。
 */
public final class ChunkNotLoadedException extends RuntimeException {
    private final int chunkX;
    private final int chunkZ;

    public ChunkNotLoadedException(int chunkX, int chunkZ) {
        super("chunk " + chunkX + ", " + chunkZ + " is not loaded");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }
}
