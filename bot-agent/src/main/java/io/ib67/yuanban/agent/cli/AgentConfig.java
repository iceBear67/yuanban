package io.ib67.yuanban.agent.cli;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentOptions;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.ingress.GameChatOptions;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.json.JsonSchema;
import io.ib67.yuanban.agent.model.OpenAiChatProvider;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.transport.TcpTransport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 命令行使用的配置文件（JSON）：
 * <pre>{@code
 * {
 *   "server": {"host": "127.0.0.1", "port": 25565},
 *   "username": "agent",
 *   "workspace": "agent-workspace",
 *   "traceDirectory": "agent-traces",
 *   "profile": "profile.json" 或 profile 对象,
 *   "model": {"baseUrl": null, "apiKeyEnv": "OPENAI_API_KEY", "model": "gpt-5", "contextWindow": 200000,
 *             "maxOutputTokens": 4096, "temperature": null, "timeoutSeconds": 120},
 *   "budget": {"actions": 5000, "modelCalls": 500, "tokens": 5000000, "wallTimeMs": null, "ticks": null},
 *   "chat": {"enabled": true, "prefix": "!agent", "maxLength": 256, "commandsPerMinute": 10, "notifyInGame": true},
 *   "redactPatterns": ["(?i)/login \\S+"]
 * }
 * }</pre>
 * 相对路径相对于配置文件所在的目录。API key 从 {@code model.apiKeyEnv} 指定的环境变量读取，不写在配置文件中。
 */
public final class AgentConfig {
    public static final JsonSchema SCHEMA = JsonSchema.parse("""
            {"type": "object", "required": ["server", "username", "model"], "additionalProperties": false, "properties": {
              "server": {"type": "object", "required": ["host", "port"], "additionalProperties": false, "properties": {
                "host": {"type": "string"}, "port": {"type": "integer", "minimum": 1, "maximum": 65535}}},
              "username": {"type": "string", "pattern": "^[A-Za-z0-9_]{1,16}$"},
              "workspace": {"type": "string"},
              "traceDirectory": {"type": "string"},
              "profile": {"type": ["string", "object"]},
              "model": {"type": "object", "required": ["model"], "additionalProperties": false, "properties": {
                "baseUrl": {"type": ["string", "null"]}, "apiKeyEnv": {"type": "string"}, "model": {"type": "string"},
                "contextWindow": {"type": "integer", "minimum": 1000}, "maxOutputTokens": {"type": "integer", "minimum": 64},
                "temperature": {"type": ["number", "null"]}, "timeoutSeconds": {"type": "integer", "minimum": 1}}},
              "budget": {"type": "object"},
              "chat": {"type": "object", "additionalProperties": false, "properties": {
                "enabled": {"type": "boolean"}, "prefix": {"type": "string", "minLength": 1},
                "maxLength": {"type": "integer", "minimum": 1}, "commandsPerMinute": {"type": "integer", "minimum": 1}, "notifyInGame": {"type": "boolean"}}},
              "redactPatterns": {"type": "array", "items": {"type": "string"}}
            }}
            """);

    private AgentConfig() {
    }

    /**
     * @throws UncheckedIOException                                 不能读取配置或 profile 文件
     * @throws io.ib67.yuanban.agent.json.SchemaViolationException 配置不符合 {@link #SCHEMA}
     * @throws IllegalStateException                                API key 的环境变量没有设置
     */
    public static AgentOptions load(Path file) {
        var json = read(file);
        SCHEMA.require(json, "agent config");
        var base = file.toAbsolutePath().getParent();
        var server = json.getAsJsonObject("server");
        var username = Json.requireString(json, "username");
        var model = json.getAsJsonObject("model");
        var keyEnv = Json.string(model, "apiKeyEnv");
        keyEnv = keyEnv == null ? "OPENAI_API_KEY" : keyEnv;
        var apiKey = System.getenv(keyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("environment variable " + keyEnv + " is not set");
        }
        var provider = new OpenAiChatProvider(OpenAiChatProvider.Options.builder()
                .baseUrl(Json.string(model, "baseUrl"))
                .apiKey(apiKey)
                .model(Json.requireString(model, "model"))
                .contextWindow((int) Json.longOr(model, "contextWindow", 128_000))
                .maxOutputTokens((int) Json.longOr(model, "maxOutputTokens", 4096))
                .temperature(model.has("temperature") && !model.get("temperature").isJsonNull() ? model.get("temperature").getAsDouble() : null)
                .timeout(Duration.ofSeconds(Json.longOr(model, "timeoutSeconds", 120)))
                .build());
        var profileElement = json.get("profile");
        AgentProfile profile;
        if (profileElement == null) {
            profile = AgentProfile.defaults();
        } else if (profileElement.isJsonObject()) {
            profile = AgentProfile.fromJson(profileElement.getAsJsonObject());
        } else {
            profile = AgentProfile.fromJson(read(base.resolve(profileElement.getAsString())));
        }
        var chat = Json.objectOrNull(json, "chat");
        var chatOptions = GameChatOptions.builder();
        if (chat != null) {
            chatOptions.enabled(Json.booleanOr(chat, "enabled", true))
                    .prefix(Json.string(chat, "prefix") == null ? "!agent" : Json.requireString(chat, "prefix"))
                    .maxLength((int) Json.longOr(chat, "maxLength", 256))
                    .commandsPerMinute((int) Json.longOr(chat, "commandsPerMinute", 10))
                    .notifyInGame(Json.booleanOr(chat, "notifyInGame", true));
        }
        var patterns = new ArrayList<Pattern>();
        var patternArray = json.getAsJsonArray("redactPatterns");
        if (patternArray != null) {
            patternArray.forEach(element -> patterns.add(Pattern.compile(element.getAsString())));
        }
        var options = AgentOptions.builder()
                .bot(BotOptions.builder().username(username)
                        .transport(new TcpTransport(Json.requireString(server, "host"), server.get("port").getAsInt())).build())
                .model(provider)
                .profile(profile)
                .workspace(base.resolve(Json.string(json, "workspace") == null ? "agent-workspace" : Json.requireString(json, "workspace")))
                .traceDirectory(base.resolve(Json.string(json, "traceDirectory") == null ? "agent-traces" : Json.requireString(json, "traceDirectory")))
                .chat(chatOptions.build())
                .secrets(List.of(apiKey))
                .redactPatterns(patterns);
        var budget = Json.objectOrNull(json, "budget");
        if (budget != null) {
            options.budget(BudgetLimits.fromJson(budget));
        }
        return options.build();
    }

    private static JsonObject read(Path file) {
        try {
            return Json.parseObject(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
