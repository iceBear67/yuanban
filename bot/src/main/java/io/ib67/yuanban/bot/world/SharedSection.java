package io.ib67.yuanban.bot.world;

import net.minestom.server.instance.palette.Palette;

/**
 * 一个区段的方块与生物群系。共享的实例在同一个 JVM 的全部 bot 之间按内容复用，永远不被写入；
 * 写入之前由 {@link SectionRef} 复制出私有副本。
 *
 * @param blocks     方块状态编号，16×16×16
 * @param biomes     生物群系编号，4×4×4
 * @param blockCount 数据包给出的非空气方块数量
 * @param fluidCount 数据包给出的流体数量
 */
public record SharedSection(Palette blocks, Palette biomes, short blockCount, short fluidCount) {
    /**
     * 复制出可以写入的副本
     */
    public SharedSection copy() {
        return new SharedSection(blocks.clone(), biomes.clone(), blockCount, fluidCount);
    }
}
