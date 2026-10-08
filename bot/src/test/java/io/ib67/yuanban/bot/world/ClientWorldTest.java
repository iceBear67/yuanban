package io.ib67.yuanban.bot.world;

import io.ib67.yuanban.bot.ChunkBuilder;
import io.ib67.yuanban.bot.ChunkNotLoadedException;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.decode.SectionDecoder;
import io.ib67.yuanban.bot.decode.SectionPool;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientWorldTest {
    private final SectionDecoder decoder = new SectionDecoder(new SectionPool());

    private ClientWorld world(int chunkRadius) {
        var world = new ClientWorld(Key.key("minecraft:overworld"), 0, -64, 384, 2, 2);
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                var decoded = decoder.decode(TestServer.flatChunk().packet(x, z, TestServer.biomeCount()), -64, 384,
                        TestServer.biomeCount());
                world.installChunk(x, z, decoded.sections(), Map.of());
            }
        }
        return world;
    }

    @Test
    void writesCopySharedSections() {
        var first = world(0);
        var second = world(0);
        first.setBlock(1, -1, 1, Block.GOLD_BLOCK.stateId());
        assertEquals(Block.GOLD_BLOCK, first.getBlock(1, -1, 1));
        assertEquals(Block.STONE, second.getBlock(1, -1, 1));
        // 两个世界原本共用同一个区段，写入之后不再共用
        assertEquals(Block.STONE, world(0).getBlock(1, -1, 1));
    }

    @Test
    void snapshotIsNotAffectedByLaterWrites() {
        var world = world(1);
        var snapshot = world.snapshot(Vec.ZERO, 16);
        var old = world.setBlock(0, -1, 0, Block.AIR.stateId());
        assertEquals(Block.STONE, old);
        assertEquals(Block.AIR, world.getBlock(0, -1, 0));
        assertEquals(Block.STONE, snapshot.getBlock(0, -1, 0));
        // 快照范围之外与未加载的区块视为无法通过
        assertEquals(Block.BARRIER, snapshot.getBlock(16 * 5, 0, 0));
    }

    @Test
    void outOfHeightIsAirAndUnloadedChunkThrows() {
        var world = world(0);
        assertEquals(Block.AIR, world.getBlock(0, -65, 0));
        assertEquals(Block.AIR, world.getBlock(0, 320, 0));
        assertThrows(ChunkNotLoadedException.class, () -> world.getBlock(16, 0, 0));
    }

    @Test
    void movingTheCenterUnloadsChunksOutsideTheArea() {
        var world = new ClientWorld(Key.key("minecraft:overworld"), 0, -64, 384, 2, 2);
        // 视距 2：加载区域半径为 max(2, 2) + 3 = 5
        for (int x = 0; x <= 5; x++) {
            var decoded = decoder.decode(TestServer.flatChunk().packet(x, 0, TestServer.biomeCount()), -64, 384,
                    TestServer.biomeCount());
            world.installChunk(x, 0, decoded.sections(), Map.of());
        }
        assertTrue(world.inLoadArea(5, 0));
        assertFalse(world.inLoadArea(6, 0));
        var unloaded = world.center(3, 0);
        assertTrue(unloaded.isEmpty());
        unloaded = world.center(-2, 0);
        assertEquals(2, unloaded.size());
        assertTrue(unloaded.contains(CoordConversion.chunkIndex(4, 0)));
        assertTrue(unloaded.contains(CoordConversion.chunkIndex(5, 0)));
        assertFalse(world.chunkLoaded(4, 0));
        assertTrue(world.chunkLoaded(3, 0));
    }

    @Test
    void loadAreaRadiusUsesAViewDistanceOfAtLeastTwo() {
        // 原版 ClientChunkCache.calculateStorageRange：半径为 max(2, 视距) + 3
        var world = new ClientWorld(Key.key("minecraft:overworld"), 0, -64, 384, 0, 2);
        assertTrue(world.inLoadArea(5, -5));
        assertFalse(world.inLoadArea(6, 0));
        world.viewDistance(1);
        assertTrue(world.inLoadArea(-5, 5));
        assertFalse(world.inLoadArea(0, -6));
        world.viewDistance(4);
        assertTrue(world.inLoadArea(7, 7));
        assertFalse(world.inLoadArea(8, 0));
    }

    @Test
    void chunkBiomesReplaceTheBiomesOfEverySection() {
        // 每个区段只有一种生物群系：MCProtocolLib 对 4×4×4 生物群系调色板的下标计算有误（bot/README.md 待办 T1），
        // 这里不覆盖按格子区分的读取
        var world = world(0);
        var data = ChunkBuilder.overworld().biome(7).biomeData(TestServer.biomeCount());
        assertTrue(world.setBiomes(0, 0, data, TestServer.biomeCount()));
        assertEquals(7, world.biome(5, -60, 9));
        assertEquals(7, world.biome(15, 319, 15));
        assertFalse(world.setBiomes(1, 0, data, TestServer.biomeCount()));
    }

    @Test
    void movingBordersUseThePreviousTickSizeForBounds() {
        var border = new WorldBorderState();
        border.lerp(100, 50, 10);
        // 原版 MovingBorderExtent：范围判断与碰撞使用上一 tick 的大小（getMinX(0) 等取 previousSize），插值时间以 tick 计
        assertEquals(100, border.toWorldBorder().diameter());
        border.tick();
        assertEquals(100, border.toWorldBorder().diameter());
        assertTrue(border.withinBounds(49.5, 0));
        border.tick();
        assertEquals(95, border.toWorldBorder().diameter(), 1e-9);
        assertFalse(border.withinBounds(49.5, 0));
        for (int i = 0; i < 7; i++) {
            border.tick();
        }
        assertEquals(60, border.toWorldBorder().diameter(), 1e-9);
        // 剩余时间为 0 的那次推进之后静止在目标大小
        border.tick();
        assertEquals(50, border.toWorldBorder().diameter());
        border.tick();
        assertEquals(50, border.toWorldBorder().diameter());
        // 插值时间小于 0：此刻为起始大小，第一次推进之后为目标大小
        border.lerp(50, 80, -5);
        assertEquals(50, border.toWorldBorder().diameter());
        border.tick();
        assertEquals(80, border.toWorldBorder().diameter());
    }

    @Test
    void blockEntityDataIsReadSeparatelyFromTheBlock() {
        var world = world(0);
        world.setBlock(2, 0, 2, Block.CHEST.stateId());
        world.blockEntities().put(2, 0, 2, NbtMap.builder().putString("CustomName", "\"x\"").build());
        assertEquals("\"x\"", Objects.requireNonNull(world.blockEntity(2, 0, 2)).getString("CustomName"));
        assertNull(world.getBlock(2, 0, 2).nbt());
        assertNull(world.blockEntity(3, 0, 2));
        assertThrows(ChunkNotLoadedException.class, () -> world.blockEntity(16, 0, 0));
        // 变成没有方块实体的方块时删除方块实体数据
        world.setBlock(2, 0, 2, Block.STONE.stateId());
        world.setBlock(2, 0, 2, Block.CHEST.stateId());
        assertNull(world.blockEntity(2, 0, 2));
    }
}
