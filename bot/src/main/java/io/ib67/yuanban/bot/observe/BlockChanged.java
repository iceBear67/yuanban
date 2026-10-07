package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

/**
 * Block Update 或 Update Section Blocks 改变了一个方块。区块加载与卸载不产生这条记录。
 *
 * @param pos 方块坐标
 */
public record BlockChanged(Point pos, Block old, Block now) implements Observation {
}
