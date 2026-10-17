package io.ib67.yuanban.bot.testing;

import io.ib67.yuanban.bot.data.BlockStates;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 用 MCProtocolLib 的调色板与 {@link MinecraftTypes#writeChunkSection} 生成 Chunk Data and Update Light 数据包。
 * 每个区段的生物群系只有一种。
 */
public final class ChunkBuilder {
    private final int minY;
    private final DataPalette[] blocks;
    private final int[] biomes;
    private final Map<Integer, BlockEntityInfo> blockEntities = new LinkedHashMap<>();

    /**
     * @param minY   维度最低 y
     * @param height 维度高度
     */
    public ChunkBuilder(int minY, int height) {
        this.minY = minY;
        this.blocks = new DataPalette[height / 16];
        this.biomes = new int[height / 16];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = DataPalette.createForBlockState(Block.AIR.stateId(), BlockStates.REGISTRY_SIZE);
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
     * 加入一个方块实体（Chunk Data 的方块实体列表）；区段中这个位置的方块不变，需要时另外用 {@link #set} 设置。
     * 坐标为区块内坐标（x、z 为 0 到 15），y 为世界坐标
     *
     * @param nbt 为 null 时写入 TAG_END（方块实体没有发给客户端的数据）
     */
    public ChunkBuilder blockEntity(int x, int y, int z, BlockEntityType type, @Nullable NbtMap nbt) {
        blockEntities.put(CoordConversion.chunkBlockIndex(x, y, z), new BlockEntityInfo(x, y, z, type, nbt));
        return this;
    }

    /**
     * 每个区段的生物群系都设为同一个编号
     */
    public ChunkBuilder biome(int biomeId) {
        Arrays.fill(biomes, biomeId);
        return this;
    }

    /**
     * Chunk Biomes 中一个区块的数据：依次为每个区段的生物群系调色板
     */
    public byte[] biomeData(int biomeCount) {
        return write(buffer -> {
            for (var biome : biomes) {
                MinecraftTypes.writeDataPalette(buffer, DataPalette.createForBiome(biome, biomeCount));
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

    /**
     * 全部区段的数据，方块数为非空气方块的数量
     */
    public byte[] data(int biomeCount) {
        return write(buffer -> {
            for (int i = 0; i < blocks.length; i++) {
                var section = new ChunkSection(nonAir(blocks[i]), 0, blocks[i], DataPalette.createForBiome(biomes[i], biomeCount));
                MinecraftTypes.writeChunkSection(buffer, section);
            }
        });
    }

    public ClientboundLevelChunkWithLightPacket packet(int chunkX, int chunkZ, int biomeCount) {
        var light = new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(), List.of());
        return new ClientboundLevelChunkWithLightPacket(chunkX, chunkZ, data(biomeCount), Map.of(),
                blockEntities.values().toArray(BlockEntityInfo[]::new), light);
    }

    private static int nonAir(DataPalette palette) {
        int count = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!Objects.requireNonNull(Block.fromStateId(palette.get(x, y, z))).air()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static byte[] write(Consumer<ByteBuf> writer) {
        var buffer = Unpooled.buffer();
        try {
            writer.accept(buffer);
            return ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }
}
