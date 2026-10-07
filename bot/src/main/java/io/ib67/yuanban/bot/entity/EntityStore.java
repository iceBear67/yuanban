package io.ib67.yuanban.bot.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 一个世界中的实体，按编号排列。
 */
public final class EntityStore {
    private final Int2ObjectSortedMap<ClientEntity> entities = new Int2ObjectAVLTreeMap<>();

    public @Nullable ClientEntity get(int id) {
        return entities.get(id);
    }

    /**
     * 加入实体；同一编号已有实体时替换它
     *
     * @return 被替换的实体
     */
    public @Nullable ClientEntity add(ClientEntity entity) {
        return entities.put(entity.id(), entity);
    }

    public @Nullable ClientEntity remove(int id) {
        return entities.remove(id);
    }

    public Collection<ClientEntity> all() {
        return entities.values();
    }
}
