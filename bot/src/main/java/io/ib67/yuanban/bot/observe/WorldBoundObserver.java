package io.ib67.yuanban.bot.observe;

import io.ib67.yuanban.bot.observe.dispatch.ObserverContextImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 按世界切换状态的 observer：bot 进入新世界时结束旧世界的状态、创建新世界的状态。
 * 实体编号与方块坐标只在一个世界中有意义，与它们有关的 observer 继承这个类。
 * <p>
 * {@link #worlds()} 与 {@link #currentWorld()} 在观察中被调度线程以外的线程调用时抛出 {@link IllegalStateException}。
 *
 * @param <W> 一个世界的状态
 */
public abstract class WorldBoundObserver<W> implements Observer {
    private final List<WorldRecord<W>> worlds = new ArrayList<>();
    private @Nullable ObserverContext context;

    /**
     * 子类订阅的类型；基类在此基础上自动加入 {@link WorldEntered}
     */
    protected abstract Subscription worldSubscription();

    /**
     * bot 进入一个世界时创建该世界的状态
     */
    protected abstract W openWorld(WorldInfo world, ObserverContext context);

    /**
     * 当前世界中的观察记录
     */
    protected abstract void observe(W world, Observation observation, ObserverContext context);

    /**
     * 一个世界的观察结束：bot 进入了下一个世界，或者 observer 关闭
     */
    protected void closeWorld(W world, WorldClosed closed, ObserverContext context) {
    }

    @Override
    public final Subscription subscription() {
        return worldSubscription().and(Subscription.of(WorldEntered.class));
    }

    @Override
    public final void onStart(ObserverContext context) {
        this.context = context;
        var world = context.world();
        if (world != null) {
            worlds.add(new WorldRecord<>(world, openWorld(world, context), null));
        }
    }

    @Override
    public final void onObservation(Observation observation, ObserverContext context) {
        if (observation instanceof WorldEntered(WorldInfo next)) {
            closeCurrent(new WorldClosed.Left(next), context);
            worlds.add(new WorldRecord<>(next, openWorld(next, context), null));
            return;
        }
        var current = current();
        if (current != null) {
            observe(current.state(), observation, context);
        }
    }

    @Override
    public final void onClose(CloseReason reason, ObserverContext context) {
        closeCurrent(new WorldClosed.ObserverClosed(reason), context);
    }

    /**
     * 按进入顺序排列的全部世界及其状态
     */
    public final List<WorldRecord<W>> worlds() {
        checkReadable();
        return List.copyOf(worlds);
    }

    /**
     * 当前世界的状态；还没有进入任何世界，或者 observer 已经关闭时为最后一个世界的状态或 null
     */
    public final @Nullable W currentWorld() {
        checkReadable();
        return worlds.isEmpty() ? null : worlds.getLast().state();
    }

    /**
     * 观察中被调度线程以外的线程调用时抛出 {@link IllegalStateException}
     */
    protected final void checkReadable() {
        ObserverContextImpl.checkReadable(context);
    }

    private @Nullable WorldRecord<W> current() {
        if (worlds.isEmpty()) {
            return null;
        }
        var last = worlds.getLast();
        return last.closed() == null ? last : null;
    }

    private void closeCurrent(WorldClosed closed, ObserverContext context) {
        var current = current();
        if (current == null) {
            return;
        }
        worlds.set(worlds.size() - 1, new WorldRecord<>(current.world(), current.state(), closed));
        closeWorld(current.state(), closed, context);
    }
}
