package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.PacketReceived;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldBoundObserver;
import io.ib67.yuanban.bot.observe.WorldInfo;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;

import java.util.List;
import java.util.function.Predicate;

/**
 * 记录中心坐标在区域内的 Particle 数据包，按世界。
 */
public final class ParticleObserver extends WorldBoundObserver<Records<ParticleRecord>> {
    private final Region region;
    private final Predicate<Particle> filter;
    private int limit = Integer.MAX_VALUE;

    ParticleObserver(Region region, Predicate<Particle> filter) {
        this.region = region;
        this.filter = filter;
    }

    /**
     * 每个世界最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public ParticleObserver limit(int limit) {
        BuiltinObserver.checkNotRegistered(this);
        this.limit = limit;
        return this;
    }

    /**
     * 当前世界中的记录
     */
    public List<ParticleRecord> particles() {
        checkReadable();
        var world = currentWorld();
        return world == null ? List.of() : world.items();
    }

    /**
     * 当前世界中是否有超出上限而没有保存的记录
     */
    public boolean truncated() {
        checkReadable();
        var world = currentWorld();
        return world != null && world.truncated();
    }

    @Override
    protected Subscription worldSubscription() {
        return Subscription.NONE.received(ParticlePacket.class);
    }

    @Override
    protected Records<ParticleRecord> openWorld(WorldInfo world, ObserverContext context) {
        return new Records<>(limit);
    }

    @Override
    protected void observe(Records<ParticleRecord> world, Observation observation, ObserverContext context) {
        if (!(observation instanceof PacketReceived(ParticlePacket packet))) {
            return;
        }
        var center = new Vec(packet.x(), packet.y(), packet.z());
        if (region.contains(center) && filter.test(packet.particle())) {
            world.add(new ParticleRecord(packet.particle(), center, new Vec(packet.offsetX(), packet.offsetY(), packet.offsetZ()),
                    packet.maxSpeed(), packet.particleCount(), packet.longDistance()));
        }
    }
}
