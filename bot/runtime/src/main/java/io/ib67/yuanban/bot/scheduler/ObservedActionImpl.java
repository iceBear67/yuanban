package io.ib67.yuanban.bot.scheduler;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.ActionCancelledException;
import io.ib67.yuanban.bot.ObservedAction;
import io.ib67.yuanban.bot.ObserverFailureException;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverHandle;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ObservedAction} 的实现。
 */
final class ObservedActionImpl<O extends Observer> implements ObservedAction<O> {
    private final Action action;
    private final ObserverHandleImpl<O> handle;

    ObservedActionImpl(Action action, ObserverHandleImpl<O> handle) {
        this.action = action;
        this.handle = handle;
    }

    @Override
    public Action action() {
        return action;
    }

    @Override
    public ObserverHandle<O> handle() {
        return handle;
    }

    @Override
    public O await(Duration timeout) {
        var impl = (ActionImpl) action;
        impl.bot().checkNotSchedulerThread("ObservedAction.await");
        // 动作以取消结束时照常返回；连接结束或 bot 因异常结束时抛出与 Action.await 相同的异常
        var actionEnded = impl.internalFuture().exceptionallyCompose(error -> error instanceof ActionCancelledException
                ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(error));
        var closed = handle.entry().closed();
        var both = CompletableFuture.allOf(actionEnded, closed);
        // observer 以 FAILED 关闭时不等动作结束
        var failed = closed.thenCompose(reason -> reason == CloseReason.FAILED
                ? CompletableFuture.completedFuture(null) : new CompletableFuture<>());
        Waits.await(CompletableFuture.anyOf(failed, both), timeout, impl.bot().pump(), impl.bot()::activeNanos,
                "observed action " + action);
        var failure = handle.failure();
        if (failure != null) {
            throw new ObserverFailureException(failure);
        }
        return handle.observer();
    }
}
