package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.state.ActionHandle;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;

/**
 * 移动队列中的一个动作。全部方法在调度线程上调用。
 */
public abstract class MovementAction {
    protected final ActionHandle handle;

    protected MovementAction(ActionHandle handle) {
        this.handle = handle;
    }

    public ActionHandle handle() {
        return handle;
    }

    /**
     * moveTo、moveForward、jumpTo 为 true：movement suspension 期间不推进，轮到时也不开始；开始时清除 rotate、lookAt 设置的朝向
     */
    abstract boolean moves();

    /**
     * 成为当前动作的那个 tick 调用一次
     */
    void start(MovementController controller) {
    }

    /**
     * 每个 tick 推进一步，返回本 tick 的期望移动；可以在其中结束动作
     */
    abstract MoveIntent tick(MovementController controller);

    /**
     * 本 tick 的移动模拟完成之后调用
     */
    void afterMove(MovementController controller, Pos from, Pos to, boolean onGround) {
    }

    /**
     * Block Update 或 Update Section Blocks 改变了一个方块
     */
    void onBlockChanged(Point blockPosition) {
    }

    /**
     * 服务端传送了玩家
     */
    void onTeleport() {
    }

    static double horizontalDistance(Point a, Point b) {
        var dx = a.x() - b.x();
        var dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
