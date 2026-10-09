package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.bot.FixtureBotFacade;
import io.ib67.yuanban.agent.bot.FixtureWorld;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.message.Envelope;
import io.ib67.yuanban.agent.message.MessageFactory;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.message.ScriptEventBus;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.plan.BundleSpec;
import io.ib67.yuanban.agent.plan.PlanActor;
import io.ib67.yuanban.agent.plan.PlanManager;
import io.ib67.yuanban.agent.plan.PlanResult;
import io.ib67.yuanban.agent.plan.PlanRole;
import io.ib67.yuanban.agent.plan.PlanServices;
import io.ib67.yuanban.agent.script.ScriptEngine;
import io.ib67.yuanban.agent.script.ScriptLimits;
import io.ib67.yuanban.agent.tool.AgentConfigurator;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.FacadeBackend;
import io.ib67.yuanban.agent.tool.LeaseManager;
import io.ib67.yuanban.agent.tool.ToolCatalog;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.Redactor;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceReader;
import io.ib67.yuanban.agent.trace.TraceStore;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code module.test}：在 fixture 中运行模块，不连接服务器，验证收件箱行为、取消传播、预算、异常恢复与 postcondition。
 * 每个 fixture 一次独立的运行：新的 fixture 外观、网关、lease 与 trace（写入 {@code runs/tests/}）；
 * 全部 fixture 通过时 draft 模块变为 tested。fixture 的格式见 {@link #FIXTURE_SCHEMA}。
 */
public final class ModuleTester {
    public static final JsonSchema FIXTURE_SCHEMA = JsonSchema.parse("""
            {"type": "object", "required": ["name"], "additionalProperties": false, "properties": {
              "name": {"type": "string", "minLength": 1, "maxLength": 64},
              "description": {"type": "string"},
              "role": {"enum": ["primary", "supervisor", "preflight", "recovery", "background"]},
              "input": true,
              "asSupervisorOf": {"type": "object", "required": ["primary"], "properties": {
                "primary": {"type": "string"}, "input": true, "policy": true}},
              "world": {"type": "object"},
              "actions": {"type": "array", "items": {"type": "object", "required": ["tool"]}},
              "waits": {"type": "array", "items": {"type": "object", "required": ["outcome"]}},
              "signals": {"type": "array", "items": {"type": "object", "required": ["topic"]}},
              "inbox": {"type": "array", "items": {"type": "object", "required": ["type"], "properties": {
                "atMs": {"type": "integer", "minimum": 0}, "type": {"type": "string"}, "payload": {"type": "object"},
                "dedupeKey": {"type": "string"}, "target": {"enum": ["plan", "primary", "supervisor"]}}}},
              "stopAtMs": {"type": "integer", "minimum": 0},
              "timeoutMs": {"type": "integer", "minimum": 1, "maximum": 600000},
              "dryRun": {"type": "boolean"},
              "grantLeases": {"type": "boolean"},
              "budget": {"type": "object"},
              "expect": {"type": "object", "additionalProperties": false, "properties": {
                "status": {"enum": ["COMPLETED", "STOPPED", "FAILED", "KILLED"]},
                "result": true,
                "events": {"type": "array", "items": {"type": "object", "required": ["type"]}},
                "noEvents": {"type": "array", "items": {"type": "string"}},
                "toolCalls": {"type": "array", "items": {"type": "string"}},
                "noToolCalls": {"type": "array", "items": {"type": "string"}},
                "maxToolCalls": {"type": "integer", "minimum": 0},
                "stopWithinMs": {"type": "integer", "minimum": 1},
                "noToolCallsAfterStop": {"type": "boolean"},
                "allActionsUsed": {"type": "boolean"}}}
            }}
            """);
    private static final AgentConfigurator NO_CONFIGURATION = new AgentConfigurator() {
        @Override
        public Set<String> keys() {
            return Set.of();
        }

        @Override
        public void set(String key, JsonElement value) {
            throw new IllegalArgumentException("module tests have no agent configuration");
        }
    };

    private final Workspace workspace;
    private final ScriptEngine engine;
    private final ScriptLimits limits;
    private final ScheduledExecutorService timer;
    private final InstantSource clock;
    private final AgentProfile profile;
    private final AtomicLong runs = new AtomicLong();

    public ModuleTester(Workspace workspace, ScriptEngine engine, ScriptLimits limits, ScheduledExecutorService timer,
                        InstantSource clock, AgentProfile profile) {
        this.workspace = workspace;
        this.engine = engine;
        this.limits = limits;
        this.timer = timer;
        this.clock = clock;
        this.profile = profile;
    }

    /**
     * 测试模块并记录报告；draft 模块全部 fixture 通过时变为 tested
     */
    public JsonObject testAndRecord(ModuleRef ref) {
        var module = workspace.load(ref);
        var report = test(module);
        var status = module.manifest().status();
        if (report.get("passed").getAsBoolean() && status == ModuleStatus.DRAFT) {
            status = ModuleStatus.TESTED;
        }
        workspace.setStatus(ref, status, report);
        report.addProperty("status", status.wire());
        return report;
    }

    /**
     * 运行模块的全部 fixture。没有 fixture 的模块不通过
     */
    public JsonObject test(Module module) {
        var results = new JsonArray();
        var passed = !module.fixtures().isEmpty();
        for (var fixture : module.fixtures()) {
            var result = runFixture(module, fixture);
            passed &= result.get("passed").getAsBoolean();
            results.add(result);
        }
        return Json.object("module", module.ref().toString(), "sourceHash", module.manifest().sourceHash(), "passed", passed,
                "fixtures", results, "note", module.fixtures().isEmpty() ? "a module needs at least one fixture" : null,
                "at", clock.millis());
    }

    private JsonObject runFixture(Module module, JsonObject fixture) {
        var schemaErrors = FIXTURE_SCHEMA.validate(fixture);
        if (!schemaErrors.isEmpty()) {
            return Json.object("name", Json.string(fixture, "name"), "passed", false, "failures", schemaErrors);
        }
        var name = Json.requireString(fixture, "name");
        var prefix = "t" + runs.incrementAndGet() + "-" + clock.millis() + "-";
        var traceFile = workspace.root().resolve("runs").resolve("tests").resolve(module.ref().name() + "-" + module.ref().version())
                .resolve(prefix + name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jsonl");
        var trace = TraceStore.open(traceFile, "module-test", "fixture", Redactor.none(), clock,
                Json.object("module", module.ref().toString(), "fixture", name));
        var world = Json.objectOrNull(fixture, "world");
        var facade = new FixtureBotFacade(new FixtureWorld(world == null ? new JsonObject() : world),
                fixture.getAsJsonArray("actions"), fixture.getAsJsonArray("waits"), fixture.getAsJsonArray("signals"));
        var gatewayRef = new AtomicReference<ToolGateway>();
        var managerRef = new AtomicReference<PlanManager>();
        var failures = new ArrayList<String>();
        try {
            var leases = new LeaseManager(trace, clock, timer, revocation -> gatewayRef.get()
                    .cancelWorldChanging(revocation.owner(), "LEASE_REVOKED: " + revocation.reason()));
            var notify = new NotifyBus(trace, clock);
            var policy = profile.policy().withDryRun(Json.booleanOr(fixture, "dryRun", false));
            var gateway = new ToolGateway(ToolCatalog.standard(), new FacadeBackend(facade, NO_CONFIGURATION), leases, notify,
                    trace, clock, policy);
            gatewayRef.set(gateway);
            var messages = new MessageFactory(clock);
            var bus = new ScriptEventBus(trace);
            var services = new PlanServices("module-test", trace, messages, bus, notify, gateway, leases, workspace,
                    engine, limits, timer, clock, profile, null, true);
            var manager = new PlanManager(services, prefix);
            managerRef.set(manager);
            var root = BudgetScope.root("fixture", BudgetLimits.fromJson(Json.objectOrNull(fixture, "budget")), clock, facade::ticks, _ -> {
            });
            var plan = startPlan(module, fixture, manager, root, leases, bus, messages);
            var stopAt = new AtomicLong(-1);
            scheduleInbox(fixture, plan, messages);
            if (fixture.has("stopAtMs")) {
                timer.schedule(() -> {
                    stopAt.set(clock.millis());
                    plan.stop("fixture stop", "fixture");
                }, fixture.get("stopAtMs").getAsLong(), TimeUnit.MILLISECONDS);
            }
            PlanResult result;
            try {
                result = plan.result().get(Json.longOr(fixture, "timeoutMs", 10_000), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                plan.kill("fixture timeout");
                result = plan.result().get(limits.stopGrace().toMillis() + 5_000, TimeUnit.MILLISECONDS);
                failures.add("did not finish within " + Json.longOr(fixture, "timeoutMs", 10_000) + " ms");
            }
            var finishedAt = clock.millis();
            // 工具调用与事件从这次运行的 trace 中读取：与注册监听的时机无关，不会漏掉脚本一开始的调用
            trace.flush();
            var recorded = TraceReader.read(traceFile);
            var source = "script:" + module.ref() + "#" + plan.id();
            var events = recorded.ofKind(TraceKind.MESSAGE).stream()
                    .map(record -> Envelope.fromJson(record.getAsJsonObject("envelope")))
                    .filter(event -> event.source().equals(source) && event.type().direction() == MessageType.Direction.EVENT)
                    .toList();
            var stopMillis = stopAt.get();
            var toolCalls = recorded.ofKind(TraceKind.TOOL_START).stream()
                    .filter(record -> plan.id().equals(Json.string(record, "plan")))
                    .map(record -> Json.object("tool", Json.requireString(record, "tool"), "args", record.get("args"),
                            "afterStop", stopMillis >= 0 && record.get("t").getAsLong() > stopMillis))
                    .toList();
            check(fixture, result, events, toolCalls, facade, stopMillis, finishedAt, failures);
            return Json.object("name", name, "passed", failures.isEmpty(), "failures", failures, "result", result.toJson(),
                    "events", Json.array(events.stream().map(event -> Json.object("type", event.type().wire(),
                            "topic", event.topic(), "payload", event.payload())).toList()),
                    "toolCalls", Json.array(toolCalls), "trace", workspace.root().relativize(traceFile).toString());
        } catch (Exception e) {
            failures.add("fixture could not run: " + e);
            return Json.object("name", name, "passed", false, "failures", failures,
                    "trace", workspace.root().relativize(traceFile).toString());
        } finally {
            var manager = managerRef.get();
            if (manager != null) {
                manager.bundles().forEach(bundle -> bundle.cancel("fixture ended"));
                for (var plan : manager.plans()) {
                    plan.kill("fixture ended");
                    plan.result().join();
                }
            }
            facade.close();
            var gateway = gatewayRef.get();
            if (gateway != null) {
                try {
                    gateway.close();
                } catch (Exception e) {
                    failures.add("gateway did not close: " + e);
                }
            }
            trace.close();
            workspace.deleteScripts(prefix);
        }
    }

    private PlanActor startPlan(Module module, JsonObject fixture, PlanManager manager, BudgetScope root, LeaseManager leases,
                                ScriptEventBus bus, MessageFactory messages) {
        var input = fixture.has("input") ? fixture.get("input") : JsonNull.INSTANCE;
        var supervising = Json.objectOrNull(fixture, "asSupervisorOf");
        if (supervising != null) {
            var spec = new JsonObject();
            spec.addProperty("primary", Json.requireString(supervising, "primary"));
            spec.add("input", supervising.has("input") ? supervising.get("input") : JsonNull.INSTANCE);
            spec.addProperty("supervisor", module.ref().toString());
            if (supervising.has("policy")) {
                spec.add("policy", supervising.get("policy"));
            }
            var bundle = manager.startBundle(BundleSpec.fromJson(spec, null), "run-test", null, root, "foreground");
            return Objects.requireNonNull(bundle.supervisor());
        }
        var roleName = Json.string(fixture, "role");
        var role = roleName == null ? module.manifest().roles().getFirst() : PlanRole.fromWire(roleName);
        var plan = manager.create(module, role, "run-test", role == PlanRole.BACKGROUND ? "background" : "foreground",
                input, root, null, null, null);
        if (role == PlanRole.PRIMARY || role == PlanRole.RECOVERY) {
            leases.acquireForeground(plan.id(), "fixture");
        }
        if (role == PlanRole.BACKGROUND && Json.booleanOr(fixture, "grantLeases", false)) {
            bus.subscribe(event -> event.type() == MessageType.ACTION_REQUEST, event -> {
                var duration = Duration.ofMillis(Math.min(Json.longOr(event.payload(), "durationMs", 1000),
                        profile.maxLease().toMillis()));
                var purpose = Json.string(event.payload(), "purpose");
                var lease = leases.tryAcquireBackground(plan.id(),
                        purpose == null ? Json.requireString(event.payload(), "tool") : purpose, duration);
                plan.deliver(messages.create("run-test", MessageType.LEASE, Priority.HIGH, "scheduler", null,
                        lease == null ? Json.object("granted", false, "reason", "lease busy")
                                : Json.object("granted", true, "leaseId", lease.id(), "expiresAt", lease.expiresAt()),
                        null, event.id()));
            });
        }
        plan.start();
        return plan;
    }

    private void scheduleInbox(JsonObject fixture, PlanActor plan, MessageFactory messages) {
        var inbox = fixture.getAsJsonArray("inbox");
        if (inbox == null) {
            return;
        }
        for (var element : inbox) {
            var entry = element.getAsJsonObject();
            var type = MessageType.fromWire(Json.requireString(entry, "type"));
            var payload = Json.objectOrNull(entry, "payload");
            var dedupeKey = Json.string(entry, "dedupeKey");
            var target = Json.string(entry, "target");
            timer.schedule(() -> {
                var receiver = plan;
                var bundle = plan.bundle();
                var primary = bundle == null ? null : bundle.primary();
                if ("primary".equals(target) && primary != null) {
                    receiver = primary;
                }
                receiver.deliver(messages.create("run-test", type, Priority.NORMAL, "fixture", null,
                        payload == null ? new JsonObject() : payload, dedupeKey, null));
            }, Json.longOr(entry, "atMs", 0), TimeUnit.MILLISECONDS);
        }
    }

    private static void check(JsonObject fixture, PlanResult result, List<Envelope> events, List<JsonObject> toolCalls,
                              FixtureBotFacade facade, long stopAt, long finishedAt, List<String> failures) {
        var expect = Json.objectOrNull(fixture, "expect");
        if (expect == null) {
            expect = Json.object("status", "COMPLETED");
        }
        var status = Json.string(expect, "status");
        if (status != null && !status.equals(result.status().name())) {
            failures.add("expected status " + status + ", got " + result.status() + (result.error() == null ? "" : " (" + result.error() + ")"));
        }
        if (expect.has("result") && !subset(expect.get("result"), result.value())) {
            failures.add("result " + Json.write(result.value()) + " does not contain " + Json.write(expect.get("result")));
        }
        var expectedEvents = expect.getAsJsonArray("events");
        if (expectedEvents != null) {
            var index = 0;
            for (var event : events) {
                if (index < expectedEvents.size() && matches(expectedEvents.get(index).getAsJsonObject(), event)) {
                    index++;
                }
            }
            if (index < expectedEvents.size()) {
                failures.add("missing event " + Json.write(expectedEvents.get(index)) + " (in order)");
            }
        }
        var noEvents = expect.getAsJsonArray("noEvents");
        if (noEvents != null) {
            for (var type : noEvents) {
                if (events.stream().anyMatch(event -> event.type().wire().equals(type.getAsString()))) {
                    failures.add("unexpected " + type.getAsString() + " event");
                }
            }
        }
        var tools = toolCalls.stream().map(call -> Json.requireString(call, "tool")).toList();
        var expectedTools = expect.getAsJsonArray("toolCalls");
        if (expectedTools != null) {
            var index = 0;
            for (var tool : tools) {
                if (index < expectedTools.size() && tool.equals(expectedTools.get(index).getAsString())) {
                    index++;
                }
            }
            if (index < expectedTools.size()) {
                failures.add("missing tool call " + expectedTools.get(index).getAsString() + " (in order), calls were " + tools);
            }
        }
        var forbidden = expect.getAsJsonArray("noToolCalls");
        if (forbidden != null) {
            for (var tool : forbidden) {
                if (tools.contains(tool.getAsString())) {
                    failures.add("unexpected tool call " + tool.getAsString());
                }
            }
        }
        if (expect.has("maxToolCalls") && tools.size() > expect.get("maxToolCalls").getAsInt()) {
            failures.add(tools.size() + " tool calls exceed " + expect.get("maxToolCalls").getAsInt());
        }
        if (expect.has("stopWithinMs")) {
            if (stopAt < 0) {
                failures.add("stopWithinMs needs stopAtMs");
            } else {
                var elapsed = finishedAt - stopAt;
                if (elapsed > expect.get("stopWithinMs").getAsLong()) {
                    failures.add("stopped after " + elapsed + " ms, expected within " + expect.get("stopWithinMs").getAsLong());
                }
            }
        }
        if (Json.booleanOr(expect, "noToolCallsAfterStop", false)
                && toolCalls.stream().anyMatch(call -> call.get("afterStop").getAsBoolean())) {
            failures.add("tool calls were started after stop");
        }
        if (!facade.unmatched().isEmpty()) {
            failures.add("actions without scripted results: " + facade.unmatched());
        }
        if (Json.booleanOr(expect, "allActionsUsed", false)) {
            var scripted = fixture.getAsJsonArray("actions");
            var used = toolCalls.stream().filter(call -> !Json.requireString(call, "tool").startsWith("observe.")).count();
            if (scripted != null && used < scripted.size()) {
                failures.add("only " + used + " of " + scripted.size() + " scripted actions were used");
            }
        }
    }

    private static boolean matches(JsonObject expected, Envelope event) {
        if (!Json.requireString(expected, "type").equals(event.type().wire())) {
            return false;
        }
        var topic = Json.string(expected, "topic");
        if (topic != null && !topic.equals(event.topic())) {
            return false;
        }
        return !expected.has("payload") || subset(expected.get("payload"), event.payload());
    }

    private static boolean subset(@Nullable JsonElement expected, @Nullable JsonElement actual) {
        if (expected == null || expected.isJsonNull()) {
            return actual == null || actual.isJsonNull();
        }
        if (actual == null) {
            return false;
        }
        if (expected.isJsonObject()) {
            if (!actual.isJsonObject()) {
                return false;
            }
            for (var entry : expected.getAsJsonObject().entrySet()) {
                if (!subset(entry.getValue(), actual.getAsJsonObject().get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isJsonPrimitive() && expected.getAsJsonPrimitive().isNumber() && actual.isJsonPrimitive()
                && actual.getAsJsonPrimitive().isNumber()) {
            return expected.getAsDouble() == actual.getAsDouble();
        }
        return expected.equals(actual);
    }
}
