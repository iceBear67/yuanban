package io.ib67.yuanban.bot.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.block.Block;

import java.util.Objects;

/**
 * 某一时刻的世界快照，供寻路线程使用。快照中的区段不会再被写入。
 * 范围内（以及范围外）未加载的区块返回 {@link Block#BARRIER}，寻路把它视为无法通过。
 */
public final class WorldSnapshot implements Block.Getter {
    private final int minY;
    private final int height;
    private final Long2ObjectMap<SharedSection[]> chunks;

    WorldSnapshot(int minY, int height, Long2ObjectMap<SharedSection[]> chunks) {
        this.minY = minY;
        this.height = height;
        this.chunks = chunks;
    }

    @Override
    public Block getBlock(int x, int y, int z, Condition condition) {
        if (y < minY || y >= minY + height) {
            return Block.AIR;
        }
        var sections = chunks.get(CoordConversion.chunkIndex(x >> 4, z >> 4));
        if (sections == null) {
            return Block.BARRIER;
        }
        return Objects.requireNonNull(Block.fromStateId(sections[(y - minY) >> 4].blocks().get(x & 15, y & 15, z & 15)));
    }
}
