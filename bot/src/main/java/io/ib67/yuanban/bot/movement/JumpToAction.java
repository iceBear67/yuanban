package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.Nullable;

/**
 * {@code jumpTo}：以落在目标方块顶面为目标起跳。着地时起跳；起跳前按目标的水平距离选择行走或疾跑
 * （未指定移动方式时，水平距离超过 2 格选择疾跑）；空中保持朝目标方向加速。起跳后再次着地即执行完毕；
 * 起跳的 tick 移动之后仍然着地（起跳没有离开地面）时同样执行完毕。
 */
public final class JumpToAction extends MovementAction {
    public static final double SPRINT_DISTANCE = 2;
    /**
     * 空中与目标顶面中心的水平距离小于这个值时不再加速
     */
    static final double AIR_STOP_DISTANCE = 0.1;

    private final Vec landing;
    private final MoveOptions options;
    private @Nullable MovementMode mode;
    private boolean jumped;
    /**
     * 本 tick 是起跳的 tick
     */
    private boolean jumpTick;
    private boolean leftGround;

    public JumpToAction(ActionImpl handle, Point target, MoveOptions options) {
        super(handle);
        this.landing = new Vec(target.blockX() + 0.5, target.blockY() + 1, target.blockZ() + 0.5);
        this.options = options;
    }

    @Override
    boolean moves() {
        return true;
    }

    @Override
    MoveIntent tick(MovementController controller) {
        var player = controller.player();
        var position = player.position();
        var distance = horizontalDistance(position, landing);
        var direction = distance < AIR_STOP_DISTANCE ? Vec.ZERO
                : MoveIntent.horizontalDirection(position.x(), position.z(), landing.x(), landing.z());
        if (!jumped && player.onGround()) {
            mode = options.mode() != null ? options.mode()
                    : distance > SPRINT_DISTANCE ? MovementMode.SPRINT : MovementMode.WALK;
            jumped = true;
            jumpTick = true;
            return new MoveIntent(direction, mode, true);
        }
        return new MoveIntent(direction, mode != null ? mode : MovementMode.WALK, false);
    }

    @Override
    void afterMove(MovementController controller, Pos from, Pos to, boolean onGround) {
        if (!jumped) {
            return;
        }
        if (jumpTick) {
            jumpTick = false;
            // 起跳的 tick 移动之后仍然着地：起跳没有离开地面（例如跳跃力为 0，或者头顶有方块），已经再次着地
            if (onGround) {
                handle.succeed();
                return;
            }
        }
        if (!onGround) {
            leftGround = true;
        } else if (leftGround) {
            handle.succeed();
        }
    }
}
