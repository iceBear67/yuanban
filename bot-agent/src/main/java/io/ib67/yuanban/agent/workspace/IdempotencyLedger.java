package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 不可重复副作用的账本：聊天、命令、丢弃、放置等调用带 idempotency key 时，执行之前记为 pending，结束之后记为 done 并保存结果。
 * 脚本从检查点重启时，done 的键直接返回上次的结果，pending 的键说明副作用可能已经发生。每次修改都写回文件（先写临时文件再改名）。
 */
public final class IdempotencyLedger {
    public enum State {
        PENDING,
        DONE
    }

    /**
     * @param result done 时的工具结果；pending 时为 null
     */
    public record Entry(String key, State state, String tool, @Nullable JsonObject result, long at) {
    }

    private final @Nullable Path file;
    private final JsonObject entries;

    private IdempotencyLedger(@Nullable Path file, JsonObject entries) {
        this.file = file;
        this.entries = entries;
    }

    /**
     * 打开文件中的账本；文件不存在时为空账本
     */
    public static IdempotencyLedger open(Path file) {
        try {
            var entries = Files.exists(file) ? Json.parseObject(Files.readString(file)) : new JsonObject();
            return new IdempotencyLedger(file, entries);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read ledger " + file, e);
        }
    }

    /**
     * 不写文件的账本（fixture 测试）
     */
    public static IdempotencyLedger inMemory() {
        return new IdempotencyLedger(null, new JsonObject());
    }

    public synchronized @Nullable Entry get(String key) {
        var entry = entries.getAsJsonObject(key);
        if (entry == null) {
            return null;
        }
        return new Entry(key, State.valueOf(Json.requireString(entry, "state")), Json.requireString(entry, "tool"),
                Json.objectOrNull(entry, "result"), entry.get("at").getAsLong());
    }

    public synchronized void pending(String key, String tool, long at) {
        entries.add(key, Json.object("state", State.PENDING.name(), "tool", tool, "result", null, "at", at));
        save();
    }

    public synchronized void done(String key, String tool, JsonObject result, long at) {
        entries.add(key, Json.object("state", State.DONE.name(), "tool", tool, "result", result, "at", at));
        save();
    }

    /**
     * 脚本自己记录的副作用（{@code api.ledger.mark}）
     */
    public synchronized void mark(String key, JsonObject data, long at) {
        entries.add(key, Json.object("state", State.DONE.name(), "tool", "script", "result", data, "at", at));
        save();
    }

    public synchronized JsonObject snapshot() {
        return entries.deepCopy();
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            var temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, Json.write(entries), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write ledger " + file, e);
        }
    }
}
