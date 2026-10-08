package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Point;
import org.cloudburstmc.nbt.NbtMap;

/**
 * Block Entity Data 改变了一个方块实体的数据。所在区块没有加载、或者该位置的方块没有这个类型的方块实体时，
 * Block Entity Data 被忽略（原版客户端行为），不产生这条记录。
 */
public record BlockEntityChanged(Point pos, NbtMap nbt) implements Observation {
}
