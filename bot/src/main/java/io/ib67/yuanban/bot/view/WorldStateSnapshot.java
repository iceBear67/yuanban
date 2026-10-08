package io.ib67.yuanban.bot.view;

import net.minestom.server.coordinate.Point;
import org.geysermc.mcprotocollib.protocol.data.game.setting.Difficulty;
import org.jetbrains.annotations.Nullable;

/**
 * 当前世界的状态的快照。
 *
 * @param worldAge           World Age：Update Time 给出，每个正常运行的 tick 加一
 * @param difficulty         Change Difficulty 给出的难度；没有收到时为 null
 * @param defaultSpawn       Set Default Spawn Position 给出的方块坐标；没有收到时为 null
 * @param viewDistance       服务端视距：Login (play) 与 Set Render Distance 给出
 * @param simulationDistance 模拟距离：Login (play) 与 Set Simulation Distance 给出
 * @param ticking            Set Ticking State 与 Step Tick 给出的 tick 状态
 * @param border             世界边框
 * @param rainLevel          雨量（Game Event 7）
 * @param thunderLevel       雷暴强度（Game Event 8）
 */
public record WorldStateSnapshot(long worldAge, @Nullable Difficulty difficulty, @Nullable Point defaultSpawn,
                                 int viewDistance, int simulationDistance,
                                 Ticking ticking, Border border, float rainLevel, float thunderLevel) {
    /**
     * tick 状态（原版 {@code TickRateManager}）
     *
     * @param stepTicks    还要步进的 tick 数
     * @param runsNormally 最近一个 tick 是否正常运行：没有冻结或者还有要步进的 tick。
     *                     为 false 时没有玩家乘客的实体不 tick，世界边框与时间不推进
     */
    public record Ticking(float tickRate, boolean frozen, int stepTicks, boolean runsNormally) {
    }

    /**
     * 世界边框（原版 {@code WorldBorder}）
     *
     * @param size                   当前的大小（原版 {@code getSize}）
     * @param boundsSize             范围判断与碰撞使用的大小：插值期间为上一 tick 的大小，静止时与 {@code size} 相同
     * @param moving                 是否正在插值
     * @param lerpTarget             插值的目标大小；不在插值中时等于 {@code size}
     * @param lerpRemainingTicks     插值剩余的 tick 数；不在插值中时为 0
     * @param portalTeleportBoundary Portal Teleport Boundary（原版的 absoluteMaxSize）
     */
    public record Border(double centerX, double centerZ, double size, double boundsSize,
                         boolean moving, double lerpTarget, long lerpRemainingTicks,
                         int portalTeleportBoundary, int warningTime, int warningBlocks) {
    }
}
