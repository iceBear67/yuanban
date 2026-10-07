package io.ib67.yuanban.bot.observe;

import net.minestom.server.network.packet.client.ClientPacket;

/**
 * 发出的数据包，在它交给连接之后投递。
 */
public record PacketSent(ClientPacket packet) implements Observation {
}
