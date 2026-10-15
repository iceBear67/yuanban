package io.ib67.yuanban.bot.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * 记录最后一次从套接字读到数据的时间，读取超时由调度线程检查。放在管线的最前面，代替 MCProtocolLib 以整数秒计的
 * {@code ReadTimeoutHandler}。
 */
final class LastReadHandler extends ChannelInboundHandlerAdapter {
    static final String NAME = "yuanban-last-read";

    private volatile long lastReadNanos = System.nanoTime();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        lastReadNanos = System.nanoTime();
        ctx.fireChannelRead(msg);
    }

    long lastReadNanos() {
        return lastReadNanos;
    }
}
