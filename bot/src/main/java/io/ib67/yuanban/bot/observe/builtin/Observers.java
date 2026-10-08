package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.PacketReceived;
import io.ib67.yuanban.bot.observe.Observation.PacketSent;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import io.ib67.yuanban.bot.view.StateReader;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 内置 observer 的工厂方法。每次调用返回一个新的 observer 实例。
 */
public final class Observers {
    private Observers() {
    }

    // 只记录的 observer

    public static ParticleObserver particlesWithin(Region region) {
        return particlesWithin(region, _ -> true);
    }

    public static ParticleObserver particlesWithin(Region region, Predicate<Particle> filter) {
        return new ParticleObserver(region, filter);
    }

    public static SoundObserver sounds(Region region) {
        return sounds(region, _ -> true);
    }

    public static SoundObserver sounds(Region region, Predicate<Sound> filter) {
        return new SoundObserver(region, filter);
    }

    public static BlockChangesObserver blockChanges(Region region) {
        return new BlockChangesObserver(region);
    }

    public static BlockAtObserver blockAt(Point pos) {
        return new BlockAtObserver(pos, null);
    }

    public static MovementObserver movementOf(Predicate<EntitySnapshot> who) {
        return new MovementObserver(who, null);
    }

    public static ChatObserver chatMessages() {
        return chatMessages(_ -> true);
    }

    public static ChatObserver chatMessages(Predicate<ChatLine> filter) {
        return new ChatObserver(filter);
    }

    public static ActionBarObserver actionBars() {
        return new ActionBarObserver();
    }

    public static DamageObserver damageTaken() {
        return new DamageObserver();
    }

    /**
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> PacketObserver<P> packets(Class<P> type) {
        return packets(type, _ -> true);
    }

    /**
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> PacketObserver<P> packets(Class<P> type, Predicate<P> filter) {
        return new PacketObserver<>(type, filter);
    }

    /**
     * @throws IllegalArgumentException type 不是客户端发往服务端的数据包类
     */
    public static <P extends MinecraftPacket> SentPacketObserver<P> sentPackets(Class<P> type) {
        return sentPackets(type, _ -> true);
    }

    /**
     * @throws IllegalArgumentException type 不是客户端发往服务端的数据包类
     */
    public static <P extends MinecraftPacket> SentPacketObserver<P> sentPackets(Class<P> type, Predicate<P> filter) {
        return new SentPacketObserver<>(type, filter);
    }

    /**
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 packets、sentPackets）
     */
    public static <O extends Observation> ObservationObserver<O> observations(Class<O> type) {
        return observations(type, _ -> true);
    }

    /**
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 packets、sentPackets）
     */
    public static <O extends Observation> ObservationObserver<O> observations(Class<O> type, Predicate<O> filter) {
        return new ObservationObserver<>(type, filter);
    }

    // 得出结论时关闭自己的 observer

    /**
     * @throws IllegalArgumentException closeWhen 没有设置 atLeast、towards、into 中的任何一项
     */
    public static MovementObserver movementOf(Predicate<EntitySnapshot> who, MovementCriteria closeWhen) {
        return new MovementObserver(who, closeWhen);
    }

    public static BlockAtObserver blockAt(Point pos, Predicate<Block> closeWhen) {
        return new BlockAtObserver(pos, closeWhen);
    }

    public static BlockBrokenObserver blockBroken(Point pos) {
        return new BlockBrokenObserver(pos);
    }

    /**
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 firstPacket）
     */
    public static <O extends Observation> FirstObserver<O> first(Class<O> type, Predicate<O> filter) {
        return new FirstObserver<>(type, filter);
    }

    /**
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> FirstPacketObserver<P> firstPacket(Class<P> type, Predicate<P> filter) {
        return new FirstPacketObserver<>(type, filter);
    }

    public static FirstChatObserver firstChat(Predicate<ChatLine> filter) {
        return new FirstChatObserver(filter);
    }

    /**
     * 消息的组件（包括子组件与翻译参数）中有翻译键为 translationKey 的翻译组件时匹配，不考虑消息的其它内容
     */
    public static FirstTranslatedChatObserver firstTranslatedChat(String translationKey) {
        return new FirstTranslatedChatObserver(translationKey);
    }

    public static ConditionObserver condition(Predicate<StateReader> closeWhen) {
        return new ConditionObserver(closeWhen);
    }

    /**
     * 玩家与 target 的距离不大于 range 时，取消 action 并关闭；action 先结束时关闭。在提交动作之后构造
     */
    public static DistanceObserver distanceWithIn(double range, Point target, Action action) {
        return new DistanceObserver(range, target, action, true);
    }

    /**
     * 玩家与 target 的距离大于 range 时，取消 action 并关闭；action 先结束时关闭。在提交动作之后构造
     */
    public static DistanceObserver distanceOutOf(double range, Point target, Action action) {
        return new DistanceObserver(range, target, action, false);
    }

    // 监听

    /**
     * 回调在调度线程上执行，不保存状态
     *
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 packetListener）
     */
    public static <O extends Observation> Observer listener(Class<O> type, Consumer<O> consumer) {
        var subscription = Subscription.of(type);
        return new Observer() {
            @Override
            public Subscription subscription() {
                return subscription;
            }

            @Override
            public void onObservation(Observation observation, ObserverContext context) {
                if (type.isInstance(observation)) {
                    consumer.accept(type.cast(observation));
                }
            }
        };
    }

    /**
     * 回调在调度线程上执行，不保存状态
     *
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> Observer packetListener(Class<P> type, Consumer<P> consumer) {
        var subscription = Subscription.NONE.received(type);
        return new Observer() {
            @Override
            public Subscription subscription() {
                return subscription;
            }

            @Override
            public void onObservation(Observation observation, ObserverContext context) {
                if (observation instanceof PacketReceived(var packet) && type.isInstance(packet)) {
                    consumer.accept(type.cast(packet));
                }
            }
        };
    }
}
