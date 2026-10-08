package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

/**
 * 收到的数据包（来自服务端），在它应用到模型之后、它产生的模型层观察记录之后投递。
 */
public record PacketReceived(MinecraftPacket packet) implements Observation {
}
