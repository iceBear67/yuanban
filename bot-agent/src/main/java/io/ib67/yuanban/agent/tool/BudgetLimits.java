package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import org.jetbrains.annotations.Nullable;

/**
 * 预算上限，为 null 的项不限制。
 *
 * @param actions    改变世界的工具调用数
 * @param ticks      bot 执行的 tick 数
 * @param wallTimeMs 经过的时间
 * @param modelCalls 模型回合数
 * @param tokens     模型输入与输出的 token 总数
 */
public record BudgetLimits(@Nullable Long actions, @Nullable Long ticks, @Nullable Long wallTimeMs,
                           @Nullable Long modelCalls, @Nullable Long tokens) {
    public static final BudgetLimits UNLIMITED = new BudgetLimits(null, null, null, null, null);

    public static final JsonSchema SCHEMA = JsonSchema.parse("""
            {"type": "object", "additionalProperties": false, "properties": {
              "actions": {"type": "integer", "minimum": 0}, "ticks": {"type": "integer", "minimum": 0},
              "wallTimeMs": {"type": "integer", "minimum": 0}, "modelCalls": {"type": "integer", "minimum": 0},
              "tokens": {"type": "integer", "minimum": 0}}}
            """);

    /**
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException 不符合 schema（包括负数）
     */
    public static BudgetLimits fromJson(@Nullable JsonObject json) {
        if (json == null) {
            return UNLIMITED;
        }
        SCHEMA.require(json, "budget");
        return new BudgetLimits(value(json, "actions"), value(json, "ticks"), value(json, "wallTimeMs"),
                value(json, "modelCalls"), value(json, "tokens"));
    }

    public JsonObject toJson() {
        return Json.object("actions", actions, "ticks", ticks, "wallTimeMs", wallTimeMs, "modelCalls", modelCalls,
                "tokens", tokens);
    }

    private static @Nullable Long value(JsonObject json, String key) {
        var element = json.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsLong();
    }
}
