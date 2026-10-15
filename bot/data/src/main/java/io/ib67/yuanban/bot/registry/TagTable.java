package io.ib67.yuanban.bot.registry;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Update Tags 中的全部标签：注册表名称 → 标签名称 → 编号集合。不可变，每次更新返回新的实例。
 */
public final class TagTable {
    public static final TagTable EMPTY = new TagTable(Map.of());

    private final Map<Key, Map<Key, IntSet>> tags;

    private TagTable(Map<Key, Map<Key, IntSet>> tags) {
        this.tags = tags;
    }

    /**
     * 按标签逐个替换：只替换数据包中提到的标签，其余标签保留
     */
    public TagTable apply(ClientboundUpdateTagsPacket packet) {
        var result = new HashMap<>(tags);
        packet.getTags().forEach((registry, registryUpdate) -> {
            var registryTags = new HashMap<>(result.getOrDefault(registry, Map.of()));
            registryUpdate.forEach((tag, entries) ->
                    registryTags.put(tag, IntSets.unmodifiable(new IntOpenHashSet(entries))));
            result.put(registry, Map.copyOf(registryTags));
        });
        return new TagTable(Map.copyOf(result));
    }

    /**
     * 删除给出的注册表的全部标签
     */
    public TagTable without(Set<Key> registries) {
        var result = new HashMap<>(tags);
        registries.forEach(result::remove);
        return new TagTable(Map.copyOf(result));
    }

    /**
     * @param registry 注册表名称，例如 {@code minecraft:block}
     * @param tag      标签名称，例如 {@code minecraft:mineable/pickaxe}
     */
    public boolean contains(Key registry, Key tag, int id) {
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
    public Set<Key> tagNames(Key registry) {
        return tags.getOrDefault(registry, Map.of()).keySet();
    }
}
