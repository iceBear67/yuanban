package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.world.SectionRef;
import io.ib67.yuanban.bot.world.SharedSection;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.instance.palette.Palettes;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.NetworkBufferTemplate;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.data.ChunkData;
import net.minestom.server.utils.block.BlockUtils;

import java.util.HashMap;
import java.util.Objects;

/**
 * 把 Chunk Data and Update Light 中的区段解码为共享的 {@link SharedSection}，方块实体取发给客户端的 NBT。
 * 光照数据与高度图在这一步丢弃。
 */
public final class SectionDecoder {
    private final SectionPool pool;

    public SectionDecoder(SectionPool pool) {
        this.pool = pool;
    }

    /**
     * @param sectionCount 区段数量 = 维度高度 / 16
     * @param biomeCount   生物群系注册表的条目数
     */
    public DecodedChunk decode(ChunkDataPacket packet, int sectionCount, int biomeCount) {
        var data = packet.chunkData().data();
        var buffer = NetworkBuffer.wrap(data, 0, data.length);
        var sections = new SectionRef[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            var start = buffer.readIndex();
            buffer.advanceRead(4); // 非空气方块数量与流体数量
            skipPalette(buffer, Palette.BLOCK_DIMENSION, Palette.BLOCK_PALETTE_MAX_BITS);
            skipPalette(buffer, Palette.BIOME_DIMENSION, Palette.BIOME_PALETTE_MAX_BITS);
            var length = (int) (buffer.readIndex() - start);
            var bytes = new byte[length];
            buffer.copyTo(start, bytes, 0, length);
            var shared = pool.get(bytes, biomeCount, () -> {
                var section = NetworkBuffer.wrap(bytes, 0, bytes.length).read(sectionType(biomeCount));
                return new SharedSection(section.blockStates(), section.biomes(), section.blockCount(), section.liquidCount());
            });
            sections[i] = new SectionRef(shared, true);
        }
        if (buffer.readableBytes() != 0) {
            throw new IllegalStateException("chunk " + packet.chunkX() + ", " + packet.chunkZ() + " has "
                    + buffer.readableBytes() + " trailing bytes after " + sectionCount + " sections");
        }
        // 对象级连接直接拿到服务端区块中的方块（带服务端的全部 NBT 与 BlockHandler）：与 TCP 连接相同，
        // 只取序列化时 BlockUtils.extractClientNbt 发给客户端的标签
        var blockEntities = new HashMap<Integer, CompoundBinaryTag>();
        packet.chunkData().blockEntities().forEach((index, block) ->
                blockEntities.put(index, Objects.requireNonNull(BlockUtils.extractClientNbt(block))));
        return new DecodedChunk(packet.chunkX(), packet.chunkZ(), sections, blockEntities);
    }

    /**
     * 一个区段：格式与 {@code ChunkData.Section.networkType} 相同，生物群系调色板使用 {@link #biomeSerializer}
     */
    private static NetworkBuffer.Type<ChunkData.Section> sectionType(int biomeCount) {
        return NetworkBufferTemplate.template(
                NetworkBuffer.SHORT, ChunkData.Section::blockCount,
                NetworkBuffer.SHORT, ChunkData.Section::liquidCount,
                Palette.BLOCK_SERIALIZER, ChunkData.Section::blockStates,
                biomeSerializer(biomeCount), ChunkData.Section::biomes,
                ChunkData.Section::new);
    }

    /**
     * 生物群系调色板（区块数据与 Chunk Biomes）：直接调色板的位数为 {@code ceillog2(生物群系数量)}，与原版
     * {@code Strategy.createForBiomes} 相同。直接调色板声明了其它位数时抛出异常；原版不看声明的位数，按这个位数读取存储，
     * 这样的数据它同样读错
     */
    // FIXME(Minestom M20): Palette.biomeSerializer 用 bitsToRepresent(生物群系数量) 计算直接调色板的位数，数量是 2 的幂时
    //  比原版多一位，原版服务端发来的直接调色板被拒绝。这里用 Palette.serializer 按原版的位数读取，见 MINESTOM-BUG-REPORT.md
    public static NetworkBuffer.Type<Palette> biomeSerializer(int biomeCount) {
        return Palette.serializer(Palette.BIOME_DIMENSION, Palette.BIOME_PALETTE_MIN_BITS, Palette.BIOME_PALETTE_MAX_BITS,
                ceilLog2(biomeCount));
    }

    /**
     * 原版 {@code Mth.ceillog2}：表示 0 到 {@code value - 1} 需要的位数，{@code value} 为 0 或 1 时为 0
     */
    private static int ceilLog2(int value) {
        return value <= 1 ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    /**
     * 读出前 {@code count} 个区段的方块调色板，不解码生物群系
     */
    public static Palette[] blockPalettes(byte[] data, int count) {
        var buffer = NetworkBuffer.wrap(data, 0, data.length);
        var palettes = new Palette[count];
        for (int i = 0; i < count; i++) {
            buffer.advanceRead(4); // 非空气方块数量与流体数量
            palettes[i] = buffer.read(Palette.BLOCK_SERIALIZER);
            skipPalette(buffer, Palette.BIOME_DIMENSION, Palette.BIOME_PALETTE_MAX_BITS);
        }
        return palettes;
    }

    /**
     * 按 {@code Palette.serializer} 的格式跳过一个调色板容器
     */
    private static void skipPalette(NetworkBuffer buffer, int dimension, int maxIndirectBits) {
        var bitsPerEntry = buffer.read(NetworkBuffer.BYTE);
        if (bitsPerEntry == 0) {
            buffer.read(NetworkBuffer.VAR_INT);
            return;
        }
        if (bitsPerEntry <= maxIndirectBits) {
            var paletteLength = buffer.read(NetworkBuffer.VAR_INT);
            for (int i = 0; i < paletteLength; i++) {
                buffer.read(NetworkBuffer.VAR_INT);
            }
        }
        buffer.advanceRead((long) Palettes.arrayLength(dimension, bitsPerEntry) * Long.BYTES);
    }
}
