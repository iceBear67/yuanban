package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.MoveOptions;
import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.scheduler.ActionImpl;
import io.ib67.yuanban.bot.world.WorldSnapshot;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.pathfinding.PNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * {@code moveTo}：在寻路线程上寻路，然后沿路径移动。玩家着地，与目标的水平距离进入 closeDistance，并且脚部与目标的站立高度
 * （{@link PathPlanner#targetHeight}）相差小于 1 时执行完毕。
 * 连续 20 tick 水平位移小于 0.05、路径上的方块被改变、服务端传送了玩家时重新寻路，最多 3 次。
 * 找不到路径，或者只能走到最近点并且已经走完时取消：寻路时目标所在区块未加载为 CHUNK_NOT_LOADED，否则为 NO_PATH。
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
     * 目标的站立高度（{@link PathPlanner#targetHeight}），每次寻路时用这次的世界快照重新计算
     */
    private double targetHeight;
    /**
     * 最近一次寻路时目标所在区块是否已加载：未加载时快照中没有目标的地形，找不到路径的原因为 CHUNK_NOT_LOADED
     */
    private boolean targetChunkLoaded;
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
        var position = player.position();
        if (player.onGround() && nearTarget(position.x(), position.y(), position.z())) {
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
                handle.cancelled(noPathReason());
                return MoveIntent.NONE;
            }
            return new MoveIntent(MoveIntent.horizontalDirection(position.x(), position.z(), target.x(), target.z()), mode, false);
        }
        return intent;
    }

    /**
     * 是否已经到达目标：与目标的水平距离不大于 closeDistance，并且高度与目标的站立高度相差小于 1（与到达 WALK 节点的高度条件相同）
     *
     * @param y 玩家脚部的高度，或者节点的站立高度
     */
    private boolean nearTarget(double x, double y, double z) {
        var dx = x - target.x();
        var dz = z - target.z();
        return Math.sqrt(dx * dx + dz * dz) <= options.closeDistance() && Math.abs(y - targetHeight) < 1.0;
    }

    /**
     * 按寻路结果开始跟随（8.4 的路径判断）；寻路期间被改变的方块在这条路径上时请求重新寻路
     *
     * @return 动作是否还在执行（找不到路径时以 {@link #noPathReason} 取消）
     */
    private boolean usePath(List<PNode> nodes) {
        // 搜索的节点数达到 maxDistance × 10 而开放列表不为空时，Minestom 在最近节点之后接一个坐标为 (0, 0, 0) 的 REPATH 节点，
        // 只有它位于第一个时才把路径判为无效：去掉它及其之后的节点，只走到最近点
        var repath = repathIndex(nodes);
        if (repath >= 0) {
            nodes = nodes.subList(0, repath);
        }
        if (nodes.isEmpty()) {
            handle.cancelled(noPathReason());
            return false;
        }
        var path = new PathFollower(nodes, Objects.requireNonNull(planningSnapshot), Objects.requireNonNull(planningBox));
        // 搜索没有完成，或者最后一个节点没有到达目标（水平距离大于 closeDistance，或者站立高度与目标的相差 1 以上）：
        // 只走到最近点（与 Minestom 的 BEST_EFFORT 相同）
        var last = nodes.getLast();
        bestEffort = repath >= 0 || !nearTarget(last.x(), path.standingHeight(nodes.size() - 1), last.z());
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
     * 找不到路径（或者只能走到最近点）时的取消原因：最近一次寻路时目标所在区块未加载为 CHUNK_NOT_LOADED，否则为 NO_PATH。
     * 目标在 maxDistance 之外时快照不包含它的区块，所以按寻路时 {@link io.ib67.yuanban.bot.world.ClientWorld} 中的状态判断
     */
    private String noPathReason() {
        return targetChunkLoaded ? ActionCancelledException.NO_PATH : ActionCancelledException.CHUNK_NOT_LOADED;
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
        targetHeight = PathPlanner.targetHeight(snapshot, target, boundingBox);
        targetChunkLoaded = controller.world().chunkLoaded(target.chunkX(), target.chunkZ());
        changedWhilePlanning.clear();
        planning = PathPlanner.plan(snapshot, player.position(), target, options.closeDistance(), options.maxDistance(),
                boundingBox, player.onGround(), controller.tags());
    }
}
