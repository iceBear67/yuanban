package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.view.StateReader;
import org.jetbrains.annotations.Nullable;

/**
 * 投递观察记录的 bot 提供给 observer 上下文的内容。
 */
public interface ObservationHost {
    Bot bot();

    StateReader reader();

    @Nullable WorldInfo world();

    boolean onSchedulerThread();
}
