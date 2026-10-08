package io.ib67.yuanban.bot.protocol;

import org.geysermc.mcprotocollib.network.packet.PacketRegistry;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.jetbrains.annotations.NotNull;

/**
 * 数据包类的方向。MCProtocolLib 的数据包都只实现 {@link MinecraftPacket}，没有区分方向的类型，
 * 所以按 {@link MinecraftCodec#CODEC} 中任一协议阶段的注册判断。{@code MinecraftPacket.class} 本身表示这一方向的全部数据包。
 */
public final class PacketDirection {
    private static final ClassValue<Boolean> CLIENTBOUND = new ClassValue<>() {
        @Override
        protected Boolean computeValue(@NotNull Class<?> type) {
            return registered(type, PacketRegistry::getClientboundId);
        }
    };
    private static final ClassValue<Boolean> SERVERBOUND = new ClassValue<>() {
        @Override
        protected Boolean computeValue(@NotNull Class<?> type) {
            return registered(type, PacketRegistry::getServerboundId);
        }
    };

    private PacketDirection() {
    }

    /**
     * @throws IllegalArgumentException 不是服务端发往客户端的数据包类
     */
    public static void requireClientbound(Class<? extends MinecraftPacket> type) {
        if (type != MinecraftPacket.class && !CLIENTBOUND.get(type)) {
            throw new IllegalArgumentException(type.getName() + " is not a clientbound packet");
        }
    }

    /**
     * @throws IllegalArgumentException 不是客户端发往服务端的数据包类
     */
    public static void requireServerbound(Class<? extends MinecraftPacket> type) {
        if (type != MinecraftPacket.class && !SERVERBOUND.get(type)) {
            throw new IllegalArgumentException(type.getName() + " is not a serverbound packet");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean registered(Class<?> type, IdLookup lookup) {
        if (!MinecraftPacket.class.isAssignableFrom(type)) {
            return false;
        }
        for (var state : ProtocolState.values()) {
            var registry = MinecraftCodec.CODEC.getCodec(state);
            if (registry == null) {
                continue;
            }
            // PacketRegistry 只以 IllegalArgumentException 表示这个类在这个阶段中没有注册
            try {
                lookup.id(registry, (Class<? extends MinecraftPacket>) type);
                return true;
            } catch (IllegalArgumentException _) {
                // 继续检查下一个阶段
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface IdLookup {
        int id(PacketRegistry registry, Class<? extends MinecraftPacket> type);
    }
}
