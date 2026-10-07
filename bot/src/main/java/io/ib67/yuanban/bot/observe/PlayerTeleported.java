package io.ib67.yuanban.bot.observe;

import net.minestom.server.coordinate.Pos;

/**
 * Synchronize Player Position。
 */
public record PlayerTeleported(Pos from, Pos to) implements Observation {
}
