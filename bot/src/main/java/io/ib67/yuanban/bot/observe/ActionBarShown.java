package io.ib67.yuanban.bot.observe;

import net.kyori.adventure.text.Component;

/**
 * Set Action Bar Text，或带 overlay 的 System Chat Message。
 */
public record ActionBarShown(Component text) implements Observation {
}
