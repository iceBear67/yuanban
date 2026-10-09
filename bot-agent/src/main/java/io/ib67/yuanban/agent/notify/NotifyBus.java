package io.ib67.yuanban.agent.notify;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;

import java.time.InstantSource;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 通知总线：每条通知先写入 trace，再按订阅交给 TUI、游戏内聊天等订阅者。
 * 订阅者在发布的线程上被调用，不能阻塞（TUI 与聊天各自放进自己的队列）。
 */
public final class NotifyBus {
    private final TraceStore trace;
    private final InstantSource clock;
    private final AtomicLong sequence = new AtomicLong();
    private final CopyOnWriteArrayList<Consumer<Notification>> subscribers = new CopyOnWriteArrayList<>();

    public NotifyBus(TraceStore trace, InstantSource clock) {
        this.trace = trace;
        this.clock = clock;
    }

    public Notification publish(NotifyKind kind, String source, String text, JsonObject payload) {
        var notification = new Notification("n-" + sequence.incrementAndGet(), kind, source, text, payload, clock.millis());
        trace.append(TraceKind.NOTIFY, Json.object("notification", notification.toJson()));
        for (var subscriber : subscribers) {
            subscriber.accept(notification);
        }
        return notification;
    }

    /**
     * @return 关闭时取消订阅
     */
    public AutoCloseable subscribe(Consumer<Notification> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }
}
