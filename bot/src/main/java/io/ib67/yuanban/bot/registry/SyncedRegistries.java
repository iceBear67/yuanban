package io.ib67.yuanban.bot.registry;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 服务端在配置阶段发来的同步注册表（wiki Registry Data：条目的顺序决定编号）。不可变。
 * 本库只使用条目名称，以及维度类型的最低 y 与高度。
 */
public final class SyncedRegistries {
    public static final Key CHAT_TYPE = Key.key("chat_type");
    public static final Key DAMAGE_TYPE = Key.key("damage_type");
    public static final Key WORLD_CLOCK = Key.key("world_clock");
    public static final Key DIMENSION_TYPE = Key.key("dimension_type");
    public static final Key BIOME = Key.key("worldgen/biome");

    private final Map<Key, List<Key>> entries;
    private final List<DimensionInfo> dimensionTypes;

    SyncedRegistries(Map<Key, List<Key>> entries, List<DimensionInfo> dimensionTypes) {
        this.entries = Map.copyOf(entries);
        this.dimensionTypes = List.copyOf(dimensionTypes);
    }

    /**
     * 注册表中按编号排列的条目名称；服务端没有发送这个注册表时为空列表
     */
    public List<Key> entries(Key registry) {
        return entries.getOrDefault(registry, List.of());
    }

    /**
     * @return 编号不在注册表中时为 null
     */
    public @Nullable Key entry(Key registry, int id) {
        var list = entries(registry);
        return id >= 0 && id < list.size() ? list.get(id) : null;
    }

    /**
     * @throws IllegalStateException 编号不在注册表中（原版解码时出错，客户端断开连接）
     */
    public Key requireEntry(Key registry, int id) {
        var key = entry(registry, id);
        if (key == null) {
            throw new IllegalStateException("id " + id + " is not in registry " + registry + " (" + size(registry) + " entries)");
        }
        return key;
    }

    public int size(Key registry) {
        return entries(registry).size();
    }

    /**
     * 生物群系的条目数，决定区块数据中生物群系全局调色板的位数
     */
    public int biomeCount() {
        return size(BIOME);
    }

    /**
     * @throws IllegalStateException 编号不在维度类型注册表中
     */
    public DimensionInfo dimensionType(int id) {
        if (id < 0 || id >= dimensionTypes.size()) {
            throw new IllegalStateException("unknown dimension type id " + id);
        }
        return dimensionTypes.get(id);
    }
}
