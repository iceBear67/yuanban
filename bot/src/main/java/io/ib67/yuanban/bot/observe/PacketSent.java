package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

/**
 * 发出的数据包（发往服务端），在它交给连接之后投递。
 */
public record PacketSent(MinecraftPacket packet) implements Observation {
}
