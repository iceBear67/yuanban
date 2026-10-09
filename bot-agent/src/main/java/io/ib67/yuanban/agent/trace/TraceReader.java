package io.ib67.yuanban.agent.trace;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 读取 JSONL trace，解析 trace 引用。
 * <p>
 * 引用的格式：{@code trace:<序号>} 一条记录；{@code trace:<起始序号>-<结束序号>} 一段记录（两端包含在内）；
 * {@code trace:msg/<消息编号>} 一条 envelope 的 message 记录。
 */
public final class TraceReader {
    private static final Pattern RANGE = Pattern.compile("trace:(\\d+)(?:-(\\d+))?");
    private static final String MESSAGE_PREFIX = "trace:msg/";

    private final List<JsonObject> records;

    private TraceReader(List<JsonObject> records) {
        this.records = records;
    }

    /**
     * @throws UncheckedIOException             不能读取文件
     * @throws com.google.gson.JsonParseException 某一行不是 JSON 对象
     * @throws IllegalStateException            版本不是 {@link TraceStore#VERSION}
     */
    public static TraceReader read(Path file) {
        try (var lines = Files.lines(file)) {
            var records = new ArrayList<JsonObject>();
            lines.filter(line -> !line.isBlank()).forEach(line -> {
                var record = Json.parseObject(line);
                var version = record.get("v").getAsInt();
                if (version != TraceStore.VERSION) {
                    throw new IllegalStateException("trace " + file + " has version " + version + ", expected " + TraceStore.VERSION);
                }
                records.add(record);
            });
            return new TraceReader(List.copyOf(records));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read trace " + file, e);
        }
    }

    public List<JsonObject> records() {
        return records;
    }

    public List<JsonObject> ofKind(TraceKind kind) {
        var wire = kind.wire();
        return records.stream().filter(record -> wire.equals(record.get("kind").getAsString())).toList();
    }

    /**
     * @throws IllegalArgumentException 引用的格式不对，或者没有引用的记录
     */
    public List<JsonObject> resolve(String ref) {
        if (ref.startsWith(MESSAGE_PREFIX)) {
            var id = ref.substring(MESSAGE_PREFIX.length());
            var found = ofKind(TraceKind.MESSAGE).stream()
                    .filter(record -> id.equals(record.getAsJsonObject("envelope").get("id").getAsString()))
                    .toList();
            if (found.isEmpty()) {
                throw new IllegalArgumentException("no message " + id + " in the trace");
            }
            return found;
        }
        var matcher = RANGE.matcher(ref);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("not a trace reference: " + ref);
        }
        var from = Long.parseLong(matcher.group(1));
        var to = matcher.group(2) == null ? from : Long.parseLong(matcher.group(2));
        var found = records.stream().filter(record -> {
            var seq = record.get("seq").getAsLong();
            return seq >= from && seq <= to;
        }).toList();
        if (found.isEmpty()) {
            throw new IllegalArgumentException("no trace records in " + ref);
        }
        return found;
    }
}
