package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.model.ToolDefinition;
import io.ib67.yuanban.agent.plan.BundleSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 简单模式：模型为每条用户指令直接编写脚本，脚本保存为 workspace 中一个模块的新 draft 版本，作为没有 supervisor 的前台 bundle 立即运行。
 * 没有 background plan、人格、preflight 与模块测试。脚本只能发出 progress，结束时 bundle 的结果唤醒模型。
 */
final class SimpleMode implements KernelMode {
    /**
     * 模型可以调用的查询函数
     */
    private static final Set<String> QUERIES = Set.of("read_reference", "memory_read", "agent_status", "tool_catalog");
    /**
     * 写进固定前缀的策略字段
     */
    private static final List<String> POLICY = List.of("external", "allowedCommands", "chatPerMinute", "unsafe", "dryRun",
            "memoryWrites", "maxMoveDistance", "interactionsPerSecond", "interactionsPerBlockPerMinute");
    /**
     * 模型的脚本使用的 manifest（description 取决策的 reason）
     */
    private static final String MANIFEST = """
            {"entry": "main", "roles": ["primary"], "messagesIn": ["stop", "pause", "resume", "bot_event", "lease"],
             "messagesOut": ["progress"], "capabilities": ["*"], "fallback": "stop-on-unknown-fault"}
            """;
    /**
     * emergency 时保留的未确认输入数
     */
    private static final int EMERGENCY_UNACKED = 5;

    private final KernelCore core;
    private final KernelServices services;

    SimpleMode(KernelCore core) {
        this.core = core;
        this.services = core.services();
    }

    @Override
    public String pinned() {
        var raw = services.profile().raw();
        var policy = new JsonObject();
        for (var key : POLICY) {
            policy.add(key, raw.get(key).deepCopy());
        }
        return AgentKernel.resource("simple-system-prompt.txt") + "\n\n"
                + AgentKernel.withScriptTools(AgentKernel.resource("simple-reference.md"))
                + "\n\n# 策略\n\n" + Json.PRETTY.toJson(policy);
    }

    @Override
    public List<ToolDefinition> tools() {
        var tools = new ArrayList<ToolDefinition>();
        for (var query : SupervisorTools.queries()) {
            if (QUERIES.contains(query.name())) {
                tools.add(query);
            }
        }
        tools.add(SupervisorTools.decision(Decisions.SIMPLE_SCHEMA));
        return List.copyOf(tools);
    }

    @Override
    public JsonSchema decisionSchema() {
        return Decisions.SIMPLE_SCHEMA;
    }

    @Override
    public String catalog() {
        return SupervisorTools.toolOverview(services.gateway().catalog());
    }

    /**
     * {@code simple-context/1}；摘要与 emergency 只保留目标、脚本与未确认的输入
     */
    @Override
    public JsonObject context(ContextKind kind) {
        var json = new JsonObject();
        json.addProperty("schema", "simple-context/1");
        var unacked = List.copyOf(core.unacked().values());
        if (kind == ContextKind.EMERGENCY) {
            json.addProperty("emergency", true);
            unacked = unacked.subList(Math.max(0, unacked.size() - EMERGENCY_UNACKED), unacked.size());
        }
        if (kind == ContextKind.TURN) {
            json.add("agent", Json.object("status", core.status().name(), "bot", services.botStatus().get()));
        }
        json.addProperty("userGoal", core.userGoal());
        json.addProperty("run", core.run());
        var foreground = core.foreground();
        json.add("script", foreground == null ? JsonNull.INSTANCE : AgentKernel.scriptSummary(foreground));
        json.add("unacked", Json.array(unacked));
        if (kind == ContextKind.TURN) {
            json.add("pendingQuestions", Json.array(core.questions()));
            json.add("progress", Json.tree(core.progress()));
            json.add("recentChat", Json.array(core.recentChat()));
            json.add("budget", services.budget().remaining());
        }
        if (kind != ContextKind.SUMMARY) {
            json.add("coldRefs", Json.array(List.of("trace:1-" + services.trace().lastSequence())));
        }
        json.add("nextChoices", Json.array(foreground == null ? List.of("run", "say", "ask")
                : List.of("run with replace: true", "stop", "say", "ask")));
        return json;
    }

    @Override
    public @Nullable String apply(String type, JsonObject decision, String reason) {
        return switch (type) {
            case "run" -> run(decision.getAsJsonObject("run"), reason);
            case "stop" -> {
                var foreground = core.foreground();
                if (foreground != null) {
                    foreground.cancel(reason);
                }
                if (Json.booleanOr(decision.getAsJsonObject("stop"), "done", false)) {
                    core.finishGoal(reason);
                }
                yield null;
            }
            default -> throw new IllegalStateException("decision " + type + " passed the schema but is not handled");
        };
    }

    /**
     * 保存源码并作为没有 supervisor 的前台 bundle 运行；已有脚本在运行时只在 replace 为 true 时替换它
     */
    private @Nullable String run(JsonObject run, String reason) {
        var current = core.foreground();
        if (current != null && !Json.booleanOr(run, "replace", false)) {
            return "script " + current.id() + " is running; stop it or run with replace: true";
        }
        var fields = Json.parseObject(MANIFEST);
        fields.addProperty("description", reason);
        var manifest = services.workspace().saveDraft(Json.requireString(run, "name"), Json.requireString(run, "source"), fields,
                List.of(), "model");
        var spec = BundleSpec.fromJson(Json.object("primary", manifest.ref().toString()), null);
        if (current != null) {
            // 旧 bundle 结束之后才能取得前台 lease：在它的 BundleDone 中启动
            core.replaceForeground(spec, "replaced by a new script");
            return null;
        }
        return core.startForeground(spec);
    }
}
