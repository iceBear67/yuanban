package io.ib67.yuanban.agent.trace;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.InstantSource;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 版本化的 JSONL trace。每行一条记录：
 * {@code {"v":1,"seq":序号,"t":epoch 毫秒,"thread":线程名,"agent":agent 编号,"bot":bot 名称,"kind":种类,...}}。
 * <p>
 * {@link #append} 只把记录放进队列，由写入线程脱敏后写出，可以在任何线程上调用（包括 bot 调度线程）；
 * {@link #appendDurable} 在写出并刷到磁盘之后完成。写入失败之后全部调用抛出 {@link UncheckedIOException}。
 */
public final class TraceStore implements AutoCloseable {
    public static final int VERSION = 1;
    private static final Set<String> RESERVED = Set.of("v", "seq", "t", "thread", "agent", "bot", "kind");

    private final Path file;
    private final String agentId;
    private final String botName;
    private final Redactor redactor;
    private final InstantSource clock;
    private final FileChannel channel;
    private final LinkedBlockingQueue<Entry> queue = new LinkedBlockingQueue<>();
    private final Thread writer;
    private final Object order = new Object();
    private long sequence;
    private volatile @Nullable IOException failure;
    private volatile boolean closed;

    private TraceStore(Path file, String agentId, String botName, Redactor redactor, InstantSource clock) throws IOException {
        this.file = file;
        this.agentId = agentId;
        this.botName = botName;
        this.redactor = redactor;
        this.clock = clock;
        Files.createDirectories(file.toAbsolutePath().getParent());
        this.channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        this.writer = Thread.ofPlatform().daemon(true).name("agent-trace-" + agentId).start(this::drain);
    }

    /**
     * 打开 trace 文件（追加写入）并写出 header
     *
     * @throws UncheckedIOException 不能创建文件
     */
    public static TraceStore open(Path file, String agentId, String botName, Redactor redactor, InstantSource clock,
                                  JsonObject header) {
        try {
            var store = new TraceStore(file, agentId, botName, redactor, clock);
            store.append(TraceKind.HEADER, header);
            return store;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open trace file " + file, e);
        }
    }

    public Path file() {
        return file;
    }

    /**
     * 最近分配的记录序号
     */
    public long lastSequence() {
        synchronized (order) {
            return sequence;
        }
    }

    /**
     * @return 记录的序号
     */
    public long append(TraceKind kind, JsonObject fields) {
        return enqueue(kind, fields, null);
    }

    /**
     * @return 写出并刷到磁盘之后以记录的序号完成
     */
    public CompletableFuture<Long> appendDurable(TraceKind kind, JsonObject fields) {
        var future = new CompletableFuture<Long>();
        enqueue(kind, fields, future);
        return future;
    }

    /**
     * 写出异常的类型、消息与堆栈
     *
     * @return 堆栈编号 {@code stk-<序号>}，其它记录用它引用
     */
    public String stack(Throwable error) {
        var text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        var seq = append(TraceKind.STACK, Json.object("type", error.getClass().getName(),
                "message", String.valueOf(error.getMessage()), "stack", text.toString()));
        return "stk-" + seq;
    }

    /**
     * 等到此前放进队列的记录都已写出
     */
    public void flush() {
        checkFailure();
        var marker = new CompletableFuture<Long>();
        queue.add(new Entry(null, marker, false));
        marker.join();
    }

    private long enqueue(TraceKind kind, JsonObject fields, @Nullable CompletableFuture<Long> durable) {
        checkFailure();
        if (closed) {
            throw new IllegalStateException("trace " + file + " is closed");
        }
        for (var key : fields.keySet()) {
            if (RESERVED.contains(key)) {
                throw new IllegalArgumentException("trace field " + key + " is reserved");
            }
        }
        var record = new JsonObject();
        synchronized (order) {
            var seq = ++sequence;
            record.addProperty("v", VERSION);
            record.addProperty("seq", seq);
            record.addProperty("t", clock.millis());
            record.addProperty("thread", Thread.currentThread().getName());
            record.addProperty("agent", agentId);
            record.addProperty("bot", botName);
            record.addProperty("kind", kind.wire());
            for (var entry : fields.entrySet()) {
                record.add(entry.getKey(), entry.getValue().deepCopy());
            }
            queue.add(new Entry(record, durable, false));
            return seq;
        }
    }

    private void drain() {
        while (true) {
            Entry entry;
            try {
                entry = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            if (entry.stop) {
                return;
            }
            if (entry.record == null) {
                if (entry.durable != null) {
                    entry.durable.complete(0L);
                }
                continue;
            }
            try {
                var line = Json.write(redactor.redact(entry.record)) + "\n";
                var buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                if (entry.durable != null) {
                    channel.force(false);
                    entry.durable.complete(entry.record.get("seq").getAsLong());
                }
            } catch (IOException e) {
                failure = e;
                if (entry.durable != null) {
                    entry.durable.completeExceptionally(new UncheckedIOException(e));
                }
                for (var pending : queue) {
                    if (pending.durable != null) {
                        pending.durable.completeExceptionally(new UncheckedIOException(e));
                    }
                }
                return;
            }
        }
    }

    private void checkFailure() {
        var error = failure;
        if (error != null) {
            throw new UncheckedIOException("failed to write trace " + file, error);
        }
    }

    /**
     * 写出队列中的记录并关闭文件
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.add(new Entry(null, null, true));
        try {
            writer.join();
            channel.force(false);
            channel.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to close trace " + file, e);
        }
        checkFailure();
    }

    /**
     * @param record 要写出的记录；为 null 时是刷新标记（stop 为 false）或结束标记（stop 为 true）
     */
    private record Entry(@Nullable JsonObject record, @Nullable CompletableFuture<Long> durable, boolean stop) {
    }
}
