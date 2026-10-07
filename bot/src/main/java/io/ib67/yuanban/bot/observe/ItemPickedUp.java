package io.ib67.yuanban.bot.observe;

/**
 * Pickup Item。
 */
public record ItemPickedUp(int collectedId, int collectorId, int count) implements Observation {
}
