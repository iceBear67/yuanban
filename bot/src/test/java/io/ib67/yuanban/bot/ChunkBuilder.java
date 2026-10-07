package io.ib67.yuanban.bot;

import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.data.ChunkData;
import net.minestom.server.network.packet.server.play.data.LightData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用 Minestom 的调色板与序列化器生成 Chunk Data and Update Light 数据包。
 */
public final class ChunkBuilder {
    private final int minY;
    private final Palette[] blocks;
    private final Palette[] biomes;
    private final Map<Integer, Block> blockEntities = new HashMap<>();

    /**
     * @param minY   维度最低 y
     * @param height 维度高度
     */
    public ChunkBuilder(int minY, int height) {
        this.minY = minY;
        this.blocks = new Palette[height / 16];
        this.biomes = new Palette[height / 16];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = Palette.blocks();
            biomes[i] = Palette.biomes();
        }
    }

    /**
     * 主世界的高度范围
     */
    public static ChunkBuilder overworld() {
        return new ChunkBuilder(-64, 384);
    }

    /**
     * 坐标为区块内坐标（x、z 为 0 到 15），y 为世界坐标
     */
    public ChunkBuilder set(int x, int y, int z, Block block) {
        blocks[(y - minY) >> 4].set(x, y & 15, z, block.stateId());
        return this;
    }

    /**
     * 加入一个方块实体（Chunk Data 的方块实体列表）；区段中这个位置的方块不变，需要时另外用 {@link #set} 设置
     */
    public ChunkBuilder blockEntity(int x, int y, int z, Block block) {
        blockEntities.put(CoordConversion.chunkBlockIndex(x, y, z), block);
        return this;
    }

    /**
     * 每个区段的生物群系都设为同一个编号
     */
    public ChunkBuilder biome(int biomeId) {
        for (var palette : biomes) {
            palette.fill(biomeId);
        }
        return this;
    }

    /**
     * Chunk Biomes 中一个区块的数据：依次为每个区段的生物群系调色板
     */
    public byte[] biomeData(int biomeCount) {
        var serializer = Palette.biomeSerializer(biomeCount);
        return NetworkBuffer.makeArray(buffer -> {
            for (var palette : biomes) {
                buffer.write(serializer, palette);
            }
        });
    }

    /**
     * 原版写出的一个生物群系直接调色板：位数一个字节，然后是原版 {@code SimpleBitStorage} 的排列
     * （每个 long 放 {@code 64 / bits} 个编号，从低位开始，编号不跨 long）
     *
     * @param bits 原版直接调色板的位数 {@code ceillog2(生物群系数量)}
     * @param ids  64 个格子的生物群系编号，下标为 {@code y × 16 + z × 4 + x}
     */
    public static byte[] vanillaDirectBiomes(int bits, int[] ids) {
        var perLong = 64 / bits;
        var longs = new long[(ids.length + perLong - 1) / perLong];
        for (int i = 0; i < ids.length; i++) {
            longs[i / perLong] |= (long) ids[i] << (i % perLong * bits);
        }
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(NetworkBuffer.BYTE, (byte) bits);
            for (var value : longs) {
                buffer.write(NetworkBuffer.LONG, value);
            }
        });
    }

    /**
     * 把 [fromY, toY] 的每一层填满
     */
    public ChunkBuilder fill(int fromY, int toY, Block block) {
        for (int y = fromY; y <= toY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    set(x, y, z, block);
                }
            }
        }
        return this;
    }

    public byte[] data(int biomeCount) {
        var type = ChunkData.Section.networkType(biomeCount);
        return NetworkBuffer.makeArray(buffer -> {
            for (int i = 0; i < blocks.length; i++) {
                var count = (short) blocks[i].count();
                buffer.write(type, new ChunkData.Section(count, (short) 0, blocks[i], biomes[i]));
            }
        });
    }

    public ChunkDataPacket packet(int chunkX, int chunkZ, int biomeCount) {
        var light = new LightData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(), List.of());
        return new ChunkDataPacket(chunkX, chunkZ, new ChunkData(Map.of(), data(biomeCount), blockEntities), light);
    }
}
