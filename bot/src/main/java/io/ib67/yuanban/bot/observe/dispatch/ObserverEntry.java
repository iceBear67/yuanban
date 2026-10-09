package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * 一个已注册的 observer。
 */
public final class ObserverEntry {
    final Observer observer;
    final Subscription subscription;
    final ObserverContextImpl context;
    /**
     * 只投递编号大于它的观察记录。在另一个 observer 的回调中注册时，到那个回调返回之前为 {@link Long#MAX_VALUE}
     */
    long startSequence;
    /**
     * 在 observer 的回调中注册时，注册时正在执行的回调层数
     */
    int startDepth;
    final CompletableFuture<CloseReason> closed = new CompletableFuture<>();
    /**
     * 以 FAILED 关闭时回调抛出的异常；在完成 {@link #closed} 之前写入
     */
    private @Nullable Throwable failure;
    /**
     * {@code observeDuring} 绑定的动作；它的 ActionCompleted 投递之后 observer 以 ACTION_COMPLETED 关闭
     */
    @Nullable Action boundAction;
    boolean closeRequested;
    boolean inCallback;

    ObserverEntry(Observer observer, Subscription subscription, ObservationHost host, ObservationDispatcher dispatcher,
                  long startSequence) {
        this.observer = observer;
        this.subscription = subscription;
        this.startSequence = startSequence;
        this.context = new ObserverContextImpl(host, dispatcher, this);
    }

    public Observer observer() {
        return observer;
    }

    public boolean isOpen() {
        return !closed.isDone();
    }

    /**
     * 关闭时以关闭原因完成；完成发生在调度线程上、{@code onClose} 执行之后
     */
    public CompletableFuture<CloseReason> closed() {
        return closed;
    }

    public @Nullable CloseReason closeReason() {
        return closed.isDone() ? closed.join() : null;
    }

    /**
     * 以 FAILED 关闭时回调抛出的异常；没有关闭或者因其它原因关闭时为 null
     */
    public @Nullable Throwable failure() {
        return closed.isDone() ? failure : null;
    }

    /**
     * 回调抛出异常：记下异常，以 FAILED 关闭
     */
    void fail(Throwable failure) {
        this.failure = failure;
        closed.complete(CloseReason.FAILED);
    }
}
