package io.ib67.yuanban.datapack.internal;

import io.ib67.yuanban.datapack.SubscriptionException;
import io.ib67.yuanban.datapack.subscribe.Lifecycle;
import io.ib67.yuanban.datapack.subscribe.LoadKind;
import io.ib67.yuanban.datapack.subscribe.SubscriberKey;
import io.ib67.yuanban.datapack.subscribe.Subscription;
import io.ib67.yuanban.datapack.topic.Topic;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 校验过的订阅关系：提交顺序，以及重载期间需要保留准备结果的 {@link Lifecycle#STARTUP} 订阅者。
 */
@ApiStatus.Internal
public final class SubscriberGraph {
    private static final Pattern DIRECTORY = Pattern.compile("[a-z0-9_.\\-/]+");
    private static final Pattern EXTENSION = Pattern.compile("\\.[a-z0-9_.\\-]+");

    private final List<Subscription<?>> applyOrder;
    private final Set<SubscriberKey<?>> retained;

    private SubscriberGraph(List<Subscription<?>> applyOrder, Set<SubscriberKey<?>> retained) {
        this.applyOrder = applyOrder;
        this.retained = retained;
    }

    /**
     * @param subscriptions 按注册顺序排列的订阅
     */
    public static SubscriberGraph build(List<Subscription<?>> subscriptions) {
        var byKey = new LinkedHashMap<SubscriberKey<?>, Subscription<?>>();
        for (var subscription : subscriptions) {
            if (byKey.putIfAbsent(subscription.key(), subscription) != null) {
                throw new SubscriptionException("订阅者 " + subscription.key() + " 重复注册");
            }
        }
        for (var subscription : subscriptions) {
            for (var dependency : subscription.after()) {
                if (!byKey.containsKey(dependency)) {
                    throw new SubscriptionException("订阅者 " + subscription.key() + " 依赖的 " + dependency + " 没有注册");
                }
            }
            for (var topic : subscription.topics()) {
                validateTopic(subscription.key(), topic);
            }
        }
        var order = topologicalOrder(byKey);
        var retained = new HashSet<SubscriberKey<?>>();
        for (var subscription : subscriptions) {
            if (subscription.lifecycle() != Lifecycle.RELOADABLE) {
                continue;
            }
            for (var dependency : subscription.after()) {
                if (byKey.get(dependency).lifecycle() == Lifecycle.STARTUP) {
                    retained.add(dependency);
                }
            }
        }
        return new SubscriberGraph(List.copyOf(order), Set.copyOf(retained));
    }

    /**
     * 拓扑排序，没有依赖关系的订阅者之间按注册顺序排列。
     */
    private static List<Subscription<?>> topologicalOrder(LinkedHashMap<SubscriberKey<?>, Subscription<?>> byKey) {
        var remaining = new ArrayList<>(byKey.values());
        var placed = new HashSet<SubscriberKey<?>>();
        var order = new ArrayList<Subscription<?>>(remaining.size());
        while (!remaining.isEmpty()) {
            Subscription<?> next = null;
            for (var candidate : remaining) {
                if (placed.containsAll(candidate.after())) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                throw new SubscriptionException("订阅者之间的依赖关系有环：" + findCycle(remaining));
            }
            remaining.remove(next);
            placed.add(next.key());
            order.add(next);
        }
        return order;
    }

    private static String findCycle(List<Subscription<?>> remaining) {
        Map<SubscriberKey<?>, Subscription<?>> byKey = remaining.stream()
                .collect(Collectors.toMap(Subscription::key, subscription -> subscription));
        var path = new ArrayList<SubscriberKey<?>>();
        var current = remaining.getFirst();
        while (!path.contains(current.key())) {
            path.add(current.key());
            // 剩下的每个订阅者都至少有一个依赖也在剩下的订阅者中
            current = current.after().stream()
                    .filter(byKey::containsKey)
                    .map(byKey::get)
                    .findFirst()
                    .orElseThrow();
        }
        var cycle = new ArrayList<>(path.subList(path.indexOf(current.key()), path.size()));
        cycle.add(current.key());
        return cycle.stream().map(SubscriberKey::toString).collect(Collectors.joining(" -> "));
    }

    private static void validateTopic(SubscriberKey<?> owner, Topic<?> topic) {
        var directory = topic.directory();
        var extension = topic.extension();
        var prefix = "订阅者 " + owner + " 声明的 topic " + topic + " ";
        if (!DIRECTORY.matcher(directory).matches()) {
            throw new SubscriptionException(prefix + "的目录只能包含 [a-z0-9_.-/] 并且不能为空");
        }
        if (directory.startsWith("/") || directory.endsWith("/")) {
            throw new SubscriptionException(prefix + "的目录不能以 / 开头或结尾");
        }
        for (var segment : directory.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new SubscriptionException(prefix + "的目录不能有空段、. 段或 .. 段");
            }
        }
        if (!EXTENSION.matcher(extension).matches()) {
            throw new SubscriptionException(prefix + "的扩展名必须以 . 开头，后面至少跟一个 [a-z0-9_.-] 字符");
        }
    }

    public List<Subscription<?>> applyOrder() {
        return applyOrder;
    }

    public Set<SubscriberKey<?>> retained() {
        return retained;
    }

    public boolean hasStartupSubscribers() {
        return applyOrder.stream().anyMatch(subscription -> subscription.lifecycle() == Lifecycle.STARTUP);
    }

    public List<Subscription<?>> participants(LoadKind kind) {
        return kind == LoadKind.INITIAL
                ? applyOrder
                : applyOrder.stream().filter(subscription -> subscription.lifecycle() == Lifecycle.RELOADABLE).toList();
    }

    public Map<SubscriberKey<?>, Object> retainedResults(Map<SubscriberKey<?>, Object> results) {
        var retainedResults = new LinkedHashMap<SubscriberKey<?>, Object>();
        for (var key : retained) {
            retainedResults.put(key, results.get(key));
        }
        return retainedResults;
    }
}
