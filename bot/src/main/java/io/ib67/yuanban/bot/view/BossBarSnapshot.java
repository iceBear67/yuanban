package io.ib67.yuanban.bot.view;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

/**
 * 一个 Boss Bar 的状态。
 *
 * @param flags 数据包中的标记位
 */
public record BossBarSnapshot(Component title, float health, BossBar.Color color, BossBar.Overlay overlay, byte flags) {
}
