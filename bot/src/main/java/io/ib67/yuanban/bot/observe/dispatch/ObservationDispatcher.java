package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.observe.ActionCompleted;
import io.ib67.yuanban.bot.observe.CloseReason;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.PacketReceived;
import io.ib67.yuanban.bot.observe.PacketSent;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * 一个 bot 的观察记录投递。只在调度线程上使用。
 * <p>
 * 模型在产生观察记录之前先查询 {@link #wants}，没有订阅者的类型不创建对象。
 * 产生的记录先进入待投递队列，由 {@link #flush()} 按产生顺序投递；投递期间产生的记录排在队列末尾，在同一次 flush 中投递。
 */
public final class ObservationDispatcher {
    private final ObservationHost host;
    private final SubscriptionIndex index = new SubscriptionIndex();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    /**
     * 在 observer 的回调中注册的 observer：从注册它的回调返回之后产生的观察记录开始投递
     */
    private final ArrayList<ObserverEntry> startingAfterCallback = new ArrayList<>();
    private long sequence;
    /**
     * 正在执行的 observer 回调的层数：回调中可以注册与关闭其它 observer，从而执行它们的回调
     */
    private int callbackDepth;
    private boolean flushing;

    public ObservationDispatcher(ObservationHost host) {
        this.host = host;
    }

    public boolean wants(Class<? extends Observation> type) {
        return !index.observations(type).isEmpty();
    }

    public boolean wantsReceived(Class<? extends MinecraftPacket> type) {
        return !index.received(type).isEmpty();
    }

    public boolean wantsSent(Class<? extends MinecraftPacket> type) {
        return !index.sent(type).isEmpty();
    }

    /**
     * 有 observer 订阅 {@link ActionCompleted}，或者有 observer 绑定在这个动作上
     */
    public boolean wantsCompletion(Action action) {
        if (wants(ActionCompleted.class)) {
            return true;
        }
        for (var entry : index.all()) {
            if (entry.boundAction == action) {
                return true;
            }
        }
        return false;
    }

    public void emit(Observation observation) {
        pending.add(new Pending(++sequence, observation));
    }

    public void emitReceived(MinecraftPacket packet) {
        if (wantsReceived(packet.getClass())) {
            emit(new PacketReceived(packet));
        }
    }

    public void emitSent(MinecraftPacket packet) {
        if (wantsSent(packet.getClass())) {
            emit(new PacketSent(packet));
        }
    }

    /**
     * 注册 observer 并调用 {@code onStart}。从这之后产生的观察记录开始投递；
     * 在另一个 observer 的回调中注册时，从那个回调返回之后产生的观察记录开始投递
     *
     * @throws ObserverFailure {@code onStart} 抛出异常
     */
    public ObserverEntry register(Observer observer) {
        var entry = new ObserverEntry(observer, observer.subscription(), host, this, sequence);
        if (callbackDepth > 0) {
            entry.startSequence = Long.MAX_VALUE;
            entry.startDepth = callbackDepth;
            startingAfterCallback.add(entry);
        }
        index.add(entry);
        call(entry, () -> observer.onStart(entry.context));
        return entry;
    }

    /**
     * 把 observer 绑定到动作上（{@code observeDuring} 在注册之后提交动作）
     */
    public void bind(ObserverEntry entry, Action action) {
        entry.boundAction = action;
    }

    /**
     * 按产生顺序投递待投递的观察记录，直到队列为空。在投递过程中再次调用时直接返回
     *
     * @throws ObserverFailure observer 的回调抛出异常
     */
    public void flush() {
        if (flushing) {
            return;
        }
        flushing = true;
        try {
            Pending next;
            while ((next = pending.poll()) != null) {
                var observation = next.observation();
                for (var entry : index.targets(observation)) {
                    if (entry.isOpen() && next.sequence() > entry.startSequence) {
                        call(entry, () -> entry.observer.onObservation(observation, entry.context));
                    }
                }
                if (observation instanceof ActionCompleted(Action action, var _)) {
                    for (var entry : index.all()) {
                        if (entry.boundAction == action) {
                            close(entry, CloseReason.ACTION_COMPLETED);
                        }
                    }
                }
            }
        } finally {
            flushing = false;
        }
    }

    /**
     * 关闭 observer：从订阅索引中移除，调用 {@code onClose}，然后完成它的关闭 future。对已关闭的 observer 不做任何事
     *
     * @throws ObserverFailure {@code onClose} 抛出异常
     */
    public void close(ObserverEntry entry, CloseReason reason) {
        if (!entry.isOpen()) {
            return;
        }
        index.remove(entry);
        try {
            entry.inCallback = true;
            callbackDepth++;
            entry.observer.onClose(reason, entry.context);
        } catch (Throwable t) {
            entry.closed.complete(CloseReason.FAILED);
            throw new ObserverFailure(t);
        } finally {
            entry.inCallback = false;
            callbackReturned();
        }
        entry.closed.complete(reason);
    }

    /**
     * 连接结束或 bot 因异常结束：丢弃待投递的记录，关闭全部 observer。
     * 某个 observer 的 {@code onClose} 抛出的异常不影响其余 observer 的关闭，最后一起抛出
     */
    public void closeAll(CloseReason reason) {
        pending.clear();
        RuntimeException failure = null;
        for (var entry : index.all()) {
            try {
                close(entry, reason);
            } catch (ObserverFailure e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void call(ObserverEntry entry, Runnable callback) {
        entry.inCallback = true;
        callbackDepth++;
        try {
            callback.run();
        } catch (Throwable t) {
            entry.inCallback = false;
            index.remove(entry);
            entry.closed.complete(CloseReason.FAILED);
            throw new ObserverFailure(t);
        } finally {
            callbackReturned();
        }
        entry.inCallback = false;
        if (entry.closeRequested) {
            close(entry, CloseReason.BY_OBSERVER);
        }
    }

    /**
     * 一个 observer 回调返回：在这个回调中注册的 observer 从此刻之后产生的观察记录开始投递
     */
    private void callbackReturned() {
        callbackDepth--;
        if (startingAfterCallback.isEmpty()) {
            return;
        }
        startingAfterCallback.removeIf(entry -> {
            if (entry.startDepth > callbackDepth) {
                entry.startSequence = sequence;
                return true;
            }
            return false;
        });
    }

    private record Pending(long sequence, Observation observation) {
    }
}
