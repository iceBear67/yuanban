package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.view.EntitySnapshot;

/**
 * 实体加入世界模型（Spawn Entity）。
 */
public record EntitySpawned(EntitySnapshot entity) implements Observation {
}
