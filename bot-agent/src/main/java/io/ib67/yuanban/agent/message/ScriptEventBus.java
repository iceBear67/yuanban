package io.ib67.yuanban.agent.message;

import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 脚本发出的事件。每条事件先写入 trace（{@link MessageType.Delivery#DURABLE} 的等到刷盘），再交给订阅者：
 * AgentKernel、所属的 PlanBundle、后台调度器。订阅者在发布的线程（脚本线程）上被调用，不能阻塞。
 */
public final class ScriptEventBus {
    private final TraceStore trace;
    private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public ScriptEventBus(TraceStore trace) {
        this.trace = trace;
    }

    public void publish(Envelope event) {
        if (event.type().direction() != MessageType.Direction.EVENT) {
            throw new IllegalArgumentException("only script events are published, got " + event.type());
        }
        var record = Json.object("envelope", event.toJson());
        if (event.type().delivery() == MessageType.Delivery.DURABLE) {
            trace.appendDurable(TraceKind.MESSAGE, record).join();
        } else {
            trace.append(TraceKind.MESSAGE, record);
        }
        for (var subscriber : subscribers) {
            if (subscriber.filter.test(event)) {
                subscriber.consumer.accept(event);
            }
        }
    }

    /**
     * @return 关闭时取消订阅
     */
    public AutoCloseable subscribe(Predicate<Envelope> filter, Consumer<Envelope> consumer) {
        var subscriber = new Subscriber(filter, consumer);
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    private record Subscriber(Predicate<Envelope> filter, Consumer<Envelope> consumer) {
    }
}
