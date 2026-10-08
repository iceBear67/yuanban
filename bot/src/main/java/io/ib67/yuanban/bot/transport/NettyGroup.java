package io.ib67.yuanban.bot.transport;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;

/**
 * 全部 TCP 连接共用的 Netty 事件循环组：第一次使用时创建，线程为守护线程，不关闭。
 * 按 MCProtocolLib 选择的传输方式创建（epoll、kqueue 或 NIO）。MCProtocolLib 自己的共享事件循环组的延迟创建不是线程安全的，
 * 所以本库自己持有一个。
 */
final class NettyGroup {
    private NettyGroup() {
    }

    static EventLoopGroup get() {
        return Holder.GROUP;
    }

    private static final class Holder {
        private static final EventLoopGroup GROUP = TransportHelper.TRANSPORT_TYPE.eventLoopGroupFactory()
                .apply(0, new DefaultThreadFactory("yuanban-bot-netty", true));
    }
}
