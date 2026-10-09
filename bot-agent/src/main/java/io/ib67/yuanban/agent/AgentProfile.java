package io.ib67.yuanban.agent;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.tool.Capabilities;
import io.ib67.yuanban.agent.tool.ToolPolicy;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * agent 的人格与策略。人格只决定关注什么与空闲时做什么，不改变安全限制。原始 JSON 也作为模型上下文的一部分。
 *
 * @param mode                         {@code full}：supervisor、background plan、preflight 与模块测试；
 *                                     {@code simple}：模型为每条指令直接编写脚本并运行，忽略 persona、initiative、backgroundPlans、
 *                                     allowedCapabilities、maxLeaseMs、maxIdleActions、defaultSupervisor、preflightModule 与 forcePreflight
 * @param persona                      给模型看的人格说明
 * @param initiative                   主动性：{@code passive}、{@code curious}、{@code active}
 * @param backgroundPlans              允许启动的 background plan
 * @param allowedCapabilities          background plan 可以使用的工具能力（在空闲窗口中取得 lease 之后）
 * @param maxLease                     background plan 一次 lease 的最长时间（配置中的 {@code maxLeaseMs}）
 * @param maxIdleActions               一个空闲窗口中最多授予 background plan 的 lease 数
 * @param neverAutonomous              background plan 永远不能使用的工具
 * @param defaultSupervisor            启动 bundle 时没有指定 supervisor 时使用的模块；为 null 时没有 supervisor
 * @param preflightModule              {@code preflight} 决策没有指定模块时使用的模块
 * @param forcePreflight               启动每个前台 bundle 之前先运行 preflight
 * @param memoryWrites                 脚本写入记忆的处理：{@code allow}、{@code deny}
 * @param saveScriptSource             是否把脚本源码写入 trace
 * @param supervisorResponseTimeout    supervisor 回应 attention、fault、question 的时限，超过时执行模块声明的 fallback
 * @param heartbeat                    前台 bundle 运行时定期唤醒模型的间隔；{@link Duration#ZERO} 表示不唤醒
 * @param reconnect                    断线之后自动重新连接；连接时等待 {@code delay}，最多 {@code maxAttempts} 次
 * @param inGameNotify                 发到游戏聊天中的通知种类（只发送短文本）
 */
public record AgentProfile(String name, String mode, String persona, String initiative, List<ModuleRef> backgroundPlans,
                           List<String> allowedCapabilities, Duration maxLease, int maxIdleActions,
                           List<String> neverAutonomous, @Nullable ModuleRef defaultSupervisor,
                           @Nullable ModuleRef preflightModule, boolean forcePreflight, String memoryWrites,
                           boolean saveScriptSource, Duration supervisorResponseTimeout, Duration heartbeat,
                           Reconnect reconnect, List<String> inGameNotify, ToolPolicy policy, JsonObject raw) {
    public record Reconnect(boolean enabled, int maxAttempts, Duration delay) {
    }

    public static final JsonSchema SCHEMA = JsonSchema.parse("""
            {"type": "object", "additionalProperties": false, "properties": {
              "name": {"type": "string", "maxLength": 64},
              "mode": {"enum": ["full", "simple"]},
              "persona": {"type": "string", "maxLength": 4000},
              "initiative": {"enum": ["passive", "curious", "active"]},
              "backgroundPlans": {"type": "array", "items": {"type": "string"}},
              "allowedCapabilities": {"type": "array", "items": {"type": "string"}},
              "maxLeaseMs": {"type": "integer", "minimum": 100, "maximum": 60000},
              "maxIdleActions": {"type": "integer", "minimum": 0, "maximum": 1000},
              "neverAutonomous": {"type": "array", "items": {"type": "string"}},
              "defaultSupervisor": {"type": ["string", "null"]},
              "preflightModule": {"type": ["string", "null"]},
              "forcePreflight": {"type": "boolean"},
              "external": {"type": "object", "additionalProperties": {"enum": ["allow", "deny"]}},
              "allowedCommands": {"type": "array", "items": {"type": "string"}},
              "chatPerMinute": {"type": "integer", "minimum": 0, "maximum": 120},
              "unsafe": {"type": "boolean"},
              "dryRun": {"type": "boolean"},
              "memoryWrites": {"enum": ["allow", "deny"]},
              "saveScriptSource": {"type": "boolean"},
              "maxMoveDistance": {"type": "number", "minimum": 1, "maximum": 256},
              "interactionsPerSecond": {"type": "integer", "minimum": 1, "maximum": 100},
              "interactionsPerBlockPerMinute": {"type": "integer", "minimum": 1, "maximum": 1200},
              "supervisorResponseTimeoutMs": {"type": "integer", "minimum": 1000},
              "heartbeatMs": {"type": "integer", "minimum": 0},
              "reconnect": {"type": "object", "additionalProperties": false, "properties": {
                "enabled": {"type": "boolean"}, "maxAttempts": {"type": "integer", "minimum": 0}, "delayMs": {"type": "integer", "minimum": 0}}},
              "inGameNotify": {"type": "array", "items": {"type": "string"}}
            }}
            """);

    private static final JsonObject DEFAULTS = Json.parseObject("""
            {
              "name": "default",
              "mode": "full",
              "persona": "一个谨慎、乐于帮忙的 Minecraft 玩家。",
              "initiative": "curious",
              "backgroundPlans": ["curiosity-observer@1", "idle-behavior@1"],
              "allowedCapabilities": ["observe.*", "locomotion.lookAt"],
              "maxLeaseMs": 2000,
              "maxIdleActions": 3,
              "neverAutonomous": ["command", "drop", "dropSlot", "chat", "configure", "unsafe.send", "attack"],
              "defaultSupervisor": "movement-watchdog@1",
              "preflightModule": "terrain-preflight@1",
              "forcePreflight": false,
              "external": {"external.chat": "allow", "external.command": "allow", "external.drop": "allow",
                           "external.dropSlot": "allow", "external.configure": "allow"},
              "allowedCommands": [],
              "chatPerMinute": 6,
              "unsafe": false,
              "dryRun": false,
              "memoryWrites": "allow",
              "saveScriptSource": true,
              "maxMoveDistance": 64,
              "interactionsPerSecond": 8,
              "interactionsPerBlockPerMinute": 30,
              "supervisorResponseTimeoutMs": 60000,
              "heartbeatMs": 300000,
              "reconnect": {"enabled": false, "maxAttempts": 3, "delayMs": 5000},
              "inGameNotify": ["need_user", "say", "recovery_exhausted", "fault", "result"]
            }
            """);

    public static AgentProfile defaults() {
        return fromJson(new JsonObject());
    }

    /**
     * 与默认值合并之后解析
     *
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException 不符合 {@link #SCHEMA}
     */
    public static AgentProfile fromJson(JsonObject json) {
        SCHEMA.require(json, "agent profile");
        var merged = DEFAULTS.deepCopy();
        for (var entry : json.entrySet()) {
            merged.add(entry.getKey(), entry.getValue().deepCopy());
        }
        var external = new HashMap<String, ToolPolicy.Risk>();
        for (var entry : merged.getAsJsonObject("external").entrySet()) {
            external.put(entry.getKey(), ToolPolicy.Risk.valueOf(entry.getValue().getAsString().toUpperCase(Locale.ROOT)));
        }
        var neverAutonomous = strings(merged, "neverAutonomous");
        var policy = new ToolPolicy(Map.copyOf(external), merged.get("unsafe").getAsBoolean(), Capabilities.of(neverAutonomous),
                merged.get("chatPerMinute").getAsInt(), strings(merged, "allowedCommands"),
                merged.get("interactionsPerSecond").getAsInt(), merged.get("interactionsPerBlockPerMinute").getAsInt(),
                merged.get("maxMoveDistance").getAsDouble(), merged.get("dryRun").getAsBoolean());
        var reconnect = merged.getAsJsonObject("reconnect");
        return new AgentProfile(merged.get("name").getAsString(), merged.get("mode").getAsString(), merged.get("persona").getAsString(),
                merged.get("initiative").getAsString(),
                strings(merged, "backgroundPlans").stream().map(ModuleRef::parse).toList(),
                strings(merged, "allowedCapabilities"), Duration.ofMillis(merged.get("maxLeaseMs").getAsLong()),
                merged.get("maxIdleActions").getAsInt(), neverAutonomous, ref(merged, "defaultSupervisor"),
                ref(merged, "preflightModule"), merged.get("forcePreflight").getAsBoolean(),
                merged.get("memoryWrites").getAsString(), merged.get("saveScriptSource").getAsBoolean(),
                Duration.ofMillis(merged.get("supervisorResponseTimeoutMs").getAsLong()),
                Duration.ofMillis(merged.get("heartbeatMs").getAsLong()),
                new Reconnect(Json.booleanOr(reconnect, "enabled", false), (int) Json.longOr(reconnect, "maxAttempts", 3),
                        Duration.ofMillis(Json.longOr(reconnect, "delayMs", 5000))),
                strings(merged, "inGameNotify"), policy, merged);
    }

    /**
     * 简单模式：模型为每条指令直接编写脚本并运行，没有 supervisor、background plan 与 preflight
     */
    public boolean simple() {
        return mode.equals("simple");
    }

    public Capabilities backgroundCapabilities() {
        return Capabilities.of(allowedCapabilities).without(policy.neverAutonomous());
    }

    private static @Nullable ModuleRef ref(JsonObject json, String key) {
        var value = Json.string(json, key);
        return value == null ? null : ModuleRef.parse(value);
    }

    private static List<String> strings(JsonObject json, String key) {
        var result = new ArrayList<String>();
        json.getAsJsonArray(key).forEach(element -> result.add(element.getAsString()));
        return List.copyOf(result);
    }
}
