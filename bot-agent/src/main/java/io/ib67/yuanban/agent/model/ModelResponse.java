package io.ib67.yuanban.agent.model;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型的回应。
 *
 * @param content      文本内容；没有时为 null
 * @param finishReason 供应商给出的结束原因
 * @param responseId   供应商给出的回应编号
 */
public record ModelResponse(@Nullable String content, List<ToolCall> toolCalls, TokenUsage usage,
                            @Nullable String finishReason, @Nullable String responseId) {
    public ModelResponse {
        toolCalls = List.copyOf(toolCalls);
    }

    /**
     * @param cachedInputTokens 输入中命中供应商缓存的部分
     */
    public record TokenUsage(long inputTokens, long outputTokens, long cachedInputTokens) {
        public long total() {
            return inputTokens + outputTokens;
        }

        public JsonObject toJson() {
            return Json.object("inputTokens", inputTokens, "outputTokens", outputTokens, "cachedInputTokens", cachedInputTokens);
        }
    }

    public JsonObject toJson() {
        return Json.object("content", content, "toolCalls", Json.array(toolCalls.stream().map(ToolCall::toJson).toList()),
                "usage", usage.toJson(), "finishReason", finishReason, "responseId", responseId);
    }

    public static ModelResponse fromJson(JsonObject json) {
        var calls = new ArrayList<ToolCall>();
        json.getAsJsonArray("toolCalls").forEach(call -> calls.add(ToolCall.fromJson(call.getAsJsonObject())));
        var usage = json.getAsJsonObject("usage");
        return new ModelResponse(Json.string(json, "content"), calls, new TokenUsage(usage.get("inputTokens").getAsLong(),
                usage.get("outputTokens").getAsLong(), usage.get("cachedInputTokens").getAsLong()),
                Json.string(json, "finishReason"), Json.string(json, "responseId"));
    }
}
