package io.ib67.yuanban.bot.world;

import io.ib67.yuanban.bot.registry.SyncedRegistries;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldClocksTest {
    private static final Key OVERWORLD_CLOCK = Key.key("overworld");
    private static final Key THE_END_CLOCK = Key.key("the_end");

    /**
     * 时钟编号与名称的对应：编号 0 为 overworld，编号 1 为 the_end。注册表由协议状态机构造，这里直接构造同样的表。
     */
    private static SyncedRegistries registries() {
        try {
            Constructor<SyncedRegistries> constructor = SyncedRegistries.class.getDeclaredConstructor(Map.class, List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Map.of(SyncedRegistries.WORLD_CLOCK, List.of(OVERWORLD_CLOCK, THE_END_CLOCK)),
                    List.of());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ClientboundSetTimePacket time(long worldAge, Map<Integer, ClockNetworkState> clocks) {
        return new ClientboundSetTimePacket(worldAge, clocks);
    }

    @Test
    void updatesAdvanceEveryClockThenReplaceTheListedOnes() {
        var clocks = new WorldClocks();
        var registries = registries();
        // 第一次更新：World Age 100，只给出 overworld 时钟的初始值
        clocks.apply(time(100, Map.of(0, new ClockNetworkState(1000, 0.25f, 1))), registries);
        assertEquals(Map.of(OVERWORLD_CLOCK, 1000.25), clocks.clocks());

        // World Age 前进 20：每个时钟按速率前进 20
        clocks.tick(120);
        assertEquals(Map.of(OVERWORLD_CLOCK, 1020.25), clocks.clocks());

        // 同时更新两个时钟：先按 World Age 的差值推进，再替换列出的时钟
        var updates = new HashMap<Integer, ClockNetworkState>();
        updates.put(0, new ClockNetworkState(500, 0.5f, 2));
        updates.put(1, new ClockNetworkState(60, 0.0f, 1));
        clocks.apply(time(130, updates), registries);
        assertEquals(Map.of(OVERWORLD_CLOCK, 500.5, THE_END_CLOCK, 60.0), clocks.clocks());

        // 速率只在推进时起作用：the_end 的速率为 1，overworld 为 2
        clocks.tick(140);
        assertEquals(Map.of(OVERWORLD_CLOCK, 520.5), Map.of(OVERWORLD_CLOCK, clocks.clocks().get(OVERWORLD_CLOCK)));
        assertEquals(70.0, clocks.clocks().get(THE_END_CLOCK));
    }

    @Test
    void theFractionIsKeptAcrossTicks() {
        var clocks = new WorldClocks();
        var registries = registries();
        clocks.apply(time(0, Map.of(0, new ClockNetworkState(0, 0.0f, 0.5f))), registries);
        // 每次前进 1 个 World Age，速率为 0.5：小数部分累积，满 1 之后计入总 tick 数
        clocks.tick(1);
        clocks.tick(2);
        assertEquals(1.0, clocks.clocks().get(OVERWORLD_CLOCK));
        clocks.tick(3);
        assertEquals(1.5, clocks.clocks().get(OVERWORLD_CLOCK));
    }

    @Test
    void unknownClockIdsFailInsteadOfBeingIgnored() {
        var clocks = new WorldClocks();
        assertThrows(IllegalStateException.class,
                () -> clocks.apply(time(0, Map.of(2, new ClockNetworkState(0, 0, 1))), registries()));
    }

    @Test
    void clocksWithoutUpdatesStayStill() {
        var clocks = new WorldClocks();
        // 没有时钟时推进什么都不做
        clocks.tick(1000);
        assertTrue(clocks.clocks().isEmpty());
    }
}
