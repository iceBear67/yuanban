package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

/**
 * 本地玩家的游戏模式改变。
 */
public record GameModeChanged(GameMode old, GameMode now) implements Observation {
}
