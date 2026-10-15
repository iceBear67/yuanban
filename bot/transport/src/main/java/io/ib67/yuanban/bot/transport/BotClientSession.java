package io.ib67.yuanban.bot.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import org.geysermc.mcprotocollib.network.NetworkConstants;
import org.geysermc.mcprotocollib.network.netty.MinecraftChannelInitializer;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import java.net.SocketAddress;

/**
 * 本库的 MCProtocolLib 客户端会话：
 * <ul>
 *     <li>数据包处理执行器为 {@code Runnable::run}：全部数据包在 Netty 事件循环上按到达顺序处理。
 *     MCProtocolLib 默认把一部分数据包（例如区块数据）交给另一个线程处理，它们与其余数据包之间的顺序没有保证；</li>
 *     <li>使用 {@link NettyGroup}；</li>
 *     <li>管线中的 {@code read-timeout} 换成 {@link LastReadHandler}。</li>
 * </ul>
 */
final class BotClientSession extends ClientNetworkSession {
    private final LastReadHandler lastRead;

    BotClientSession(SocketAddress remoteAddress, MinecraftProtocol protocol, LastReadHandler lastRead) {
        super(remoteAddress, protocol, Runnable::run, null, null);
        this.lastRead = lastRead;
    }

    @Override
    protected EventLoopGroup getEventLoopGroup() {
        return NettyGroup.get();
    }

    @Override
    protected ChannelHandler getChannelHandler() {
        return new MinecraftChannelInitializer<>(_ -> {
            getPacketProtocol().newClientSession(this);
            return this;
        }, true) {
            @Override
            protected void addHandlers(BotClientSession session, Channel channel) {
                super.addHandlers(session, channel);
                channel.pipeline().replace(NetworkConstants.READ_TIMEOUT_NAME, LastReadHandler.NAME, lastRead);
            }
        };
    }
}
