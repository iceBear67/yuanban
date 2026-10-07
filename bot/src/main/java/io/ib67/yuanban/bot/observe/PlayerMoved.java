package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Pos;

/**
 * 自身的移动模拟使位置变化的 tick。
 */
public record PlayerMoved(Pos from, Pos to, boolean onGround) implements Observation {
}
