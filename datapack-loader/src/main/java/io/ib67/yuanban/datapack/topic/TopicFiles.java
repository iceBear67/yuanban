package io.ib67.yuanban.datapack.topic;

import net.kyori.adventure.key.Key;

import java.util.NavigableMap;
import java.util.Optional;

/**
 * 覆盖模式的视图：每个资源 ID 对应优先级最高的文件，按 {@link Key} 的自然顺序排列。
 */
public interface TopicFiles {
    NavigableMap<Key, Resource> files();

    default Optional<Resource> find(Key id) {
        return Optional.ofNullable(files().get(id));
    }
}
