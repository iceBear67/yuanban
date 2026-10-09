package io.ib67.yuanban.agent.message;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 一个脚本的收件箱。规则：
 * <ul>
 *     <li>带 dedupeKey 的消息：序号不大于这个键已经收到的最大序号时丢弃（旧消息不能覆盖更新的策略），否则替换队列中同一个键的旧消息；</li>
 *     <li>{@link MessageType.Delivery#LATEST_WINS}（focus）且没有 dedupeKey：替换队列中同类型、没有 dedupeKey 的旧消息；</li>
 *     <li>{@link MessageType.Delivery#DROPPABLE}（signal）：超过 {@code droppableCapacity} 时丢弃最早的一条；</li>
 *     <li>其余消息超过 {@code capacity} 时不入队，由 {@code onOverflow} 处理（脚本安全停机并通知用户）。</li>
 * </ul>
 * 等待中的 {@link #next} 按调用顺序取得匹配的消息。线程安全。
 */
public final class Mailbox {
    private final int capacity;
    private final int droppableCapacity;
    private final Consumer<Envelope> onDelivered;
    private final Consumer<Envelope> onDropped;
    private final Consumer<Envelope> onOverflow;
    private final ArrayDeque<Envelope> queue = new ArrayDeque<>();
    private final List<Waiter> waiters = new ArrayList<>();
    private final Map<String, Long> latestByKey = new HashMap<>();
    private long cursor;
    private boolean closed;

    /**
     * @param onDelivered 一条消息交给脚本时调用（在交付的线程上，持有锁）
     * @param onDropped   一条消息因去重、替换或容量被丢弃时调用
     * @param onOverflow  不可丢弃的消息超过容量时调用，这条消息不入队
     */
    public Mailbox(int capacity, int droppableCapacity, Consumer<Envelope> onDelivered, Consumer<Envelope> onDropped,
                   Consumer<Envelope> onOverflow) {
        this.capacity = capacity;
        this.droppableCapacity = droppableCapacity;
        this.onDelivered = onDelivered;
        this.onDropped = onDropped;
        this.onOverflow = onOverflow;
    }

    public enum Offer {
        DELIVERED,
        QUEUED,
        STALE,
        OVERFLOW,
        CLOSED
    }

    public synchronized Offer offer(Envelope message) {
        if (closed) {
            onDropped.accept(message);
            return Offer.CLOSED;
        }
        if (message.type().direction() != MessageType.Direction.CONTROL) {
            throw new IllegalArgumentException("only control messages are delivered to scripts, got " + message.type());
        }
        var key = message.dedupeKey();
        if (key != null) {
            var latest = latestByKey.get(key);
            if (latest != null && latest >= message.sequence()) {
                onDropped.accept(message);
                return Offer.STALE;
            }
            latestByKey.put(key, message.sequence());
            removeQueued(queued -> key.equals(queued.dedupeKey()));
        } else if (message.type().delivery() == MessageType.Delivery.LATEST_WINS) {
            removeQueued(queued -> queued.type() == message.type() && queued.dedupeKey() == null);
        }
        var iterator = waiters.iterator();
        while (iterator.hasNext()) {
            var waiter = iterator.next();
            if (waiter.future.isDone()) {
                iterator.remove();
                continue;
            }
            if (waiter.filter.matches(message) && waiter.future.complete(message)) {
                iterator.remove();
                delivered(message);
                return Offer.DELIVERED;
            }
        }
        if (message.type().delivery() == MessageType.Delivery.DROPPABLE) {
            if (countDroppable() >= droppableCapacity) {
                removeFirst(queued -> queued.type().delivery() == MessageType.Delivery.DROPPABLE);
            }
        } else if (queue.size() - countDroppable() >= capacity) {
            onOverflow.accept(message);
            return Offer.OVERFLOW;
        }
        queue.addLast(message);
        return Offer.QUEUED;
    }

    /**
     * 第一条匹配的消息；没有时等到下一条匹配的消息。完成为 null 表示信箱已经关闭或者调用方取消了等待（{@code complete(null)}）
     */
    public synchronized CompletableFuture<@Nullable Envelope> next(Filter filter) {
        var future = new CompletableFuture<@Nullable Envelope>();
        if (closed) {
            future.complete(null);
            return future;
        }
        var found = take(filter);
        if (found != null) {
            future.complete(found);
            return future;
        }
        waiters.add(new Waiter(filter, future));
        return future;
    }

    /**
     * 第一条匹配的消息；没有时为 null，不等待
     */
    public synchronized @Nullable Envelope poll(Filter filter) {
        return closed ? null : take(filter);
    }

    /**
     * 最后交给脚本的消息的序号
     */
    public synchronized long cursor() {
        return cursor;
    }

    public synchronized int size() {
        return queue.size();
    }

    /**
     * 关闭信箱：等待中的 {@link #next} 以 null 完成，之后的消息被丢弃
     */
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var waiter : waiters) {
            waiter.future.complete(null);
        }
        waiters.clear();
        queue.forEach(onDropped);
        queue.clear();
    }

    private @Nullable Envelope take(Filter filter) {
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            var message = iterator.next();
            if (filter.matches(message)) {
                iterator.remove();
                delivered(message);
                return message;
            }
        }
        return null;
    }

    private void delivered(Envelope message) {
        cursor = Math.max(cursor, message.sequence());
        onDelivered.accept(message);
    }

    private int countDroppable() {
        var count = 0;
        for (var message : queue) {
            if (message.type().delivery() == MessageType.Delivery.DROPPABLE) {
                count++;
            }
        }
        return count;
    }

    private void removeQueued(Predicate<Envelope> predicate) {
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            var queued = iterator.next();
            if (predicate.test(queued)) {
                iterator.remove();
                onDropped.accept(queued);
            }
        }
    }

    private void removeFirst(Predicate<Envelope> predicate) {
        var iterator = queue.iterator();
        while (iterator.hasNext()) {
            var queued = iterator.next();
            if (predicate.test(queued)) {
                iterator.remove();
                onDropped.accept(queued);
                return;
            }
        }
    }

    /**
     * 取消息的条件
     *
     * @param types   接受的类型；为空时接受全部
     * @param replyTo 只接受回答这条消息的消息；为 null 时不限制。stop 消息总是满足这一项（等待回答时也要能停止）
     */
    public record Filter(Set<MessageType> types, @Nullable String replyTo) {
        public static final Filter ANY = new Filter(Set.of(), null);

        public Filter {
            types = Set.copyOf(types);
        }

        public boolean matches(Envelope message) {
            return (types.isEmpty() || types.contains(message.type()))
                    && (replyTo == null || replyTo.equals(message.replyTo()) || message.type() == MessageType.STOP);
        }
    }

    private record Waiter(Filter filter, CompletableFuture<@Nullable Envelope> future) {
    }
}
