package io.ib67.yuanban.agent.model;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;

/**
 * 模型发起的一次函数调用。
 *
 * @param arguments 参数的 JSON 文本，原样保存（可能不是合法的 JSON）
 */
public record ToolCall(String id, String name, String arguments) {
    public JsonObject toJson() {
        return Json.object("id", id, "name", name, "arguments", arguments);
    }

    public static ToolCall fromJson(JsonObject json) {
        return new ToolCall(Json.requireString(json, "id"), Json.requireString(json, "name"), Json.requireString(json, "arguments"));
    }
}
