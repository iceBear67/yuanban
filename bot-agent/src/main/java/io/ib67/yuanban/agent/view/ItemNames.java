package io.ib67.yuanban.agent.view;

import com.google.gson.JsonParser;
import net.minestom.data.MinestomData;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 物品编号与名称的对照，取自 {@code net.minestom:data} 的 {@code item.json}（bot 读取物品数据的同一个文件）。
 */
public final class ItemNames {
    private static final String[] NAMES;
    private static final Map<String, Integer> IDS = new HashMap<>();

    static {
        var stream = Objects.requireNonNull(MinestomData.resource("item.json"), "net.minestom:data has no item.json");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            NAMES = new String[json.size()];
            for (var entry : json.entrySet()) {
                var id = entry.getValue().getAsJsonObject().get("id").getAsInt();
                if (id < 0 || id >= NAMES.length || NAMES[id] != null) {
                    throw new IllegalStateException("item " + entry.getKey() + " has an invalid id " + id + " in item.json");
                }
                NAMES[id] = entry.getKey();
                IDS.put(entry.getKey(), id);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read item.json from net.minestom:data", e);
        }
    }

    private ItemNames() {
    }

    /**
     * @throws IllegalArgumentException 没有这个物品编号
     */
    public static String name(int id) {
        if (id < 0 || id >= NAMES.length) {
            throw new IllegalArgumentException("unknown item id " + id);
        }
        return NAMES[id];
    }

    /**
     * @param name 带命名空间的名称，例如 {@code minecraft:dirt}
     * @throws IllegalArgumentException 没有这个物品
     */
    public static int id(String name) {
        var id = IDS.get(name);
        if (id == null) {
            throw new IllegalArgumentException("unknown item " + name);
        }
        return id;
    }
}
