package io.ib67.yuanban.bot.observe;

/**
 * Set Experience。
 */
public record ExperienceChanged(float progress, int level, int total) implements Observation {
}
