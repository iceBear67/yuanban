package io.ib67.yuanban.agent.model;

import com.google.gson.JsonObject;

/**
 * 提供给模型的函数。
 *
 * @param parameters 参数的 JSON Schema
 */
public record ToolDefinition(String name, String description, JsonObject parameters) {
    public ToolDefinition {
        parameters = parameters.deepCopy();
    }

    @Override
    public JsonObject parameters() {
        return parameters.deepCopy();
    }
}
