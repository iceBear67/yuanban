package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * 记录观察到的内容：每条观察记录经过提取规则得到一条记录，提取规则返回 null 时不记录。由 {@link Observers} 的工厂方法创建。
 *
 * @param <T> 记录的类型
 */
public final class RecordingObserver<T> extends BuiltinObserver {
    private final Subscription subscription;
    private final BiFunction<Observation, ObserverContext, @Nullable T> extractor;
    private final AtomicBoolean limitSet = new AtomicBoolean(false);
    private Records<T> records = new Records<>(Integer.MAX_VALUE);

    RecordingObserver(Subscription subscription, BiFunction<Observation, ObserverContext, @Nullable T> extractor) {
        this.subscription = subscription;
        this.extractor = extractor;
    }

    /**
     * 最多保存的记录数，只能调用一次
     *
     * @throws IllegalStateException 已经调用过
     */
    public RecordingObserver<T> limit(int limit) {
        if (!this.limitSet.compareAndSet(false, true)) {
            throw new IllegalStateException("limit was already set for " + this);
        }
        this.records = new Records<>(limit);
        return this;
    }

    public List<T> items() {
        checkReadable();
        return records.items();
    }

    public boolean truncated() {
        checkReadable();
        return records.truncated();
    }

    @Override
    public Subscription subscription() {
        return subscription;
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        var item = extractor.apply(observation, context);
        if (item != null) {
            records.add(item);
        }
    }
}
