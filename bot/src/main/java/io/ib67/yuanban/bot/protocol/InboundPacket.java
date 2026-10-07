package io.ib67.yuanban.bot.protocol;

import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.Nullable;

/**
 * 连接交给调度线程的一个数据包，以及解码上下文为它准备的附加数据（解码出的区块、标签、注册表）。
 */
public record InboundPacket(ServerPacket packet, @Nullable Object attachment) {
}
