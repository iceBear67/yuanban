package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.text.Component;

/**
 * Combat Death，目标为自己。
 */
public record PlayerDied(Component message) implements Observation {
}
