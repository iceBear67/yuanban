package io.ib67.yuanban.bot.observe;

/**
 * 一个世界的观察结束的方式。
 */
public sealed interface WorldClosed {
    /**
     * bot 进入了下一个世界
     */
    record Left(WorldInfo next) implements WorldClosed {
    }

    /**
     * observer 关闭
     */
    record ObserverClosed(CloseReason reason) implements WorldClosed {
    }
}
