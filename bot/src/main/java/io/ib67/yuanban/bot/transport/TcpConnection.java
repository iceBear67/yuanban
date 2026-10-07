package io.ib67.yuanban.bot.transport;

import io.ib67.yuanban.bot.decode.TcpInbound;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.PacketWriting;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一条 TCP 连接：一个入站虚拟线程读取并解码数据包，一个写虚拟线程编码并写出调度线程交来的数据包。
 * 通道为 JDK 的 {@link AsynchronousSocketChannel}：每次写入只把套接字能够立即接受的部分交给套接字，
 * 写线程因此知道套接字什么时候不能再接受数据。
 * <p>
 * 关闭按原版客户端的行为（原版 {@code Connection.disconnect} 关闭 Netty 通道，没有交给套接字的数据被丢弃）：
 * {@link #close()} 之前交出的批次按顺序写出，直到套接字不能再接受数据（发送缓冲区已满）为止，
 * 没有交给套接字的部分与剩下的批次以异常结束，套接字随即关闭；写线程正在等待套接字可写时调用 {@code close()}，立即关闭。
 * 关闭从不等待对端读取，两个线程与套接字都会释放。
 */
public final class TcpConnection implements ClientConnection {
    private static final Batch CLOSE = new Batch(List.of(), new CompletableFuture<>());

    private final String host;
    private final int port;
    private final AsynchronousSocketChannel channel;
    private final InboundSink sink;
    private final TcpInbound inbound;
    private final LinkedBlockingQueue<Batch> outgoing = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean closing;
    /**
     * 写线程正在等待套接字可写：剩下的数据要等对端读取之后才能交给套接字
     */
    private volatile boolean awaitingWritable;

    private TcpConnection(String host, int port, AsynchronousSocketChannel channel, InboundSink sink) {
        this.host = host;
        this.port = port;
        this.channel = channel;
        this.sink = sink;
        this.inbound = new TcpInbound(Channels.newChannel(Channels.newInputStream(channel)), sink);
    }

    static TcpConnection start(String host, int port, AsynchronousSocketChannel channel, InboundSink sink) {
        var connection = new TcpConnection(host, port, channel, sink);
        Thread.ofVirtual().name("yuanban-bot-tcp-inbound").start(connection::readLoop);
        Thread.ofVirtual().name("yuanban-bot-tcp-write").start(connection::writeLoop);
        return connection;
    }

    @Override
    public String serverAddress() {
        return host;
    }

    @Override
    public int serverPort() {
        return port;
    }

    /**
     * @return 全部交给套接字之后完成；没有交给套接字之前连接关闭时以异常完成
     */
    @Override
    public CompletableFuture<Void> send(List<ClientPacket> packets) {
        var future = new CompletableFuture<Void>();
        synchronized (outgoing) {
            if (closing) {
                future.completeExceptionally(new ClosedChannelException());
            } else {
                outgoing.add(new Batch(List.copyOf(packets), future));
            }
        }
        return future;
    }

    /**
     * 关闭连接：之前交出的批次写到套接字不能再接受数据为止，然后关闭套接字；写线程正在等待套接字可写时立即关闭
     */
    @Override
    public void close() {
        synchronized (outgoing) {
            if (closing) {
                return;
            }
            closing = true;
            outgoing.add(CLOSE);
        }
        if (awaitingWritable) {
            closeChannel();
        }
    }

    /**
     * 入站线程最后一次读到数据的时间（{@link System#nanoTime()}）
     */
    public long lastReadNanos() {
        return inbound.lastReadNanos();
    }

    private void readLoop() {
        try {
            inbound.run();
        } catch (EOFException e) {
            finish(null);
        } catch (Throwable t) {
            finish(closing ? null : t);
        }
    }

    private void writeLoop() {
        var buffer = NetworkBuffer.resizableBuffer();
        var state = ConnectionState.HANDSHAKE;
        Batch batch = null;
        try {
            while (true) {
                batch = outgoing.take();
                if (batch == CLOSE) {
                    // close() 之前交出的数据包都已经交给套接字
                    finish(null);
                    return;
                }
                for (var packet : batch.packets()) {
                    // 注册表由入站线程在读到 Finish Configuration 时构建。游戏阶段的数据包在 bot 回应 Finish Configuration
                    // 之后才产生，那时入站线程已经构建好对应的注册表；入站线程读到下一个 Finish Configuration 之前，
                    // 服务端要先收到这里写出的 Acknowledge Configuration
                    buffer.registries(sink.registries());
                    var threshold = inbound.compressionThreshold();
                    // 阈值为 0 表示全部压缩；Minestom 把小于等于 0 的阈值当作不压缩，所以用 1 表示同样的效果
                    PacketWriting.writeFramedPacket(buffer, state, packet, threshold < 0 ? 0 : Math.max(threshold, 1));
                    state = PacketVanilla.nextClientState(packet, state);
                }
                var bytes = ByteBuffer.wrap(buffer.read(NetworkBuffer.RAW_BYTES));
                buffer.clear();
                while (bytes.hasRemaining()) {
                    // 异步通道的写入先把套接字能够立即接受的部分交给套接字；一个字节都不能接受时才等待
                    var write = channel.write(bytes);
                    if (!write.isDone()) {
                        awaitingWritable = true;
                        if (closing) {
                            // close() 之前交出的批次：原版关闭通道时丢弃没有交给套接字的部分
                            closeChannel();
                        }
                        write.get();
                        awaitingWritable = false;
                    } else {
                        write.get();
                    }
                }
                batch.written().complete(null);
                batch = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            var cause = t instanceof ExecutionException execution ? execution.getCause() : t;
            // 已经取出、还没有写完的一批同样以异常结束
            if (batch != null) {
                batch.written().completeExceptionally(cause);
            }
            finish(closing ? null : cause);
        }
    }

    /**
     * 关闭通道：等待中的读取与写入以异常结束，两个线程随之结束
     */
    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to close the connection to " + host + ":" + port, e);
        }
    }

    private void finish(@Nullable Throwable error) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            if (error == null) {
                error = e;
            }
        }
        synchronized (outgoing) {
            closing = true;
            Batch pending;
            while ((pending = outgoing.poll()) != null) {
                if (pending != CLOSE) {
                    pending.written().completeExceptionally(new ClosedChannelException());
                }
            }
            outgoing.add(CLOSE);
        }
        sink.closed(error);
    }

    private record Batch(List<ClientPacket> packets, CompletableFuture<Void> written) {
    }
}
