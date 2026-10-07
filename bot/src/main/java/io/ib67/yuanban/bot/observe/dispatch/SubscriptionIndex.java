package io.ib67.yuanban.bot.observe.dispatch;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.PacketReceived;
import io.ib67.yuanban.bot.observe.PacketSent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按观察记录类型与数据包类索引已注册的 observer。查询结果按具体类缓存，注册与关闭时清空缓存。
 */
final class SubscriptionIndex {
    private final List<ObserverEntry> entries = new ArrayList<>();
    private final Map<Class<?>, List<ObserverEntry>> observationCache = new HashMap<>();
    private final Map<Class<?>, List<ObserverEntry>> receivedCache = new HashMap<>();
    private final Map<Class<?>, List<ObserverEntry>> sentCache = new HashMap<>();

    void add(ObserverEntry entry) {
        entries.add(entry);
        invalidate();
    }

    void remove(ObserverEntry entry) {
        entries.remove(entry);
        invalidate();
    }

    List<ObserverEntry> all() {
        return List.copyOf(entries);
    }

    /**
     * 订阅了这个观察记录类型的 observer，按注册顺序
     */
    List<ObserverEntry> observations(Class<? extends Observation> type) {
        return observationCache.computeIfAbsent(type, t -> matching(entry -> entry.subscription.observations(), t));
    }

    List<ObserverEntry> received(Class<?> packetType) {
        return receivedCache.computeIfAbsent(packetType, t -> matching(entry -> entry.subscription.received(), t));
    }

    List<ObserverEntry> sent(Class<?> packetType) {
        return sentCache.computeIfAbsent(packetType, t -> matching(entry -> entry.subscription.sent(), t));
    }

    /**
     * 一条观察记录的投递目标
     */
    List<ObserverEntry> targets(Observation observation) {
        return switch (observation) {
            case PacketReceived(var packet) -> received(packet.getClass());
            case PacketSent(var packet) -> sent(packet.getClass());
            default -> observations(observation.getClass());
        };
    }

    private List<ObserverEntry> matching(java.util.function.Function<ObserverEntry, Set<? extends Class<?>>> selector,
                                         Class<?> type) {
        var result = new ArrayList<ObserverEntry>();
        for (var entry : entries) {
            for (var subscribed : selector.apply(entry)) {
                if (subscribed.isAssignableFrom(type)) {
                    result.add(entry);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private void invalidate() {
        observationCache.clear();
        receivedCache.clear();
        sentCache.clear();
    }
}
