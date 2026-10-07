package io.ib67.yuanban.bot.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.jetbrains.annotations.Nullable;

/**
 * 方块实体的 NBT，按方块坐标保存。坐标按原版 {@code BlockPos.asLong} 的布局编码（x、z 各 26 位，y 12 位）。
 */
public final class BlockEntityStore {
    private final Long2ObjectMap<CompoundBinaryTag> entries = new Long2ObjectOpenHashMap<>();

    public @Nullable CompoundBinaryTag get(int x, int y, int z) {
        return entries.get(encode(x, y, z));
    }

    public void put(int x, int y, int z, CompoundBinaryTag nbt) {
        entries.put(encode(x, y, z), nbt);
    }

    public void remove(int x, int y, int z) {
        entries.remove(encode(x, y, z));
    }

    /**
     * 删除一个区块列中的全部方块实体
     */
    public void removeChunk(int chunkX, int chunkZ) {
        entries.keySet().removeIf((long key) -> (decodeX(key) >> 4) == chunkX && (decodeZ(key) >> 4) == chunkZ);
    }

    private static long encode(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int decodeX(long key) {
        return (int) (key >> 38);
    }

    private static int decodeZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
