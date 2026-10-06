package io.ib67.yuanban.datapack.selection;

import java.util.List;

/**
 * 选择算法对启用列表做的调整，由宿主输出日志。
 *
 * @param missing            启用列表中已经不存在的数据包
 * @param autoEnabled        新发现并自动启用的数据包
 * @param skippedForFeatures 新发现、但所需特性没有启用而没有启用的数据包
 * @param removedForFeatures 所需特性没有启用而被移出启用列表的数据包
 * @param forcedFeaturePacks 为提供世界已启用的特性而强制启用的特性数据包
 */
public record SelectionReport(List<String> missing,
                              List<String> autoEnabled,
                              List<String> skippedForFeatures,
                              List<String> removedForFeatures,
                              List<String> forcedFeaturePacks) {
    public static final SelectionReport EMPTY = new SelectionReport(List.of(), List.of(), List.of(), List.of(), List.of());

    public SelectionReport {
        missing = List.copyOf(missing);
        autoEnabled = List.copyOf(autoEnabled);
        skippedForFeatures = List.copyOf(skippedForFeatures);
        removedForFeatures = List.copyOf(removedForFeatures);
        forcedFeaturePacks = List.copyOf(forcedFeaturePacks);
    }
}
