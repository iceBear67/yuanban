package io.ib67.yuanban.bot.world;

import net.minestom.server.instance.palette.Palette;

/**
 * 区块列中的一个区段：持有一个 {@link SharedSection} 和一个「共享」标记，第一次写入时复制出私有副本再写入。
 * 只在调度线程上使用。
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
    public void setBiomes(Palette biomes) {
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
