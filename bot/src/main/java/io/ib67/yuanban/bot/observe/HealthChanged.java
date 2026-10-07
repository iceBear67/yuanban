package io.ib67.yuanban.bot.observe;

/**
 * Set Health。
 */
public record HealthChanged(float oldHealth, float health, int food, float saturation) implements Observation {
}
