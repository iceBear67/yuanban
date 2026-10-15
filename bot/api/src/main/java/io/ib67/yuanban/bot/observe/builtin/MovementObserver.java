package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.EntityMoved;
import io.ib67.yuanban.bot.observe.Observation.EntityRemoved;
import io.ib67.yuanban.bot.observe.Observation.EntitySpawned;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldBoundObserver;
import io.ib67.yuanban.bot.observe.WorldInfo;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 跟踪满足 who 的实体的移动，按世界。跟踪的实体：开始跟踪时（注册时以及每次进入新世界时）模型中已有的、满足 who 的实体，
 * 以及之后 {@link EntitySpawned} 中满足 who 的实体；who 只在开始跟踪时判断一次。
 * 给出关闭条件时，每条 {@link EntityMoved} 之后对相应实体重新判断，满足条件的实体数达到要求时关闭自己。
 */
public final class MovementObserver extends WorldBoundObserver<MovementObserver.Tracking> {
    private final Predicate<EntitySnapshot> who;
    private final @Nullable MovementCriteria closeWhen;
    private boolean met;

    MovementObserver(Predicate<EntitySnapshot> who, @Nullable MovementCriteria closeWhen) {
        if (closeWhen != null) {
            closeWhen.validate();
        }
        this.who = who;
        this.closeWhen = closeWhen;
    }

    /**
     * 当前世界中每个被跟踪实体一条记录，按开始跟踪的顺序
     */
    public List<MovementTrack> tracks() {
        checkReadable();
        var world = currentWorld();
        return world == null ? List.of() : world.tracks();
    }

    /**
     * 是否因满足条件而关闭
     *
     * @throws IllegalStateException 只记录的 observer（没有给出关闭条件）没有结论
     */
    public boolean met() {
        checkReadable();
        requireCriteria();
        return met;
    }

    /**
     * 当前世界中满足条件的实体
     */
    public List<MovementTrack> metBy() {
        checkReadable();
        requireCriteria();
        var world = currentWorld();
        if (world == null) {
            return List.of();
        }
        return world.tracks.values().stream().filter(track -> world.satisfied.contains(track.entityId())).toList();
    }

    @Override
    protected Subscription worldSubscription() {
        return Subscription.of(EntitySpawned.class, EntityMoved.class, EntityRemoved.class);
    }

    @Override
    protected Tracking openWorld(WorldInfo world, ObserverContext context) {
        var tracking = new Tracking();
        for (var entity : context.state().entities()) {
            if (who.test(entity)) {
                tracking.start(entity);
            }
        }
        return tracking;
    }

    @Override
    protected void observe(Tracking world, Observation observation, ObserverContext context) {
        switch (observation) {
            case EntitySpawned(var entity) -> {
                if (who.test(entity)) {
                    world.start(entity);
                }
            }
            case EntityMoved(int entityId, var _, var to, var _) -> {
                var track = world.tracks.get(entityId);
                if (track == null) {
                    return;
                }
                track = new MovementTrack(entityId, track.type(), track.start(), to, track.removed());
                world.tracks.put(entityId, track);
                if (closeWhen != null && !world.satisfied.contains(entityId) && closeWhen.test(track)) {
                    world.satisfied.add(entityId);
                    if (world.satisfied.size() >= closeWhen.entities() && !met) {
                        met = true;
                        context.close();
                    }
                }
            }
            case EntityRemoved(var last) -> {
                var track = world.tracks.get(last.entityId());
                if (track != null) {
                    world.tracks.put(last.entityId(), new MovementTrack(track.entityId(), track.type(), track.start(),
                            track.current(), true));
                }
            }
            default -> {
            }
        }
    }

    private void requireCriteria() {
        if (closeWhen == null) {
            throw new IllegalStateException("movementOf without close criteria has no conclusion");
        }
    }

    /**
     * 一个世界中的跟踪状态
     */
    public static final class Tracking {
        private final Map<Integer, MovementTrack> tracks = new LinkedHashMap<>();
        private final List<Integer> satisfied = new ArrayList<>();

        private void start(EntitySnapshot entity) {
            var position = entity.position().asVec();
            tracks.put(entity.entityId(), new MovementTrack(entity.entityId(), entity.type(), position, position, false));
        }

        public List<MovementTrack> tracks() {
            return List.copyOf(tracks.values());
        }

        /**
         * 满足条件的实体编号，实体被移除后保留
         */
        public List<Integer> satisfied() {
            return List.copyOf(satisfied);
        }
    }
}
