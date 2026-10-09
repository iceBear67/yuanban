package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.bot.Signal;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.message.Envelope;
import io.ib67.yuanban.agent.message.Mailbox;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.workspace.Checkpoint;
import io.ib67.yuanban.agent.workspace.Module;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 一个 PlanBundle：立即执行的 primary 与只读的 supervisor，以及按需启动的 preflight、recovery 子计划，共享预算、权限、消息序号与 trace。
 * <ul>
 *     <li>创建时 primary 取得 capability lease：前台 bundle 以前台优先级取得，嵌套 bundle 由上层计划委托；</li>
 *     <li>supervisor 收到低成本信号（位置、伤害、聊天、世界重建、区块、primary 的工具调用与进度），可以暂停 primary，
 *     请求 preflight 与 recovery，但不能自己改变世界；</li>
 *     <li>primary 正常结束时 supervisor 收到 drain，处理最后一批事件后返回；</li>
 *     <li>取消时按顺序停止 supervisor、primary、子计划，等它们结束之后释放 lease 与订阅；</li>
 *     <li>任何子计划的异常都按策略变为 fault、replan 或 bundle failure，不会静默结束。</li>
 * </ul>
 */
public final class PlanBundle {
    public enum Status {
        STARTING,
        RUNNING,
        PAUSED,
        DRAINING,
        ENDING,
        ENDED
    }

    /**
     * 转为 bot_event 发给 primary 的信号
     */
    private static final Set<String> BOT_EVENTS = Set.of("world_entered", "player_died", "disconnected");

    private final PlanManager manager;
    private final PlanServices services;
    private final String id;
    private final BundleSpec spec;
    private final String run;
    private final @Nullable PlanActor owner;
    private final String originKind;
    private final BudgetScope budget;
    private final CompletableFuture<BundleResult> done = new CompletableFuture<>();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private final List<PlanActor> children = new CopyOnWriteArrayList<>();
    private final List<JsonElement> facts = new CopyOnWriteArrayList<>();
    private volatile Status status = Status.STARTING;
    /**
     * 在安全点替换 primary 时的新模块与检查点；没有在替换时为 null
     */
    private volatile @Nullable Replacement replacement;
    private @Nullable AutoCloseable primaryToolSubscription;
    private @Nullable AutoCloseable primaryProgressSubscription;
    private volatile @Nullable PlanActor primary;
    private @Nullable PlanActor supervisor;
    private BundleResult.@Nullable Status endStatus;
    private @Nullable String endReason;
    private @Nullable JsonElement endData;

    PlanBundle(PlanManager manager, String id, BundleSpec spec, String run, @Nullable PlanActor owner, BudgetScope parentBudget,
               String originKind) {
        this.manager = manager;
        this.services = manager.services();
        this.id = id;
        this.spec = spec;
        this.run = run;
        this.owner = owner;
        this.originKind = originKind;
        this.budget = parentBudget.child(id, spec.budget(), this::budgetExhausted);
    }

    public String id() {
        return id;
    }

    public String run() {
        return run;
    }

    public BundleSpec spec() {
        return spec;
    }

    public Status status() {
        return status;
    }

    public @Nullable PlanActor primary() {
        return primary;
    }

    public @Nullable PlanActor supervisor() {
        return supervisor;
    }

    public List<PlanActor> children() {
        return List.copyOf(children);
    }

    /**
     * bundle 生命周期结束时完成（不表示游戏效果）
     */
    public CompletableFuture<BundleResult> done() {
        return done.copy();
    }

    /**
     * 创建计划、取得 lease、订阅信号并启动 primary 与 supervisor
     *
     * @throws IllegalArgumentException 模块不存在或不能以对应角色启动
     * @throws IllegalStateException    不能取得 lease
     */
    synchronized void start() {
        Checkpoint restore = null;
        if (spec.resumeLineage() != null) {
            restore = services.workspace().latestCheckpoint(spec.resumeLineage());
            if (restore == null) {
                throw new IllegalArgumentException("lineage " + spec.resumeLineage() + " has no checkpoint");
            }
            if (!restore.module().name().equals(spec.primary().name())) {
                throw new IllegalArgumentException("the checkpoint of " + spec.resumeLineage() + " belongs to " + restore.module()
                        + ", not to a version of " + spec.primary().name());
            }
        }
        var primaryPlan = manager.create(manager.load(spec.primary()), PlanRole.PRIMARY, run, originKind, spec.input(), budget,
                this, restore, spec.resumeLineage());
        PlanActor supervisorPlan = null;
        if (spec.supervisor() != null) {
            var supervisorInput = Json.object("bundle", id, "primary", primaryPlan.id(), "primaryModule", spec.primary().toString(),
                    "primaryInput", spec.input(), "rules", Json.array(spec.rules().stream().map(BundleSpec.Rule::toJson).toList()));
            supervisorPlan = manager.create(manager.load(spec.supervisor()), PlanRole.SUPERVISOR, run, originKind, supervisorInput,
                    budget, this, null, null);
        }
        if (owner == null) {
            services.leases().acquireForeground(primaryPlan.id(), "bundle " + id);
        } else {
            services.leases().delegate(owner.id(), primaryPlan.id(), "bundle " + id);
        }
        primary = primaryPlan;
        supervisor = supervisorPlan;
        subscriptions.add(services.gateway().subscribeSignals(this::onSignal));
        subscribePrimary(primaryPlan);
        if (supervisorPlan != null) {
            var supervisorSource = supervisorPlan.source();
            subscriptions.add(services.events().subscribe(event -> event.source().equals(supervisorSource)
                    && event.type() == MessageType.FAULT, this::onSupervisorFault));
        }
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "started", "bundle", id, "run", run,
                "spec", spec.toJson(), "primary", primaryPlan.id(), "supervisor", supervisorPlan == null ? null : supervisorPlan.id(),
                "owner", owner == null ? null : owner.id()));
        status = Status.RUNNING;
        primaryPlan.start();
        if (supervisorPlan != null) {
            supervisorPlan.start();
            supervisorPlan.result().whenComplete((result, _) -> onSupervisorEnded(result));
        }
        primaryPlan.result().whenComplete((result, _) -> onPrimaryEnded(result));
    }

    /**
     * 向 bundle 中的计划发送控制消息
     *
     * @param target {@code primary}、{@code supervisor} 或计划编号
     * @throws IllegalArgumentException 没有这个计划
     */
    public Mailbox.Offer send(String target, MessageType type, JsonObject payload, @Nullable String dedupeKey,
                              @Nullable String replyTo, String source) {
        var plan = resolve(target);
        return plan.deliver(services.messages().create(run, type, Priority.NORMAL, source, null, payload, dedupeKey, replyTo));
    }

    /**
     * 暂停 primary：保留 lease，取消它正在执行的改变世界的调用，之后的调用等到恢复
     */
    public synchronized void pausePrimary(String reason) {
        var plan = requirePrimary();
        if (status == Status.ENDING || status == Status.ENDED) {
            return;
        }
        services.leases().suspend(plan.id(), reason);
        services.gateway().cancelWorldChanging(plan.id(), "PAUSED: " + reason);
        plan.deliver(services.messages().create(run, MessageType.PAUSE, Priority.HIGH, "bundle:" + id, null,
                Json.object("reason", reason), null, null));
        status = Status.PAUSED;
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "paused", "bundle", id, "reason", reason));
    }

    public synchronized void resumePrimary() {
        var plan = requirePrimary();
        if (status != Status.PAUSED) {
            return;
        }
        services.leases().resume(plan.id());
        plan.deliver(services.messages().create(run, MessageType.RESUME, Priority.HIGH, "bundle:" + id, null,
                Json.object(), null, null));
        status = Status.RUNNING;
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "resumed", "bundle", id));
    }

    /**
     * 启动一次性的只读 preflight，它的返回值作为事实记入 bundle
     */
    public CompletableFuture<PlanResult> preflight(ModuleRef module, JsonElement input) {
        var currentSupervisor = supervisor;
        var source = currentSupervisor == null ? "bundle:" + id : currentSupervisor.source();
        services.events().publish(services.messages().create(run, MessageType.NEED_PREFLIGHT, Priority.HIGH, source,
                module.toString(), Json.object("bundle", id, "module", module.toString(), "input", input), null, null));
        var child = manager.create(manager.load(module), PlanRole.PREFLIGHT, run, originKind, input, budget, this, null, null);
        children.add(child);
        child.start();
        return child.result().thenApply(result -> {
            if (result.value() != null) {
                facts.add(result.value());
            }
            return result;
        });
    }

    /**
     * 暂停 primary，把 lease 委托给 recovery 计划；recovery 结束后 lease 回到（仍暂停的）primary，由 supervisor 决定恢复
     */
    public CompletableFuture<PlanResult> recover(ModuleRef module, JsonElement input) {
        var plan = requirePrimary();
        if (status != Status.PAUSED) {
            pausePrimary("recovery by " + module);
        }
        var child = manager.create(manager.load(module), PlanRole.RECOVERY, run, originKind, input, budget, this, null, null);
        services.leases().delegate(plan.id(), child.id(), "recovery " + module);
        children.add(child);
        child.start();
        return child.result();
    }

    /**
     * 在安全点（primary 写下检查点之后）把 primary 换成同一模块的新版本：旧 primary 停止，新版本从检查点继续，沿用幂等账本，
     * lease 交给新 primary，bundle 不结束
     *
     * @throws IllegalStateException bundle 已经在结束，或者已经在替换
     */
    public synchronized void replacePrimary(Module module, Checkpoint checkpoint) {
        var old = requirePrimary();
        if (status == Status.ENDING || status == Status.ENDED || replacement != null) {
            throw new IllegalStateException("bundle " + id + " cannot replace its primary now (" + status + ")");
        }
        replacement = new Replacement(module, checkpoint, old.lineage(), old.input());
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "replacing", "bundle", id, "old", old.id(),
                "module", module.ref().toString(), "checkpoint", checkpoint.id()));
        old.stop("replaced by " + module.ref() + " at checkpoint " + checkpoint.id(), "bundle:" + id);
    }

    public void replan(String reason, @Nullable JsonElement data) {
        services.timer().execute(() -> end(BundleResult.Status.REPLAN, reason, data));
    }

    /**
     * 按顺序取消：supervisor、primary 的 JS、等待中的工具调用、子计划、临时 observer（信号订阅）
     */
    public void cancel(String reason) {
        end(BundleResult.Status.CANCELLED, reason, null);
    }

    public JsonObject describe() {
        var currentPrimary = primary;
        var currentSupervisor = supervisor;
        return Json.object("bundle", id, "run", run, "status", status.name(), "spec", spec.toJson(),
                "primary", currentPrimary == null ? null : currentPrimary.describe(),
                "supervisor", currentSupervisor == null ? null : currentSupervisor.describe(),
                "children", Json.array(children.stream().map(PlanActor::describe).toList()), "facts", Json.array(facts),
                "lease", services.leases().describe(), "budget", budget.usage());
    }

    private void onSignal(Signal signal) {
        var payload = signal.data();
        payload.addProperty("tick", signal.tick());
        var sup = supervisor;
        if (sup != null && sup.running()) {
            sup.deliver(services.messages().create(run, MessageType.SIGNAL, Priority.LOW, "bundle:" + id, signal.topic(),
                    payload, null, null));
        }
        if (BOT_EVENTS.contains(signal.topic())) {
            var plan = primary;
            if (plan != null && plan.running()) {
                plan.deliver(services.messages().create(run, MessageType.BOT_EVENT, Priority.HIGH, "bundle:" + id,
                        signal.topic(), payload, null, null));
            }
            for (var child : children) {
                if (child.running()) {
                    child.deliver(services.messages().create(run, MessageType.BOT_EVENT, Priority.HIGH, "bundle:" + id,
                            signal.topic(), payload, null, null));
                }
            }
        }
        if (signal.topic().equals("disconnected")) {
            services.timer().execute(() -> end(BundleResult.Status.DISCONNECTED, "bot disconnected", payload));
        }
    }

    private void onPrimaryTool(ToolGateway.ToolEvent event) {
        var sup = supervisor;
        if (sup == null || !sup.running()) {
            return;
        }
        var payload = Json.object("callId", event.callId(), "tool", event.tool(), "args", event.args(),
                "result", event.result() == null ? null : event.result().toJson());
        sup.deliver(services.messages().create(run, MessageType.SIGNAL, Priority.NORMAL, "bundle:" + id,
                event.result() == null ? "primary_tool_start" : "primary_tool_end", payload, null, null));
    }

    private void onPrimaryProgress(Envelope event) {
        var sup = supervisor;
        if (sup != null && sup.running()) {
            var payload = event.payload();
            payload.addProperty("messageId", event.id());
            sup.deliver(services.messages().create(run, MessageType.SIGNAL, Priority.NORMAL, "bundle:" + id,
                    "primary_progress", payload, null, null));
        }
    }

    private void onSupervisorFault(Envelope event) {
        var kind = Json.string(event.payload(), "kind");
        var rule = spec.rules().stream().filter(candidate -> kind != null && candidate.on().contains(kind)).findFirst().orElse(null);
        if (rule == null) {
            return;
        }
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "policy", "bundle", id, "fault", event.id(),
                "faultKind", kind, "action", rule.action()));
        services.timer().execute(() -> {
            switch (rule.action()) {
                case "replan" -> end(BundleResult.Status.REPLAN, "supervisor fault " + kind, event.payload());
                case "pause" -> pausePrimary("supervisor fault " + kind);
                case "fail" -> end(BundleResult.Status.FAILED, "supervisor fault " + kind, event.payload());
                case "preflight" -> {
                    var module = rule.preflight() != null ? rule.preflight() : services.profile().preflightModule();
                    if (module == null) {
                        end(BundleResult.Status.FAULT, "no preflight module for fault " + kind, event.payload());
                        return;
                    }
                    pausePrimary("preflight after fault " + kind);
                    preflight(module, Json.object("bundle", id, "fault", event.payload(), "primaryInput", spec.input()))
                            .thenAccept(result -> {
                                var sup = supervisor;
                                if (sup != null && sup.running()) {
                                    sup.deliver(services.messages().create(run, MessageType.SIGNAL, Priority.HIGH, "bundle:" + id,
                                            "preflight_result", result.toJson(), null, null));
                                }
                            });
                }
                default -> {
                    // fault：AgentKernel 已经通过 ScriptEventBus 收到这条事件
                }
            }
        });
    }

    private void onPrimaryEnded(@Nullable PlanResult result) {
        if (result == null) {
            end(BundleResult.Status.FAULT, "primary ended without a result", null);
            return;
        }
        synchronized (this) {
            if (status == Status.ENDING || status == Status.ENDED) {
                return;
            }
            var pending = replacement;
            if (pending != null) {
                replacement = null;
                startReplacement(pending, result);
                return;
            }
        }
        switch (result.status()) {
            case COMPLETED -> {
                var sup = supervisor;
                if (sup == null || !sup.running()) {
                    end(BundleResult.Status.COMPLETED, null, result.value());
                    return;
                }
                status = Status.DRAINING;
                sup.deliver(services.messages().create(run, MessageType.DRAIN, Priority.HIGH, "bundle:" + id, null,
                        Json.object("primary", result.toJson()), null, null));
                services.timer().schedule(() -> sup.stop("drain timeout", "bundle:" + id), spec.drain().toMillis(), TimeUnit.MILLISECONDS);
            }
            case STOPPED -> end(BundleResult.Status.CANCELLED, result.reason(), result.value());
            case FAILED, KILLED -> end(spec.onPrimaryFailure().equals("replan") ? BundleResult.Status.REPLAN : BundleResult.Status.FAULT,
                    "primary " + result.status().name().toLowerCase(Locale.ROOT) + ": " + result.error(), result.toJson());
        }
    }

    private void onSupervisorEnded(@Nullable PlanResult result) {
        if (status == Status.DRAINING) {
            var plan = primary;
            end(BundleResult.Status.COMPLETED, null, plan == null ? null : plan.result().join().value());
            return;
        }
        if (result == null || status == Status.ENDING || status == Status.ENDED) {
            return;
        }
        if (result.status() == PlanResult.Status.FAILED || result.status() == PlanResult.Status.KILLED) {
            if (spec.onSupervisorFailure().equals("fail")) {
                end(BundleResult.Status.FAILED, "supervisor " + result.status().name().toLowerCase(Locale.ROOT)
                        + ": " + result.error(), result.toJson());
            } else {
                services.events().publish(services.messages().create(run, MessageType.FAULT, Priority.HIGH, "bundle:" + id,
                        "supervisor_failed", Json.object("kind", "supervisor_failed", "reason", result.error(),
                                "data", result.toJson()), "supervisor_failed:" + id, null));
            }
        }
    }

    private void startReplacement(Replacement pending, PlanResult oldResult) {
        closeQuietly(primaryToolSubscription);
        closeQuietly(primaryProgressSubscription);
        var next = manager.create(pending.module(), PlanRole.PRIMARY, run, originKind, pending.input(), budget, this,
                pending.checkpoint(), pending.lineage());
        // 旧 primary 结束时已经释放了它的 lease
        if (owner == null) {
            services.leases().acquireForeground(next.id(), "bundle " + id + " (replacement)");
        } else {
            services.leases().delegate(owner.id(), next.id(), "bundle " + id + " (replacement)");
        }
        primary = next;
        subscribePrimary(next);
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "replaced", "bundle", id, "old", oldResult.toJson(),
                "new", next.id(), "module", pending.module().ref().toString()));
        var sup = supervisor;
        if (sup != null && sup.running()) {
            sup.deliver(services.messages().create(run, MessageType.SIGNAL, Priority.HIGH, "bundle:" + id, "primary_replaced",
                    Json.object("old", oldResult.planId(), "new", next.id(), "module", pending.module().ref().toString()), null, null));
        }
        if (status == Status.PAUSED) {
            status = Status.RUNNING;
        }
        next.start();
        next.result().whenComplete((nextResult, _) -> onPrimaryEnded(nextResult));
    }

    private void subscribePrimary(PlanActor plan) {
        primaryToolSubscription = services.gateway().onToolEvent(plan.id(), this::onPrimaryTool);
        var source = plan.source();
        primaryProgressSubscription = services.events().subscribe(event -> event.source().equals(source)
                && event.type() == MessageType.PROGRESS, this::onPrimaryProgress);
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            throw new IllegalStateException("failed to close a subscription", e);
        }
    }

    private record Replacement(Module module, Checkpoint checkpoint, String lineage, JsonElement input) {
    }

    private void budgetExhausted(BudgetScope.Exhaustion exhaustion) {
        services.timer().execute(() -> end(BundleResult.Status.BUDGET_EXHAUSTED, exhaustion.scope() + " " + exhaustion.kind(),
                exhaustion.toJson()));
    }

    private void end(BundleResult.Status result, @Nullable String reason, @Nullable JsonElement data) {
        List<PlanActor> plans = new ArrayList<>();
        synchronized (this) {
            if (status == Status.ENDING || status == Status.ENDED) {
                return;
            }
            status = Status.ENDING;
            endStatus = result;
            endReason = reason;
            endData = data;
        }
        var stopReason = "bundle " + id + " " + result.name().toLowerCase(Locale.ROOT) + (reason == null ? "" : ": " + reason);
        var source = "bundle:" + id;
        var currentSupervisor = supervisor;
        if (currentSupervisor != null) {
            currentSupervisor.stop(stopReason, source);
            plans.add(currentSupervisor);
        }
        var currentPrimary = primary;
        if (currentPrimary != null) {
            currentPrimary.stop(stopReason, source);
            plans.add(currentPrimary);
        }
        for (var child : children) {
            child.stop(stopReason, source);
            plans.add(child);
        }
        CompletableFuture.allOf(plans.stream().map(PlanActor::result).toArray(CompletableFuture[]::new))
                .whenComplete((_, _) -> complete());
    }

    private void complete() {
        subscriptions.forEach(PlanBundle::closeQuietly);
        closeQuietly(primaryToolSubscription);
        closeQuietly(primaryProgressSubscription);
        var currentPrimary = primary;
        var currentSupervisor = supervisor;
        if (currentPrimary != null) {
            services.leases().release(currentPrimary.id(), "bundle " + id + " ended");
        }
        var bundleResult = new BundleResult(id, Objects.requireNonNull(endStatus),
                currentPrimary == null ? null : currentPrimary.result().join(),
                currentSupervisor == null ? null : currentSupervisor.result().join(), facts, endReason, endData);
        status = Status.ENDED;
        services.trace().append(TraceKind.BUNDLE, Json.object("event", "ended", "result", bundleResult.toJson()));
        services.events().publish(services.messages().create(run, MessageType.RESULT, Priority.HIGH, "bundle:" + id,
                bundleResult.status().name().toLowerCase(Locale.ROOT), bundleResult.toJson(), null, null));
        manager.bundleEnded(this);
        done.complete(bundleResult);
    }

    private PlanActor requirePrimary() {
        var plan = primary;
        if (plan == null) {
            throw new IllegalStateException("bundle " + id + " has not started");
        }
        return plan;
    }

    private PlanActor resolve(String target) {
        if (target.equals("primary")) {
            return requirePrimary();
        }
        var currentSupervisor = supervisor;
        if (target.equals("supervisor")) {
            if (currentSupervisor == null) {
                throw new IllegalArgumentException("bundle " + id + " has no supervisor");
            }
            return currentSupervisor;
        }
        var currentPrimary = primary;
        if (currentPrimary != null && currentPrimary.id().equals(target)) {
            return currentPrimary;
        }
        if (currentSupervisor != null && currentSupervisor.id().equals(target)) {
            return currentSupervisor;
        }
        return children.stream().filter(child -> child.id().equals(target)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no plan " + target + " in bundle " + id));
    }
}
