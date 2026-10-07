package io.ib67.yuanban.bot.observe;

/**
 * bot 的实际暂停状态改变。
 */
public record MovementSuspensionChanged(boolean suspended) implements Observation {
}
