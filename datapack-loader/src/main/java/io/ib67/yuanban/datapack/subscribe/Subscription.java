package io.ib67.yuanban.datapack.subscribe;

import io.ib67.yuanban.datapack.topic.Topic;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 订阅者及其声明：生命周期、读取的 topic、依赖的订阅者。
 *
 * @param <R> 准备结果的类型
 */
public final class Subscription<R> {
    private final SubscriberKey<R> key;
    private final Subscriber<R> subscriber;
    private final Lifecycle lifecycle;
    private final Set<Topic<?>> topics;
    private final Set<SubscriberKey<?>> after;

    private Subscription(SubscriberKey<R> key, Subscriber<R> subscriber, Lifecycle lifecycle,
                         Set<Topic<?>> topics, Set<SubscriberKey<?>> after) {
        this.key = key;
        this.subscriber = subscriber;
        this.lifecycle = lifecycle;
        this.topics = topics;
        this.after = after;
    }

    public static <R> Builder<R> builder(SubscriberKey<R> key, Subscriber<R> subscriber) {
        return new Builder<>(Objects.requireNonNull(key, "key"), Objects.requireNonNull(subscriber, "subscriber"));
    }

    public SubscriberKey<R> key() {
        return key;
    }

    public Subscriber<R> subscriber() {
        return subscriber;
    }

    public Lifecycle lifecycle() {
        return lifecycle;
    }

    public Set<Topic<?>> topics() {
        return topics;
    }

    public Set<SubscriberKey<?>> after() {
        return after;
    }

    @Override
    public String toString() {
        return "Subscription[" + key + "]";
    }

    public static final class Builder<R> {
        private final SubscriberKey<R> key;
        private final Subscriber<R> subscriber;
        private @Nullable Lifecycle lifecycle;
        private final Set<Topic<?>> topics = new LinkedHashSet<>();
        private final Set<SubscriberKey<?>> after = new LinkedHashSet<>();

        private Builder(SubscriberKey<R> key, Subscriber<R> subscriber) {
            this.key = key;
            this.subscriber = subscriber;
        }

        public Builder<R> lifecycle(Lifecycle lifecycle) {
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            return this;
        }

        public Builder<R> topic(Topic<?> topic) {
            topics.add(Objects.requireNonNull(topic, "topic"));
            return this;
        }

        public Builder<R> after(SubscriberKey<?> dependency) {
            after.add(Objects.requireNonNull(dependency, "dependency"));
            return this;
        }

        public Subscription<R> build() {
            if (lifecycle == null) {
                throw new IllegalStateException("subscription " + key + " does not declare a lifecycle");
            }
            return new Subscription<>(key, subscriber, lifecycle, Set.copyOf(topics), Set.copyOf(after));
        }
    }
}
