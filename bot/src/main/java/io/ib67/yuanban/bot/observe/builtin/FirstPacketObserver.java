package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.PacketReceived;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.protocol.PacketDirection;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 收到第一个匹配的数据包（服务端发往客户端的数据包）时关闭自己。
 */
public final class FirstPacketObserver<P extends MinecraftPacket> extends BuiltinObserver {
    private final Class<P> type;
    private final Predicate<P> filter;
    private @Nullable P matched;

    FirstPacketObserver(Class<P> type, Predicate<P> filter) {
        PacketDirection.requireClientbound(type);
        this.type = type;
        this.filter = filter;
    }

    /**
     * 匹配的数据包；在匹配之前被关闭时为空
     */
    public Optional<P> matched() {
        checkReadable();
        return Optional.ofNullable(matched);
    }

    @Override
    public Subscription subscription() {
        return Subscription.NONE.received(type);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (matched == null && observation instanceof PacketReceived(var packet) && type.isInstance(packet)) {
            var typed = type.cast(packet);
            if (filter.test(typed)) {
                matched = typed;
                context.close();
            }
        }
    }
}
