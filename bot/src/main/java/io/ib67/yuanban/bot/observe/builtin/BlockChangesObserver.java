package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.BlockChanged;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldBoundObserver;
import io.ib67.yuanban.bot.observe.WorldInfo;

import java.util.List;

/**
 * 记录区域内的方块变化，按世界。
 */
public final class BlockChangesObserver extends WorldBoundObserver<Records<BlockChanged>> {
    private final Region region;
    private int limit = Integer.MAX_VALUE;

    BlockChangesObserver(Region region) {
        this.region = region;
    }

    /**
     * 每个世界最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public BlockChangesObserver limit(int limit) {
        BuiltinObserver.checkNotRegistered(this);
        this.limit = limit;
        return this;
    }

    public List<BlockChanged> changes() {
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
        return Subscription.of(BlockChanged.class);
    }

    @Override
    protected Records<BlockChanged> openWorld(WorldInfo world, ObserverContext context) {
        return new Records<>(limit);
    }

    @Override
    protected void observe(Records<BlockChanged> world, Observation observation, ObserverContext context) {
        if (observation instanceof BlockChanged changed && region.contains(changed.pos())) {
            world.add(changed);
        }
    }
}
