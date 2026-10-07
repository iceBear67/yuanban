package io.ib67.yuanban.bot.view;

/**
 * 在任意线程上查询 bot 的模型。
 * 在其它线程调用时，每个方法作为查询任务交给调度线程并等待结果；在调度线程上调用时直接执行。
 * 需要在同一时刻完成多项读取时使用 {@link io.ib67.yuanban.bot.Bot#inspect}。
 */
public interface BotView extends StateReader {
}
