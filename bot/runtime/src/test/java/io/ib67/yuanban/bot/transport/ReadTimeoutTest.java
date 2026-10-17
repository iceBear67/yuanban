package io.ib67.yuanban.bot.transport;

import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.TestServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读取超时由调度线程执行（登录阶段不 tick），所以经过 {@link BotRuntime} 检查；连接本身的读写行为在
 * {@code :bot:transport} 的 {@code TcpConnectionTest} 中。
 */
class ReadTimeoutTest {
    @Test
    void readTimeoutAppliesDuringLogin() throws Exception {
        try (var listener = ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
             var runtime = BotRuntime.create()) {
            var bot = runtime.connect(BotOptions.builder().username("silent")
                    .transport(new TcpTransport("127.0.0.1", ((InetSocketAddress) listener.getLocalAddress()).getPort()))
                    .readTimeout(Duration.ofMillis(300))
                    .build());
            try (var _ = listener.accept()) {
                // 服务端一直不发送数据：登录阶段不 tick，调度线程按读取超时的时间醒来并断开
                var thrown = assertThrows(BotDisconnectedException.class, () -> bot.joined().await(TestServer.TIMEOUT));
                assertTrue(thrown.getMessage().contains("timed out"), thrown.getMessage());
                assertEquals(BotStatus.DISCONNECTED, bot.status());
            }
        }
    }

    @Test
    void aZeroReadTimeoutNeverClosesTheConnection() throws Exception {
        try (var listener = ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
             var runtime = BotRuntime.create()) {
            var bot = runtime.connect(BotOptions.builder().username("patient")
                    .transport(new TcpTransport("127.0.0.1", ((InetSocketAddress) listener.getLocalAddress()).getPort()))
                    .readTimeout(Duration.ZERO)
                    .build());
            try (var _ = listener.accept()) {
                // 服务端不发送数据也没有超时：bot 停在登录状态
                Thread.sleep(600);
                assertEquals(BotStatus.LOGIN, bot.status());
            }
        }
    }
}
