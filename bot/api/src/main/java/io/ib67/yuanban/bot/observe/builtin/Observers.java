package io.ib67.yuanban.bot.observe.builtin;

import io.ib67.yuanban.bot.Action;
import io.ib67.yuanban.bot.observe.Observation;
import io.ib67.yuanban.bot.observe.Observation.ActionBarShown;
import io.ib67.yuanban.bot.observe.Observation.BlockChanged;
import io.ib67.yuanban.bot.observe.Observation.ChatReceived;
import io.ib67.yuanban.bot.observe.Observation.EntityDamaged;
import io.ib67.yuanban.bot.observe.Observation.HealthChanged;
import io.ib67.yuanban.bot.observe.Observation.PacketReceived;
import io.ib67.yuanban.bot.observe.Observation.PacketSent;
import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverContext;
import io.ib67.yuanban.bot.observe.Region;
import io.ib67.yuanban.bot.observe.Subscription;
import io.ib67.yuanban.bot.transport.PacketDirection;
import io.ib67.yuanban.bot.view.ChatLine;
import io.ib67.yuanban.bot.view.EntitySnapshot;
import io.ib67.yuanban.bot.view.StateReader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 内置 observer 的工厂方法。每次调用返回一个新的 observer 实例。
 */
public final class Observers {
    private Observers() {
    }

    // 只记录的 observer

    /**
     * 记录中心坐标在区域内的 Particle 数据包，按世界。
     */
    public static WorldRecordingObserver<ParticleRecord> particlesWithin(Region region) {
        return particlesWithin(region, _ -> true);
    }

    /**
     * 记录中心坐标在区域内的 Particle 数据包，按世界。
     */
    public static WorldRecordingObserver<ParticleRecord> particlesWithin(Region region, Predicate<Particle> filter) {
        return new WorldRecordingObserver<>(Subscription.NONE.received(ClientboundLevelParticlesPacket.class), (observation, _) -> {
            if (!(observation instanceof PacketReceived(ClientboundLevelParticlesPacket packet))) {
                return null;
            }
            var center = new Vec(packet.getX(), packet.getY(), packet.getZ());
            if (region.contains(center) && filter.test(packet.getParticle())) {
                return new ParticleRecord(packet.getParticle(), center,
                        new Vec(packet.getOffsetX(), packet.getOffsetY(), packet.getOffsetZ()), packet.getVelocityOffset(),
                        packet.getAmount(), packet.isLongDistance());
            }
            return null;
        });
    }

    /**
     * 记录位置在区域内的音效，按世界。实体音效按收到时该实体在模型中的位置判断范围；实体不在模型中时不记录。
     */
    public static WorldRecordingObserver<SoundRecord> sounds(Region region) {
        return sounds(region, _ -> true);
    }

    /**
     * 记录位置在区域内的音效，按世界。实体音效按收到时该实体在模型中的位置判断范围；实体不在模型中时不记录。
     */
    public static WorldRecordingObserver<SoundRecord> sounds(Region region, Predicate<Sound> filter) {
        return new WorldRecordingObserver<>(Subscription.NONE.received(ClientboundSoundPacket.class, ClientboundSoundEntityPacket.class),
                (observation, context) -> {
                    if (!(observation instanceof PacketReceived(var packet))) {
                        return null;
                    }
                    switch (packet) {
                        case ClientboundSoundPacket sound -> {
                            // MCProtocolLib 已经把坐标除以 8
                            var origin = new Vec(sound.getX(), sound.getY(), sound.getZ());
                            if (region.contains(origin) && filter.test(sound.getSound())) {
                                return new SoundRecord(sound.getSound(), sound.getCategory(), origin, sound.getVolume(),
                                        sound.getPitch(), null);
                            }
                        }
                        case ClientboundSoundEntityPacket sound -> {
                            var entity = context.state().entity(sound.getEntityId());
                            if (entity != null && region.contains(entity.position()) && filter.test(sound.getSound())) {
                                return new SoundRecord(sound.getSound(), sound.getCategory(), entity.position().asVec(),
                                        sound.getVolume(), sound.getPitch(), sound.getEntityId());
                            }
                        }
                        default -> {
                        }
                    }
                    return null;
                });
    }

    /**
     * 记录区域内的方块变化，按世界。
     */
    public static WorldRecordingObserver<BlockChanged> blockChanges(Region region) {
        return new WorldRecordingObserver<>(Subscription.of(BlockChanged.class), (observation, _) -> {
            if (observation instanceof BlockChanged changed && region.contains(changed.pos())) {
                return changed;
            }
            return null;
        });
    }

    public static BlockAtObserver blockAt(Point pos) {
        return new BlockAtObserver(pos, null);
    }

    public static MovementObserver movementOf(Predicate<EntitySnapshot> who) {
        return new MovementObserver(who, null);
    }

    /**
     * 记录满足条件的聊天消息。
     */
    public static RecordingObserver<ChatLine> chatMessages() {
        return chatMessages(_ -> true);
    }

    /**
     * 记录满足条件的聊天消息。
     */
    public static RecordingObserver<ChatLine> chatMessages(Predicate<ChatLine> filter) {
        return new RecordingObserver<>(Subscription.of(ChatReceived.class), (observation, _) -> {
            if (observation instanceof ChatReceived(ChatLine line) && filter.test(line)) {
                return line;
            }
            return null;
        });
    }

    /**
     * 记录显示过的动作栏文本。
     */
    public static RecordingObserver<Component> actionBars() {
        return new RecordingObserver<>(Subscription.of(ActionBarShown.class), (observation, _) -> {
            if (observation instanceof ActionBarShown(Component text)) {
                return text;
            }
            return null;
        });
    }

    /**
     * 记录本地玩家受到的伤害：目标为自己的 Damage Event 与生命值下降的 Set Health。
     */
    public static RecordingObserver<DamageRecord> damageTaken() {
        return new RecordingObserver<>(Subscription.of(EntityDamaged.class, HealthChanged.class), (observation, context) -> {
            switch (observation) {
                case EntityDamaged damaged -> {
                    if (damaged.entityId() == context.state().player().entityId()) {
                        return new DamageRecord.Event(damaged);
                    }
                }
                case HealthChanged changed -> {
                    if (changed.health() < changed.oldHealth()) {
                        return new DamageRecord.HealthDrop(changed);
                    }
                }
                default -> {
                }
            }
            return null;
        });
    }

    /**
     * 记录收到的、满足条件的某类数据包（服务端发往客户端的数据包）。
     *
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> RecordingObserver<P> packets(Class<P> type) {
        return packets(type, _ -> true);
    }

    /**
     * 记录收到的、满足条件的某类数据包（服务端发往客户端的数据包）。
     *
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> RecordingObserver<P> packets(Class<P> type, Predicate<P> filter) {
        PacketDirection.requireClientbound(type);
        return new RecordingObserver<>(Subscription.NONE.received(type), (observation, _) -> {
            if (observation instanceof PacketReceived(var packet) && type.isInstance(packet)) {
                var typed = type.cast(packet);
                if (filter.test(typed)) {
                    return typed;
                }
            }
            return null;
        });
    }

    /**
     * 记录发出的、满足条件的某类数据包（客户端发往服务端的数据包）。
     *
     * @throws IllegalArgumentException type 不是客户端发往服务端的数据包类
     */
    public static <P extends MinecraftPacket> RecordingObserver<P> sentPackets(Class<P> type) {
        return sentPackets(type, _ -> true);
    }

    /**
     * 记录发出的、满足条件的某类数据包（客户端发往服务端的数据包）。
     *
     * @throws IllegalArgumentException type 不是客户端发往服务端的数据包类
     */
    public static <P extends MinecraftPacket> RecordingObserver<P> sentPackets(Class<P> type, Predicate<P> filter) {
        PacketDirection.requireServerbound(type);
        return new RecordingObserver<>(Subscription.NONE.sent(type), (observation, _) -> {
            if (observation instanceof PacketSent(var packet) && type.isInstance(packet)) {
                var typed = type.cast(packet);
                if (filter.test(typed)) {
                    return typed;
                }
            }
            return null;
        });
    }

    /**
     * 记录满足条件的某类观察记录。
     *
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 packets、sentPackets）
     */
    public static <O extends Observation> RecordingObserver<O> observations(Class<O> type) {
        return observations(type, _ -> true);
    }

    /**
     * 记录满足条件的某类观察记录。
     *
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 packets、sentPackets）
     */
    public static <O extends Observation> RecordingObserver<O> observations(Class<O> type, Predicate<O> filter) {
        return new RecordingObserver<>(Subscription.of(type), (observation, _) -> {
            if (type.isInstance(observation)) {
                var typed = type.cast(observation);
                if (filter.test(typed)) {
                    return typed;
                }
            }
            return null;
        });
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
     * 出现第一条匹配的观察记录时关闭自己。
     *
     * @throws IllegalArgumentException type 为 {@link PacketReceived} 或 {@link PacketSent}（数据包使用 firstPacket）
     */
    public static <O extends Observation> FirstMatchObserver<O> first(Class<O> type, Predicate<O> filter) {
        return new FirstMatchObserver<>(Subscription.of(type), (observation, _) -> {
            if (type.isInstance(observation)) {
                var typed = type.cast(observation);
                if (filter.test(typed)) {
                    return typed;
                }
            }
            return null;
        });
    }

    /**
     * 收到第一个匹配的数据包（服务端发往客户端的数据包）时关闭自己。
     *
     * @throws IllegalArgumentException type 不是服务端发往客户端的数据包类
     */
    public static <P extends MinecraftPacket> FirstMatchObserver<P> firstPacket(Class<P> type, Predicate<P> filter) {
        PacketDirection.requireClientbound(type);
        return new FirstMatchObserver<>(Subscription.NONE.received(type), (observation, _) -> {
            if (observation instanceof PacketReceived(var packet) && type.isInstance(packet)) {
                var typed = type.cast(packet);
                if (filter.test(typed)) {
                    return typed;
                }
            }
            return null;
        });
    }

    /**
     * 收到第一条匹配的聊天消息时关闭自己。
     */
    public static FirstMatchObserver<ChatLine> firstChat(Predicate<ChatLine> filter) {
        return new FirstMatchObserver<>(Subscription.of(ChatReceived.class), (observation, _) -> {
            if (observation instanceof ChatReceived(ChatLine line) && filter.test(line)) {
                return line;
            }
            return null;
        });
    }

    /**
     * 收到第一条含有指定翻译键的聊天消息时关闭自己：消息的组件（包括子组件与翻译参数）中有这个翻译键的翻译组件即为匹配，
     * 不考虑消息的其它内容。{@link ChatLine#plainText()} 不翻译翻译组件，按翻译键匹配加入消息、死亡消息等由服务端翻译键组成的消息。
     */
    public static FirstMatchObserver<ChatLine> firstTranslatedChat(String translationKey) {
        return new FirstMatchObserver<>(Subscription.of(ChatReceived.class), (observation, _) -> {
            if (observation instanceof ChatReceived(ChatLine line) && containsTranslation(line.message(), translationKey)) {
                return line;
            }
            return null;
        });
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

    private static boolean containsTranslation(Component component, String translationKey) {
        if (component instanceof TranslatableComponent translatable) {
            if (translatable.key().equals(translationKey)) {
                return true;
            }
            for (var argument : translatable.arguments()) {
                if (argument.value() instanceof Component value && containsTranslation(value, translationKey)) {
                    return true;
                }
            }
        }
        for (var child : component.children()) {
            if (containsTranslation(child, translationKey)) {
                return true;
            }
        }
        return false;
    }
}
