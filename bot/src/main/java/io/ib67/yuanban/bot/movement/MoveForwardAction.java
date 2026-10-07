package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;

import java.util.Objects;

/**
 * {@code moveForward}：沿直线移动，不寻路、不跳跃。累计水平位移达到给出的格数时执行完毕；
 * 连续 20 tick 被阻挡（水平位移小于 0.05）时取消。
 */
public final class MoveForwardAction extends MovementAction {
    private final double blocks;
    private final MoveOptions options;
    private final MovementMode mode;
    private Vec direction = Vec.ZERO;
    private double travelled;
    private int blockedTicks;

    public MoveForwardAction(ActionImpl handle, double blocks, MoveOptions options) {
        super(handle);
        this.blocks = blocks;
        this.options = options;
        this.mode = Objects.requireNonNullElse(options.mode(), MovementMode.WALK);
    }

    @Override
    boolean moves() {
        return true;
    }

    @Override
    void start(MovementController controller) {
        var requested = options.direction();
        if (requested != null) {
            direction = MoveIntent.horizontalDirection(0, 0, requested.x(), requested.z());
        } else {
            var yaw = Math.toRadians(controller.player().position().yaw());
            direction = new Vec(-Math.sin(yaw), 0, Math.cos(yaw));
        }
    }

    @Override
    MoveIntent tick(MovementController controller) {
        return new MoveIntent(direction, mode, false);
    }

    @Override
    void afterMove(MovementController controller, Pos from, Pos to, boolean onGround) {
        var displacement = horizontalDistance(from, to);
        travelled += displacement;
        if (travelled >= blocks) {
            handle.succeed();
            return;
        }
        if (displacement < MoveToAction.STUCK_DISTANCE) {
            blockedTicks++;
            if (blockedTicks >= MoveToAction.STUCK_TICKS) {
                handle.cancelled(ActionCancelledException.BLOCKED);
            }
        } else {
            blockedTicks = 0;
        }
    }
}
