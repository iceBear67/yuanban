package io.ib67.yuanban.bot.observe;

import org.jetbrains.annotations.Nullable;

/**
 * Update Score 或 Reset Score。
 *
 * @param score 新的分数；移除时为 null
 */
public record ScoreChanged(String objective, String holder, @Nullable Integer score) implements Observation {
}
