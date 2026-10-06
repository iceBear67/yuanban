package io.ib67.yuanban.datapack.selection;

import java.util.List;

/**
 * 持久化的启用列表和禁用列表，对应原版 {@code level.dat} 中的 {@code DataPacks}。
 *
 * @param enabled  启用的数据包 ID，优先级从低到高
 * @param disabled 禁用的数据包 ID
 */
public record DataPackConfig(List<String> enabled, List<String> disabled) {
    public DataPackConfig {
        enabled = List.copyOf(enabled);
        disabled = List.copyOf(disabled);
    }
}
