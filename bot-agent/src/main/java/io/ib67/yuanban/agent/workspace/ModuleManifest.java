package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.plan.PlanRole;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块契约：actor 接收与发出的消息、需要的能力、可以担任的角色、本地 fallback 与源码 hash。
 *
 * @param entry        入口函数名，签名为 {@code async function entry(api, input)}
 * @param inputSchema  输入的 JSON Schema；没有时不校验
 * @param roles        可以担任的角色，运行中不能改变
 * @param capabilities 需要的工具能力（模式见 {@link io.ib67.yuanban.agent.tool.Capabilities}）
 * @param sourceHash   {@code sha256:<hex>}，加载时检查
 * @param testReport   最近一次 module.test 的报告文件名；没有时为 null
 */
public record ModuleManifest(String name, int version, String entry, @Nullable JsonObject inputSchema, List<PlanRole> roles,
                             List<String> messagesIn, List<String> messagesOut, List<String> capabilities, Fallback fallback,
                             String sourceHash, ModuleStatus status, String description, String createdBy, long createdAt,
                             @Nullable String testReport) {
    /**
     * 模型或脚本保存 draft 时给出的字段的 schema（name、version、sourceHash、status 等由 workspace 填写）
     */
    public static final JsonSchema DRAFT_SCHEMA = JsonSchema.parse("""
            {"type": "object", "additionalProperties": false, "required": ["roles", "capabilities", "description"],
             "properties": {
               "entry": {"type": "string", "pattern": "^[A-Za-z_$][A-Za-z0-9_$]*$", "maxLength": 64},
               "inputSchema": {"type": "object"},
               "roles": {"type": "array", "minItems": 1, "uniqueItems": true,
                 "items": {"enum": ["primary", "supervisor", "preflight", "recovery", "background"]}},
               "messagesIn": {"type": "array", "uniqueItems": true,
                 "items": {"enum": ["goal", "focus", "policy", "stop", "user_reply", "pause", "resume", "drain", "lease", "signal", "bot_event"]}},
               "messagesOut": {"type": "array", "uniqueItems": true,
                 "items": {"enum": ["progress", "attention", "result", "fault", "checkpoint", "question", "observation",
                   "interest", "suggestion", "action_request", "need_preflight"]}},
               "capabilities": {"type": "array", "maxItems": 64, "items": {"type": "string", "pattern": "^[a-zA-Z*][a-zA-Z.*]*$", "maxLength": 64}},
               "fallback": {"enum": ["pause-on-unknown-fault", "stop-on-unknown-fault", "continue"]},
               "description": {"type": "string", "minLength": 1, "maxLength": 2000}
             }}
            """);

    public ModuleManifest {
        roles = List.copyOf(roles);
        messagesIn = List.copyOf(messagesIn);
        messagesOut = List.copyOf(messagesOut);
        capabilities = List.copyOf(capabilities);
        inputSchema = inputSchema == null ? null : inputSchema.deepCopy();
    }

    public ModuleRef ref() {
        return new ModuleRef(name, version);
    }

    public ModuleManifest withStatus(ModuleStatus status, @Nullable String testReport) {
        return new ModuleManifest(name, version, entry, inputSchema, roles, messagesIn, messagesOut, capabilities, fallback,
                sourceHash, status, description, createdBy, createdAt, testReport);
    }

    public JsonObject toJson() {
        return Json.object("name", name, "version", version, "entry", entry, "inputSchema", inputSchema,
                "roles", roles.stream().map(PlanRole::wire).toList(), "messagesIn", messagesIn, "messagesOut", messagesOut,
                "capabilities", capabilities, "fallback", fallback.wire(), "sourceHash", sourceHash, "status", status.wire(),
                "description", description, "createdBy", createdBy, "createdAt", createdAt, "testReport", testReport);
    }

    public static ModuleManifest fromJson(JsonObject json) {
        var roles = new ArrayList<PlanRole>();
        json.getAsJsonArray("roles").forEach(role -> roles.add(PlanRole.fromWire(role.getAsString())));
        return new ModuleManifest(Json.requireString(json, "name"), json.get("version").getAsInt(),
                Json.requireString(json, "entry"), Json.objectOrNull(json, "inputSchema"), roles,
                strings(json.getAsJsonArray("messagesIn")), strings(json.getAsJsonArray("messagesOut")),
                strings(json.getAsJsonArray("capabilities")), Fallback.fromWire(Json.requireString(json, "fallback")),
                Json.requireString(json, "sourceHash"), ModuleStatus.fromWire(Json.requireString(json, "status")),
                Json.requireString(json, "description"), Json.requireString(json, "createdBy"),
                json.get("createdAt").getAsLong(), Json.string(json, "testReport"));
    }

    /**
     * 由 draft 字段创建 manifest
     *
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException draft 字段不符合 {@link #DRAFT_SCHEMA}
     */
    public static ModuleManifest draft(ModuleRef ref, JsonObject fields, String sourceHash, String createdBy, long createdAt) {
        DRAFT_SCHEMA.require(fields, "module manifest");
        var roles = new ArrayList<PlanRole>();
        fields.getAsJsonArray("roles").forEach(role -> roles.add(PlanRole.fromWire(role.getAsString())));
        var inputSchema = Json.objectOrNull(fields, "inputSchema");
        if (inputSchema != null) {
            JsonSchema.of(inputSchema);
        }
        var fallback = Json.string(fields, "fallback");
        var entry = Json.string(fields, "entry");
        return new ModuleManifest(ref.name(), ref.version(), entry == null ? "main" : entry, inputSchema, roles,
                fields.has("messagesIn") ? strings(fields.getAsJsonArray("messagesIn")) : List.of(),
                fields.has("messagesOut") ? strings(fields.getAsJsonArray("messagesOut")) : List.of(),
                strings(fields.getAsJsonArray("capabilities")),
                fallback == null ? Fallback.PAUSE_ON_UNKNOWN_FAULT : Fallback.fromWire(fallback), sourceHash,
                ModuleStatus.DRAFT, Json.requireString(fields, "description"), createdBy, createdAt, null);
    }

    private static List<String> strings(@Nullable JsonArray array) {
        var result = new ArrayList<String>();
        if (array != null) {
            array.forEach(element -> result.add(element.getAsString()));
        }
        return result;
    }
}
