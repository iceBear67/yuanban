package io.ib67.yuanban.datapack.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.datapack.TagFileException;
import io.ib67.yuanban.datapack.tag.TagEntry;
import io.ib67.yuanban.datapack.topic.Resource;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析标签文件，格式和原版 {@code TagFile} 相同。未知字段忽略。
 */
@ApiStatus.Internal
public final class TagFileParser {
    private static final char TAG_PREFIX = '#';

    private final Resource resource;

    private TagFileParser(Resource resource) {
        this.resource = resource;
    }

    public static ParsedTagFile parse(Resource resource) {
        JsonElement json;
        try {
            json = resource.readJson();
        } catch (IOException e) {
            throw new TagFileException("无法读取标签文件 " + resource, e);
        } catch (JsonParseException e) {
            throw new TagFileException("标签文件 " + resource + " 的 JSON 语法错误：" + e.getMessage(), e);
        }
        return new TagFileParser(resource).parseRoot(json);
    }

    private ParsedTagFile parseRoot(JsonElement json) {
        if (!json.isJsonObject()) {
            throw error("", "必须是对象");
        }
        var root = json.getAsJsonObject();
        var values = root.get("values");
        if (values == null) {
            throw error("values", "缺少必需字段");
        }
        if (!values.isJsonArray()) {
            throw error("values", "必须是数组");
        }
        var replace = root.has("replace") && bool(root.get("replace"), "replace");
        var array = values.getAsJsonArray();
        var entries = new ArrayList<TagEntry>(array.size());
        for (int i = 0; i < array.size(); i++) {
            entries.add(entry(array.get(i), "values[" + i + "]"));
        }
        return new ParsedTagFile(replace, List.copyOf(entries));
    }

    private TagEntry entry(JsonElement element, String path) {
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            var id = object.get("id");
            if (id == null) {
                throw error(path + ".id", "缺少必需字段");
            }
            var required = !object.has("required") || bool(object.get("required"), path + ".required");
            return reference(string(id, path + ".id"), required, path + ".id");
        }
        return reference(string(element, path), true, path);
    }

    private TagEntry reference(String value, boolean required, String path) {
        var tag = !value.isEmpty() && value.charAt(0) == TAG_PREFIX;
        var id = tag ? value.substring(1) : value;
        try {
            return new TagEntry(Key.key(id), tag, required);
        } catch (InvalidKeyException e) {
            throw new TagFileException("标签文件 " + resource + " 中 " + path + " 的 \"" + value + "\" 不是合法的命名空间 ID", e);
        }
    }

    private String string(JsonElement element, String path) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw error(path, "必须是字符串");
        }
        return primitive.getAsString();
    }

    private boolean bool(JsonElement element, String path) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw error(path, "必须是布尔值");
        }
        return primitive.getAsBoolean();
    }

    private TagFileException error(String path, String message) {
        return new TagFileException("标签文件 " + resource + " 中 " + (path.isEmpty() ? "<根>" : path) + " 不合法：" + message);
    }

    public record ParsedTagFile(boolean replace, List<TagEntry> entries) {
    }
}
