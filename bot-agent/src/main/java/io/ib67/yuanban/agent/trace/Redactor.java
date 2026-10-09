package io.ib67.yuanban.agent.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.agent.json.Json;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 写入 trace 之前的脱敏：替换为 {@code [REDACTED:<hash 前 12 位>]}，保留 hash 便于比较同一个值。
 * <ul>
 *     <li>键名为 API key、Authorization、password、secret、token 等的字段整体替换；</li>
 *     <li>字符串中的已知秘密（例如配置的 API key）、{@code Bearer} 令牌、{@code sk-} 开头的密钥、URL 中的用户名与密码；</li>
 *     <li>配置的额外模式（例如聊天中输入的登录密码命令）。</li>
 * </ul>
 */
public final class Redactor {
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)^(api[-_]?key|authorization|proxy[-_]?authorization|password|passwd|secret|client[-_]?secret|token|access[-_]?token|refresh[-_]?token|cookie)$");
    private static final List<Pattern> BUILTIN_PATTERNS = List.of(
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=\\-]+"),
            Pattern.compile("sk-[A-Za-z0-9_\\-]{8,}"));
    private static final Pattern URL_USER_INFO = Pattern.compile("([a-zA-Z][a-zA-Z0-9+.\\-]*://)([^/@\\s]+)@");

    private final Set<String> secrets;
    private final List<Pattern> patterns;

    /**
     * @param secrets       要替换的字面值，长度小于 4 的被忽略
     * @param extraPatterns 额外替换的正则表达式，整个匹配被替换
     */
    public Redactor(Collection<String> secrets, Collection<Pattern> extraPatterns) {
        this.secrets = secrets.stream().filter(secret -> secret.length() >= 4).collect(Collectors.toUnmodifiableSet());
        this.patterns = List.copyOf(extraPatterns);
    }

    public static Redactor none() {
        return new Redactor(Set.of(), List.of());
    }

    public JsonElement redact(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            for (var entry : element.getAsJsonObject().entrySet()) {
                var value = entry.getValue();
                if (SENSITIVE_KEY.matcher(entry.getKey()).matches() && !value.isJsonNull()) {
                    result.addProperty(entry.getKey(), mask(Json.write(value)));
                } else {
                    result.add(entry.getKey(), redact(value));
                }
            }
            return result;
        }
        if (element.isJsonArray()) {
            var result = new JsonArray();
            for (var item : element.getAsJsonArray()) {
                result.add(redact(item));
            }
            return result;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(redact(element.getAsString()));
        }
        return element;
    }

    public String redact(String text) {
        var result = text;
        for (var secret : secrets) {
            if (result.contains(secret)) {
                result = result.replace(secret, mask(secret));
            }
        }
        for (var pattern : BUILTIN_PATTERNS) {
            result = replaceAll(pattern, result);
        }
        for (var pattern : patterns) {
            result = replaceAll(pattern, result);
        }
        var matcher = URL_USER_INFO.matcher(result);
        var builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group(1) + mask(matcher.group(2)) + "@"));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String replaceAll(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        var builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(mask(matcher.group())));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    static String mask(String value) {
        return "[REDACTED:" + Json.sha256(value).substring("sha256:".length(), "sha256:".length() + 12) + "]";
    }
}
