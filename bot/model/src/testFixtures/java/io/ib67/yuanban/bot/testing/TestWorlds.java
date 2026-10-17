package io.ib67.yuanban.bot.testing;

import io.ib67.yuanban.bot.data.BlockStates;
import io.ib67.yuanban.bot.world.ClientWorld;
import io.ib67.yuanban.bot.world.SectionRef;
import io.ib67.yuanban.bot.world.SharedSection;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;

import java.util.Map;

/**
 * 直接构造区段的客户端世界，不经过数据包解码。主世界的高度范围（最低 y −64，高度 384）。
 * 同一个 {@link SharedSection} 可以被多个世界共用，用来检查写入之前的复制。
 */
public final class TestWorlds {
    public static final Key OVERWORLD = Key.key("minecraft:overworld");
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int BIOME = 0;

    private TestWorlds() {
    }

    /**
     * 整个区段都是 {@code block}
     */
    public static SharedSection section(Block block, int biome) {
        var blocks = DataPalette.createForBlockState(block.stateId(), BlockStates.REGISTRY_SIZE);
        var biomes = DataPalette.createForBiome(biome, 1);
        return new SharedSection(blocks, biomes, block.air() ? 0 : 4096, 0);
    }

    /**
     * 全部是空气的一组区段，区段可以写入
     */
    public static SectionRef[] airSections(int biome) {
        var air = section(Block.AIR, biome);
        var sections = new SectionRef[HEIGHT / 16];
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new SectionRef(air.copy(), false);
        }
        return sections;
    }

    /**
     * y 小于 floorY 为石头、其余为空气的一组区段，区段可以写入
     */
    public static SectionRef[] flatSections(int floorY, int biome) {
        var stone = section(Block.STONE, biome);
        var air = section(Block.AIR, biome);
        var sections = new SectionRef[HEIGHT / 16];
        for (int i = 0; i < sections.length; i++) {
            var minY = MIN_Y + i * 16;
            sections[i] = new SectionRef(minY < floorY ? stone.copy() : air.copy(), false);
        }
        return sections;
    }

    /**
     * 改变区段中的一个方块，坐标为区块内坐标（x、z 为 0 到 15），y 为世界坐标
     */
    public static void set(SectionRef[] sections, int x, int y, int z, Block block) {
        sections[(y - MIN_Y) >> 4].setBlock(x & 15, y & 15, z & 15, block.stateId());
    }

    /**
     * 把 [fromY, toY] 的每一层填满
     */
    public static void fill(SectionRef[] sections, int fromY, int toY, Block block) {
        for (int y = fromY; y <= toY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    set(sections, x, y, z, block);
                }
            }
        }
    }

    /**
     * 复制一份区段引用，新的数组中每个区段各自持有引用
     */
    public static SectionRef[] sameSections(SectionRef[] sections) {
        var copy = new SectionRef[sections.length];
        System.arraycopy(sections, 0, copy, 0, sections.length);
        return copy;
    }

    public static void install(ClientWorld world, int chunkX, int chunkZ, SectionRef[] sections) {
        world.installChunk(chunkX, chunkZ, sections, Map.of());
    }

    /**
     * 加载一个 y 小于 floorY 为石头的区块
     */
    public static void installFlat(ClientWorld world, int chunkX, int chunkZ, int floorY, int biome) {
        install(world, chunkX, chunkZ, flatSections(floorY, biome));
    }

    /**
     * 主世界，视距与模拟距离都是 2，区块坐标为 [−radius, radius] 的正方形内已经加载平地（y 小于 0 为石头）
     */
    public static ClientWorld flatWorld(int radius) {
        return flatWorld(2, 2, radius);
    }

    public static ClientWorld flatWorld(int viewDistance, int simulationDistance, int radius) {
        var world = new ClientWorld(OVERWORLD, 0, MIN_Y, HEIGHT, viewDistance, simulationDistance);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                installFlat(world, x, z, 0, BIOME);
            }
        }
        return world;
    }

    /**
     * 主世界，没有任何区块
     */
    public static ClientWorld emptyWorld() {
        return new ClientWorld(OVERWORLD, 0, MIN_Y, HEIGHT, 2, 2);
    }
}
