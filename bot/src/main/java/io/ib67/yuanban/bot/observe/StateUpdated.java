package io.ib67.yuanban.bot.observe;

/**
 * 一轮调度中该 bot 的状态有变化时一条，在这一轮的最后投递。
 */
public record StateUpdated() implements Observation {
}
