package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.ObserverFailureException;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverHandle;
import io.ib67.yuanban.bot.observe.dispatch.ObserverEntry;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@link ObserverHandle} 的实现。
 */
final class ObserverHandleImpl<O extends Observer> implements ObserverHandle<O> {
    private final BotInstance bot;
    private final ObserverEntry entry;
    private final O observer;

    ObserverHandleImpl(BotInstance bot, ObserverEntry entry, O observer) {
        this.bot = bot;
        this.entry = entry;
        this.observer = observer;
    }

    ObserverEntry entry() {
        return entry;
    }

    @Override
    public O observer() {
        return observer;
    }

    @Override
    public boolean isOpen() {
        return entry.isOpen();
    }

    @Override
    public @Nullable CloseReason closeReason() {
        return entry.closeReason();
    }

    @Override
    public <R> R read(Function<? super O, R> reader) {
        if (!entry.isOpen()) {
            return reader.apply(observer);
        }
        return bot.query(() -> reader.apply(observer));
    }

    @Override
    public @Nullable Throwable failure() {
        return entry.failure();
    }

    @Override
    public CloseReason awaitClose(Duration timeout) {
        bot.checkNotSchedulerThread("ObserverHandle.awaitClose");
        var reason = Waits.await(entry.closed(), timeout, bot.pump(), "observer " + observer + " to close");
        if (reason == CloseReason.FAILED) {
            throw new ObserverFailureException(Objects.requireNonNull(entry.failure()));
        }
        return reason;
    }

    /**
     * 这次关闭中 {@code onClose} 抛出异常时，调用方收到 {@link ObserverFailureException}：在调度线程上调用时直接抛出；
     * 在其它线程调用时由调度线程处理，等它处理完之后在调用线程上抛出
     */
    @Override
    public void close() {
        if (!entry.isOpen()) {
            return;
        }
        if (bot.onSchedulerThread()) {
            closeByHandle();
            return;
        }
        var result = new CompletableFuture<Void>();
        bot.observerTask(() -> {
            try {
                closeByHandle();
            } catch (ObserverFailureException e) {
                result.completeExceptionally(e);
                return;
            }
            result.complete(null);
        });
        Waits.join(result);
    }

    /**
     * 调度线程：以 BY_HANDLE 关闭；在这之前已经关闭的不做任何事
     *
     * @throws ObserverFailureException 这次关闭中 {@code onClose} 抛出异常
     */
    private void closeByHandle() {
        if (!entry.isOpen()) {
            return;
        }
        bot.dispatcher().close(entry, CloseReason.BY_HANDLE);
        var failure = entry.failure();
        if (failure != null) {
            throw new ObserverFailureException(failure);
        }
    }
}
