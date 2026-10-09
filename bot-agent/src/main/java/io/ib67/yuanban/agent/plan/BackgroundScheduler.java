package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.message.Envelope;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人格驱动的 background plan：按 profile 启动，前台 bundle 结束后重新启动；用户停止、安全事件、预算耗尽与断线时全部停止。
 * <p>
 * 空闲窗口由调度器明确授予：没有前台 bundle 时，background plan 的 action_request 在工具属于 profile 允许的能力、
 * 不属于 neverAutonomous、本窗口授予的 lease 数没有达到 maxIdleActions 时得到一个最长 maxLease 的 lease。
 * background plan 的 trace 来源标为 {@code background:<模块>@<版本>}。
 */
public final class BackgroundScheduler implements AutoCloseable {
    public static final String RUN = "run-bg";

    private final PlanManager plans;
    private final PlanServices services;
    private final BudgetScope budget;
    private final Map<ModuleRef, PlanActor> running = new ConcurrentHashMap<>();
    private final AutoCloseable subscription;
    private volatile boolean foregroundActive;
    private volatile boolean enabled = true;
    private int idleActions;

    public BackgroundScheduler(PlanManager plans, BudgetScope budget) {
        this.plans = plans;
        this.services = plans.services();
        this.budget = budget;
        this.subscription = services.events().subscribe(event -> event.type() == MessageType.ACTION_REQUEST, this::onActionRequest);
    }

    /**
     * 检查 profile 中的 background plan 都存在并且可以以 background 角色启动
     *
     * @throws IllegalArgumentException 有模块不存在或不能作为 background 启动
     */
    public void validate() {
        for (var ref : services.profile().backgroundPlans()) {
            var module = plans.load(ref);
            if (!module.manifest().roles().contains(PlanRole.BACKGROUND)) {
                throw new IllegalArgumentException("profile background plan " + ref + " cannot run as background");
            }
            if (!module.manifest().status().startable()) {
                throw new IllegalArgumentException("profile background plan " + ref + " is " + module.manifest().status().wire());
            }
        }
    }

    /**
     * 启动还没有运行的 background plan，从各自最近的检查点继续
     */
    public synchronized void startAll() {
        if (!enabled) {
            return;
        }
        for (var ref : services.profile().backgroundPlans()) {
            var existing = running.get(ref);
            if (existing != null && existing.running()) {
                continue;
            }
            var lineage = "bg-" + ref.name() + "-" + ref.version();
            var restore = services.workspace().latestCheckpoint(lineage);
            var plan = plans.create(plans.load(ref), PlanRole.BACKGROUND, RUN, "background", JsonNull.INSTANCE, budget,
                    null, restore, lineage);
            running.put(ref, plan);
            plan.result().whenComplete((_, _) -> running.remove(ref, plan));
            plan.start();
        }
    }

    public synchronized void stopAll(String reason) {
        for (var plan : running.values()) {
            plan.stop(reason, "scheduler");
        }
        var owner = services.leases().owner();
        if (owner != null && running.values().stream().anyMatch(plan -> plan.id().equals(owner))) {
            services.leases().revokeAll("BACKGROUND_STOPPED");
        }
    }

    public synchronized void foregroundStarted() {
        foregroundActive = true;
    }

    /**
     * 前台结束：开始新的空闲窗口
     *
     * @param restart 按人格重新启动 background plan；用户停止或安全事件之后为 false，等用户恢复或提交新目标
     */
    public synchronized void foregroundEnded(boolean restart) {
        foregroundActive = false;
        idleActions = 0;
        if (restart) {
            startAll();
        }
    }

    public synchronized void enabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            if (!foregroundActive) {
                startAll();
            }
        } else {
            stopAll("background plans disabled");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public List<PlanActor> plans() {
        return List.copyOf(running.values());
    }

    @Override
    public void close() throws Exception {
        subscription.close();
    }

    private void onActionRequest(Envelope request) {
        var plan = running.values().stream().filter(candidate -> request.source().endsWith("#" + candidate.id())).findFirst().orElse(null);
        if (plan == null) {
            return;
        }
        var payload = request.payload();
        var tool = Json.string(payload, "tool");
        String denied = null;
        JsonObject reply;
        synchronized (this) {
            if (tool == null || !services.profile().backgroundCapabilities().allows(tool)) {
                denied = tool + " is not allowed for background plans by the profile";
            } else if (foregroundActive) {
                denied = "a foreground bundle is running";
            } else if (idleActions >= services.profile().maxIdleActions()) {
                denied = "the idle window already used " + idleActions + " leases";
            }
            if (denied == null) {
                var maxLease = services.profile().maxLease();
                var duration = Duration.ofMillis(Math.min(Json.longOr(payload, "durationMs", maxLease.toMillis()), maxLease.toMillis()));
                var purpose = Json.string(payload, "purpose");
                var lease = services.leases().tryAcquireBackground(plan.id(), purpose == null ? tool : purpose, duration);
                if (lease == null) {
                    denied = "the lease is held by " + services.leases().owner();
                } else {
                    idleActions++;
                }
                reply = lease == null ? null : Json.object("granted", true, "leaseId", lease.id(), "expiresAt", lease.expiresAt(),
                        "tool", tool);
            } else {
                reply = null;
            }
        }
        if (reply == null) {
            reply = Json.object("granted", false, "reason", denied);
        }
        services.trace().append(TraceKind.LEASE, Json.object("event", "action_request", "plan", plan.id(), "request", request.id(),
                "reply", reply));
        plan.deliver(services.messages().create(RUN, MessageType.LEASE, Priority.HIGH, "scheduler", null, reply, null, request.id()));
    }

    /**
     * 正在运行的 background plan 中编号为 id 的一个
     */
    public @Nullable PlanActor plan(String id) {
        return running.values().stream().filter(plan -> plan.id().equals(id)).findFirst().orElse(null);
    }
}
