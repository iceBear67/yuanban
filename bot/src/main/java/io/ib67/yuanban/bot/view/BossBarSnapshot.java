package io.ib67.yuanban.bot.view;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarDivision;

/**
 * 一个 Boss Bar 的状态。
 *
 * @param darkenSky    使天空变暗（标记 0x1）
 * @param playEndMusic 播放末地音乐（标记 0x2）
 * @param showFog      显示雾（标记 0x4）
 */
public record BossBarSnapshot(Component title, float health, BossBarColor color, BossBarDivision division,
                              boolean darkenSky, boolean playEndMusic, boolean showFog) {
}
