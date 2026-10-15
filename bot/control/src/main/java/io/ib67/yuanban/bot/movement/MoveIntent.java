package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.MovementMode;
import net.minestom.server.coordinate.Vec;

/**
 * 一个 tick 的期望移动：水平方向（单位向量或零向量）、移动方式、是否起跳。
 */
public record MoveIntent(Vec direction, MovementMode mode, boolean jump) {
    public static final MoveIntent NONE = new MoveIntent(Vec.ZERO, MovementMode.WALK, false);

    /**
     * 朝 from 指向 to 的水平方向移动
     */
    public static Vec horizontalDirection(double fromX, double fromZ, double toX, double toZ) {
        var dx = toX - fromX;
        var dz = toZ - fromZ;
        var length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-7) {
            return Vec.ZERO;
        }
        return new Vec(dx / length, 0, dz / length);
    }

    public boolean moving() {
        return !direction.isZero();
    }

    /**
     * 暂停期间保持的期望：方向与移动方式不变，不起跳
     */
    public MoveIntent frozen() {
        return jump ? new MoveIntent(direction, mode, false) : this;
    }
}
