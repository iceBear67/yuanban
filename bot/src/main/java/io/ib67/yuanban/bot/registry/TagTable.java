package io.ib67.yuanban.bot.registry;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.kyori.adventure.key.Key;
import net.minestom.server.network.packet.server.common.TagsPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Update Tags 中的全部标签：注册表名称 → 标签名称 → 编号集合。不可变，每次更新返回新的实例。
 * 注册表名称与标签名称都使用带命名空间的完整形式。
 */
public final class TagTable {
    public static final TagTable EMPTY = new TagTable(Map.of());

    private final Map<String, Map<String, IntSet>> tags;

    private TagTable(Map<String, Map<String, IntSet>> tags) {
        this.tags = tags;
    }

    /**
     * 按标签逐个替换：只替换数据包中提到的标签，其余标签保留
     */
    public TagTable apply(TagsPacket packet) {
        var result = new HashMap<>(tags);
        for (var registry : packet.registries()) {
            var name = Key.key(registry.registry()).asString();
            var registryTags = new HashMap<>(result.getOrDefault(name, Map.of()));
            for (var tag : registry.tags()) {
                registryTags.put(Key.key(tag.identifier()).asString(),
                        IntSets.unmodifiable(new IntOpenHashSet(tag.entries())));
            }
            result.put(name, Map.copyOf(registryTags));
        }
        return new TagTable(Map.copyOf(result));
    }

    /**
     * 删除给出的注册表的全部标签
     */
    public TagTable without(Set<String> registries) {
        var result = new HashMap<>(tags);
        registries.forEach(result::remove);
        return new TagTable(Map.copyOf(result));
    }

    /**
     * @param registry 注册表名称，例如 {@code minecraft:block}
     * @param tag      标签名称，例如 {@code minecraft:mineable/pickaxe}
     */
    public boolean contains(String registry, String tag, int id) {
        var registryTags = tags.get(registry);
        if (registryTags == null) {
            return false;
        }
        var entries = registryTags.get(tag);
        return entries != null && entries.contains(id);
    }

    /**
     * 注册表中的全部标签名称
     */
    public Set<String> tagNames(String registry) {
        return tags.getOrDefault(registry, Map.of()).keySet();
    }
}
