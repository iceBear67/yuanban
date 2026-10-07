package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.view.EntitySnapshot;

/**
 * 实体从世界模型中移除（Remove Entities）。
 *
 * @param last 移除前的最后状态
 */
public record EntityRemoved(EntitySnapshot last) implements Observation {
}
