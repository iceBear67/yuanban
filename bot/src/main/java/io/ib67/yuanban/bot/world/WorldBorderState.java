package io.ib67.yuanban.bot.world;

import net.minestom.server.instance.WorldBorder;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;

/**
 * 世界边框（原版 {@code WorldBorder}）。由 Initialize World Border 与各个 Set Border 包更新；插值时间以 tick 计（原版与 Minestom 相同），
 * 大小插值随 tick 推进，世界冻结并且不在步进中的 tick 不推进（{@link TickState#runsNormally()}）。
 * 范围判断与碰撞使用的大小在插值期间为上一 tick 的大小（原版 {@code MovingBorderExtent} 的 {@code getMinX(0)} 等取 {@code previousSize}）。
 */
public final class WorldBorderState {
    private double centerX = WorldBorder.DEFAULT_BORDER.centerX();
    private double centerZ = WorldBorder.DEFAULT_BORDER.centerZ();
    /**
     * 原版 {@code BorderExtent.getSize()}
     */
    private double size = WorldBorder.DEFAULT_BORDER.diameter();
    /**
     * 范围判断与碰撞使用的大小：插值期间为上一 tick 的大小（原版 {@code MovingBorderExtent.previousSize}），静止时与 {@link #size} 相同
     */
    private double boundsSize = size;
    /**
     * 是否正在插值（原版的 {@code MovingBorderExtent}）
     */
    private boolean moving;
    private double lerpFrom;
    private double lerpTo;
    private double lerpDuration;
    private long lerpProgress;
    private int portalBoundary = WorldBorder.DEFAULT_BORDER.dimensionTeleportBoundary();
    private int warningTime = WorldBorder.DEFAULT_BORDER.warningTime();
    private int warningBlocks = WorldBorder.DEFAULT_BORDER.warningDistance();

    /**
     * 原版 {@code handleInitializeBorder}：插值时间大于 0 时从旧的大小插值到新的大小，否则直接设为新的大小
     */
    public void initialize(ClientboundInitializeBorderPacket packet) {
        centerX = packet.getNewCenterX();
        centerZ = packet.getNewCenterZ();
        if (packet.getLerpTime() > 0) {
            lerp(packet.getOldSize(), packet.getNewSize(), packet.getLerpTime());
        } else {
            size(packet.getNewSize());
        }
        portalBoundary = packet.getNewAbsoluteMaxSize();
        warningTime = packet.getWarningTime();
        warningBlocks = packet.getWarningBlocks();
    }

    public void center(double x, double z) {
        centerX = x;
        centerZ = z;
    }

    /**
     * 原版 {@code setSize}：静止在这个大小
     */
    public void size(double diameter) {
        moving = false;
        size = diameter;
        boundsSize = diameter;
    }

    /**
     * 原版 {@code lerpSizeBetween}：两个大小相同时静止；否则开始插值，此刻的大小为起始大小。
     * 插值时间小于等于 0 时同样开始插值，第一次推进之后静止在目标大小
     */
    public void lerp(double from, double to, long ticks) {
        if (from == to) {
            size(to);
            return;
        }
        moving = true;
        lerpFrom = from;
        lerpTo = to;
        lerpDuration = ticks;
        lerpProgress = ticks;
        size = lerpSize();
        boundsSize = size;
    }

    public void warningTime(int warningTime) {
        this.warningTime = warningTime;
    }

    public void warningBlocks(int warningBlocks) {
        this.warningBlocks = warningBlocks;
    }

    /**
     * 原版 {@code MovingBorderExtent.update}：剩余时间减一，上一 tick 的大小留作范围判断使用，再算出新的大小；
     * 剩余时间不大于 0 时静止在目标大小
     */
    public void tick() {
        if (!moving) {
            return;
        }
        lerpProgress--;
        boundsSize = size;
        size = lerpSize();
        if (lerpProgress <= 0) {
            size(lerpTo);
        }
    }

    /**
     * 原版 {@code MovingBorderExtent.calculateSize}
     */
    private double lerpSize() {
        var progress = (lerpDuration - lerpProgress) / lerpDuration;
        return progress < 1.0 ? lerpFrom + progress * (lerpTo - lerpFrom) : lerpTo;
    }

    /**
     * 原版 {@code WorldBorder.isWithinBounds}：x 与 z 都在 [中心 − 直径 / 2, 中心 + 直径 / 2) 之内，
     * 两端限制在 ±Portal Teleport Boundary（原版的 absoluteMaxSize）之内；插值期间的直径为上一 tick 的大小
     */
    public boolean withinBounds(double x, double z) {
        var half = boundsSize / 2;
        return x >= limit(centerX - half) && x < limit(centerX + half) && z >= limit(centerZ - half) && z < limit(centerZ + half);
    }

    private double limit(double value) {
        return Math.clamp(value, -portalBoundary, portalBoundary);
    }

    /**
     * 原版 {@code CollisionGetter.noBorderCollision} 的反面：位于 (x, z) 的实体在边框之内并且靠近边框（{@code isInsideCloseToBorder}：
     * 到最近一边的距离小于包围盒宽度的 2 倍，范围放宽包围盒宽度，宽度至少为 1），并且包围盒伸进边框的碰撞形状
     * （范围向外取整之后的外侧，{@code getCollisionShape}）时为 true
     *
     * @param minX 包围盒的最小 x，其余参数同理
     */
    public boolean collides(double x, double z, double minX, double minZ, double maxX, double maxZ) {
        var half = boundsSize / 2;
        var borderMinX = limit(centerX - half);
        var borderMaxX = limit(centerX + half);
        var borderMinZ = limit(centerZ - half);
        var borderMaxZ = limit(centerZ + half);
        var size = Math.max(Math.max(Math.abs(maxX - minX), Math.abs(maxZ - minZ)), 1.0);
        var distance = Math.min(Math.min(x - borderMinX, borderMaxX - x), Math.min(z - borderMinZ, borderMaxZ - z));
        var close = distance < size * 2.0 && x >= borderMinX - size && x < borderMaxX + size
                && z >= borderMinZ - size && z < borderMaxZ + size;
        return close && (minX < Math.floor(borderMinX) || maxX > Math.ceil(borderMaxX)
                || minZ < Math.floor(borderMinZ) || maxZ > Math.ceil(borderMaxZ));
    }

    /**
     * 交给 {@code PhysicsUtils} 计算碰撞的边框：直径为范围判断使用的大小（插值期间为上一 tick 的大小，原版的碰撞形状同样由它得出）
     */
    public WorldBorder toWorldBorder() {
        return new WorldBorder(boundsSize, centerX, centerZ, warningBlocks, warningTime, portalBoundary);
    }
}
