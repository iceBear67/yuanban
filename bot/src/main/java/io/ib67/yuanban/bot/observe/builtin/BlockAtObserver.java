package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.BlockChanged;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldBoundObserver;
import io.ib67.yuanban.bot.observe.WorldInfo;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 跟踪一个位置的方块，按世界。给出关闭条件时，该位置的方块满足条件就关闭自己（进入世界时先检查一次当前方块）。
 */
public final class BlockAtObserver extends WorldBoundObserver<BlockAtObserver.Tracked> {
    private final Point position;
    private final @Nullable Predicate<Block> closeWhen;
    private boolean met;

    BlockAtObserver(Point position, @Nullable Predicate<Block> closeWhen) {
        this.position = position;
        this.closeWhen = closeWhen;
    }

    /**
     * 当前世界中该位置的方块；所在区块没有加载、也没有收到过变化时为 null
     */
    public @Nullable Block current() {
        checkReadable();
        var world = currentWorld();
        return world == null ? null : world.current;
    }

    /**
     * 当前世界中该位置的每次变化
     */
    public List<BlockChanged> history() {
        checkReadable();
        var world = currentWorld();
        return world == null ? List.of() : List.copyOf(world.history);
    }

    /**
     * 是否因方块满足条件而关闭
     *
     * @throws IllegalStateException 只记录的 observer（没有给出关闭条件）没有结论
     */
    public boolean met() {
        checkReadable();
        if (closeWhen == null) {
            throw new IllegalStateException("blockAt without a close condition has no conclusion");
        }
        return met;
    }

    @Override
    protected Subscription worldSubscription() {
        return Subscription.of(BlockChanged.class);
    }

    @Override
    protected Tracked openWorld(WorldInfo world, ObserverContext context) {
        var state = context.state();
        var loaded = state.chunkLoaded(CoordConversion.globalToChunk(position.blockX()), CoordConversion.globalToChunk(position.blockZ()));
        var tracked = new Tracked(loaded ? state.block(position) : null);
        check(tracked.current, context);
        return tracked;
    }

    @Override
    protected void observe(Tracked world, Observation observation, ObserverContext context) {
        if (observation instanceof BlockChanged changed && samePosition(changed.pos(), position)) {
            world.current = changed.now();
            world.history.add(changed);
            check(changed.now(), context);
        }
    }

    private void check(@Nullable Block block, ObserverContext context) {
        if (closeWhen != null && block != null && !met && closeWhen.test(block)) {
            met = true;
            context.close();
        }
    }

    static boolean samePosition(Point a, Point b) {
        return a.blockX() == b.blockX() && a.blockY() == b.blockY() && a.blockZ() == b.blockZ();
    }

    /**
     * 一个世界中该位置的方块与变化
     */
    public static final class Tracked {
        private @Nullable Block current;
        private final List<BlockChanged> history = new ArrayList<>();

        private Tracked(@Nullable Block current) {
            this.current = current;
        }

        public @Nullable Block current() {
            return current;
        }

        public List<BlockChanged> history() {
            return List.copyOf(history);
        }
    }
}
