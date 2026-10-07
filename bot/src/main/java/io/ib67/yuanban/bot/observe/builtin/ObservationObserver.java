package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;

import java.util.List;
import java.util.function.Predicate;

/**
 * 记录满足条件的某类观察记录。
 */
public final class ObservationObserver<O extends Observation> extends BuiltinObserver {
    private final Class<O> type;
    private final Predicate<O> filter;
    private final Subscription subscription;
    private Records<O> observations = new Records<>(Integer.MAX_VALUE);

    /**
     * @throws IllegalArgumentException type 为 PacketReceived 或 PacketSent（数据包使用 packets、sentPackets）
     */
    ObservationObserver(Class<O> type, Predicate<O> filter) {
        this.type = type;
        this.filter = filter;
        this.subscription = Subscription.of(type);
    }

    /**
     * 最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public ObservationObserver<O> limit(int limit) {
        checkNotRegistered(this);
        this.observations = new Records<>(limit);
        return this;
    }

    public List<O> observations() {
        checkReadable();
        return observations.items();
    }

    public boolean truncated() {
        checkReadable();
        return observations.truncated();
    }

    @Override
    public Subscription subscription() {
        return subscription;
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (type.isInstance(observation)) {
            var typed = type.cast(observation);
            if (filter.test(typed)) {
                observations.add(typed);
            }
        }
    }
}
