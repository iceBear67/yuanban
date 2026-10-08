package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.data.BlockStates;
import io.ib67.yuanban.bot.world.SectionRef;
import io.ib67.yuanban.bot.world.SharedSection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.PaletteType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 把 Chunk Data and Update Light 中的区段解码为共享的 {@link SharedSection}，并确定方块实体的 NBT。
 * 光照数据与高度图在这一步丢弃。
 */
public final class SectionDecoder {
    private final SectionPool pool;

    public SectionDecoder(SectionPool pool) {
        this.pool = pool;
    }

    /**
     * @param minY       当前维度的最低 y
     * @param height     当前维度的高度，区段数量 = 高度 / 16
     * @param biomeCount 生物群系注册表的条目数
     */
    public DecodedChunk decode(ClientboundLevelChunkWithLightPacket packet, int minY, int height, int biomeCount) {
        var data = packet.getChunkData();
        var buffer = Unpooled.wrappedBuffer(data);
        var sections = new SectionRef[height / 16];
        for (int i = 0; i < sections.length; i++) {
            var start = buffer.readerIndex();
            buffer.skipBytes(4); // 非空气方块数量与流体数量
            skipPalette(buffer, PaletteType.BLOCK_STATE);
            skipPalette(buffer, PaletteType.BIOME);
            var bytes = ByteBufUtil.getBytes(buffer, start, buffer.readerIndex() - start);
            var shared = pool.get(bytes, biomeCount, () -> {
                var section = MinecraftTypes.readChunkSection(Unpooled.wrappedBuffer(bytes), BlockStates.REGISTRY_SIZE, biomeCount);
                return new SharedSection(section.getBlockData(), section.getBiomeData(), section.getBlockCount(),
                        section.getFluidCount());
            });
            sections[i] = new SectionRef(shared, true);
        }
        if (buffer.isReadable()) {
            throw new IllegalStateException("chunk " + packet.getX() + ", " + packet.getZ() + " has "
                    + buffer.readableBytes() + " trailing bytes after " + sections.length + " sections");
        }
        return new DecodedChunk(packet.getX(), packet.getZ(), sections, blockEntities(packet, sections, minY, height));
    }

    /**
     * 方块实体的方块由区段中该位置的方块决定；该位置的方块没有同一类型的方块实体时（包括超出维度高度），
     * 这条数据被忽略（原版客户端 {@code LevelChunk.replaceWithPacketData} 的行为）
     */
    private static Map<Integer, NbtMap> blockEntities(ClientboundLevelChunkWithLightPacket packet, SectionRef[] sections,
                                                      int minY, int height) {
        var result = new HashMap<Integer, NbtMap>();
        for (var entry : packet.getBlockEntities()) {
            var type = Objects.requireNonNull(entry.getType(), "unknown block entity type");
            var y = entry.getY();
            if (y < minY || y >= minY + height) {
                continue;
            }
            var stateId = sections[(y - minY) >> 4].block(entry.getX(), y & 15, entry.getZ());
            var blockType = Objects.requireNonNull(Block.fromStateId(stateId)).blockEntityType();
            if (blockType == null || blockType.id() != type.ordinal()) {
                continue;
            }
            // 方块实体没有发给客户端的 NBT 时，服务端写入 TAG_END
            result.put(CoordConversion.chunkBlockIndex(entry.getX(), y, entry.getZ()),
                    Objects.requireNonNullElse(entry.getNbt(), NbtMap.EMPTY));
        }
        return result;
    }

    /**
     * 按区段的格式跳过一个调色板容器：位数，然后位数为 0 时是一个 VarInt；位数不超过间接调色板的上限时是调色板长度与各个编号；
     * 然后（位数不为 0 时）是按这个位数存放全部条目所需的 long，与 MCProtocolLib 的 {@code BitStorage} 相同
     */
    private static void skipPalette(ByteBuf buffer, PaletteType type) {
        var bitsPerEntry = buffer.readUnsignedByte();
        if (bitsPerEntry == 0) {
            MinecraftTypes.readVarInt(buffer);
            return;
        }
        if (bitsPerEntry <= type.getMaxBitsPerEntry()) {
            var paletteLength = MinecraftTypes.readVarInt(buffer);
            for (int i = 0; i < paletteLength; i++) {
                MinecraftTypes.readVarInt(buffer);
            }
        }
        var valuesPerLong = 64 / bitsPerEntry;
        buffer.skipBytes((type.getStorageSize() + valuesPerLong - 1) / valuesPerLong * Long.BYTES);
    }
}
