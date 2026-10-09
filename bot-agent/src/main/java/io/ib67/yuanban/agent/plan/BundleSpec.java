package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动 PlanBundle 的参数。
 *
 * @param supervisor          为 null 时没有 supervisor
 * @param rules               supervisor 的 fault 按 kind 匹配的处理规则，按顺序取第一条
 * @param drain               primary 结束之后等 supervisor 处理最后一批事件的时间
 * @param onPrimaryFailure    primary 抛出异常或被强制结束时 bundle 的结果：{@code fault} 或 {@code replan}
 * @param onSupervisorFailure supervisor 抛出异常或被强制结束时：{@code fault}（转发给上层，primary 继续）或 {@code fail}（取消 bundle）
 * @param resumeLineage       primary 从这个 lineage 最近的检查点继续，沿用它的幂等账本（同一模块的任意版本）；为 null 时从头开始
 */
public record BundleSpec(ModuleRef primary, JsonElement input, @Nullable ModuleRef supervisor, List<Rule> rules,
                         BudgetLimits budget, Duration drain, String onPrimaryFailure, String onSupervisorFailure,
                         @Nullable String resumeLineage) {
    /**
     * @param on        匹配的 fault kind（{@code stalled}、{@code hazard}、{@code timeout} 等）
     * @param action    {@code replan}、{@code pause}、{@code fault}、{@code fail} 或 {@code preflight}
     * @param preflight action 为 preflight 时运行的模块；为 null 时使用 profile 的 preflightModule
     */
    public record Rule(List<String> on, String action, @Nullable ModuleRef preflight) {
        public Rule {
            on = List.copyOf(on);
        }

        public JsonObject toJson() {
            return Json.object("on", on, "action", action, "preflight", preflight == null ? null : preflight.toString());
        }
    }

    public static final JsonSchema SCHEMA = JsonSchema.parse("""
            {"type": "object", "required": ["primary"], "additionalProperties": false, "properties": {
              "primary": {"type": "string", "pattern": "^[a-z][a-z0-9-]*@[0-9]+$"},
              "input": true,
              "supervisor": {"type": ["string", "null"], "pattern": "^[a-z][a-z0-9-]*@[0-9]+$"},
              "policy": {"oneOf": [{"$ref": "#/$defs/rule"}, {"type": "array", "items": {"$ref": "#/$defs/rule"}, "maxItems": 16}]},
              "budget": {"type": "object"},
              "drainMs": {"type": "integer", "minimum": 0, "maximum": 60000},
              "onPrimaryFailure": {"enum": ["fault", "replan"]},
              "onSupervisorFailure": {"enum": ["fault", "fail"]},
              "resumeLineage": {"type": "string", "pattern": "^[a-z0-9-]+$", "maxLength": 64}
            },
            "$defs": {"rule": {"type": "object", "required": ["on", "action"], "additionalProperties": false, "properties": {
              "on": {"type": "array", "items": {"type": "string", "maxLength": 64}, "minItems": 1},
              "action": {"enum": ["replan", "pause", "fault", "fail", "preflight"]},
              "preflight": {"type": "string", "pattern": "^[a-z][a-z0-9-]*@[0-9]+$"}}}}}
            """);

    /**
     * @param defaultSupervisor JSON 中没有 supervisor 键时使用；键的值为 null 时没有 supervisor
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException 不符合 {@link #SCHEMA}
     */
    public static BundleSpec fromJson(JsonObject json, @Nullable ModuleRef defaultSupervisor) {
        SCHEMA.require(json, "bundle");
        var rules = new ArrayList<Rule>();
        var policy = json.get("policy");
        if (policy != null) {
            for (var element : policy.isJsonArray() ? policy.getAsJsonArray() : List.of(policy)) {
                var rule = element.getAsJsonObject();
                var on = new ArrayList<String>();
                rule.getAsJsonArray("on").forEach(kind -> on.add(kind.getAsString()));
                var preflight = Json.string(rule, "preflight");
                rules.add(new Rule(on, Json.requireString(rule, "action"), preflight == null ? null : ModuleRef.parse(preflight)));
            }
        }
        ModuleRef supervisor;
        if (json.has("supervisor")) {
            var value = Json.string(json, "supervisor");
            supervisor = value == null ? null : ModuleRef.parse(value);
        } else {
            supervisor = defaultSupervisor;
        }
        var input = json.get("input");
        var onPrimary = Json.string(json, "onPrimaryFailure");
        var onSupervisor = Json.string(json, "onSupervisorFailure");
        return new BundleSpec(ModuleRef.parse(Json.requireString(json, "primary")), input == null ? JsonNull.INSTANCE : input,
                supervisor, rules, BudgetLimits.fromJson(Json.objectOrNull(json, "budget")),
                Duration.ofMillis(Json.longOr(json, "drainMs", 3000)), onPrimary == null ? "fault" : onPrimary,
                onSupervisor == null ? "fault" : onSupervisor, Json.string(json, "resumeLineage"));
    }

    public JsonObject toJson() {
        return Json.object("primary", primary.toString(), "input", input, "supervisor", supervisor == null ? null : supervisor.toString(),
                "policy", Json.array(rules.stream().map(Rule::toJson).toList()), "budget", budget.toJson(),
                "drainMs", drain.toMillis(), "onPrimaryFailure", onPrimaryFailure, "onSupervisorFailure", onSupervisorFailure,
                "resumeLineage", resumeLineage);
    }
}
