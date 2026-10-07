package io.ib67.yuanban.crafting.testing;

import io.ib67.yuanban.crafting.MapDataAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 测试代码作为宿主提供的地图数据，保存在内存中。放大地图按原版 {@code MapItemSavedData.scaled()} 和
 * {@code MapItemSavedData.createFresh} 计算新地图的缩放等级和中心坐标。
 */
public final class InMemoryMaps implements MapDataAccess {
    private static final int MAX_SCALE = 4;

    private final Map<Integer, MapState> maps = new HashMap<>();
    private int nextId;

    /**
     * 地图的数据。{@code explorationMap} 对应原版地图上有探险家地图专用标记。
     */
    public record MapState(int centerX, int centerZ, int scale, boolean explorationMap) {
    }

    /**
     * 原版 {@code MapItemSavedData.createFresh}：按缩放等级把中心坐标对齐到地图网格，返回新地图的 ID。
     */
    public int create(double originX, double originZ, int scale, boolean explorationMap) {
        int size = 128 * (1 << scale);
        int areaX = (int) Math.floor((originX + 64.0) / size);
        int areaZ = (int) Math.floor((originZ + 64.0) / size);
        int x = areaX * size + size / 2 - 64;
        int z = areaZ * size + size / 2 - 64;
        int id = nextId++;
        maps.put(id, new MapState(x, z, scale, explorationMap));
        return id;
    }

    public MapState get(int mapId) {
        var state = maps.get(mapId);
        if (state == null) {
            throw new IllegalArgumentException("no map with id " + mapId);
        }
        return state;
    }

    public int size() {
        return maps.size();
    }

    @Override
    public Optional<MapInfo> find(int mapId) {
        return Optional.ofNullable(maps.get(mapId)).map(state -> new MapInfo(state.scale(), state.explorationMap()));
    }

    /**
     * 原版 {@code MapItemSavedData.scaled()}：以原地图的中心重新建立一张缩放等级加一的地图，新地图没有标记。
     */
    @Override
    public OptionalInt createScaledCopy(int mapId) {
        var state = maps.get(mapId);
        if (state == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(create(state.centerX(), state.centerZ(), Math.clamp(state.scale() + 1, 0, MAX_SCALE), false));
    }
}
