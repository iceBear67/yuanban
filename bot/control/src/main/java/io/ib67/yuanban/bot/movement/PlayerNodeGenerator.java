package io.ib67.yuanban.bot.movement;

import io.ib67.yuanban.bot.registry.TagTable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.kyori.adventure.key.Key;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.pathfinding.PNode;
import net.minestom.server.entity.pathfinding.generators.GroundNodeGenerator;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * 寻路使用的节点生成器。Minestom 的 {@code NodeGenerator.canMoveTowards} 把节点脚部任何不是 {@link Block#AIR} 的方块
 * （草、花、单层雪、{@code cave_air} 等）当作障碍；本类只按碰撞箱判断能否走过去，另外避开本库不模拟其效果的方块
 * （{@link #unsimulated}）：走进这些方块之后，{@link Kinematics} 计算的移动与原版客户端不同。
 * 节点高度、起跳与下落的规则与 {@link GroundNodeGenerator} 相同。内部有可变状态，每次寻路新建一个。
 */
public final class PlayerNodeGenerator extends GroundNodeGenerator {
    private static final Key BLOCK_REGISTRY = Key.key("block");
    private static final Key CLIMBABLE = Key.key("climbable");
    /**
     * 原版 {@code checkInsideBlocks} 把包围盒各边向内收缩这个值，再找出它接触的方块
     */
    private static final double INSIDE_DEFLATE = 1.0E-5;
    /**
     * 原版 26.2 中覆盖 {@code entityInside}、碰撞箱为空，并且会让实体减速、受到伤害或者被传送的方块。
     * 有碰撞箱的仙人掌、营火、蜂蜜块由碰撞判断处理
     */
    private static final IntSet EFFECT_BLOCKS = IntSets.unmodifiable(new IntOpenHashSet(new int[]{
            Block.COBWEB.id(), Block.SWEET_BERRY_BUSH.id(), Block.POWDER_SNOW.id(), Block.FIRE.id(), Block.SOUL_FIRE.id(),
            Block.WITHER_ROSE.id(), Block.NETHER_PORTAL.id(), Block.END_PORTAL.id(), Block.END_GATEWAY.id()}));
    /**
     * 原版 26.2 的 {@code TrapDoorBlock}：名称以 {@code _trapdoor} 结尾的全部方块
     */
    private static final IntSet TRAPDOORS;

    static {
        var trapdoors = new IntOpenHashSet();
        for (var block : Block.values()) {
            if (block.key().value().endsWith("_trapdoor")) {
                trapdoors.add(block.id());
            }
        }
        TRAPDOORS = IntSets.unmodifiable(trapdoors);
    }

    private final TagTable tags;

    /**
     * @param tags 判断方块是否可以攀爬（{@code minecraft:climbable}）
     */
    public PlayerNodeGenerator(TagTable tags) {
        this.tags = tags;
    }

    /**
     * 父类找到的节点中，去掉从当前节点移动过去时接触本库不模拟其效果的方块的节点。
     * {@code JUMP} 节点与 {@code FALL} 节点的落点不经过 {@link #canMoveTowards} 的终点，由这一步排除
     */
    @Override
    public Collection<? extends PNode> getWalkable(Block.Getter getter, Set<PNode> visited, PNode current, Point goal,
                                                   BoundingBox boundingBox) {
        var walkable = new ArrayList<PNode>();
        for (var node : super.getWalkable(getter, visited, current, goal, boundingBox)) {
            if (!unsimulated(getter, current.x(), current.y(), current.z(), node.x(), node.y(), node.z(), boundingBox)) {
                walkable.add(node);
            }
        }
        return walkable;
    }

    /**
     * 从起点移动到终点时不接触本库不模拟其效果的方块，并且直线移动时三个方向都没有碰撞（碰撞判断与 Minestom 的默认实现相同）
     */
    @Override
    public boolean canMoveTowards(Block.Getter getter, Point start, Point end, BoundingBox boundingBox) {
        if (unsimulated(getter, start.x(), start.y(), start.z(), end.x(), end.y(), end.z(), boundingBox)) {
            return false;
        }
        var result = CollisionUtils.handlePhysics(getter, boundingBox, start.asPos(), end.sub(start).asVec(), null, false);
        return !result.collisionX() && !result.collisionY() && !result.collisionZ();
    }

    /**
     * 包围盒从 from 移动到 to 时是否新接触本库不模拟其效果的方块。只看新接触的方块：起点已经接触的方块不算，
     * 否则站在这些方块中时走不出来。
     * <ul>
     *     <li>包围盒放在两处的范围合起来，各边向内收缩 1.0E-5（原版 {@code checkInsideBlocks}）之后接触的方块中有流体
     *     （水、熔岩、含水方块、海草、海带、气泡柱）或者 {@link #EFFECT_BLOCKS} 中的方块。节点之间相邻，
     *     斜向移动时包围盒经过的四格、下落时经过的整列都在这个范围内；</li>
     *     <li>原版 {@code LivingEntity.onClimbable}：终点所在的列上，脚部在两处高度之间经过的方块属于 {@code minecraft:climbable}，
     *     或者是打开的活板门，下面是朝向相同的梯子。</li>
     * </ul>
     */
    private boolean unsimulated(Block.Getter getter, double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                                BoundingBox box) {
        var startMinX = (int) Math.floor(fromX + box.minX() + INSIDE_DEFLATE);
        var startMinY = (int) Math.floor(fromY + box.minY() + INSIDE_DEFLATE);
        var startMinZ = (int) Math.floor(fromZ + box.minZ() + INSIDE_DEFLATE);
        var startMaxX = (int) Math.floor(fromX + box.maxX() - INSIDE_DEFLATE);
        var startMaxY = (int) Math.floor(fromY + box.maxY() - INSIDE_DEFLATE);
        var startMaxZ = (int) Math.floor(fromZ + box.maxZ() - INSIDE_DEFLATE);
        var minX = Math.min(startMinX, (int) Math.floor(toX + box.minX() + INSIDE_DEFLATE));
        var minY = Math.min(startMinY, (int) Math.floor(toY + box.minY() + INSIDE_DEFLATE));
        var minZ = Math.min(startMinZ, (int) Math.floor(toZ + box.minZ() + INSIDE_DEFLATE));
        var maxX = Math.max(startMaxX, (int) Math.floor(toX + box.maxX() - INSIDE_DEFLATE));
        var maxY = Math.max(startMaxY, (int) Math.floor(toY + box.maxY() - INSIDE_DEFLATE));
        var maxZ = Math.max(startMaxZ, (int) Math.floor(toZ + box.maxZ() - INSIDE_DEFLATE));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var touchedAtStart = x >= startMinX && x <= startMaxX && y >= startMinY && y <= startMaxY
                            && z >= startMinZ && z <= startMaxZ;
                    if (touchedAtStart) {
                        continue;
                    }
                    var block = getter.getBlock(x, y, z, Block.Getter.Condition.TYPE);
                    if (block.fluid() || EFFECT_BLOCKS.contains(block.id())) {
                        return true;
                    }
                }
            }
        }
        var feetX = (int) Math.floor(toX);
        var feetZ = (int) Math.floor(toZ);
        var startFeetX = (int) Math.floor(fromX);
        var startFeetY = (int) Math.floor(fromY);
        var startFeetZ = (int) Math.floor(fromZ);
        for (int y = (int) Math.floor(Math.min(fromY, toY)); y <= (int) Math.floor(Math.max(fromY, toY)); y++) {
            if (feetX == startFeetX && y == startFeetY && feetZ == startFeetZ) {
                continue;
            }
            if (climbable(getter, feetX, y, feetZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 原版 {@code LivingEntity.onClimbable}（不在旁观模式时）：脚部所在方块属于 {@code minecraft:climbable}，
     * 或者是打开的活板门，下面是朝向相同的梯子
     */
    private boolean climbable(Block.Getter getter, int x, int y, int z) {
        var feet = getter.getBlock(x, y, z, Block.Getter.Condition.TYPE);
        if (tags.contains(BLOCK_REGISTRY, CLIMBABLE, feet.id())) {
            return true;
        }
        if (TRAPDOORS.contains(feet.id()) && Boolean.parseBoolean(feet.getProperty("open"))) {
            var below = getter.getBlock(x, y - 1, z, Block.Getter.Condition.TYPE);
            return below.id() == Block.LADDER.id() && Objects.equals(below.getProperty("facing"), feet.getProperty("facing"));
        }
        return false;
    }
}
