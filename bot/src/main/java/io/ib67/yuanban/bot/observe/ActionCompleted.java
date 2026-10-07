package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.Action;
import org.jetbrains.annotations.Nullable;

/**
 * 一个动作结束。
 *
 * @param error 执行完毕时为 null，其余情况为 {@link Action#future()} 完成时的异常
 */
public record ActionCompleted(Action action, @Nullable Throwable error) implements Observation {
}
