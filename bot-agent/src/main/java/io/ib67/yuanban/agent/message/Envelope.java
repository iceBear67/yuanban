package io.ib67.yuanban.agent.message;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

/**
 * 跨越脚本、PlanBundle 与 AgentKernel 的消息。
 *
 * @param id          agent 内唯一的编号，{@code msg-<序号>}
 * @param run         所属的运行：前台目标为 {@code run-<n>}，background plan 为 {@code run-bg}
 * @param sequence    agent 内单调递增的序号；同一个 run 的子计划共用这个计数
 * @param schema      envelope 的版本，见 {@link #SCHEMA_VERSION}
 * @param source      发出者：{@code script:<模块>@<版本>#<计划>}、{@code bundle:<编号>}、{@code kernel}、{@code user:<来源>}、{@code gateway}
 * @param topic       主题，用于去重与过滤；没有时为 null
 * @param requiresAck 接收方必须确认：持久化的事件由 AgentKernel 确认，有序控制消息在脚本取出时确认
 * @param dedupeKey   去重键：同一个键的消息只保留序号最大的一条，序号更小的消息到达时被丢弃
 * @param replyTo     回答的消息编号；不是回答时为 null
 * @param at          创建时间（epoch 毫秒）
 */
public record Envelope(String id, String run, long sequence, int schema, MessageType type, Priority priority,
                       String source, @Nullable String topic, JsonObject payload, boolean requiresAck,
                       @Nullable String dedupeKey, @Nullable String replyTo, long at) {
    public static final int SCHEMA_VERSION = 1;

    public Envelope {
        payload = payload.deepCopy();
    }

    @Override
    public JsonObject payload() {
        return payload.deepCopy();
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("run", run);
        json.addProperty("sequence", sequence);
        json.addProperty("schema", schema);
        json.addProperty("type", type.wire());
        json.addProperty("priority", priority.wire());
        json.addProperty("source", source);
        json.addProperty("topic", topic);
        json.add("payload", payload.deepCopy());
        json.addProperty("requiresAck", requiresAck);
        json.addProperty("dedupeKey", dedupeKey);
        json.addProperty("replyTo", replyTo);
        json.addProperty("at", at);
        return json;
    }

    public static Envelope fromJson(JsonObject json) {
        return new Envelope(Json.requireString(json, "id"), Json.requireString(json, "run"),
                json.get("sequence").getAsLong(), json.get("schema").getAsInt(),
                MessageType.fromWire(Json.requireString(json, "type")),
                Priority.fromWire(Json.requireString(json, "priority")), Json.requireString(json, "source"),
                Json.string(json, "topic"), json.getAsJsonObject("payload"), json.get("requiresAck").getAsBoolean(),
                Json.string(json, "dedupeKey"), Json.string(json, "replyTo"), json.get("at").getAsLong());
    }
}
