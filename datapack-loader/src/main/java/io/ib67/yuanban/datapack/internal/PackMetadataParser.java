package io.ib67.yuanban.datapack.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ib67.yuanban.datapack.LoaderSettings;
import io.ib67.yuanban.datapack.PackMetadataException;
import io.ib67.yuanban.datapack.pack.FilterRule;
import io.ib67.yuanban.datapack.pack.FormatRange;
import io.ib67.yuanban.datapack.pack.OverlayEntry;
import io.ib67.yuanban.datapack.pack.PackFormat;
import io.ib67.yuanban.datapack.pack.PackMetadata;
import io.ib67.yuanban.datapack.pack.ResourceFilter;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 解析 {@code pack.mcmeta}。只读取 26.2 起的字段，{@code pack_format}、{@code supported_formats}
 * 以及 overlay 条目中的 {@code formats} 忽略。
 */
@ApiStatus.Internal
public final class PackMetadataParser {
    private static final Pattern OVERLAY_DIRECTORY = Pattern.compile("[-_a-zA-Z0-9.]+");
    private static final int MAX_FORMAT_ARRAY_LENGTH = 256;

    private final String packId;
    private final LoaderSettings settings;

    private PackMetadataParser(String packId, LoaderSettings settings) {
        this.packId = packId;
        this.settings = settings;
    }

    public static PackMetadata parse(String packId, JsonElement root, LoaderSettings settings) {
        return new PackMetadataParser(packId, settings).parseRoot(root);
    }

    private PackMetadata parseRoot(JsonElement root) {
        var rootObject = object(root, "");
        var pack = object(required(rootObject, "pack", ""), "pack");
        var description = required(pack, "description", "pack");
        if (!(description.isJsonObject() || description.isJsonArray()
                || description.isJsonPrimitive() && description.getAsJsonPrimitive().isString())) {
            throw error("pack.description", "必须是字符串、对象或数组");
        }
        var formats = formatRange(pack, "pack");
        var features = rootObject.has("features") ? features(object(rootObject.get("features"), "features")) : Set.<Key>of();
        var filter = rootObject.has("filter") ? filter(object(rootObject.get("filter"), "filter")) : ResourceFilter.NONE;
        var overlays = rootObject.has("overlays") ? overlays(object(rootObject.get("overlays"), "overlays")) : List.<OverlayEntry>of();
        return new PackMetadata(description.deepCopy(), formats, features, filter, overlays);
    }

    private FormatRange formatRange(JsonObject owner, String path) {
        var min = format(required(owner, "min_format", path), join(path, "min_format"), 0);
        var max = format(required(owner, "max_format", path), join(path, "max_format"), PackFormat.ANY_MINOR);
        if (min.compareTo(max) > 0) {
            throw error(join(path, "min_format"), "min_format (" + min + ") 大于 max_format (" + max + ")");
        }
        return new FormatRange(min, max);
    }

    private PackFormat format(JsonElement element, String path, int defaultMinor) {
        if (element.isJsonArray()) {
            var array = element.getAsJsonArray();
            if (array.isEmpty() || array.size() > MAX_FORMAT_ARRAY_LENGTH) {
                throw error(path, "数组长度必须在 1 到 " + MAX_FORMAT_ARRAY_LENGTH + " 之间");
            }
            var major = nonNegativeInt(array.get(0), path + "[0]");
            var minor = array.size() > 1 ? nonNegativeInt(array.get(1), path + "[1]") : defaultMinor;
            for (int i = 2; i < array.size(); i++) {
                nonNegativeInt(array.get(i), path + "[" + i + "]");
            }
            return PackFormat.of(major, minor);
        }
        return PackFormat.of(nonNegativeInt(element, path), defaultMinor);
    }

    /**
     * 和原版 {@code Codec.INT} 相同，数字用 {@link Number#intValue()} 转换：小数截断取整。
     */
    private int nonNegativeInt(JsonElement element, String path) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw error(path, "必须是非负整数");
        }
        var value = primitive.getAsNumber().intValue();
        if (value < 0) {
            throw error(path, "必须是非负整数");
        }
        return value;
    }

    private Set<Key> features(JsonObject features) {
        var enabled = array(required(features, "enabled", "features"), "features.enabled");
        var result = new HashSet<Key>();
        for (int i = 0; i < enabled.size(); i++) {
            var path = "features.enabled[" + i + "]";
            var flag = key(string(enabled.get(i), path), path);
            if (!settings.featureFlags().contains(flag)) {
                throw error(path, "未知的特性开关 " + flag.asString());
            }
            result.add(flag);
        }
        return result;
    }

    private ResourceFilter filter(JsonObject filter) {
        var block = array(required(filter, "block", "filter"), "filter.block");
        var rules = new ArrayList<FilterRule>(block.size());
        for (int i = 0; i < block.size(); i++) {
            var path = "filter.block[" + i + "]";
            var rule = object(block.get(i), path);
            rules.add(new FilterRule(pattern(rule, "namespace", path), pattern(rule, "path", path)));
        }
        return new ResourceFilter(rules);
    }

    private @Nullable Pattern pattern(JsonObject owner, String field, String ownerPath) {
        if (!owner.has(field)) {
            return null;
        }
        var path = join(ownerPath, field);
        var regex = string(owner.get(field), path);
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw error(path, "正则表达式无法编译：" + e.getDescription(), e);
        }
    }

    private List<OverlayEntry> overlays(JsonObject overlays) {
        var entries = array(required(overlays, "entries", "overlays"), "overlays.entries");
        var result = new ArrayList<OverlayEntry>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            var path = "overlays.entries[" + i + "]";
            var entry = object(entries.get(i), path);
            var directoryPath = join(path, "directory");
            var directory = string(required(entry, "directory", path), directoryPath);
            if (!OVERLAY_DIRECTORY.matcher(directory).matches()) {
                throw error(directoryPath, "目录名 \"" + directory + "\" 必须匹配 " + OVERLAY_DIRECTORY.pattern());
            }
            result.add(new OverlayEntry(directory, formatRange(entry, path)));
        }
        return result;
    }

    private Key key(String value, String path) {
        try {
            return Key.key(value);
        } catch (InvalidKeyException e) {
            throw error(path, "\"" + value + "\" 不是合法的命名空间 ID", e);
        }
    }

    private JsonElement required(JsonObject owner, String field, String ownerPath) {
        var element = owner.get(field);
        if (element == null) {
            throw error(join(ownerPath, field), "缺少必需字段");
        }
        return element;
    }

    private JsonObject object(JsonElement element, String path) {
        if (!element.isJsonObject()) {
            throw error(path, "必须是对象");
        }
        return element.getAsJsonObject();
    }

    private JsonArray array(JsonElement element, String path) {
        if (!element.isJsonArray()) {
            throw error(path, "必须是数组");
        }
        return element.getAsJsonArray();
    }

    private String string(JsonElement element, String path) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw error(path, "必须是字符串");
        }
        return primitive.getAsString();
    }

    private static String join(String owner, String field) {
        return owner.isEmpty() ? field : owner + "." + field;
    }

    private PackMetadataException error(String path, String message) {
        return new PackMetadataException(packId, path, message);
    }

    private PackMetadataException error(String path, String message, Throwable cause) {
        return new PackMetadataException(packId, path, message, cause);
    }
}
