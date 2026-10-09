package io.ib67.yuanban.agent.model;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 与供应商无关的模型消息。
 */
public sealed interface ModelMessage {
    String text();

    JsonObject toJson();

    record System(String text) implements ModelMessage {
        @Override
        public JsonObject toJson() {
            return Json.object("role", "system", "text", text);
        }
    }

    record User(String text) implements ModelMessage {
        @Override
        public JsonObject toJson() {
            return Json.object("role", "user", "text", text);
        }
    }

    /**
     * @param content 文本内容；只有工具调用时为 null
     */
    record Assistant(@Nullable String content, List<ToolCall> toolCalls) implements ModelMessage {
        public Assistant {
            toolCalls = List.copyOf(toolCalls);
        }

        @Override
        public String text() {
            var builder = new StringBuilder(content == null ? "" : content);
            for (var call : toolCalls) {
                builder.append('\n').append(call.name()).append(' ').append(call.arguments());
            }
            return builder.toString();
        }

        @Override
        public JsonObject toJson() {
            return Json.object("role", "assistant", "content", content,
                    "toolCalls", Json.array(toolCalls.stream().map(ToolCall::toJson).toList()));
        }
    }

    record ToolResult(String callId, String text) implements ModelMessage {
        @Override
        public JsonObject toJson() {
            return Json.object("role", "tool", "callId", callId, "text", text);
        }
    }

    static ModelMessage fromJson(JsonObject json) {
        return switch (Json.requireString(json, "role")) {
            case "system" -> new System(Json.requireString(json, "text"));
            case "user" -> new User(Json.requireString(json, "text"));
            case "assistant" -> {
                var calls = new ArrayList<ToolCall>();
                json.getAsJsonArray("toolCalls").forEach(call -> calls.add(ToolCall.fromJson(call.getAsJsonObject())));
                yield new Assistant(Json.string(json, "content"), calls);
            }
            case "tool" -> new ToolResult(Json.requireString(json, "callId"), Json.requireString(json, "text"));
            default -> throw new IllegalArgumentException("unknown message role in " + json);
        };
    }
}
