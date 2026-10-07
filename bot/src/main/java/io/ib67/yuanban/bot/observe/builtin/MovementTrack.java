package io.ib67.yuanban.bot.observe.builtin;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;

/**
 * 一个被跟踪实体的移动。
 *
 * @param start   开始跟踪时的位置
 * @param current 最后的位置
 * @param removed 实体是否已被移除（记录保留）
 */
public record MovementTrack(int entityId, EntityType type, Point start, Point current, boolean removed) {
    public Vec displacement() {
        return current.asVec().sub(start);
    }
}
