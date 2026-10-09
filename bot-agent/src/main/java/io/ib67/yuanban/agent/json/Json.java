package io.ib67.yuanban.agent.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 本模块全部 JSON 的读写。解析使用严格模式；写出时遇到 NaN 与无穷抛出异常。
 */
public final class Json {
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
    public static final Gson PRETTY = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private Json() {
    }

    /**
     * @throws JsonParseException 不是合法的 JSON，或者其后还有内容
     */
    public static JsonElement parse(String text) {
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            var element = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                throw new JsonParseException("trailing content after JSON value");
            }
            return element;
        } catch (IOException e) {
            throw new JsonParseException(e);
        }
    }

    /**
     * @throws JsonParseException 不是 JSON 对象
     */
    public static JsonObject parseObject(String text) {
        var element = parse(text);
        if (!element.isJsonObject()) {
            throw new JsonParseException("expected a JSON object, got " + element);
        }
        return element.getAsJsonObject();
    }

    public static String write(@Nullable Object value) {
        return GSON.toJson(value);
    }

    public static String write(@Nullable JsonElement value) {
        return GSON.toJson(value == null ? JsonNull.INSTANCE : value);
    }

    public static JsonElement tree(@Nullable Object value) {
        return GSON.toJsonTree(value);
    }

    public static <T> T read(JsonElement element, Class<T> type) {
        return GSON.fromJson(element, type);
    }

    public static JsonObject object() {
        return new JsonObject();
    }

    /**
     * 按键值对构造对象，值经过 {@link #tree} 转换
     *
     * @param keyValues 键与值交替排列
     */
    public static JsonObject object(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs expected");
        }
        var object = new JsonObject();
        for (var i = 0; i < keyValues.length; i += 2) {
            var value = keyValues[i + 1];
            object.add((String) keyValues[i], value instanceof JsonElement element ? element : tree(value));
        }
        return object;
    }

    public static JsonArray array(Iterable<?> values) {
        var array = new JsonArray();
        for (var value : values) {
            array.add(value instanceof JsonElement element ? element : tree(value));
        }
        return array;
    }

    /**
     * UTF-8 编码之后的字节数
     */
    public static int byteLength(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    public static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static @Nullable String string(JsonObject object, String key) {
        var element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    public static String requireString(JsonObject object, String key) {
        var value = string(object, key);
        if (value == null) {
            throw new JsonParseException("missing string field " + key);
        }
        return value;
    }

    public static @Nullable JsonObject objectOrNull(JsonObject object, String key) {
        var element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsJsonObject();
    }

    public static long longOr(JsonObject object, String key, long fallback) {
        var element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsLong();
    }

    public static double doubleOr(JsonObject object, String key, double fallback) {
        var element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsDouble();
    }

    public static boolean booleanOr(JsonObject object, String key, boolean fallback) {
        var element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }
}
