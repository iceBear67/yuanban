package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.view.StateReader;
import org.jetbrains.annotations.Nullable;

/**
 * observer 回调中可用的上下文。
 */
public interface ObserverContext {
    Bot bot();

    /**
     * 回调时刻的模型，只读，只能在回调中使用
     */
    StateReader state();

    /**
     * 当前世界；不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）为 null
     */
    @Nullable WorldInfo world();

    /**
     * observer 关闭自己，当前回调返回后生效，关闭原因为 {@link CloseReason#BY_OBSERVER}
     */
    void close();
}
