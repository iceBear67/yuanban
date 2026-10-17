package io.ib67.yuanban.bot.testing;

import io.ib67.yuanban.bot.transport.ClientConnection;
import io.ib67.yuanban.bot.transport.InboundSink;
import io.ib67.yuanban.bot.transport.Transport;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 测试中扮演服务端一侧的连接对端：测试线程按顺序把 MCProtocolLib 的数据包对象交给 bot；
 * bot 发出的数据包按顺序记录下来。
 */
public final class TestPeer implements Transport {
    private static final AtomicLong BARRIER = new AtomicLong(1_000_000);

    private final List<MinecraftPacket> sent = new ArrayList<>();
    private volatile @Nullable InboundSink sink;
    private volatile boolean closedByBot;
    private volatile @Nullable CompletableFuture<Void> heldWrites;

    @Override
    public ClientConnection open(String username, InboundSink inbound) {
        this.sink = inbound;
        return new ClientConnection() {
            @Override
            public String serverAddress() {
                return "test.invalid";
            }

            @Override
            public int serverPort() {
                return 25565;
            }

            @Override
            public CompletableFuture<Void> send(List<MinecraftPacket> packets) {
                synchronized (sent) {
                    sent.addAll(packets);
                    sent.notifyAll();
                }
                var held = heldWrites;
                return held == null ? CompletableFuture.completedFuture(null) : held.copy();
            }

            @Override
            public void close() {
                closedByBot = true;
                inbound.closed(null);
            }
        };
    }

    public InboundSink sink() {
        return Objects.requireNonNull(sink, "the bot has not opened the connection");
    }

    /**
     * 按顺序把数据包交给 bot（测试线程串行调用 {@link InboundSink#accept}）
     */
    public void push(MinecraftPacket... packets) {
        for (var packet : packets) {
            sink().accept(packet);
        }
    }

    public void push(List<? extends MinecraftPacket> packets) {
        packets.forEach(packet -> sink().accept(packet));
    }

    /**
     * 服务端关闭连接
     */
    public void close(@Nullable Throwable error) {
        sink().closed(error);
    }

    public boolean closedByBot() {
        return closedByBot;
    }

    /**
     * 之后交出的数据包照常记录，但它们的写出要等返回的 future 完成：正常完成表示写出，异常完成表示写出之前连接断开
     */
    public CompletableFuture<Void> holdWrites() {
        var held = new CompletableFuture<Void>();
        heldWrites = held;
        return held;
    }

    /**
     * 到目前为止 bot 发出的全部数据包
     */
    public List<MinecraftPacket> sent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    public int mark() {
        synchronized (sent) {
            return sent.size();
        }
    }

    /**
     * 从 mark 开始 bot 发出的数据包
     */
    public List<MinecraftPacket> sentSince(int mark) {
        synchronized (sent) {
            return List.copyOf(sent.subList(mark, sent.size()));
        }
    }

    public <P extends MinecraftPacket> List<P> sentSince(int mark, Class<P> type) {
        return sentSince(mark).stream().filter(type::isInstance).map(type::cast).toList();
    }

    /**
     * 等到 bot 从 mark 开始发出一个满足条件的数据包
     */
    public <P extends MinecraftPacket> P awaitSent(int mark, Class<P> type, Predicate<P> filter, Duration timeout) {
        var deadline = System.nanoTime() + timeout.toNanos();
        synchronized (sent) {
            while (true) {
                for (int i = mark; i < sent.size(); i++) {
                    var packet = sent.get(i);
                    if (type.isInstance(packet) && filter.test(type.cast(packet))) {
                        return type.cast(packet);
                    }
                }
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new AssertionError("timed out waiting for " + type.getSimpleName() + "; sent since mark: " + sentSince(mark));
                }
                try {
                    sent.wait(Math.max(1, remaining / 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        }
    }

    public <P extends MinecraftPacket> P awaitSent(int mark, Class<P> type, Duration timeout) {
        return awaitSent(mark, type, _ -> true, timeout);
    }

    /**
     * 发送一个 Keep Alive 并等它的应答：应答发出时，之前交给 bot 的数据包都已经应用并投递了观察记录
     */
    public void barrier() {
        var id = BARRIER.incrementAndGet();
        var mark = mark();
        push(new ClientboundKeepAlivePacket(id));
        awaitSent(mark, ServerboundKeepAlivePacket.class, packet -> packet.getPingId() == id, Duration.ofSeconds(10));
    }
}
