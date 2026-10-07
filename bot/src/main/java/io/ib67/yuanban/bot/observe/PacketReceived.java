package io.ib67.yuanban.bot.observe;

import net.minestom.server.network.packet.server.ServerPacket;

/**
 * 收到的数据包，在它应用到模型之后、它产生的模型层观察记录之后投递。
 */
public record PacketReceived(ServerPacket packet) implements Observation {
}
