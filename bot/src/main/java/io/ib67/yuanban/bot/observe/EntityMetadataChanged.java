package io.ib67.yuanban.bot.observe;

import net.minestom.server.entity.Metadata;

import java.util.Map;

/**
 * Set Entity Metadata 改变了实体的元数据。
 *
 * @param changed 数据包中的条目，按索引
 */
public record EntityMetadataChanged(int entityId, Map<Integer, Metadata.Entry<?>> changed) implements Observation {
    public EntityMetadataChanged {
        changed = Map.copyOf(changed);
    }
}
