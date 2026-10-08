package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.PacketReceived;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.protocol.PacketDirection;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

import java.util.List;
import java.util.function.Predicate;

/**
 * 记录收到的、满足条件的某类数据包（服务端发往客户端的数据包）。
 */
public final class PacketObserver<P extends MinecraftPacket> extends BuiltinObserver {
    private final Class<P> type;
    private final Predicate<P> filter;
    private Records<P> packets = new Records<>(Integer.MAX_VALUE);

    PacketObserver(Class<P> type, Predicate<P> filter) {
        PacketDirection.requireClientbound(type);
        this.type = type;
        this.filter = filter;
    }

    /**
     * 最多保存的记录数，在注册之前设置
     *
     * @throws IllegalStateException 已经注册过
     */
    public PacketObserver<P> limit(int limit) {
        checkNotRegistered(this);
        this.packets = new Records<>(limit);
        return this;
    }

    public List<P> packets() {
        checkReadable();
        return packets.items();
    }

    public boolean truncated() {
        checkReadable();
        return packets.truncated();
    }

    @Override
    public Subscription subscription() {
        return Subscription.NONE.received(type);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (observation instanceof PacketReceived(var packet) && type.isInstance(packet)) {
            var typed = type.cast(packet);
            if (filter.test(typed)) {
                packets.add(typed);
            }
        }
    }
}
