package io.ib67.yuanban.bot.world;

import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 方块实体的 NBT 按坐标保存，坐标按原版 {@code BlockPos.asLong} 的布局编码。
 */
class BlockEntityStoreTest {
    private static final NbtMap NBT = NbtMap.builder().putString("id", "minecraft:chest").build();

    @Test
    void valuesAreStoredByExactCoordinates() {
        var store = new BlockEntityStore();
        assertNull(store.get(0, 0, 0));
        store.put(0, 0, 0, NBT);
        assertEquals(NBT, store.get(0, 0, 0));
        // 每个坐标分量都参与编码
        assertNull(store.get(1, 0, 0));
        assertNull(store.get(0, 1, 0));
        assertNull(store.get(0, 0, 1));
        // 负坐标与正坐标不会相同
        store.put(-1, -1, -1, NBT);
        assertEquals(NBT, store.get(-1, -1, -1));
        assertNull(store.get(1, 1, 1));
    }

    @Test
    void puttingAgainReplacesTheValue() {
        var store = new BlockEntityStore();
        store.put(3, 64, -7, NBT);
        var replacement = NbtMap.builder().putString("id", "minecraft:barrel").build();
        store.put(3, 64, -7, replacement);
        assertEquals(replacement, store.get(3, 64, -7));
    }

    @Test
    void removingNeedsTheExactCoordinates() {
        var store = new BlockEntityStore();
        store.put(3, 64, -7, NBT);
        store.remove(4, 64, -7);
        assertEquals(NBT, store.get(3, 64, -7));
        store.remove(3, 64, -7);
        assertNull(store.get(3, 64, -7));
    }

    @Test
    void aChunkRemovalOnlyDropsItsOwnColumn() {
        var store = new BlockEntityStore();
        store.put(0, 64, 0, NBT);
        store.put(15, 64, 15, NBT);
        store.put(16, 64, 16, NBT);
        // 方块坐标 -1 属于区块 (-1, -1)
        store.put(-1, 64, -1, NBT);
        // 区块 (0, 0) 覆盖方块坐标 0 到 15
        store.removeChunk(0, 0);
        assertNull(store.get(0, 64, 0));
        assertNull(store.get(15, 64, 15));
        // 相邻区块不受影响
        assertEquals(NBT, store.get(16, 64, 16));
        assertEquals(NBT, store.get(-1, 64, -1));
        store.removeChunk(-1, -1);
        assertNull(store.get(-1, 64, -1));
    }

    @Test
    void heightsAcrossTheDimensionRangeAreDistinct() {
        var store = new BlockEntityStore();
        store.put(0, -64, 0, NBT);
        assertEquals(NBT, store.get(0, -64, 0));
        // 维度范围内的不同高度不会相同
        assertNull(store.get(0, 319, 0));
        assertNull(store.get(0, 0, 0));
        store.put(0, 319, 0, NBT);
        assertEquals(NBT, store.get(0, 319, 0));
    }
}
