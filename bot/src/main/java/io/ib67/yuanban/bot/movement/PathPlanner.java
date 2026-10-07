package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.world.WorldSnapshot;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.pathfinding.PNode;
import net.minestom.server.entity.pathfinding.PathGenerator;
import net.minestom.server.entity.pathfinding.generators.GroundNodeGenerator;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 在世界快照上用 Minestom 的寻路计算路径。每次寻路使用一个新的虚拟线程，算完结束。
 */
public final class PathPlanner {
    /**
     * Minestom {@code Navigator.setPathTo} 的默认参数
     */
    public static final double PATH_VARIANCE = 20;
    /**
     * Minestom {@code PathGenerator.generate} 中 {@code gravitySnap} 的最大下落距离
     */
    static final double GRAVITY_SNAP_MAX_FALL = 100;

    private PathPlanner() {
    }

    /**
     * 目标的站立高度：与 {@code PathGenerator.generate} 相同，先用 {@code GroundNodeGenerator.gravitySnap} 把目标的高度换成整数的节点高度
     * （找不到时保持原来的高度），再按节点计算站立高度（{@link PathFollower#standingHeight(double, double, double, Block.Getter, BoundingBox)}）。
     * 完整路径的最后一个节点就是这样换过高度的目标，它的站立高度与此相同
     */
    static double targetHeight(Block.Getter snapshot, Point target, BoundingBox boundingBox) {
        // GroundNodeGenerator 内部有可变状态，每次新建一个
        var y = new GroundNodeGenerator().gravitySnap(snapshot, target.x(), target.y(), target.z(), boundingBox, GRAVITY_SNAP_MAX_FALL)
                .orElse(target.y());
        return PathFollower.standingHeight(target.x(), y, target.z(), snapshot, boundingBox);
    }

    /**
     * @return 路径的节点列表；列表为空表示找不到路径
     */
    public static CompletableFuture<List<PNode>> plan(WorldSnapshot snapshot, Pos start, Point target, double closeDistance,
                                                      double maxDistance, BoundingBox boundingBox, boolean onGround) {
        var result = new CompletableFuture<List<PNode>>();
        Thread.ofVirtual().name("yuanban-bot-path").start(() -> {
            try {
                // GroundNodeGenerator 内部有可变状态，每次寻路新建一个
                var path = PathGenerator.generate(snapshot, start, target, closeDistance, maxDistance, PATH_VARIANCE,
                        boundingBox, onGround, new GroundNodeGenerator(), null);
                result.complete(List.copyOf(path.getNodes()));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }
}
