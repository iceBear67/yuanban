package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Point;

/**
 * 一个改变实体位置的数据包。坐标为模型算出的绝对位置。
 */
public record EntityMoved(int entityId, Point from, Point to, boolean onGround) implements Observation {
}
