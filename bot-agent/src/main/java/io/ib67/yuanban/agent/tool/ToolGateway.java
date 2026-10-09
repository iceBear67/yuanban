package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.bot.Signal;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.SchemaViolationException;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.notify.NotifyKind;
import io.ib67.yuanban.agent.plan.Caller;
import io.ib67.yuanban.agent.plan.PlanRole;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;
import io.ib67.yuanban.agent.view.Vec3;
import io.ib67.yuanban.agent.workspace.IdempotencyLedger;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 脚本触碰 bot 的唯一入口。一次调用依次经过：工具与参数校验 → 能力与角色 → 风险层策略与限流 → 预算 → dry-run 拦截 →
 * 幂等账本 → capability lease（被暂停时等到恢复）→ external 层调用发出 action 通知 → 改变世界的调用串行执行 → 后端执行 → trace。
 * 每次调用在自己的虚拟线程上执行，结果以 future 返回。
 */
public final class ToolGateway implements AutoCloseable {
    /**
     * 一次调用的开始或结束，交给 PlanBundle（转为 supervisor 的信号）
     *
     * @param result 开始时为 null
     */
    public record ToolEvent(String planId, String callId, String tool, JsonObject args, @Nullable ToolResult result) {
    }

    private record InFlight(String callId, CancelToken token, boolean worldChanging) {
    }

    private final ToolCatalog catalog;
    private final ToolBackend backend;
    private final LeaseManager leases;
    private final NotifyBus notifications;
    private final TraceStore trace;
    private final InstantSource clock;
    private final RateLimiter limiter;
    private final Semaphore worldLock = new Semaphore(1, true);
    private final Map<String, Set<InFlight>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, String> closedPlans = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ordinals = new ConcurrentHashMap<>();
    private final AtomicLong actionIds = new AtomicLong();
    private final Map<String, CopyOnWriteArrayList<Consumer<ToolEvent>>> toolListeners = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Signal>> signalSinks = new CopyOnWriteArrayList<>();
    private final AutoCloseable backendSubscription;
    private volatile ToolPolicy policy;

    public ToolGateway(ToolCatalog catalog, ToolBackend backend, LeaseManager leases, NotifyBus notifications,
                       TraceStore trace, InstantSource clock, ToolPolicy policy) {
        this.catalog = catalog;
        this.backend = backend;
        this.leases = leases;
        this.notifications = notifications;
        this.trace = trace;
        this.clock = clock;
        this.policy = policy;
        this.limiter = new RateLimiter(clock);
        this.backendSubscription = backend.subscribe(this::signal);
    }

    public ToolCatalog catalog() {
        return catalog;
    }

    public ToolBackend backend() {
        return backend;
    }

    public ToolPolicy policy() {
        return policy;
    }

    public void policy(ToolPolicy policy) {
        this.policy = policy;
        trace.append(TraceKind.STATE, Json.object("event", "policy", "dryRun", policy.dryRun(), "unsafe", policy.unsafe()));
    }

    /**
     * 调用一个工具。网关内部的程序错误以异常完成 future（在出错的位置结束，不转换成结果）
     *
     * @param observer {@code withObserver} 给出的 observer 描述；没有时为 null
     */
    public CompletableFuture<ToolResult> invoke(Caller caller, String callId, String tool, JsonObject args,
                                                @Nullable JsonObject observer) {
        var ordinal = ordinals.computeIfAbsent(caller.planKey(), _ -> new AtomicLong()).incrementAndGet();
        trace.append(TraceKind.TOOL_START, Json.object("callId", callId, "plan", caller.planId(), "planKey", caller.planKey(),
                "origin", caller.origin(), "run", caller.run(), "tool", tool, "args", args, "observer", observer,
                "ordinal", ordinal));
        notifyListeners(caller.planId(), new ToolEvent(caller.planId(), callId, tool, args, null));
        var future = new CompletableFuture<ToolResult>();
        Thread.ofVirtual().name("agent-tool-" + callId).start(() -> {
            var started = System.nanoTime();
            ToolResult result;
            String actionId = null;
            try {
                var spec = catalog.get(tool);
                if (spec != null && spec.worldChanging()) {
                    actionId = "a-" + actionIds.incrementAndGet();
                }
                result = run(caller, callId, spec, tool, args, observer, ordinal);
            } catch (Throwable t) {
                var stack = trace.stack(t);
                trace.append(TraceKind.TOOL_END, Json.object("callId", callId, "plan", caller.planId(), "planKey", caller.planKey(),
                        "origin", caller.origin(), "tool", tool, "ordinal", ordinal, "error", t.toString(), "stackRef", stack));
                future.completeExceptionally(t);
                return;
            }
            var elapsed = (System.nanoTime() - started) / 1_000_000;
            result = result.withTiming(result.actionId() != null ? result.actionId() : actionId, elapsed);
            if (observer != null && result.value() != null) {
                trace.append(TraceKind.OBSERVATION, Json.object("callId", callId, "plan", caller.planId(),
                        "observer", observer, "conclusion", result.value()));
            }
            trace.append(TraceKind.TOOL_END, Json.object("callId", callId, "plan", caller.planId(), "planKey", caller.planKey(),
                    "origin", caller.origin(), "tool", tool, "ordinal", ordinal, "result", result.toJson()));
            notifyListeners(caller.planId(), new ToolEvent(caller.planId(), callId, tool, args, result));
            future.complete(result);
        });
        return future;
    }

    /**
     * 取消计划正在执行的全部调用，之后的调用直接以 CANCELLED 结束
     */
    public void closePlan(String planId, String reason) {
        closedPlans.put(planId, reason);
        cancel(planId, reason, false);
    }

    /**
     * 计划的脚本结束之后清理它的记录（结束的脚本不会再调用工具，正在结束的调用已经被 {@link #closePlan} 取消）
     */
    public void forget(String planId, String planKey) {
        closedPlans.remove(planId);
        ordinals.remove(planKey);
        toolListeners.remove(planId);
        inFlight.computeIfPresent(planId, (_, flights) -> flights.isEmpty() ? null : flights);
    }

    /**
     * 取消计划正在执行的全部调用
     */
    public void cancelAll(String planId, String reason) {
        cancel(planId, reason, false);
    }

    /**
     * 取消计划正在执行的改变世界的调用（暂停 primary、收回 lease）
     */
    public void cancelWorldChanging(String planId, String reason) {
        cancel(planId, reason, true);
    }

    /**
     * 订阅一个计划的调用开始与结束
     */
    public AutoCloseable onToolEvent(String planId, Consumer<ToolEvent> listener) {
        var listeners = toolListeners.computeIfAbsent(planId, _ -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * 订阅 bot 的低成本信号。sink 可能在 bot 调度线程上被调用，不能阻塞
     */
    public AutoCloseable subscribeSignals(Consumer<Signal> sink) {
        signalSinks.add(sink);
        return () -> signalSinks.remove(sink);
    }

    @Override
    public void close() throws Exception {
        backendSubscription.close();
    }

    private ToolResult run(Caller caller, String callId, @Nullable ToolSpec spec, String tool, JsonObject args,
                           @Nullable JsonObject observerJson, long ordinal) {
        var closed = closedPlans.get(caller.planId());
        if (closed != null) {
            return ToolResult.of(ResultKind.CANCELLED, closed);
        }
        if (spec == null) {
            return ToolResult.of(ResultKind.INVALID_ARGUMENT, "unknown tool " + tool);
        }
        var errors = spec.schema().validate(args);
        if (!errors.isEmpty()) {
            return ToolResult.of(ResultKind.INVALID_ARGUMENT, String.join("; ", errors));
        }
        ObserverSpec observer = null;
        if (observerJson != null) {
            if (!spec.acceptsObserver()) {
                return ToolResult.of(ResultKind.INVALID_ARGUMENT, tool + " does not accept an observer");
            }
            try {
                observer = ObserverSpec.parse(observerJson);
            } catch (SchemaViolationException e) {
                return ToolResult.of(ResultKind.INVALID_ARGUMENT, e.getMessage());
            }
        }
        var policy = this.policy;
        var denied = checkPermission(caller, spec, args, policy);
        if (denied != null) {
            return denied;
        }
        var timeoutMs = Json.longOr(args, "timeoutMs", spec.defaultTimeout().toMillis());
        if (timeoutMs > spec.maxTimeout().toMillis()) {
            return ToolResult.of(ResultKind.INVALID_ARGUMENT, "timeoutMs " + timeoutMs + " exceeds the limit "
                    + spec.maxTimeout().toMillis() + " of " + tool);
        }
        var exhausted = caller.budget().check();
        if (exhausted == null && spec.worldChanging()) {
            exhausted = caller.budget().charge(BudgetScope.Kind.ACTIONS, 1);
        }
        if (exhausted != null) {
            return ToolResult.of(ResultKind.BUDGET_EXHAUSTED, exhausted.scope() + " " + exhausted.kind(), exhausted.toJson());
        }
        if (spec.sideEffect() && policy.dryRun()) {
            return ToolResult.of(ResultKind.WOULD_EXECUTE, "dry-run", Json.object("tool", tool, "args", args, "observer", observerJson));
        }
        var key = spec.sideEffect() ? Json.string(args, "idempotencyKey") : null;
        if (key != null) {
            var entry = caller.ledger().get(key);
            if (entry != null && entry.state() == IdempotencyLedger.State.DONE) {
                var previous = entry.result();
                return ToolResult.of(ResultKind.DUPLICATE_SUPPRESSED, key, previous);
            }
            if (entry != null) {
                return ToolResult.of(ResultKind.UNCERTAIN_SIDE_EFFECT, key);
            }
        }
        var limited = rateLimit(spec, args, policy);
        if (limited != null) {
            return limited;
        }
        var token = new CancelToken();
        var flight = new InFlight(callId, token, spec.worldChanging());
        inFlight.computeIfAbsent(caller.planId(), _ -> ConcurrentHashMap.newKeySet()).add(flight);
        try {
            if (closedPlans.containsKey(caller.planId())) {
                return ToolResult.of(ResultKind.CANCELLED, closedPlans.get(caller.planId()));
            }
            if (spec.worldChanging()) {
                var lease = awaitLease(caller, token);
                if (lease != null) {
                    return lease;
                }
            }
            if (spec.layer() == ToolLayer.EXTERNAL) {
                notifications.publish(NotifyKind.ACTION, caller.origin(), tool + " " + Json.write(args),
                        Json.object("tool", tool, "args", args, "plan", caller.planId()));
            }
            if (spec.worldChanging()) {
                if (!acquireWorldLock(token)) {
                    return ToolResult.of(ResultKind.CANCELLED, token.reason());
                }
                try {
                    // 等锁期间可能失去或暂停了 lease
                    if (leases.hold(caller.planId()) != LeaseManager.Hold.HELD) {
                        return ToolResult.of(ResultKind.CANCELLED, "LEASE_CHANGED");
                    }
                    return execute(caller, callId, spec, tool, args, observer, ordinal, token, key, policy);
                } finally {
                    worldLock.release();
                }
            }
            return execute(caller, callId, spec, tool, args, observer, ordinal, token, key, policy);
        } finally {
            var flights = inFlight.get(caller.planId());
            if (flights != null) {
                flights.remove(flight);
            }
        }
    }

    private ToolResult execute(Caller caller, String callId, ToolSpec spec, String tool, JsonObject args,
                               @Nullable ObserverSpec observer, long ordinal, CancelToken token, @Nullable String key,
                               ToolPolicy policy) {
        if (key != null) {
            caller.ledger().pending(key, tool, clock.millis());
        }
        var timeout = Duration.ofMillis(Json.longOr(args, "timeoutMs", spec.defaultTimeout().toMillis()));
        var invocation = new ToolInvocation(callId, caller.planId(), caller.planKey(), tool, args, observer, timeout, ordinal);
        var result = backend.execute(spec, invocation, token, policy);
        if (key != null) {
            caller.ledger().done(key, tool, result.toJson(), clock.millis());
        }
        return result;
    }

    private @Nullable ToolResult checkPermission(Caller caller, ToolSpec spec, JsonObject args, ToolPolicy policy) {
        var tool = spec.name();
        if (!caller.capabilities().allows(tool)) {
            return ToolResult.of(ResultKind.PERMISSION_DENIED, "capability " + tool + " is not granted to "
                    + caller.origin() + " as " + caller.role().wire());
        }
        if (spec.worldChanging() && !caller.role().mayWrite()) {
            return ToolResult.of(ResultKind.PERMISSION_DENIED, caller.role().wire() + " plans are read-only");
        }
        if (caller.role() == PlanRole.BACKGROUND && policy.neverAutonomous().allows(tool)) {
            return ToolResult.of(ResultKind.PERMISSION_DENIED, tool + " is never autonomous");
        }
        if (spec.layer() == ToolLayer.UNSAFE && !policy.unsafe()) {
            return ToolResult.of(ResultKind.PERMISSION_DENIED, "unsafe tools are disabled by the profile");
        }
        if (spec.layer() == ToolLayer.EXTERNAL && policy.risk(tool) == ToolPolicy.Risk.DENY) {
            return ToolResult.of(ResultKind.PERMISSION_DENIED, tool + " is denied by the policy");
        }
        if (tool.equals("external.command") && !policy.commandAllowed(Json.requireString(args, "command"))) {
            return ToolResult.of(ResultKind.PERMISSION_DENIED, "command is not in the allowed list " + policy.allowedCommands());
        }
        return null;
    }

    private @Nullable ToolResult rateLimit(ToolSpec spec, JsonObject args, ToolPolicy policy) {
        if (spec.name().equals("external.chat") && !limiter.tryAcquire("chat", policy.chatPerMinute(), 60_000)) {
            return ToolResult.of(ResultKind.RATE_LIMITED, "more than " + policy.chatPerMinute() + " chat messages per minute");
        }
        if (spec.layer() != ToolLayer.INTERACTION) {
            return null;
        }
        if (args.has("x") && args.has("y") && args.has("z")) {
            var pos = new Vec3(args.get("x").getAsDouble(), args.get("y").getAsDouble(), args.get("z").getAsDouble()).block();
            if (!limiter.tryAcquire("block:" + pos.x() + "," + pos.y() + "," + pos.z(), policy.interactionsPerBlockPerMinute(), 60_000)) {
                return ToolResult.of(ResultKind.RATE_LIMITED, "more than " + policy.interactionsPerBlockPerMinute()
                        + " interactions per minute at " + pos);
            }
        }
        if (!limiter.tryAcquire("interaction", policy.interactionsPerSecond(), 1_000)) {
            return ToolResult.of(ResultKind.RATE_LIMITED, "more than " + policy.interactionsPerSecond() + " interactions per second");
        }
        return null;
    }

    /**
     * 计划持有 lease 时直接执行；被暂停、或者把 lease 委托给了子计划（仍在持有链上）时等到 lease 回到它手里并恢复；
     * 不在持有链上时为 LEASE_REQUIRED
     *
     * @return 可以执行时为 null；否则为结果
     */
    private @Nullable ToolResult awaitLease(Caller caller, CancelToken token) {
        var hold = leases.hold(caller.planId());
        if (hold == LeaseManager.Hold.HELD) {
            return null;
        }
        if (hold == LeaseManager.Hold.NOT_HELD && !leases.inChain(caller.planId())) {
            return ToolResult.of(ResultKind.LEASE_REQUIRED, caller.planId() + " does not hold the capability lease (owner: "
                    + leases.owner() + ")");
        }
        try {
            var held = leases.awaitHeld(caller.planId());
            await(held, token);
            // 调用被取消或线程被中断时 future 没有正常完成，计划没有取得 lease
            if (!held.isDone() || held.isCancelled()) {
                return ToolResult.of(ResultKind.CANCELLED, token.cancelled() ? token.reason() : "interrupted");
            }
            return null;
        } catch (LeaseManager.LeaseLostException e) {
            return ToolResult.of(ResultKind.CANCELLED, "LEASE_LOST");
        }
    }

    /**
     * 等到 future 完成或调用被取消
     *
     * @return future 的结果；被取消时为 null
     */
    private static <T> @Nullable T await(CompletableFuture<T> future, CancelToken token) {
        token.onCancel(() -> future.cancel(false));
        try {
            return future.get();
        } catch (CancellationException e) {
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private boolean acquireWorldLock(CancelToken token) {
        try {
            while (!token.cancelled()) {
                if (worldLock.tryAcquire(50, TimeUnit.MILLISECONDS)) {
                    if (token.cancelled()) {
                        worldLock.release();
                        return false;
                    }
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void cancel(String planId, String reason, boolean worldChangingOnly) {
        var flights = inFlight.get(planId);
        if (flights == null) {
            return;
        }
        for (var flight : flights) {
            if (!worldChangingOnly || flight.worldChanging()) {
                flight.token().cancel(reason);
            }
        }
    }

    private void notifyListeners(String planId, ToolEvent event) {
        var listeners = toolListeners.get(planId);
        if (listeners != null) {
            listeners.forEach(listener -> listener.accept(event));
        }
    }

    private void signal(Signal signal) {
        trace.append(TraceKind.OBSERVATION, Json.object("signal", Json.object("topic", signal.topic(),
                "data", signal.data(), "tick", signal.tick())));
        for (var sink : signalSinks) {
            sink.accept(signal);
        }
    }
}
