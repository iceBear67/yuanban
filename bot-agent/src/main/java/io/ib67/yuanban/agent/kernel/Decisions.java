package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;

import java.util.List;
import java.util.Set;

/**
 * 模型决策的 schema。决策作为 {@code submit_decision} 函数的参数给出，先按当前模式的 schema 校验，再由 AgentKernel 应用。
 */
public final class Decisions {
    public static final String TOOL_NAME = "submit_decision";
    public static final Set<String> TYPES = Set.of("send", "start", "patch_script", "preflight", "ask", "say", "stop");

    public static final JsonSchema SCHEMA = JsonSchema.parse("""
            {
              "type": "object",
              "required": ["decision", "reason", "refs"],
              "additionalProperties": false,
              "properties": {
                "decision": {"enum": ["send", "start", "patch_script", "preflight", "ask", "say", "stop"]},
                "target": {"type": "string", "maxLength": 128},
                "reason": {"type": "string", "minLength": 1, "maxLength": 2000},
                "refs": {"type": "array", "maxItems": 64, "items": {"type": "string", "maxLength": 128}},
                "budgetImpact": {"type": "string", "maxLength": 500},
                "expected": {"type": "string", "maxLength": 1000},
                "send": {"type": "object", "required": ["type", "payload"], "additionalProperties": false, "properties": {
                  "type": {"enum": ["goal", "focus", "policy", "stop", "user_reply"]},
                  "payload": {"type": "object"},
                  "dedupeKey": {"type": "string", "maxLength": 128},
                  "replyTo": {"type": "string", "maxLength": 64}}},
                "start": {"type": "object", "required": ["primary"], "additionalProperties": false, "properties": {
                  "primary": {"type": "string", "pattern": "^[a-z][a-z0-9-]*@[0-9]+$"},
                  "input": true,
                  "supervisor": {"type": ["string", "null"], "pattern": "^[a-z][a-z0-9-]*@[0-9]+$"},
                  "policy": true,
                  "budget": {"type": "object"},
                  "drainMs": {"type": "integer", "minimum": 0, "maximum": 60000},
                  "onPrimaryFailure": {"enum": ["fault", "replan"]},
                  "onSupervisorFailure": {"enum": ["fault", "fail"]},
                  "replace": {"type": "boolean"},
                  "preflightDone": {"type": "boolean"},
                  "resumeLineage": {"type": "string", "pattern": "^[a-z0-9-]+$", "maxLength": 64}}},
                "patch": {"type": "object", "required": ["module", "source", "manifest", "fixtures"], "additionalProperties": false, "properties": {
                  "module": {"type": "string", "pattern": "^[a-z][a-z0-9-]{0,47}$"},
                  "source": {"type": "string", "minLength": 1, "maxLength": 200000},
                  "manifest": {"type": "object"},
                  "fixtures": {"type": "array", "minItems": 1, "maxItems": 32, "items": {"type": "object"}},
                  "replacePlan": {"type": "string", "maxLength": 64}}},
                "preflight": {"type": "object", "additionalProperties": false, "properties": {
                  "module": {"type": "string", "pattern": "^[a-z][a-z0-9-]*@[0-9]+$"},
                  "input": true}},
                "ask": {"type": "object", "required": ["question"], "additionalProperties": false, "properties": {
                  "question": {"type": "string", "minLength": 1, "maxLength": 2000},
                  "choices": {"type": "array", "maxItems": 16, "items": {"type": "string", "maxLength": 200}}}},
                "say": {"type": "object", "required": ["text"], "additionalProperties": false, "properties": {
                  "text": {"type": "string", "minLength": 1, "maxLength": 2000},
                  "done": {"type": "boolean"}}},
                "stop": {"type": "object", "required": ["scope"], "additionalProperties": false, "properties": {
                  "scope": {"enum": ["foreground", "background", "all", "plan"]},
                  "plan": {"type": "string", "maxLength": 64},
                  "done": {"type": "boolean"}}}
              },
              "allOf": [
                {"if": {"properties": {"decision": {"const": "send"}}}, "then": {"required": ["send", "target"]}},
                {"if": {"properties": {"decision": {"const": "start"}}}, "then": {"required": ["start"]}},
                {"if": {"properties": {"decision": {"const": "patch_script"}}}, "then": {"required": ["patch"]}},
                {"if": {"properties": {"decision": {"const": "preflight"}}}, "then": {"required": ["preflight"]}},
                {"if": {"properties": {"decision": {"const": "ask"}}}, "then": {"required": ["ask"]}},
                {"if": {"properties": {"decision": {"const": "say"}}}, "then": {"required": ["say"]}},
                {"if": {"properties": {"decision": {"const": "stop"}}}, "then": {"required": ["stop"]}}
              ]
            }
            """);

    /**
     * 简单模式的决策：run 提交脚本源码并立即运行，say、ask 与完整模式相同，stop 停止正在运行的脚本
     */
    public static final JsonSchema SIMPLE_SCHEMA = JsonSchema.parse("""
            {
              "type": "object",
              "required": ["decision", "reason"],
              "additionalProperties": false,
              "properties": {
                "decision": {"enum": ["run", "say", "ask", "stop"]},
                "reason": {"type": "string", "minLength": 1, "maxLength": 2000},
                "refs": {"type": "array", "maxItems": 64, "items": {"type": "string", "maxLength": 128}},
                "run": {"type": "object", "required": ["name", "source"], "additionalProperties": false, "properties": {
                  "name": {"type": "string", "pattern": "^[a-z][a-z0-9-]{0,47}$"},
                  "source": {"type": "string", "minLength": 1, "maxLength": 200000},
                  "replace": {"type": "boolean"}}},
                "ask": {"type": "object", "required": ["question"], "additionalProperties": false, "properties": {
                  "question": {"type": "string", "minLength": 1, "maxLength": 2000},
                  "choices": {"type": "array", "maxItems": 16, "items": {"type": "string", "maxLength": 200}}}},
                "say": {"type": "object", "required": ["text"], "additionalProperties": false, "properties": {
                  "text": {"type": "string", "minLength": 1, "maxLength": 2000},
                  "done": {"type": "boolean"}}},
                "stop": {"type": "object", "additionalProperties": false, "properties": {
                  "done": {"type": "boolean"}}}
              },
              "allOf": [
                {"if": {"properties": {"decision": {"const": "run"}}}, "then": {"required": ["run"]}},
                {"if": {"properties": {"decision": {"const": "ask"}}}, "then": {"required": ["ask"]}},
                {"if": {"properties": {"decision": {"const": "say"}}}, "then": {"required": ["say"]}},
                {"if": {"properties": {"decision": {"const": "stop"}}}, "then": {"required": ["stop"]}}
              ]
            }
            """);

    private Decisions() {
    }

    /**
     * 按完整模式的 {@link #SCHEMA} 校验
     *
     * @return 校验错误；没有时为空
     */
    public static List<String> validate(String arguments) {
        return validate(arguments, SCHEMA);
    }

    /**
     * @return 校验错误；没有时为空
     */
    public static List<String> validate(String arguments, JsonSchema schema) {
        JsonObject json;
        try {
            json = Json.parseObject(arguments);
        } catch (RuntimeException e) {
            return List.of("arguments are not a JSON object: " + e.getMessage());
        }
        return schema.validate(json);
    }
}
