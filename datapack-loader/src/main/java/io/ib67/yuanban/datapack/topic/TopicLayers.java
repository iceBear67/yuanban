package io.ib67.yuanban.datapack.topic;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.NavigableMap;

/**
 * 分层模式的视图：每个资源 ID 对应它的全部版本，列表按优先级从低到高排列，不会为空。
 */
public interface TopicLayers {
    NavigableMap<Key, List<Resource>> layers();
}
