package io.ib67.yuanban.datapack.selection;

import net.kyori.adventure.key.Key;

import java.util.Objects;
import java.util.Set;

/**
 * 启动会话的输入。
 *
 * @param config        持久化的启用列表和禁用列表；新建世界的时候来自 {@code server.properties}
 * @param worldFeatures 世界已启用的特性，{@link SelectionMode#LOAD_WORLD} 使用，{@link SelectionMode#CREATE_WORLD} 忽略
 * @param mode          选择模式
 * @param safeMode      安全模式，对应原版 {@code --safeMode}
 */
public record SelectionRequest(DataPackConfig config, Set<Key> worldFeatures, SelectionMode mode, boolean safeMode) {
    public SelectionRequest {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(mode, "mode");
        worldFeatures = Set.copyOf(worldFeatures);
    }
}
