package io.ib67.yuanban.bot.observe;

import org.jetbrains.annotations.Nullable;

/**
 * {@link WorldBoundObserver} 记录的一个世界及其状态。
 *
 * @param closed 这个世界的观察结束的方式；仍在观察时为 null
 */
public record WorldRecord<W>(WorldInfo world, W state, @Nullable WorldClosed closed) {
}
