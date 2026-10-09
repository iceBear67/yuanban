package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.bot.Signal;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceReader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 确定性重放的后端：每个计划（按 planKey）的第 n 次调用返回 trace 中这个计划第 n 条 {@code tool_end} 记录的结果，
 * 按记录的耗时等待；信号按 trace 中的相对时间投递。工具名与记录不一致、或者记录用完时结果为 REPLAY_MISMATCH，
 * 并记入 {@link #mismatches()}。
 */
public final class RecordedBackend implements ToolBackend {
    private final Map<String, ArrayDeque<JsonObject>> calls = new HashMap<>();
    private final List<JsonObject> signals = new ArrayList<>();
    private final List<String> mismatches = new CopyOnWriteArrayList<>();
    private final long traceStart;
    private final long startNanos = System.nanoTime();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("replay-signals").factory());

    public RecordedBackend(TraceReader trace) {
        this.traceStart = trace.records().getFirst().get("t").getAsLong();
        for (var record : trace.ofKind(TraceKind.TOOL_END)) {
            calls.computeIfAbsent(Json.requireString(record, "planKey"), _ -> new ArrayDeque<>()).add(record);
        }
        for (var record : trace.ofKind(TraceKind.OBSERVATION)) {
            if (record.has("signal")) {
                signals.add(record);
            }
        }
    }

    public List<String> mismatches() {
        return List.copyOf(mismatches);
    }

    @Override
    public ToolResult execute(ToolSpec spec, ToolInvocation invocation, CancelToken cancel, ToolPolicy policy) {
        JsonObject record;
        synchronized (calls) {
            var queue = calls.get(invocation.planKey());
            record = queue == null ? null : queue.poll();
        }
        if (record == null || !invocation.tool().equals(Json.requireString(record, "tool"))) {
            var mismatch = invocation.planKey() + " call " + invocation.ordinal() + ": " + invocation.tool()
                    + (record == null ? " has no recorded result" : " was recorded as " + Json.requireString(record, "tool"));
            mismatches.add(mismatch);
            return ToolResult.of(ResultKind.REPLAY_MISMATCH, mismatch);
        }
        var result = ToolResult.fromJson(record.getAsJsonObject("result"));
        var done = new CountDownLatch(1);
        cancel.onCancel(done::countDown);
        try {
            if (done.await(result.elapsedMs(), TimeUnit.MILLISECONDS)) {
                return ToolResult.of(ResultKind.CANCELLED, cancel.reason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.of(ResultKind.CANCELLED, "interrupted");
        }
        return result;
    }

    @Override
    public AutoCloseable subscribe(Consumer<Signal> sink) {
        var active = new boolean[]{true};
        var elapsed = (System.nanoTime() - startNanos) / 1_000_000;
        for (var record : signals) {
            var at = record.get("t").getAsLong() - traceStart;
            if (at < elapsed) {
                continue;
            }
            var signal = record.getAsJsonObject("signal");
            timer.schedule(() -> {
                synchronized (active) {
                    if (active[0]) {
                        sink.accept(new Signal(Json.requireString(signal, "topic"), signal.getAsJsonObject("data"),
                                signal.get("tick").getAsLong()));
                    }
                }
            }, at - elapsed, TimeUnit.MILLISECONDS);
        }
        return () -> {
            synchronized (active) {
                active[0] = false;
            }
        };
    }

    @Override
    public CompletableFuture<Void> loaded() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public long ticks() {
        return (System.nanoTime() - startNanos) / 50_000_000;
    }

    @Override
    public boolean ended() {
        return false;
    }

    /**
     * 停止投递信号
     */
    public void close() {
        timer.shutdownNow();
    }
}
