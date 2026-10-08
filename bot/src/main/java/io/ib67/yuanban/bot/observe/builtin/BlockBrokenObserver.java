package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.BlockChanged;
import io.ib67.yuanban.bot.observe.Observation.ChunkLoaded;
import io.ib67.yuanban.bot.observe.Observation.WorldEntered;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 该位置的方块变成方块编号（{@link Block#id()}）与 {@link #original()} 不同的方块时关闭自己；进入新世界时也关闭。
 * 只改变方块状态（例如朝向、含水）不算变成不同的方块。
 * <ul>
 *     <li>在世界中注册时，{@code original} 为此刻该位置的方块；所在区块未加载时 {@code onStart} 抛出
 *     {@link io.ib67.yuanban.bot.ChunkNotLoadedException}；</li>
 *     <li>不在任何世界中时注册，坐标属于下一个世界：收到的第一条 {@link WorldEntered} 不关闭，
 *     {@code original} 为进入之后该位置所在区块第一次加载时的方块，在这之前该位置不会产生 {@link BlockChanged}。</li>
 * </ul>
 */
public final class BlockBrokenObserver extends BuiltinObserver {
    private final Point position;
    /**
     * 是否已经有了坐标所属的世界：在世界中注册，或者不在世界中注册之后进入了下一个世界
     */
    private boolean inWorld;
    private @Nullable Block original;
    private @Nullable Block changedTo;

    BlockBrokenObserver(Point position) {
        this.position = new Vec(position.blockX(), position.blockY(), position.blockZ());
    }

    /**
     * 是否因方块变化而关闭
     */
    public boolean broken() {
        checkReadable();
        return changedTo != null;
    }

    /**
     * 注册时该位置的方块；不在任何世界中注册时，为进入下一个世界之后该位置所在区块第一次加载时的方块，
     * 在这之前为 null
     */
    public @Nullable Block original() {
        checkReadable();
        return original;
    }

    /**
     * 第一次变成的不同方块；没有时为 null
     */
    public @Nullable Block changedTo() {
        checkReadable();
        return changedTo;
    }

    @Override
    public Subscription subscription() {
        return Subscription.of(BlockChanged.class, ChunkLoaded.class, WorldEntered.class);
    }

    @Override
    void started(ObserverContext context) {
        if (context.world() != null) {
            inWorld = true;
            original = context.state().block(position);
        }
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        switch (observation) {
            case BlockChanged changed -> {
                // 区块加载之前该位置没有 Block Update 可以应用，original 一定已经读到
                if (BlockAtObserver.samePosition(changed.pos(), position) && changed.now().id() != Objects.requireNonNull(original).id()) {
                    changedTo = changed.now();
                    context.close();
                }
            }
            case ChunkLoaded(int x, int z) -> {
                if (original == null && inWorld && x == CoordConversion.globalToChunk(position.blockX())
                        && z == CoordConversion.globalToChunk(position.blockZ())) {
                    original = context.state().block(position);
                }
            }
            case WorldEntered _ -> {
                if (inWorld) {
                    context.close();
                } else {
                    inWorld = true;
                }
            }
            default -> {
            }
        }
    }
}
