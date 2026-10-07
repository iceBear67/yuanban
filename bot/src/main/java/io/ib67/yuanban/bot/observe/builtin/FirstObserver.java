package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 出现第一条匹配的观察记录时关闭自己。
 */
public final class FirstObserver<O extends Observation> extends BuiltinObserver {
    private final Class<O> type;
    private final Predicate<O> filter;
    private final Subscription subscription;
    private @Nullable O matched;

    /**
     * @throws IllegalArgumentException type 为 PacketReceived 或 PacketSent（数据包使用 firstPacket）
     */
    FirstObserver(Class<O> type, Predicate<O> filter) {
        this.type = type;
        this.filter = filter;
        this.subscription = Subscription.of(type);
    }

    /**
     * 匹配的观察记录；在匹配之前被关闭时为空
     */
    public Optional<O> matched() {
        checkReadable();
        return Optional.ofNullable(matched);
    }

    @Override
    public Subscription subscription() {
        return subscription;
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (matched == null && type.isInstance(observation)) {
            var typed = type.cast(observation);
            if (filter.test(typed)) {
                matched = typed;
                context.close();
            }
        }
    }
}
