package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.PacketReceived;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.observe.WorldBoundObserver;
import io.ib67.yuanban.bot.observe.WorldInfo;
import net.minestom.server.coordinate.Vec;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.List;
import java.util.function.Predicate;

/**
 * 记录位置在区域内的音效，按世界。实体音效按收到时该实体在模型中的位置判断范围；实体不在模型中时不记录。
 */
public final class SoundObserver extends WorldBoundObserver<Records<SoundRecord>> {
    private final Region region;
    private final Predicate<Sound> filter;
    private int limit = Integer.MAX_VALUE;

    SoundObserver(Region region, Predicate<Sound> filter) {
        this.region = region;
        this.filter = filter;
    }

    /**
     * 每个世界最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public SoundObserver limit(int limit) {
        BuiltinObserver.checkNotRegistered(this);
        this.limit = limit;
        return this;
    }

    public List<SoundRecord> sounds() {
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
        return Subscription.NONE.received(ClientboundSoundPacket.class, ClientboundSoundEntityPacket.class);
    }

    @Override
    protected Records<SoundRecord> openWorld(WorldInfo world, ObserverContext context) {
        return new Records<>(limit);
    }

    @Override
    protected void observe(Records<SoundRecord> world, Observation observation, ObserverContext context) {
        if (!(observation instanceof PacketReceived(var packet))) {
            return;
        }
        switch (packet) {
            case ClientboundSoundPacket sound -> {
                // MCProtocolLib 已经把坐标除以 8
                var origin = new Vec(sound.getX(), sound.getY(), sound.getZ());
                if (region.contains(origin) && filter.test(sound.getSound())) {
                    world.add(new SoundRecord(sound.getSound(), sound.getCategory(), origin, sound.getVolume(),
                            sound.getPitch(), null));
                }
            }
            case ClientboundSoundEntityPacket sound -> {
                var entity = context.state().entity(sound.getEntityId());
                if (entity != null && region.contains(entity.position()) && filter.test(sound.getSound())) {
                    world.add(new SoundRecord(sound.getSound(), sound.getCategory(), entity.position().asVec(),
                            sound.getVolume(), sound.getPitch(), sound.getEntityId()));
                }
            }
            default -> {
            }
        }
    }
}
