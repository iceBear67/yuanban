package io.ib67.yuanban.bot.player;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 本地玩家位置与朝向的插值（原版 {@code InterpolationHandler}，3 步）：Synchronize Vehicle Position 指向本地玩家时开始，
 * 之后原版每次执行 {@code LivingEntity.aiStep} 前进一步。不可变，每一步得到新的插值。
 *
 * @param steps    剩余的步数，大于 0
 * @param target   目标位置与朝向（原版 {@code InterpolationData}）
 * @param previous 开始插值时、或者上一步之后的位置与朝向（原版 {@code previousTickPosition} 与 {@code previousTickRot}），
 *                 这之后本地的移动与转向在下一步加到目标上
 */
public record Interpolation(int steps, Pos target, Pos previous) {
    /**
     * 原版 {@code InterpolationHandler.DEFAULT_INTERPOLATION_STEPS}
     */
    public static final int STEPS = 3;

    /**
     * 原版 {@code InterpolationHandler.interpolateTo}：没有正在进行的插值，或者目标与正在进行的不同时，从当前的位置与朝向开始新的插值
     *
     * @param active  正在进行的插值；没有时为 null
     * @param current 当前的位置与朝向
     */
    public static Interpolation start(@Nullable Interpolation active, Pos current, Pos target) {
        if (active != null && active.target.equals(target)) {
            return active;
        }
        return new Interpolation(STEPS, target, current);
    }

    /**
     * 原版 {@code InterpolationHandler.interpolate}：先把上一步之后本地的移动加到目标位置上（加上之后玩家的包围盒不碰撞时，
     * 原版 {@code level.noCollision}），把上一步之后本地的转向加到目标朝向上；然后向目标前进剩余步数分之一，
     * 偏航角取较近的方向（原版 {@code Mth.rotLerp}）。新的朝向按原版 {@code Entity.setRot} 对 360 取余，俯仰角限制到 [-90, 90]
     *
     * @param current 当前的位置与朝向
     * @param free    玩家的包围盒放在这个位置时不碰撞
     */
    public Step step(Pos current, Predicate<Vec> free) {
        var alpha = 1.0 / steps;
        var targetPosition = target.asVec();
        var moved = targetPosition.add(current.asVec().sub(previous.asVec()));
        if (free.test(moved)) {
            targetPosition = moved;
        }
        var targetYaw = target.yaw() + (current.yaw() - previous.yaw());
        var targetPitch = target.pitch() + (current.pitch() - previous.pitch());
        var yaw = (float) (current.yaw() + alpha * wrapDegrees((double) targetYaw - current.yaw())) % 360.0F;
        var pitch = Math.clamp((float) (current.pitch() + alpha * ((double) targetPitch - current.pitch())) % 360.0F, -90.0F, 90.0F);
        var position = new Pos(
                current.x() + alpha * (targetPosition.x() - current.x()),
                current.y() + alpha * (targetPosition.y() - current.y()),
                current.z() + alpha * (targetPosition.z() - current.z()),
                yaw, pitch);
        var next = steps > 1
                ? new Interpolation(steps - 1,
                        new Pos(targetPosition.x(), targetPosition.y(), targetPosition.z(), targetYaw, targetPitch), position)
                : null;
        return new Step(position, next);
    }

    /**
     * 原版 {@code Mth.wrapDegrees(double)}：换算到 [-180, 180)
     */
    private static double wrapDegrees(double angle) {
        var wrapped = angle % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    /**
     * @param position 这一步之后的位置与朝向
     * @param next     之后的插值；步数用完时为 null
     */
    public record Step(Pos position, @Nullable Interpolation next) {
    }
}
