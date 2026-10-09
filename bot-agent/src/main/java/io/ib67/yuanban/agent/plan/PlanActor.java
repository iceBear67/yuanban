package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.json.SchemaViolationException;
import io.ib67.yuanban.agent.message.Envelope;
import io.ib67.yuanban.agent.message.Mailbox;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.notify.NotifyKind;
import io.ib67.yuanban.agent.script.ScriptApi;
import io.ib67.yuanban.agent.script.ScriptRunner;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.Capabilities;
import io.ib67.yuanban.agent.tool.ResultKind;
import io.ib67.yuanban.agent.tool.ToolResult;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.workspace.Checkpoint;
import io.ib67.yuanban.agent.workspace.IdempotencyLedger;
import io.ib67.yuanban.agent.workspace.Module;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import io.ib67.yuanban.agent.workspace.Workspace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 一个自治计划：一个模块版本以一个角色运行的 GraalJS actor。实现脚本的全部主机方法（{@link ScriptApi}），
 * 并以 {@link Caller} 的身份调用 ToolGateway。运行中固定模块版本与源码 hash。
 */
public final class PlanActor implements Caller, ScriptApi {
    private static final ExecutorService BLOCKING = Executors.newVirtualThreadPerTaskExecutor();

    private final PlanManager manager;
    private final PlanServices services;
    private final String id;
    private final Module module;
    private final PlanRole role;
    private final String run;
    private final String origin;
    private final String planKey;
    private final String lineage;
    private final JsonElement input;
    private final @Nullable PlanBundle bundle;
    private final @Nullable Checkpoint restore;
    private final BudgetScope budget;
    private final IdempotencyLedger ledger;
    private final Capabilities capabilities;
    private final Mailbox mailbox;
    private final ScriptRunner runner;
    private final CompletableFuture<PlanResult> result = new CompletableFuture<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<String>> sleeps = ConcurrentHashMap.newKeySet();
    private final AtomicLong calls = new AtomicLong();
    private volatile @Nullable String stopReason;
    private volatile @Nullable Checkpoint lastCheckpoint;

    /**
     * @param originKind {@code foreground} 或 {@code background}
     * @throws IllegalArgumentException 模块不能以这个角色启动，或者输入不符合模块的 inputSchema
     */
    PlanActor(PlanManager manager, String id, Module module, PlanRole role, String run, String originKind, String planKey,
              String lineage, JsonElement input, @Nullable PlanBundle bundle, @Nullable Checkpoint restore,
              BudgetScope parentBudget) {
        this.manager = manager;
        this.services = manager.services();
        var manifest = module.manifest();
        if (!manifest.status().startable() && !services.allowDraft()) {
            throw new IllegalArgumentException(module.ref() + " is " + manifest.status().wire() + "; only tested modules can start");
        }
        if (!manifest.roles().contains(role)) {
            throw new IllegalArgumentException(module.ref() + " cannot run as " + role.wire() + ", its roles are " + manifest.roles());
        }
        if (manifest.inputSchema() != null) {
            var errors = JsonSchema.of(manifest.inputSchema()).validate(input);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("input of " + module.ref() + " does not match its schema: " + String.join("; ", errors));
            }
        }
        this.id = id;
        this.module = module;
        this.role = role;
        this.run = run;
        this.origin = originKind + ":" + module.ref();
        this.planKey = planKey;
        this.lineage = lineage;
        this.input = input.deepCopy();
        this.bundle = bundle;
        this.restore = restore;
        this.budget = parentBudget.child(id, BudgetLimits.UNLIMITED, this::budgetExhausted);
        this.ledger = services.workspace().ledger(lineage);
        this.capabilities = capabilities(manifest.capabilities(), role);
        var limits = services.limits();
        this.mailbox = new Mailbox(limits.mailboxCapacity(), limits.signalCapacity(), this::delivered, this::dropped,
                message -> services.timer().execute(() -> overflow(message)));
        this.runner = new ScriptRunner(id, services.engine(), limits, services.timer(),
                line -> services.trace().append(TraceKind.SCRIPT_OUTPUT, Json.object("plan", id, "line", line)));
    }

    /**
     * 启动脚本线程
     */
    public void start() {
        var manifest = module.manifest();
        var record = Json.object("plan", id, "module", module.ref().toString(), "sourceHash", manifest.sourceHash(),
                "role", role.wire(), "origin", origin, "run", run, "planKey", planKey, "lineage", lineage,
                "bundle", bundle == null ? null : bundle.id(), "input", input, "capabilities", capabilities.toString(),
                "restoredFrom", restore == null ? null : restore.id());
        if (services.profile().saveScriptSource()) {
            record.addProperty("source", module.source());
        }
        services.trace().append(TraceKind.SCRIPT_START, record);
        manager.started(this);
        var self = Json.object("plan", id, "module", module.ref().name(), "version", module.ref().version(),
                "role", role.wire(), "origin", origin, "run", run, "bundle", bundle == null ? null : bundle.id(),
                "lineage", lineage, "planKey", planKey);
        var state = restore == null ? JsonNull.INSTANCE : restore.data();
        runner.start(this, module.source(), module.ref().toString(), manifest.entry(), Json.write(input), Json.write(self),
                Json.write(state)).whenComplete(this::finish);
        var maxWallTime = services.limits().maxWallTime();
        if (!maxWallTime.isZero()) {
            services.timer().schedule(() -> stop("maximum wall time " + maxWallTime + " reached", "runtime"),
                    maxWallTime.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public String id() {
        return id;
    }

    public ModuleRef module() {
        return module.ref();
    }

    public Module moduleSource() {
        return module;
    }

    public @Nullable PlanBundle bundle() {
        return bundle;
    }

    public CompletableFuture<PlanResult> result() {
        return result.copy();
    }

    public boolean running() {
        return !result.isDone();
    }

    public @Nullable Checkpoint lastCheckpoint() {
        return lastCheckpoint;
    }

    public JsonElement input() {
        return input.deepCopy();
    }

    public long mailboxCursor() {
        return mailbox.cursor();
    }

    public JsonObject describe() {
        var checkpoint = lastCheckpoint;
        return Json.object("plan", id, "module", module.ref().toString(), "sourceHash", module.manifest().sourceHash(),
                "role", role.wire(), "origin", origin, "run", run, "status", running() ? (stopReason == null ? "running" : "stopping")
                        : result.join().status().name().toLowerCase(Locale.ROOT),
                "lineage", lineage, "bundle", bundle == null ? null : bundle.id(), "mailboxCursor", mailbox.cursor(),
                "mailboxSize", mailbox.size(),
                "inFlight", List.copyOf(inFlight), "checkpoint", checkpoint == null ? null : checkpoint.summary(),
                "fallback", module.manifest().fallback().wire(), "suspended", services.leases().suspended(id));
    }

    /**
     * 把一条控制消息放进收件箱
     */
    public Mailbox.Offer deliver(Envelope message) {
        if (message.type() != MessageType.SIGNAL) {
            services.trace().append(TraceKind.MESSAGE, Json.object("envelope", message.toJson(), "to", id));
        }
        return mailbox.offer(message);
    }

    /**
     * 要求停止：取消正在执行的工具调用、拒绝之后的调用、投递 stop 消息；宽限期之后仍未返回时强制结束
     */
    public void stop(String reason, String source) {
        if (stopReason != null || result.isDone()) {
            return;
        }
        stopReason = reason;
        services.gateway().closePlan(id, "STOPPED: " + reason);
        // 停止时提前结束 api.sleep，让脚本尽快检查 api.cancelled()
        sleeps.forEach(sleep -> sleep.complete("null"));
        deliver(services.messages().create(run, MessageType.STOP, Priority.URGENT, source, null,
                Json.object("reason", reason), null, null));
        services.timer().schedule(() -> {
            if (!result.isDone()) {
                runner.kill("did not stop within " + services.limits().stopGrace().toMillis() + " ms after stop: " + reason);
            }
        }, services.limits().stopGrace().toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 立即强制结束
     */
    public void kill(String reason) {
        if (stopReason == null) {
            stopReason = reason;
        }
        services.gateway().closePlan(id, "KILLED: " + reason);
        runner.kill(reason);
    }

    // Caller

    @Override
    public String planId() {
        return id;
    }

    @Override
    public String planKey() {
        return planKey;
    }

    @Override
    public String lineage() {
        return lineage;
    }

    @Override
    public String origin() {
        return origin;
    }

    @Override
    public String run() {
        return run;
    }

    @Override
    public PlanRole role() {
        return role;
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    @Override
    public BudgetScope budget() {
        return budget;
    }

    @Override
    public IdempotencyLedger ledger() {
        return ledger;
    }

    // ScriptApi

    @Override
    public CompletableFuture<String> invoke(String tool, String argsJson, @Nullable String observerJson) {
        var count = calls.incrementAndGet();
        if (count > services.limits().maxToolCalls()) {
            return CompletableFuture.completedFuture(Json.write(ToolResult.of(ResultKind.LIMIT_EXCEEDED,
                    "more than " + services.limits().maxToolCalls() + " tool calls").toJson()));
        }
        JsonObject args;
        JsonObject observer;
        try {
            args = Json.parseObject(argsJson);
            observer = observerJson == null ? null : Json.parseObject(observerJson);
        } catch (JsonParseException e) {
            return CompletableFuture.completedFuture(Json.write(ToolResult.of(ResultKind.INVALID_ARGUMENT, e.getMessage()).toJson()));
        }
        var callId = id + "-t" + count;
        inFlight.add(callId);
        return services.gateway().invoke(this, callId, tool, args, observer)
                .whenComplete((_, _) -> inFlight.remove(callId))
                .thenApply(toolResult -> Json.write(toolResult.toJson()));
    }

    @Override
    public CompletableFuture<String> inboxNext(String filterJson) {
        Mailbox.Filter filter;
        long timeout;
        try {
            var spec = Json.parseObject(filterJson);
            filter = filter(spec);
            timeout = Json.longOr(spec, "timeoutMs", 0);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        var future = mailbox.next(filter);
        if (timeout > 0) {
            services.timer().schedule(() -> future.complete(null), timeout, TimeUnit.MILLISECONDS);
        }
        return future.thenApply(message -> message == null ? "null" : Json.write(message.toJson()));
    }

    @Override
    public String inboxPoll(String filterJson) {
        var message = mailbox.poll(filter(Json.parseObject(filterJson)));
        return message == null ? "null" : Json.write(message.toJson());
    }

    @Override
    public String emit(String type, String specJson) {
        try {
            var messageType = MessageType.fromWire(type);
            if (messageType.direction() != MessageType.Direction.EVENT || messageType == MessageType.CHECKPOINT) {
                return error(type + " cannot be emitted by a script" + (messageType == MessageType.CHECKPOINT ? "; use api.checkpoint" : ""));
            }
            if (!module.manifest().messagesOut().contains(type)) {
                return error(module.ref() + " does not declare " + type + " in messagesOut");
            }
            var spec = Json.parseObject(specJson);
            var payloadElement = spec.get("payload");
            var payload = payloadElement != null && payloadElement.isJsonObject()
                    ? payloadElement.getAsJsonObject() : Json.object("value", payloadElement);
            if (Json.booleanOr(spec, "expectReply", false)) {
                payload.addProperty("expectReply", true);
            }
            var size = Json.byteLength(Json.write(payload));
            if (size > services.limits().maxPayloadBytes()) {
                return error("payload of " + size + " bytes exceeds " + services.limits().maxPayloadBytes());
            }
            var priority = Json.string(spec, "priority");
            var envelope = services.messages().create(run, messageType, priority == null ? Priority.NORMAL : Priority.fromWire(priority),
                    source(), Json.string(spec, "topic"), payload, Json.string(spec, "dedupeKey"), null);
            services.events().publish(envelope);
            return ok(Json.tree(envelope.id()));
        } catch (IllegalArgumentException | JsonParseException e) {
            return error(e.getMessage());
        }
    }

    @Override
    public String notify(String kind, String payloadJson) {
        try {
            var notifyKind = NotifyKind.fromWire(kind);
            if (!notifyKind.scriptAllowed()) {
                return error("scripts cannot send " + kind + " notifications");
            }
            var payload = Json.parseObject(payloadJson);
            var text = Json.write(payload);
            if (Json.byteLength(text) > services.limits().maxPayloadBytes()) {
                return error("notification payload is too large");
            }
            payload.addProperty("plan", id);
            var notification = services.notifications().publish(notifyKind, source(),
                    kind + " " + (text.length() > 160 ? text.substring(0, 160) + "…" : text), payload);
            return ok(Json.tree(notification.id()));
        } catch (IllegalArgumentException | JsonParseException e) {
            return error(e.getMessage());
        }
    }

    @Override
    public String checkpoint(String name, String dataJson) {
        try {
            var data = Json.parse(dataJson);
            if (Json.byteLength(dataJson) > services.limits().maxPayloadBytes()) {
                return error("checkpoint data is too large");
            }
            var checkpoint = services.workspace().saveCheckpoint(id, lineage, module.ref(), module.manifest().sourceHash(), name,
                    mailbox.cursor(), data, new ArrayList<>(inFlight), ledger.snapshot());
            lastCheckpoint = checkpoint;
            services.trace().append(TraceKind.CHECKPOINT, checkpoint.summary());
            services.events().publish(services.messages().create(run, MessageType.CHECKPOINT, Priority.NORMAL, source(), name,
                    checkpoint.summary(), "checkpoint:" + id, null));
            return ok(Json.tree(checkpoint.id()));
        } catch (JsonParseException e) {
            return error(e.getMessage());
        }
    }

    @Override
    public String budget(String limitsJson) {
        try {
            var limits = Json.parseObject(limitsJson);
            if (!limits.isEmpty()) {
                budget.narrow(BudgetLimits.fromJson(limits));
            }
            return ok(budget.remaining());
        } catch (SchemaViolationException | JsonParseException e) {
            return error(e.getMessage());
        }
    }

    @Override
    public CompletableFuture<String> sleep(long millis) {
        var max = services.limits().maxSleep().toMillis();
        if (millis < 0 || millis > max) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("sleep must be between 0 and " + max + " ms"));
        }
        var future = new CompletableFuture<String>();
        sleeps.add(future);
        future.whenComplete((_, _) -> sleeps.remove(future));
        if (stopReason != null) {
            future.complete("null");
            return future;
        }
        services.timer().schedule(() -> future.complete("null"), millis, TimeUnit.MILLISECONDS);
        return future;
    }

    @Override
    public CompletableFuture<String> workspace(String op, String argsJson) {
        return blocking(() -> Json.write(workspaceOp(op, Json.parseObject(argsJson))));
    }

    @Override
    public CompletableFuture<String> bundle(String op, String argsJson) {
        JsonObject args;
        try {
            args = Json.parseObject(argsJson);
        } catch (JsonParseException e) {
            return CompletableFuture.failedFuture(e);
        }
        return manager.bundleOp(this, op, args).thenApply(Json::write);
    }

    @Override
    public String ledger(String op, String key, String dataJson) {
        try {
            return switch (op) {
                case "get" -> {
                    var entry = ledger.get(key);
                    yield ok(entry == null ? JsonNull.INSTANCE : Json.object("state", entry.state().name(),
                            "tool", entry.tool(), "result", entry.result(), "at", entry.at()));
                }
                case "mark" -> {
                    ledger.mark(key, Json.parseObject(dataJson), services.clock().millis());
                    yield ok(Json.tree(true));
                }
                default -> error("unknown ledger operation " + op);
            };
        } catch (JsonParseException e) {
            return error(e.getMessage());
        }
    }

    @Override
    public boolean cancelled() {
        return stopReason != null;
    }

    @Override
    public void log(String level, String message) {
        var limit = services.limits().maxLogLineBytes();
        services.trace().append(TraceKind.SCRIPT_OUTPUT, Json.object("plan", id, "level", level,
                "line", message.length() > limit ? message.substring(0, limit) : message));
    }

    String source() {
        return "script:" + module.ref() + "#" + id;
    }

    private JsonElement workspaceOp(String op, JsonObject args) {
        var workspace = services.workspace();
        return switch (op) {
            case "module.list" -> Workspace.describe(workspace.list());
            case "module.search" -> Workspace.describe(workspace.search(Json.requireString(args, "query")));
            case "module.get" -> {
                var loaded = workspace.load(ModuleRef.parse(Json.requireString(args, "module")));
                yield Json.object("manifest", loaded.manifest().toJson(), "source", loaded.source(), "fixtures", loaded.fixtures().size());
            }
            case "module.test" -> {
                var tester = services.tester();
                if (tester == null) {
                    throw new IllegalStateException("module.test is not available inside a module test");
                }
                yield tester.testAndRecord(ModuleRef.parse(Json.requireString(args, "module")));
            }
            case "module.saveDraft" -> {
                if (!capabilities.allows("workspace.saveDraft")) {
                    throw new IllegalStateException(module.ref() + " does not have the workspace.saveDraft capability");
                }
                var fixtures = new ArrayList<JsonObject>();
                var fixtureArray = args.getAsJsonArray("fixtures");
                if (fixtureArray != null) {
                    fixtureArray.forEach(element -> fixtures.add(element.getAsJsonObject()));
                }
                yield workspace.saveDraft(Json.requireString(args, "name"), Json.requireString(args, "source"),
                        args.getAsJsonObject("manifest"), fixtures, source()).toJson();
            }
            case "memory.read" -> {
                var memory = workspace.readMemory(Json.requireString(args, "key"));
                yield memory == null ? JsonNull.INSTANCE : memory;
            }
            case "memory.write" -> {
                var key = Json.requireString(args, "key");
                var value = args.get("value");
                var policy = services.profile().memoryWrites();
                if (policy.equals("deny")) {
                    throw new IllegalStateException("memory writes are denied by the profile");
                }
                workspace.writeMemory(key, value, origin);
                services.notifications().publish(NotifyKind.ACTION, origin, "写入记忆 " + key + " = " + Json.write(value),
                        Json.object("key", key, "value", value, "plan", id));
                yield Json.object("key", key);
            }
            case "checkpoint.latest" -> {
                var checkpoint = workspace.latestCheckpoint(lineage);
                yield checkpoint == null ? JsonNull.INSTANCE : checkpoint.toJson();
            }
            default -> throw new IllegalArgumentException("unknown workspace operation " + op);
        };
    }

    private static <T> CompletableFuture<T> blocking(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, BLOCKING);
    }

    private Mailbox.Filter filter(JsonObject spec) {
        var types = new HashSet<MessageType>();
        var array = spec.getAsJsonArray("types");
        if (array != null) {
            array.forEach(type -> types.add(MessageType.fromWire(type.getAsString())));
        }
        return new Mailbox.Filter(types, Json.string(spec, "replyTo"));
    }

    private void delivered(Envelope message) {
        if (message.type().delivery() != MessageType.Delivery.DROPPABLE) {
            services.trace().append(TraceKind.DELIVERED, Json.object("plan", id, "id", message.id(), "sequence", message.sequence()));
        }
    }

    private void dropped(Envelope message) {
        services.trace().append(TraceKind.DROPPED, Json.object("plan", id, "id", message.id(), "type", message.type().wire()));
    }

    private void overflow(Envelope message) {
        services.notifications().publish(NotifyKind.NEED_USER, source(), id + " 的收件箱已满，计划已停止", Json.object("plan", id,
                "dropped", message.toJson()));
        stop("mailbox overflow", "runtime");
    }

    private void budgetExhausted(BudgetScope.Exhaustion exhaustion) {
        services.events().publish(services.messages().create(run, MessageType.FAULT, Priority.HIGH, source(),
                "budget_exhausted", Json.object("kind", "budget_exhausted", "reason", exhaustion.kind().name(),
                        "data", exhaustion.toJson()), "budget:" + id + ":" + exhaustion.kind(), null));
    }

    private void finish(ScriptRunner.@Nullable Outcome outcome, @Nullable Throwable error) {
        PlanResult.Status status;
        JsonElement value = null;
        String message = null;
        if (error != null) {
            status = PlanResult.Status.FAILED;
            message = error.toString();
        } else {
            // future 没有异常地完成时一定带着结果
            var finished = Objects.requireNonNull(outcome);
            switch (finished.status()) {
                case RETURNED -> {
                    status = stopReason == null ? PlanResult.Status.COMPLETED : PlanResult.Status.STOPPED;
                    value = Json.parse(Objects.requireNonNull(finished.resultJson()));
                }
                case THREW -> {
                    status = PlanResult.Status.FAILED;
                    message = finished.error();
                }
                default -> {
                    status = PlanResult.Status.KILLED;
                    message = finished.error();
                }
            }
        }
        services.gateway().closePlan(id, "PLAN_ENDED");
        services.gateway().forget(id, planKey);
        services.leases().release(id, "plan ended");
        mailbox.close();
        var planResult = new PlanResult(id, module.ref(), role, status, value, message, stopReason);
        services.trace().append(TraceKind.SCRIPT_END, planResult.toJson());
        result.complete(planResult);
    }

    private Capabilities capabilities(List<String> declared, PlanRole role) {
        var base = Capabilities.of(declared);
        return switch (role) {
            case PRIMARY, RECOVERY -> base;
            case SUPERVISOR, PREFLIGHT -> base.and(Capabilities.of(List.of("observe.*", "workspace.*")));
            case BACKGROUND -> {
                var allowed = new ArrayList<>(services.profile().allowedCapabilities());
                allowed.add("observe.*");
                yield base.and(Capabilities.of(allowed)).without(services.profile().policy().neverAutonomous());
            }
        };
    }

    private static String ok(JsonElement value) {
        return Json.write(Json.object("ok", true, "value", value));
    }

    private static String error(String message) {
        return Json.write(Json.object("ok", false, "error", message));
    }
}
