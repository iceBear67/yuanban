package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldBoundObserver;
import io.ib67.yuanban.bot.observe.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 按世界记录观察到的内容：每条观察记录经过提取规则得到一条记录，提取规则返回 null 时不记录；每个世界的记录分别保存与计数。
 * 由 {@link Observers} 的工厂方法创建。
 *
 * @param <T> 记录的类型
 */
public final class WorldRecordingObserver<T> extends WorldBoundObserver<Records<T>> {
    private final Subscription subscription;
    private final BiFunction<Observation, ObserverContext, @Nullable T> extractor;
    private int limit = Integer.MAX_VALUE;

    WorldRecordingObserver(Subscription subscription, BiFunction<Observation, ObserverContext, @Nullable T> extractor) {
        this.subscription = subscription;
        this.extractor = extractor;
    }

    /**
     * 每个世界最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public WorldRecordingObserver<T> limit(int limit) {
        BuiltinObserver.checkNotRegistered(this);
        this.limit = limit;
        return this;
    }

    /**
     * 当前世界的记录
     */
    public List<T> items() {
        checkReadable();
        var world = currentWorld();
        return world == null ? List.of() : world.items();
    }

    public boolean truncated() {
        checkReadable();
        var world = currentWorld();
        return world != null && world.truncated();
    }

    @Override
    protected Subscription worldSubscription() {
        return subscription;
    }

    @Override
    protected Records<T> openWorld(WorldInfo world, ObserverContext context) {
        return new Records<>(limit);
    }

    @Override
    protected void observe(Records<T> world, Observation observation, ObserverContext context) {
        var item = extractor.apply(observation, context);
        if (item != null) {
            world.add(item);
        }
    }
}
