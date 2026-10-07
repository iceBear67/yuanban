package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import io.ib67.yuanban.bot.world.WorldSnapshot;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.pathfinding.PNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * {@code moveTo}：在寻路线程上寻路，然后沿路径移动。水平距离进入 closeDistance 并且玩家着地时执行完毕。
 * 连续 20 tick 水平位移小于 0.05、路径上的方块被改变、服务端传送了玩家时重新寻路，最多 3 次。
 */
public final class MoveToAction extends MovementAction {
    public static final int MAX_REPATHS = 3;
    public static final int STUCK_TICKS = 20;
    public static final double STUCK_DISTANCE = 0.05;

    private final Point target;
    private final MoveOptions options;
    private final MovementMode mode;
    private @Nullable CompletableFuture<List<PNode>> planning;
    /**
     * 正在进行的寻路使用的世界快照与玩家包围盒，路径跟随按它们计算节点的站立高度
     */
    private @Nullable WorldSnapshot planningSnapshot;
    private @Nullable BoundingBox planningBox;
    /**
     * 寻路期间被 Block Update 改变的方块
     */
    private final List<Point> changedWhilePlanning = new ArrayList<>();
    private @Nullable PathFollower follower;
    private boolean bestEffort;
    private int repaths;
    private int stuckTicks;
    private boolean repathRequested;

    public MoveToAction(ActionImpl handle, Point target, MoveOptions options) {
        super(handle);
        this.target = target;
        this.options = options;
        this.mode = Objects.requireNonNullElse(options.mode(), MovementMode.WALK);
    }

    @Override
    boolean moves() {
        return true;
    }

    @Override
    void start(MovementController controller) {
        plan(controller);
    }

    @Override
    MoveIntent tick(MovementController controller) {
        var player = controller.player();
        if (player.onGround() && horizontalDistance(player.position(), target) <= options.closeDistance()) {
            handle.succeed();
            return MoveIntent.NONE;
        }
        if (planning != null) {
            if (!planning.isDone()) {
                return MoveIntent.NONE;
            }
            var nodes = planning.join();
            planning = null;
            // 寻路期间服务端传送了玩家：这条路径从旧位置出发，不使用它，下面重新寻路
            if (!repathRequested && !usePath(nodes)) {
                return MoveIntent.NONE;
            }
        }
        if (repathRequested) {
            repathRequested = false;
            if (repaths >= MAX_REPATHS) {
                handle.cancelled(ActionCancelledException.BLOCKED);
                return MoveIntent.NONE;
            }
            repaths++;
            plan(controller);
            return MoveIntent.NONE;
        }
        var path = Objects.requireNonNull(follower);
        var intent = path.next(player, mode);
        if (path.finished()) {
            if (bestEffort) {
                handle.cancelled(ActionCancelledException.NO_PATH);
                return MoveIntent.NONE;
            }
            var position = player.position();
            return new MoveIntent(MoveIntent.horizontalDirection(position.x(), position.z(), target.x(), target.z()), mode, false);
        }
        return intent;
    }

    /**
     * 按寻路结果开始跟随（8.4 的路径判断）；寻路期间被改变的方块在这条路径上时请求重新寻路
     *
     * @return 动作是否还在执行（找不到路径时以 NO_PATH 取消）
     */
    private boolean usePath(List<PNode> nodes) {
        // 搜索的节点数达到 maxDistance × 10 而开放列表不为空时，Minestom 在最近节点之后接一个坐标为 (0, 0, 0) 的 REPATH 节点，
        // 只有它位于第一个时才把路径判为无效：去掉它及其之后的节点，只走到最近点
        var repath = repathIndex(nodes);
        if (repath >= 0) {
            nodes = nodes.subList(0, repath);
        }
        if (nodes.isEmpty()) {
            handle.cancelled(ActionCancelledException.NO_PATH);
            return false;
        }
        // 搜索没有完成，或者最后一个节点与目标的距离大于 closeDistance：只走到最近点（与 Minestom 的 BEST_EFFORT 相同）
        var last = nodes.getLast();
        bestEffort = repath >= 0 || horizontalDistance(new Vec(last.x(), last.y(), last.z()), target) > options.closeDistance();
        var path = new PathFollower(nodes, Objects.requireNonNull(planningSnapshot), Objects.requireNonNull(planningBox));
        follower = path;
        stuckTicks = 0;
        // 世界快照在开始寻路时取得，之后改变的方块没有反映在路径中
        if (changedWhilePlanning.stream().anyMatch(path::touches)) {
            repathRequested = true;
        }
        return true;
    }

    @Override
    void afterMove(MovementController controller, Pos from, Pos to, boolean onGround) {
        if (planning != null || follower == null) {
            return;
        }
        if (horizontalDistance(from, to) < STUCK_DISTANCE) {
            stuckTicks++;
            if (stuckTicks >= STUCK_TICKS) {
                stuckTicks = 0;
                repathRequested = true;
            }
        } else {
            stuckTicks = 0;
        }
    }

    /**
     * 路径上的方块被改变时重新寻路；寻路期间改变的方块先记下来，寻路完成之后再检查（{@link #usePath}）
     */
    @Override
    void onBlockChanged(Point blockPosition) {
        if (planning != null) {
            changedWhilePlanning.add(blockPosition);
        } else if (follower != null && follower.touches(blockPosition)) {
            repathRequested = true;
        }
    }

    /**
     * 服务端传送了玩家时重新寻路；寻路期间的传送让正在计算的路径作废，寻路完成之后不使用它，直接重新寻路
     */
    @Override
    void onTeleport() {
        repathRequested = true;
    }

    /**
     * @return 第一个 REPATH 节点的下标；没有时为 -1
     */
    private static int repathIndex(List<PNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getType() == PNode.Type.REPATH) {
                return i;
            }
        }
        return -1;
    }

    private void plan(MovementController controller) {
        var player = controller.player();
        var snapshot = controller.world().snapshot(player.position(), options.maxDistance());
        var boundingBox = player.boundingBox();
        follower = null;
        planningSnapshot = snapshot;
        planningBox = boundingBox;
        changedWhilePlanning.clear();
        planning = PathPlanner.plan(snapshot, player.position(), target, options.closeDistance(), options.maxDistance(),
                boundingBox, player.onGround());
    }
}
