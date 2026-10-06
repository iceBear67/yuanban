package io.ib67.yuanban.datapack.selection;

import io.ib67.yuanban.datapack.pack.Pack;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 选择算法的结果。
 *
 * @param packs           启用的数据包，优先级从低到高
 * @param enabledFeatures 世界启用的特性
 * @param config          需要持久化的启用列表和禁用列表
 * @param report          对启用列表做的调整
 */
public record Selection(List<Pack> packs, Set<Key> enabledFeatures, DataPackConfig config, SelectionReport report) {
    public Selection {
        packs = List.copyOf(packs);
        enabledFeatures = Set.copyOf(enabledFeatures);
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(report, "report");
    }

    public List<String> ids() {
        return packs.stream().map(Pack::id).toList();
    }
}
