package io.ib67.yuanban.bot.world;

import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;

/**
 * 区块列中的一个区段：持有一个 {@link SharedSection} 和一个「共享」标记，第一次写入时复制出私有副本再写入。
 * 只在调度线程上使用。
 * <p>
 * 不使用 {@code DataPalette.set} 的返回值（它返回的是调色板中的下标，不是方块状态），也不使用
 * {@code ChunkSection.setBlock}（它按 set 的返回值维护方块数量）：需要旧的状态时先用 {@link #block} 读取。
 */
public final class SectionRef {
    private SharedSection section;
    private boolean shared;

    /**
     * @param shared section 是否可能被其它对象引用（来自 {@code SectionPool} 的实例总是共享的）
     */
    public SectionRef(SharedSection section, boolean shared) {
        this.section = section;
        this.shared = shared;
    }

    /**
     * 方块状态编号，坐标为区段内的坐标
     */
    public int block(int x, int y, int z) {
        return section.blocks().get(x, y, z);
    }

    /**
     * 生物群系编号，坐标为区段内以 4 格为单位的坐标
     */
    // TODO(T1): MCProtocolLib 的 DataPalette.index 对 4×4×4 的生物群系调色板算错下标（y << 3 | z << 1 | x，
    //  原版为 y << 4 | z << 2 | x），不是整个区段同一种生物群系时这里读出的结果错误
    public int biome(int x, int y, int z) {
        return section.biomes().get(x, y, z);
    }

    public void setBlock(int x, int y, int z, int stateId) {
        ensurePrivate();
        section.blocks().set(x, y, z, stateId);
    }

    /**
     * 替换整个区段的生物群系
     */
    public void setBiomes(DataPalette biomes) {
        ensurePrivate();
        section = new SharedSection(section.blocks(), biomes, section.blockCount(), section.fluidCount());
    }

    /**
     * 交出当前的区段给快照使用，并把自己标记为共享；之后的写入先复制，交出的区段因此不会再变化
     */
    public SharedSection share() {
        shared = true;
        return section;
    }

    private void ensurePrivate() {
        if (shared) {
            section = section.copy();
            shared = false;
        }
    }
}
