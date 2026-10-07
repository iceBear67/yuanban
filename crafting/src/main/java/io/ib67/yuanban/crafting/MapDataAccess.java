package io.ib67.yuanban.crafting;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * 地图数据，由宿主提供。地图放大配方（{@code crafting_special_mapextending}）的匹配和取出结果时的处理读取它。
 * 方法在玩家所在实例的线程上调用。
 */
public interface MapDataAccess {
    /**
     * 原版 {@code MapItem.getSavedData}：地图 ID 对应的数据，地图不存在时为空。
     */
    Optional<MapInfo> find(int mapId);

    /**
     * 原版 {@code MapItem.scaleMap}：按原版 {@code MapItemSavedData.scaled()} 复制地图 {@code mapId} 的数据
     * （缩放等级加一，最大为 4，按新的缩放等级重新计算中心坐标，保留追踪设置和维度），分配新的地图 ID 并返回；
     * 地图不存在时为空。
     */
    OptionalInt createScaledCopy(int mapId);

    /**
     * @param scale          缩放等级，0 到 4
     * @param explorationMap 原版 {@code MapItemSavedData.isExplorationMap()}：地图上有探险家地图专用的标记
     */
    record MapInfo(int scale, boolean explorationMap) {
    }
}
