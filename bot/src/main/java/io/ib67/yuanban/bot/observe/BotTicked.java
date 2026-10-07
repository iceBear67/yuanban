package io.ib67.yuanban.bot.observe;

/**
 * 一次 tick 结束。
 *
 * @param tick bot 已经执行的 tick 数，从 1 开始
 */
public record BotTicked(long tick) implements Observation {
}
