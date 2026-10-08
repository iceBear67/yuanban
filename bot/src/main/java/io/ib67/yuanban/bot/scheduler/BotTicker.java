package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.observe.BotTicked;
import net.minestom.server.coordinate.CoordConversion;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

/**
 * tick 内的顺序：推进计数 → 快捷栏同步 → Player Loaded → 手部交互 → 其它实体 → 移动 → 世界边框、World Age 与时钟
 * → Client Tick End → BotTicked。
 * 每一步产生的观察记录在该步完成后投递。
 */
final class BotTicker {
    private BotTicker() {
    }

    static void tick(BotInstance bot) {
        var state = bot.state();
        var player = state.player();
        var world = state.world();
        var dispatcher = bot.dispatcher();
        var tick = state.nextTick();

        player.tickTimers();
        // 原版 Minecraft.tick 开头推进 TickRateManager，决定这个 tick 是否正常运行
        world.tickState().tick();

        // 原版 MultiPlayerGameMode.tick：快捷栏槽位与上次发送的不同时发送 Set Held Item，在 Player Loaded 之前
        bot.hand().ensureHasSentCarriedItem();

        var position = player.position();
        var ready = world.chunkLoaded(CoordConversion.globalToChunk(position.x()), CoordConversion.globalToChunk(position.z()))
                || position.y() < world.minY() || position.y() >= world.minY() + world.height()
                || player.gameMode() == GameMode.SPECTATOR
                || player.dead();
        // 原版 ClientPacketListener.tick 中的 tickClientLoad：自 Login (play) 或 Respawn 起超过 30 秒时，条件不满足也发送
        if (state.loadTracker().tick(ready, System.nanoTime())) {
            state.outbox().send(ServerboundPlayerLoadedPacket.INSTANCE);
            bot.markLoaded();
        }
        dispatcher.flush();

        if (state.loadTracker().loaded()) {
            bot.hand().tick();
            dispatcher.flush();
        }
        // 原版 ClientLevel.tickEntities：其它实体与本地玩家是否加载无关，载具在它的乘客（包括本地玩家）之前执行
        world.tickEntities(player.entityId());
        if (state.loadTracker().loaded()) {
            bot.movement().tick(bot.movementSuspended(), bot.hand().lookRotation(), bot.hand().itemInUse());
            dispatcher.flush();
        }
        // 原版 Minecraft.tick 在实体 tick 之后调用 ClientLevel.tick：只在正常运行时依次推进世界边框，
        // 再执行 tickTime（World Age 加一，时钟按新的 World Age 推进）；世界冻结并且不在步进中时都不推进
        if (world.tickState().runsNormally()) {
            world.border().tick();
            world.worldAge(world.worldAge() + 1);
            state.clocks().tick(world.worldAge());
        }

        state.outbox().send(ServerboundClientTickEndPacket.INSTANCE);
        if (dispatcher.wants(BotTicked.class)) {
            dispatcher.emit(new BotTicked(tick));
        }
        dispatcher.flush();
    }
}
