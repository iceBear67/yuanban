package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.MovementMode;
import io.ib67.yuanban.bot.player.LocalPlayer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.pathfinding.PNode;
import net.minestom.server.instance.block.Block;

import java.util.List;

/**
 * 每 tick 沿路径节点给出期望移动：朝下一个节点的中心移动；节点的站立高度高于脚部时，到达节点所在格子的边缘起跳；
 * {@code FALL} 向节点方向走出边缘。{@code GroundNodeGenerator} 不产生 {@code WALK}、{@code JUMP}、{@code FALL} 以外的节点类型。
 * <p>
 * {@code GroundNodeGenerator} 的节点高度是整数：它从上往下找到包围盒下方第一层有 {@code solid()} 方块的格子，
 * 节点高度为这一层加一，与方块碰撞箱的实际高度无关（半砖上的节点高度为半砖所在层加一，比半砖顶面高 0.5）。
 * 所以本类按碰撞箱计算每个节点的站立高度（{@link #standingHeight}），到达与起跳都按它判断。
 */
public final class PathFollower {
    /**
     * 与节点中心的水平距离小于这个值时视为到达节点
     */
    static final double REACH_DISTANCE = 0.3;
    /**
     * 玩家半宽 0.3 加上节点中心到格子边缘的 0.5，再留 0.1 的余量：在这个水平距离以内起跳
     */
    static final double JUMP_DISTANCE = 0.9;
    /**
     * 节点的站立高度比脚部高出这个值以上时需要起跳：{@code PhysicsUtils} 没有原版的台阶抬升，再低的台阶也要跳上去；
     * 余量留给碰撞计算的误差（Minestom 的碰撞计算停下的高度与碰撞箱顶面相差不到 1.0E-4）
     */
    static final double CLIMB_THRESHOLD = 1.0E-3;

    private final List<PNode> nodes;
    private final double[] standingHeights;
    private int index;

    /**
     * @param blocks      寻路使用的世界快照
     * @param boundingBox 寻路使用的玩家包围盒
     */
    public PathFollower(List<PNode> nodes, Block.Getter blocks, BoundingBox boundingBox) {
        this.nodes = nodes;
        this.standingHeights = new double[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            standingHeights[i] = standingHeight(nodes.get(i), blocks, boundingBox);
        }
    }

    /**
     * 节点的站立高度：在节点的水平位置上，让玩家的包围盒从节点高度加一格处向下移动两格（Minestom 的碰撞计算，
     * 与移动模拟相同），停下的高度。节点之下的支撑方块顶面在节点高度减一到节点高度加 0.5（栅栏）之间，
     * 节点所在层还可以有地毯、雪等不算 {@code solid()} 但有碰撞箱的方块，所以从加一格处开始；开始时已经与包围盒相交的碰撞箱被忽略
     */
    static double standingHeight(PNode node, Block.Getter blocks, BoundingBox boundingBox) {
        return CollisionUtils.handlePhysics(blocks, boundingBox, new Pos(node.x(), node.y() + 1, node.z()), new Vec(0, -2, 0),
                null, false).newPosition().y();
    }

    public boolean finished() {
        return index >= nodes.size();
    }

    public List<PNode> nodes() {
        return nodes;
    }

    /**
     * 本 tick 的期望移动；全部节点都已到达时为 {@link MoveIntent#NONE}
     */
    public MoveIntent next(LocalPlayer player, MovementMode mode) {
        var position = player.position();
        while (index < nodes.size() && reached(nodes.get(index), standingHeights[index], position.x(), position.y(), position.z(),
                player.onGround())) {
            index++;
        }
        if (finished()) {
            return MoveIntent.NONE;
        }
        var node = nodes.get(index);
        var direction = MoveIntent.horizontalDirection(position.x(), position.z(), node.x(), node.z());
        var jump = false;
        if (player.onGround() && standingHeights[index] > position.y() + CLIMB_THRESHOLD) {
            var dx = node.x() - position.x();
            var dz = node.z() - position.z();
            jump = Math.sqrt(dx * dx + dz * dz) <= JUMP_DISTANCE || player.horizontalCollision();
        }
        return new MoveIntent(direction, mode, jump);
    }

    /**
     * 路径是否经过这个方块（节点所在格子及其上下各一格）
     */
    public boolean touches(Point blockPosition) {
        for (int i = index; i < nodes.size(); i++) {
            var node = nodes.get(i);
            if (node.blockX() == blockPosition.blockX() && node.blockZ() == blockPosition.blockZ()
                    && Math.abs(node.blockY() - blockPosition.blockY()) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param standingHeight 节点的站立高度
     */
    private static boolean reached(PNode node, double standingHeight, double x, double y, double z, boolean onGround) {
        var dx = node.x() - x;
        var dz = node.z() - z;
        if (Math.sqrt(dx * dx + dz * dz) >= REACH_DISTANCE) {
            return false;
        }
        return switch (node.getType()) {
            case JUMP -> y >= standingHeight - 0.1;
            case FALL -> onGround && y <= standingHeight + 0.5;
            default -> Math.abs(y - standingHeight) < 1.0;
        };
    }
}
