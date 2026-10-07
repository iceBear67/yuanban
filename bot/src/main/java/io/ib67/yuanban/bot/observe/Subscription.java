package io.ib67.yuanban.bot.observe;

import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.ServerPacket;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * observer 要接收的观察记录类型。全部按 {@link Class#isInstance} 匹配。
 *
 * @param observations 模型层与 bot 自身的观察记录类型，不能包含 {@link PacketReceived} 与 {@link PacketSent}
 * @param received     以 {@link PacketReceived} 投递的入站数据包类；{@code ServerPacket.class} 表示全部
 * @param sent         以 {@link PacketSent} 投递的出站数据包类；{@code ClientPacket.class} 表示全部
 */
public record Subscription(Set<Class<? extends Observation>> observations,
                           Set<Class<? extends ServerPacket>> received,
                           Set<Class<? extends ClientPacket>> sent) {
    public static final Subscription NONE = new Subscription(Set.of(), Set.of(), Set.of());

    public Subscription {
        observations = Set.copyOf(observations);
        received = Set.copyOf(received);
        sent = Set.copyOf(sent);
        if (observations.contains(PacketReceived.class) || observations.contains(PacketSent.class)) {
            throw new IllegalArgumentException("PacketReceived and PacketSent must be subscribed through received() and sent()");
        }
    }

    @SafeVarargs
    public static Subscription of(Class<? extends Observation>... types) {
        return new Subscription(Set.of(types), Set.of(), Set.of());
    }

    @SafeVarargs
    public final Subscription received(Class<? extends ServerPacket>... types) {
        return new Subscription(observations, union(received, List.of(types)), sent);
    }

    @SafeVarargs
    public final Subscription sent(Class<? extends ClientPacket>... types) {
        return new Subscription(observations, received, union(sent, List.of(types)));
    }

    public Subscription and(Subscription other) {
        return new Subscription(union(observations, other.observations), union(received, other.received),
                union(sent, other.sent));
    }

    private static <T> Set<T> union(Set<T> left, Iterable<? extends T> right) {
        var result = new HashSet<T>(left);
        right.forEach(result::add);
        return result;
    }
}
