package io.ib67.yuanban.agent.script;

import io.ib67.yuanban.agent.json.Json;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 一个自治脚本的执行：独占一个平台线程与一个受限 Context，按事件循环执行任务（启动、结算异步调用）。
 * Context 只在这个线程上使用；其它线程通过 {@link #track} 的 future 完成时把结算交给这个线程。
 * <p>
 * 一轮执行超过 {@link ScriptLimits#maxTurn()} 或语句数超过上限时视为忙循环，强制关闭 Context。
 */
public final class ScriptRunner {
    /**
     * 脚本的结束情况
     *
     * @param resultJson 正常返回时的返回值 JSON；其余为 null
     * @param error      抛出异常或被强制结束时的说明；正常返回时为 null
     */
    public record Outcome(Status status, @Nullable String resultJson, @Nullable String error) {
        public enum Status {
            RETURNED,
            THREW,
            KILLED
        }
    }

    private final String name;
    private final ScriptEngine engine;
    private final ScriptLimits limits;
    private final ScheduledExecutorService timer;
    private final Consumer<String> output;
    private final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final AtomicLong callIds = new AtomicLong();
    private final CompletableFuture<Outcome> outcome = new CompletableFuture<>();
    /**
     * 入口函数返回或抛出异常时记下的结果；事件循环在这一轮结束后退出，关闭 Context 之后才完成 {@link #outcome}
     */
    private volatile @Nullable Outcome ended;
    private volatile long turnStarted;
    private volatile @Nullable String killReason;
    private volatile @Nullable Context context;
    private @Nullable Thread thread;
    private @Nullable Value runtime;
    private @Nullable ScheduledFuture<?> watchdog;

    /**
     * @param output 脚本输出的每一行（已截断）
     */
    public ScriptRunner(String name, ScriptEngine engine, ScriptLimits limits, ScheduledExecutorService timer,
                        Consumer<String> output) {
        this.name = name;
        this.engine = engine;
        this.limits = limits;
        this.timer = timer;
        this.output = output;
    }

    /**
     * 启动脚本线程：创建 Context，执行前导脚本，求值模块，调用入口函数
     *
     * @param moduleName 源码的名称（出现在堆栈中）
     */
    public CompletableFuture<Outcome> start(ScriptApi api, String source, String moduleName, String entry,
                                            String inputJson, String selfJson, String stateJson) {
        if (thread != null) {
            throw new IllegalStateException("script " + name + " already started");
        }
        thread = Thread.ofPlatform().daemon(true).name("agent-script-" + name).stackSize(limits.threadStackBytes())
                .start(() -> loop(api, source, moduleName, entry, inputJson, selfJson, stateJson));
        watchdog = timer.scheduleAtFixedRate(this::checkTurn, 100, 100, TimeUnit.MILLISECONDS);
        return outcome;
    }

    /**
     * 跟踪一个异步调用：完成时在脚本线程上结算对应的 Promise
     *
     * @return 调用编号，交给脚本
     */
    public String track(CompletableFuture<String> future) {
        var id = "c-" + callIds.incrementAndGet();
        if (pending.size() >= limits.maxPendingCalls()) {
            post(() -> settle(id, Json.write(Json.object("error", "LIMIT_EXCEEDED: more than " + limits.maxPendingCalls()
                    + " pending calls")), false));
            return id;
        }
        pending.put(id, future);
        future.whenComplete((json, error) -> {
            pending.remove(id);
            if (error != null) {
                post(() -> settle(id, Json.write(Json.object("error", error.toString())), false));
            } else {
                var size = Json.byteLength(json);
                if (size > limits.maxResultBytes()) {
                    post(() -> settle(id, Json.write(Json.object("error", "RESULT_TOO_LARGE: " + size + " bytes")), false));
                } else {
                    post(() -> settle(id, json, true));
                }
            }
        });
        return id;
    }

    /**
     * 强制结束：关闭 Context（正在执行的 JS 被取消），结束状态为 KILLED
     */
    public void kill(String reason) {
        if (killReason != null || ended != null || outcome.isDone()) {
            return;
        }
        killReason = reason;
        var current = context;
        if (current != null) {
            current.close(true);
        }
        tasks.add(() -> {
        });
    }

    /**
     * 等待中的异步调用对应的 future；调用已经结算时为 null
     */
    public @Nullable CompletableFuture<String> pending(String callId) {
        return pending.get(callId);
    }

    public int pendingCount() {
        return pending.size();
    }

    public CompletableFuture<Outcome> outcome() {
        return outcome;
    }

    public boolean onScriptThread() {
        return Thread.currentThread() == thread;
    }

    /**
     * 由前导脚本在入口函数返回时调用
     */
    void finish(String resultJson) {
        end(new Outcome(Outcome.Status.RETURNED, resultJson, null));
    }

    /**
     * 由前导脚本在入口函数抛出异常时调用
     */
    void fail(String error) {
        end(new Outcome(Outcome.Status.THREW, null, error));
    }

    private void end(Outcome result) {
        if (ended == null) {
            ended = result;
        }
    }

    private void post(Runnable task) {
        tasks.add(task);
    }

    private void loop(ScriptApi api, String source, String moduleName, String entry, String inputJson, String selfJson,
                      String stateJson) {
        Outcome result;
        try (var out = new LineOutput(output, limits.maxLogLineBytes())) {
            result = run(api, source, moduleName, entry, inputJson, selfJson, stateJson, out);
        } catch (RuntimeException e) {
            result = new Outcome(Outcome.Status.THREW, null, e.toString());
        }
        if (watchdog != null) {
            watchdog.cancel(false);
        }
        pending.values().forEach(future -> future.cancel(false));
        outcome.complete(result);
    }

    private Outcome run(ScriptApi api, String source, String moduleName, String entry, String inputJson, String selfJson,
                        String stateJson, OutputStream out) {
        var ctx = engine.newContext(out, limits);
        context = ctx;
        try {
            if (killReason != null) {
                return new Outcome(Outcome.Status.KILLED, null, killReason);
            }
            turn(() -> {
                ctx.getBindings("js").putMember("__host", new ScriptHost(api, this));
                var prelude = ctx.eval(Source.newBuilder("js", engine.prelude(), "prelude.js").buildLiteral());
                runtime = prelude;
                var module = ctx.eval(Source.newBuilder("js", source, moduleName + ".mjs")
                        .mimeType("application/javascript+module").buildLiteral());
                Objects.requireNonNull(prelude.getMember("boot"), "prelude.js did not define boot")
                        .execute(module, entry, inputJson, selfJson, stateJson);
            });
            while (ended == null && killReason == null) {
                var task = tasks.take();
                if (killReason != null) {
                    break;
                }
                turn(task);
            }
        } catch (PolyglotException e) {
            if (!e.isCancelled() && !e.isResourceExhausted()) {
                return new Outcome(Outcome.Status.THREW, null, describe(e));
            }
            if (killReason == null) {
                killReason = "busy loop: more than " + limits.maxStatementsPerTurn() + " statements in one turn";
            }
        } catch (InterruptedException e) {
            killReason = "interrupted";
        } finally {
            context = null;
            ctx.close(true);
        }
        var result = ended;
        if (killReason != null || result == null) {
            return new Outcome(Outcome.Status.KILLED, null, killReason);
        }
        return result;
    }

    private void turn(Runnable task) {
        var ctx = context;
        if (ctx != null) {
            ctx.resetLimits();
        }
        turnStarted = System.nanoTime();
        try {
            task.run();
        } finally {
            turnStarted = 0;
        }
    }

    private void settle(String callId, String json, boolean ok) {
        var current = runtime;
        if (current == null || ended != null) {
            return;
        }
        current.getMember("settle").execute(callId, json, ok);
    }

    private void checkTurn() {
        var started = turnStarted;
        if (started != 0 && System.nanoTime() - started > limits.maxTurn().toNanos()) {
            kill("busy loop: one turn ran longer than " + limits.maxTurn().toMillis() + " ms");
        }
    }

    private static String describe(PolyglotException e) {
        var location = e.getSourceLocation();
        return e.getMessage() + (location == null ? "" : " at " + location);
    }

    /**
     * 按行收集脚本输出，每行截断到上限
     */
    private static final class LineOutput extends OutputStream {
        private final Consumer<String> sink;
        private final int maxBytes;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private LineOutput(Consumer<String> sink, int maxBytes) {
            this.sink = sink;
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                flushLine();
            } else if (line.size() < maxBytes) {
                line.write(b);
            }
        }

        @Override
        public synchronized void close() {
            if (line.size() > 0) {
                flushLine();
            }
        }

        private void flushLine() {
            sink.accept(line.toString(StandardCharsets.UTF_8));
            line.reset();
        }
    }
}
