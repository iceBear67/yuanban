package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverHandle;
import io.ib67.yuanban.bot.observe.dispatch.ObserverEntry;
import io.ib67.yuanban.bot.observe.dispatch.ObserverFailure;
import io.ib67.yuanban.bot.BotFailureException;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
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
    public CloseReason awaitClose(Duration timeout) {
        bot.checkNotSchedulerThread("ObserverHandle.awaitClose");
        return Waits.await(entry.closed(), timeout, bot.pump(), "observer " + observer + " to close");
    }

    /**
     * {@code onClose} 抛出的异常结束 bot，调用方收到 {@link BotFailureException}：在调度线程上调用时直接抛出；
     * 在其它线程调用时由调度线程处理，等它处理完之后在调用线程上抛出
     */
    @Override
    public void close() {
        if (!entry.isOpen()) {
            return;
        }
        if (bot.onSchedulerThread()) {
            try {
                bot.dispatcher().close(entry, CloseReason.BY_HANDLE);
            } catch (ObserverFailure e) {
                bot.fail(e);
                throw new BotFailureException(e.getCause());
            }
            return;
        }
        var result = new CompletableFuture<Void>();
        bot.observerTask(() -> {
            try {
                bot.dispatcher().close(entry, CloseReason.BY_HANDLE);
            } catch (ObserverFailure e) {
                result.completeExceptionally(new BotFailureException(e.getCause()));
                // 由调度循环的处理入口结束 bot
                throw e;
            }
            result.complete(null);
        });
        Waits.join(result);
    }
}
