package io.ib67.yuanban.bot.observe;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Function;

/**
 * {@link io.ib67.yuanban.bot.Bot#observe} 返回的句柄。
 */
public interface ObserverHandle<O extends Observer> extends AutoCloseable {
    O observer();

    boolean isOpen();

    @Nullable CloseReason closeReason();

    /**
     * 观察期间读取 observer 的状态：reader 在调度线程上执行，返回值不应包含 observer 内部的可变对象。
     * observer 已经关闭时直接在调用线程上执行。
     */
    <R> R read(Function<? super O, R> reader);

    /**
     * 等到关闭。等待期间执行 {@link io.ib67.yuanban.bot.RuntimeOptions#waitPump()}。
     *
     * @throws io.ib67.yuanban.bot.BotTimeoutException 超时
     * @throws IllegalStateException                   在调度线程上调用
     */
    CloseReason awaitClose(Duration timeout);

    /**
     * 关闭观察；对已关闭的 observer 不做任何事。
     * 在其它线程调用时，等到调度线程处理完才返回，返回时 {@code onClose} 已经执行；在调度线程上调用时立即关闭。
     *
     * @throws io.ib67.yuanban.bot.BotFailureException {@code onClose} 抛出异常，这个异常结束了 bot（两种线程都是这样）
     */
    @Override
    void close();
}
