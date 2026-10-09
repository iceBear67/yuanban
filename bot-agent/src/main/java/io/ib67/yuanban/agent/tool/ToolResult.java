package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

/**
 * 一次工具调用的统一结果。{@code ok} 只表示 Java 调用与 observer 得到了预期结论，不表示服务端执行了游戏效果。
 *
 * @param reason      分类之下的原因：取消原因（{@code NO_PATH} 等）、网关取消的原因、校验错误的说明
 * @param actionId    改变世界的调用的动作编号 {@code a-<n>}；其余为 null
 * @param value       调用的值：读取的快照、observer 的结论；失败时为 null
 * @param observation 动作结束之后玩家的位置等简短观察；没有时为 null
 * @param error       原始异常的类型与消息；没有时为 null
 * @param stackRef    trace 中堆栈记录的编号；没有时为 null
 */
public record ToolResult(boolean ok, ResultKind kind, @Nullable String reason, @Nullable String actionId, long elapsedMs,
                         @Nullable JsonElement value, @Nullable JsonObject observation, @Nullable String error,
                         @Nullable String stackRef) {
    public static ToolResult ok(@Nullable JsonElement value) {
        return new ToolResult(true, ResultKind.OK, null, null, 0, value, null, null, null);
    }

    public static ToolResult of(ResultKind kind, @Nullable String reason) {
        return new ToolResult(kind.ok(), kind, reason, null, 0, null, null, null, null);
    }

    public static ToolResult of(ResultKind kind, @Nullable String reason, @Nullable JsonElement value) {
        return new ToolResult(kind.ok(), kind, reason, null, 0, value, null, null, null);
    }

    public ToolResult withTiming(@Nullable String actionId, long elapsedMs) {
        return new ToolResult(ok, kind, reason, actionId, elapsedMs, value, observation, error, stackRef);
    }

    public ToolResult withObservation(@Nullable JsonObject observation) {
        return new ToolResult(ok, kind, reason, actionId, elapsedMs, value, observation, error, stackRef);
    }

    public ToolResult withError(Throwable throwable, @Nullable String stackRef) {
        return new ToolResult(ok, kind, reason, actionId, elapsedMs, value, observation,
                throwable.getClass().getName() + ": " + throwable.getMessage(), stackRef);
    }

    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("ok", ok);
        json.addProperty("kind", kind.name());
        json.addProperty("reason", reason);
        json.addProperty("actionId", actionId);
        json.addProperty("elapsedMs", elapsedMs);
        json.add("value", value == null ? JsonNull.INSTANCE : value.deepCopy());
        json.add("observation", observation == null ? JsonNull.INSTANCE : observation.deepCopy());
        json.addProperty("error", error);
        json.addProperty("stackRef", stackRef);
        return json;
    }

    public static ToolResult fromJson(JsonObject json) {
        var value = json.get("value");
        var observation = json.get("observation");
        return new ToolResult(json.get("ok").getAsBoolean(), ResultKind.valueOf(Json.requireString(json, "kind")),
                Json.string(json, "reason"), Json.string(json, "actionId"), json.get("elapsedMs").getAsLong(),
                value == null || value.isJsonNull() ? null : value,
                observation == null || observation.isJsonNull() ? null : observation.getAsJsonObject(),
                Json.string(json, "error"), Json.string(json, "stackRef"));
    }
}
