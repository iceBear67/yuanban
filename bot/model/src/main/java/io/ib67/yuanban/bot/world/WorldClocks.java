package io.ib67.yuanban.bot.world;

import io.ib67.yuanban.bot.registry.SyncedRegistries;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;

import java.util.HashMap;
import java.util.Map;

/**
 * Update Time 给出的各时钟（原版 {@code ClientClockManager}）。时钟属于连接，不属于世界：原版的 {@code ClientClockManager} 由
 * {@code ClientPacketListener} 持有，每次 Finish Configuration 进入游戏阶段时新建，Login (play) 与 Respawn 时保留。
 * 时钟按 World Age 的差值推进：每个正常运行的 tick 在 World Age 加一之后推进一次；Update Time 先按它的 World Age 推进全部时钟，
 * 再替换其中列出的时钟。只在调度线程上使用。
 */
public final class WorldClocks {
    private final Map<Key, Clock> clocks = new HashMap<>();
    private long lastTickWorldAge;

    /**
     * 原版 {@code ClientClockManager.handleUpdates}：先按数据包的 World Age 推进全部时钟，再用数据包中的值替换列出的时钟
     *
     * @param registries 把数据包中时钟的注册表编号换成名称
     * @throws IllegalStateException 时钟的编号不在世界时钟注册表中
     */
    public void apply(ClientboundSetTimePacket packet, SyncedRegistries registries) {
        tick(packet.getGameTime());
        packet.getClockUpdates().forEach((id, state) -> {
            var key = registries.requireEntry(SyncedRegistries.WORLD_CLOCK, id);
            var clock = clocks.computeIfAbsent(key, _ -> new Clock());
            clock.totalTicks = state.totalTicks();
            clock.partialTick = state.partialTick();
            clock.rate = state.rate();
        });
    }

    /**
     * 原版 {@code ClientClockManager.tick}：每个时钟前进「World Age 与上次推进时的差值 × 速率」，整数部分计入总 tick 数，
     * 小数部分以 float 保存
     */
    public void tick(long worldAge) {
        var delta = worldAge - lastTickWorldAge;
        lastTickWorldAge = worldAge;
        for (var clock : clocks.values()) {
            var partial = clock.partialTick + (double) delta * clock.rate;
            // 原版 Mth.floor(double) 返回 int
            long full = (int) Math.floor(partial);
            clock.partialTick = (float) (partial - full);
            clock.totalTicks += full;
        }
    }

    /**
     * 各时钟当前的 tick 数（含小数部分）
     */
    public Map<Key, Double> clocks() {
        var result = new HashMap<Key, Double>();
        clocks.forEach((key, clock) -> result.put(key, clock.totalTicks + (double) clock.partialTick));
        return result;
    }

    /**
     * 原版 {@code ClientClockManager.ClockInstance}
     */
    private static final class Clock {
        private long totalTicks;
        private float partialTick;
        private float rate = 1;
    }
}
