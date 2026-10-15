package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 记下第一条匹配的内容，然后关闭自己：提取规则返回的第一个非 null 的值即为匹配。由 {@link Observers} 的工厂方法创建。
 *
 * @param <T> 匹配的内容的类型
 */
public final class FirstMatchObserver<T> extends BuiltinObserver {
    private final Subscription subscription;
    private final BiFunction<Observation, ObserverContext, @Nullable T> extractor;
    private @Nullable T matched;

    FirstMatchObserver(Subscription subscription, BiFunction<Observation, ObserverContext, @Nullable T> extractor) {
        this.subscription = subscription;
        this.extractor = extractor;
    }

    /**
     * 匹配的内容；在匹配之前被关闭时为空
     */
    public Optional<T> matched() {
        checkReadable();
        return Optional.ofNullable(matched);
    }

    @Override
    public Subscription subscription() {
        return subscription;
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (matched != null) {
            return;
        }
        var item = extractor.apply(observation, context);
        if (item != null) {
            matched = item;
            context.close();
        }
    }
}
