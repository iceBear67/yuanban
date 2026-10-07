package io.ib67.yuanban.bot.movement;

import org.jetbrains.annotations.Nullable;

/**
 * 每个 tick 按优先级决定朝向：
 * 1. 正在运行、需要对准目标的手部动作看向目标；
 * 2. 正在运行的 moveTo、moveForward、jumpTo 看向移动方向；
 * 3. 最近一次 rotate 或 lookAt 设置的朝向，持续到下一个 moveTo、moveForward、jumpTo 开始，或者服务端设置玩家的朝向；
 * 4. 保持上一 tick 的朝向（包括服务端设置的朝向）。
 */
public final class RotationArbiter {
    private @Nullable Rotation manual;

    /**
     * rotate 或 lookAt 设置朝向
     */
    public void manual(Rotation rotation) {
        this.manual = rotation;
    }

    /**
     * 一个 moveTo、moveForward、jumpTo 开始，或者服务端设置了玩家的朝向
     */
    public void clearManual() {
        this.manual = null;
    }

    /**
     * @param handTarget 手部动作要看向的朝向
     * @param moveTarget 正在运行的移动动作要看向的朝向；移动方向为零时为 null
     * @param moving     是否有正在运行的移动动作
     * @param previous   上一 tick 的朝向
     */
    public Rotation decide(@Nullable Rotation handTarget, @Nullable Rotation moveTarget, boolean moving, Rotation previous) {
        if (handTarget != null) {
            return handTarget;
        }
        if (moving) {
            return moveTarget != null ? moveTarget : previous;
        }
        if (manual != null) {
            return manual;
        }
        return previous;
    }
}
