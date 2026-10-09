package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentIntent;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.message.Mailbox;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.message.Priority;
import io.ib67.yuanban.agent.model.ToolDefinition;
import io.ib67.yuanban.agent.notify.NotifyKind;
import io.ib67.yuanban.agent.plan.BackgroundScheduler;
import io.ib67.yuanban.agent.plan.BundleSpec;
import io.ib67.yuanban.agent.plan.PlanActor;
import io.ib67.yuanban.agent.plan.PlanResult;
import io.ib67.yuanban.agent.plan.PlanRole;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import io.ib67.yuanban.agent.workspace.Workspace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 完整模式：模型作为 supervisor 管理前台 PlanBundle（primary 与 supervisor 脚本）、人格驱动的 background plan、preflight、
 * 模块的 draft 与测试、检查点替换，以及 supervisor 没有及时回应 attention、fault、question 时执行模块声明的 fallback。
 */
final class SupervisorMode implements KernelMode {
    private final KernelCore core;
    private final KernelServices services;
    private final BackgroundScheduler background;
    /**
     * 用户停止、暂停、预算耗尽或安全事件之后，前台结束时不按人格重新启动 background plan，直到用户恢复或提交新目标
     */
    private boolean backgroundHeld;
    /**
     * profile 强制 preflight 时，等 preflight 结果的 start
     */
    private @Nullable JsonObject pendingStart;
    private final LinkedHashMap<String, JsonObject> decisions = new LinkedHashMap<>();
    private final Map<String, JsonObject> focus = new LinkedHashMap<>();
    private final Map<String, ModuleRef> replacements = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> fallbacks = new HashMap<>();

    SupervisorMode(KernelCore core, BackgroundScheduler background) {
        this.core = core;
        this.services = core.services();
        this.background = background;
    }

    @Override
    public String pinned() {
        return AgentKernel.resource("supervisor-system-prompt.txt") + "\n\n"
                + AgentKernel.withScriptTools(AgentKernel.resource("supervisor-reference.md"))
                + "\n\n# agent profile\n\n人格：" + services.profile().persona() + "\n\n" + Json.PRETTY.toJson(services.profile().raw());
    }

    @Override
    public List<ToolDefinition> tools() {
        var tools = new ArrayList<>(SupervisorTools.queries());
        tools.add(SupervisorTools.decision(Decisions.SCHEMA));
        return List.copyOf(tools);
    }

    @Override
    public JsonSchema decisionSchema() {
        return Decisions.SCHEMA;
    }

    @Override
    public String catalog() {
        return "# workspace 模块目录\n\n" + Json.PRETTY.toJson(Workspace.describe(services.workspace().list()))
                + "\n\n" + SupervisorTools.toolOverview(services.gateway().catalog());
    }

    @Override
    public JsonObject context(ContextKind kind) {
        return switch (kind) {
            case TURN -> fullContext();
            case SUMMARY -> summaryContext();
            case EMERGENCY -> emergencyContext();
        };
    }

    @Override
    public @Nullable String apply(String type, JsonObject decision, String reason) {
        return switch (type) {
            case "send" -> send(Json.requireString(decision, "target"), decision.getAsJsonObject("send"), reason);
            case "start" -> start(decision.getAsJsonObject("start"));
            case "patch_script" -> patch(decision.getAsJsonObject("patch"));
            case "preflight" -> {
                var spec = decision.getAsJsonObject("preflight");
                var module = Json.string(spec, "module");
                var ref = module != null ? ModuleRef.parse(module) : services.profile().preflightModule();
                if (ref == null) {
                    yield "no preflight module given and the profile has none";
                }
                runPreflight(ref, spec.has("input") ? spec.get("input") : JsonNull.INSTANCE, null);
                yield null;
            }
            case "stop" -> stop(decision.getAsJsonObject("stop"), reason);
            default -> throw new IllegalStateException("decision " + type + " passed the schema but is not handled");
        };
    }

    // 生命周期

    @Override
    public List<PlanActor> background() {
        return background.plans();
    }

    @Override
    public void goalSubmitted(AgentIntent intent) {
        backgroundHeld = false;
        decisions.put("userGoal", Json.object("key", "userGoal", "value", intent.text(),
                "source", "user:" + intent.source().name().toLowerCase(Locale.ROOT)));
    }

    /**
     * 第一次加载：按人格启动 background plan
     */
    @Override
    public void loaded() {
        if (!backgroundHeld) {
            background.startAll();
        }
    }

    @Override
    public void hold(@Nullable String stopReason) {
        backgroundHeld = true;
        if (stopReason != null) {
            background.stopAll(stopReason);
        }
    }

    @Override
    public void resumed(boolean idle) {
        backgroundHeld = false;
        if (idle) {
            background.foregroundEnded(true);
        }
    }

    @Override
    public void foregroundStarted(BundleSpec spec) {
        pendingStart = null;
        background.foregroundStarted();
        decisions.put("foreground", Json.object("key", "foreground", "value", spec.toJson(), "source", "supervisor"));
    }

    @Override
    public void foregroundEnded() {
        background.foregroundEnded(!backgroundHeld);
    }

    @Override
    public void stopping(String reason) {
        pendingStart = null;
        background.stopAll(reason);
        fallbacks.values().forEach(future -> future.cancel(false));
        fallbacks.clear();
    }

    @Override
    public void expectResponse(String messageId) {
        var timeout = services.profile().supervisorResponseTimeout();
        fallbacks.put(messageId, services.timer().schedule(() -> core.post(() -> handleFallback(messageId)), timeout.toMillis(),
                TimeUnit.MILLISECONDS));
    }

    @Override
    public void responded(String messageId) {
        var future = fallbacks.remove(messageId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void checkpointed(String planId) {
        var replacement = replacements.remove(planId);
        if (replacement != null) {
            replaceAtCheckpoint(planId, replacement);
        }
    }

    // 决策

    private @Nullable String send(String target, JsonObject send, String reason) {
        var plan = resolvePlan(target);
        if (plan == null) {
            return "no running plan for target " + target;
        }
        var messageType = MessageType.fromWire(Json.requireString(send, "type"));
        var payload = send.getAsJsonObject("payload");
        if (messageType == MessageType.STOP) {
            plan.stop(reason, "kernel");
        } else {
            var offer = plan.deliver(services.messages().create(plan.run(), messageType, Priority.NORMAL, "kernel", null,
                    payload, Json.string(send, "dedupeKey"), Json.string(send, "replyTo")));
            if (offer == Mailbox.Offer.STALE) {
                return "the message is older than a message with the same dedupeKey and was dropped";
            }
            if (offer == Mailbox.Offer.CLOSED) {
                return "the plan " + plan.id() + " has ended";
            }
        }
        if (messageType == MessageType.FOCUS) {
            focus.put(plan.id(), payload);
        }
        if (messageType == MessageType.POLICY) {
            var dedupeKey = Json.string(send, "dedupeKey");
            var key = "policy:" + plan.module().name() + ":" + (dedupeKey == null ? "latest" : dedupeKey);
            decisions.put(key, Json.object("key", key, "value", payload, "source", "supervisor"));
        }
        var replyTo = Json.string(send, "replyTo");
        if (replyTo != null) {
            core.acknowledge(replyTo);
        }
        return null;
    }

    private @Nullable String start(JsonObject spec) {
        var replace = Json.booleanOr(spec, "replace", false);
        var preflightDone = Json.booleanOr(spec, "preflightDone", false);
        var bundleJson = spec.deepCopy();
        bundleJson.remove("replace");
        bundleJson.remove("preflightDone");
        var bundleSpec = BundleSpec.fromJson(bundleJson, services.profile().defaultSupervisor());
        var primary = services.workspace().load(bundleSpec.primary()).manifest();
        if (!primary.status().startable()) {
            return bundleSpec.primary() + " is " + primary.status().wire() + "; only tested modules can start";
        }
        if (services.profile().forcePreflight() && !preflightDone) {
            var module = services.profile().preflightModule();
            if (module == null) {
                return "the profile forces preflight but has no preflightModule";
            }
            runPreflight(module, Json.object("primaryInput", bundleSpec.input()), spec);
            return null;
        }
        var current = core.foreground();
        if (current != null) {
            if (!replace) {
                return "foreground bundle " + current.id() + " is running; send messages to it, stop it, or start with replace: true";
            }
            // 旧 bundle 结束之后才能取得前台 lease：在它的 BundleDone 中启动
            core.replaceForeground(bundleSpec, "replaced by a new bundle");
            return null;
        }
        return core.startForeground(bundleSpec);
    }

    private @Nullable String patch(JsonObject patch) {
        var name = Json.requireString(patch, "module");
        var replacePlan = Json.string(patch, "replacePlan");
        if (replacePlan != null) {
            var plan = services.plans().plan(replacePlan);
            var foreground = core.foreground();
            if (plan == null || foreground == null || foreground.primary() != plan) {
                return "only the running foreground primary can be replaced at a checkpoint, " + replacePlan + " is not";
            }
            if (!plan.module().name().equals(name)) {
                return "the replacement must be a new version of " + plan.module().name();
            }
        }
        var fixtures = new ArrayList<JsonObject>();
        patch.getAsJsonArray("fixtures").forEach(element -> fixtures.add(element.getAsJsonObject()));
        var manifest = services.workspace().saveDraft(name, Json.requireString(patch, "source"), patch.getAsJsonObject("manifest"),
                fixtures, "supervisor");
        var ref = manifest.ref();
        // module.test 在虚拟线程上运行，报告回到 kernel 线程
        Thread.ofVirtual().name("agent-module-test-" + ref).start(() -> {
            var report = services.tester().testAndRecord(ref);
            core.post(() -> handleModuleTested(ref, report, replacePlan));
        });
        return null;
    }

    private @Nullable String stop(JsonObject stop, String reason) {
        switch (Json.requireString(stop, "scope")) {
            case "foreground" -> {
                var foreground = core.foreground();
                if (foreground != null) {
                    foreground.cancel(reason);
                }
            }
            case "background" -> background.stopAll(reason);
            case "all" -> {
                var foreground = core.foreground();
                if (foreground != null) {
                    foreground.cancel(reason);
                }
                background.stopAll(reason);
            }
            case "plan" -> {
                var plan = services.plans().plan(Json.requireString(stop, "plan"));
                if (plan == null) {
                    return "no running plan " + Json.string(stop, "plan");
                }
                plan.stop(reason, "kernel");
            }
            default -> throw new IllegalStateException("scope passed the schema but is not handled");
        }
        if (Json.booleanOr(stop, "done", false)) {
            core.finishGoal(reason);
        }
        return null;
    }

    private void runPreflight(ModuleRef ref, JsonElement input, @Nullable JsonObject start) {
        var run = core.run();
        var runId = run == null ? "run-0" : run;
        CompletionStage<PlanResult> result;
        var foreground = core.foreground();
        if (foreground != null) {
            result = foreground.preflight(ref, input);
        } else {
            var plan = services.plans().create(services.workspace().load(ref), PlanRole.PREFLIGHT, runId, "foreground", input,
                    services.budget(), null, null, null);
            plan.start();
            result = plan.result();
        }
        result.thenAccept(planResult -> core.post(() -> preflightDone(planResult, start)));
    }

    private void preflightDone(PlanResult result, @Nullable JsonObject start) {
        pendingStart = start;
        core.addHot("preflight-" + result.planId(), Json.object("type", "preflight_result", "result", result.toJson(),
                "pendingStart", start), true);
    }

    private void replaceAtCheckpoint(String planId, ModuleRef ref) {
        var plan = services.plans().plan(planId);
        var bundle = core.foreground();
        var checkpoint = plan == null ? null : plan.lastCheckpoint();
        if (plan == null || bundle == null || bundle.primary() != plan || checkpoint == null) {
            core.addHot("replace-failed-" + planId, Json.object("type", "replace_failed", "plan", planId, "module", ref.toString(),
                    "reason", "the plan is no longer the foreground primary"), true);
            return;
        }
        bundle.replacePrimary(services.workspace().load(ref), checkpoint);
        core.addHot("replaced-" + planId, Json.object("type", "replaced", "old", planId, "module", ref.toString(),
                "checkpoint", checkpoint.id()), false);
    }

    private void handleFallback(String id) {
        fallbacks.remove(id);
        var item = core.unacked().get(id);
        if (item == null) {
            return;
        }
        var planId = AgentKernel.planOf(Json.requireString(item, "source"));
        var plan = services.plans().plan(planId);
        if (plan == null || !plan.running()) {
            return;
        }
        var fallback = plan.moduleSource().manifest().fallback();
        services.trace().append(TraceKind.STATE, Json.object("event", "fallback", "message", id, "plan", planId,
                "fallback", fallback.wire()));
        var reason = "supervisor did not respond to " + id;
        switch (fallback) {
            case PAUSE_ON_UNKNOWN_FAULT -> {
                var bundle = plan.bundle();
                if (bundle != null && bundle.primary() == plan) {
                    bundle.pausePrimary(reason);
                } else {
                    services.leases().suspend(planId, reason);
                }
            }
            case STOP_ON_UNKNOWN_FAULT -> plan.stop(reason, "kernel");
            case CONTINUE -> {
            }
        }
        services.notifications().publish(NotifyKind.NEED_USER, "kernel", "supervisor 没有及时处理 " + id + "，计划 " + planId
                + " 执行 fallback " + fallback.wire(), item);
    }

    private void handleModuleTested(ModuleRef module, JsonObject report, @Nullable String replacePlan) {
        core.catalogChanged();
        var passed = report.get("passed").getAsBoolean();
        core.addHot("module-test-" + module, Json.object("type", "module_test", "module", module.toString(), "passed", passed,
                "report", AgentKernel.truncate(report), "replacePlan", replacePlan), true);
        if (!passed) {
            return;
        }
        if (replacePlan != null) {
            replacements.put(replacePlan, module);
        }
    }

    private @Nullable PlanActor resolvePlan(String target) {
        var bundle = core.foreground();
        if (target.equals("primary") || target.equals("foreground")) {
            return bundle == null ? null : bundle.primary();
        }
        if (target.equals("supervisor")) {
            return bundle == null ? null : bundle.supervisor();
        }
        if (target.startsWith("background:")) {
            var ref = ModuleRef.parse(target.substring("background:".length()));
            return background.plans().stream().filter(plan -> plan.module().equals(ref)).findFirst().orElse(null);
        }
        return services.plans().plan(target);
    }

    // 上下文

    /**
     * 本回合的 {@code supervisor-context/1}
     */
    private JsonObject fullContext() {
        var json = summaryContext();
        json.add("agent", Json.object("status", core.status().name(), "bot", services.botStatus().get()));
        json.add("background", Json.array(background.plans().stream().map(PlanActor::describe).toList()));
        json.add("lease", services.leases().describe());
        json.add("pendingQuestions", Json.array(core.questions()));
        json.add("progress", Json.tree(core.progress()));
        json.add("recentChat", Json.array(core.recentChat()));
        json.add("budget", services.budget().remaining());
        json.add("pendingStart", pendingStart);
        json.add("coldRefs", Json.array(List.of("trace:1-" + services.trace().lastSequence())));
        return json;
    }

    /**
     * 摘要与完整上下文共有的部分：目标、脚本、关注点、未确认的输入与之前的决定
     */
    private JsonObject summaryContext() {
        var json = new JsonObject();
        json.addProperty("schema", "supervisor-context/1");
        json.addProperty("userGoal", core.userGoal());
        json.addProperty("run", core.run());
        var foreground = core.foreground();
        json.add("script", foreground == null ? JsonNull.INSTANCE : AgentKernel.scriptSummary(foreground));
        json.add("focus", Json.tree(focus));
        json.add("unacked", Json.array(List.copyOf(core.unacked().values())));
        json.add("decisions", Json.array(List.copyOf(decisions.values())));
        json.add("nextSupervisorChoices", nextChoices());
        return json;
    }

    private JsonObject emergencyContext() {
        var json = new JsonObject();
        json.addProperty("schema", "supervisor-context/1");
        json.addProperty("emergency", true);
        json.addProperty("userGoal", core.userGoal());
        var foreground = core.foreground();
        json.add("script", foreground == null ? JsonNull.INSTANCE : AgentKernel.scriptSummary(foreground));
        var latest = core.unacked().values().stream()
                .filter(item -> Set.of("attention", "fault", "question").contains(Json.string(item, "type"))).toList();
        json.add("unacked", Json.array(latest.subList(Math.max(0, latest.size() - 5), latest.size())));
        json.add("coldRefs", Json.array(List.of("trace:1-" + services.trace().lastSequence())));
        json.add("nextSupervisorChoices", nextChoices());
        return json;
    }

    private JsonArray nextChoices() {
        var choices = new ArrayList<String>();
        if (core.foreground() != null) {
            choices.add("send focus/policy/stop/user_reply to the foreground primary or supervisor");
            choices.add("stop the foreground bundle");
            choices.add("say (leave the script unchanged)");
        } else {
            choices.add("start a bundle for the user goal");
            choices.add("say");
        }
        choices.add("ask the user");
        choices.add("preflight");
        choices.add("patch_script");
        return Json.array(choices);
    }
}
