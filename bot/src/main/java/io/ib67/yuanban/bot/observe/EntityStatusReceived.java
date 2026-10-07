package io.ib67.yuanban.bot.observe;

/**
 * Entity Event。
 */
public record EntityStatusReceived(int entityId, byte status) implements Observation {
}
