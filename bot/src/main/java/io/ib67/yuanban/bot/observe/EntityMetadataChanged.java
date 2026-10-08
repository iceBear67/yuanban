package io.ib67.yuanban.bot.observe;

import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;

import java.util.Map;

/**
 * Set Entity Metadata 改变了实体的元数据。
 *
 * @param changed 数据包中的条目，按索引
 */
public record EntityMetadataChanged(int entityId, Map<Integer, EntityMetadata<?, ?>> changed) implements Observation {
    public EntityMetadataChanged {
        changed = Map.copyOf(changed);
    }
}
