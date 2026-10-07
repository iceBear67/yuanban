package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.transport.InboundSink;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockEntityType;
import net.minestom.server.instance.heightmap.Heightmap;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.data.ChunkData;
import net.minestom.server.network.packet.server.play.data.LightData;
import net.minestom.server.network.packet.server.play.data.PlayerSpawnInfo;
import net.minestom.server.registry.Registries;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * TCP 连接的入站读取：把套接字数据读进 {@link NetworkBuffer}，按原版客户端的规则分帧、解压与解析，每次只读出一个数据包。
 * 读出 Set Compression 后立即切换压缩标记，因为同一次读取中紧跟的数据包已经是压缩格式。
 * <ul>
 *     <li>分帧（原版 {@code Varint21FrameDecoder}）：长度 VarInt 最多 3 个字节，长度为 0 时出错；</li>
 *     <li>解压（原版 {@code CompressionDecoder}，客户端不检查解压后的长度）：声明长度为 0 时其余字节就是数据包，
 *     否则解压到声明长度的缓冲区，写出的字节数少于声明时出错；数据比声明更长时只取声明长度的部分（原版客户端行为）；</li>
 *     <li>解析（原版 {@code PacketDecoder}）：读完一个数据包之后还有剩余字节时出错。</li>
 * </ul>
 */
// FIXME(Minestom M11): PacketReading 按服务端的属性 ServerProperties 限制长度：HANDSHAKE、STATUS、LOGIN 阶段 8192 字节，
//  其余阶段与解压后的长度 2097151 字节；长度 VarInt 可以占 4 到 5 个字节，剩余字节只记警告。
//  这些都是服务端的规则，所以这里不使用 PacketReading，自己完成分帧、解压与解析，见 MINESTOM-BUG-REPORT.md
public final class TcpInbound {
    private static final int INITIAL_BUFFER_SIZE = 1 << 16;
    /**
     * 原版 {@code Varint21FrameDecoder}：帧长度最多占 3 个字节
     */
    private static final int MAX_FRAME_LENGTH_BYTES = 3;
    private static final int CHUNK_DATA_ID = PacketVanilla.SERVER_PACKET_PARSER.play().packetInfo(ChunkDataPacket.class).id();
    private static final NetworkBuffer.Type<Map<Heightmap.Type, long[]>> HEIGHTMAPS = Heightmap.Type.NETWORK_TYPE
            .mapValue(NetworkBuffer.LONG_ARRAY, Heightmap.Type.values().length);

    private final ReadableByteChannel channel;
    private final InboundSink sink;
    private final Inflater inflater = new Inflater();
    private ConnectionState state = ConnectionState.LOGIN;
    private boolean compressed;
    private int minY;
    private int height;
    private volatile int compressionThreshold = -1;
    private volatile long lastReadNanos = System.nanoTime();

    /**
     * @param channel 阻塞模式的通道，握手已经由出站一侧发出，入站从 Login 阶段开始
     */
    public TcpInbound(ReadableByteChannel channel, InboundSink sink) {
        this.channel = channel;
        this.sink = sink;
    }

    /**
     * 读取直到通道结束。对端关闭时抛出 {@link java.io.EOFException}
     */
    public void run() throws IOException, DataFormatException {
        var buffer = NetworkBuffer.staticBuffer(INITIAL_BUFFER_SIZE);
        while (true) {
            buffer.readChannel(channel);
            lastReadNanos = System.nanoTime();
            readAvailable(buffer);
        }
    }

    /**
     * 读出缓冲区中全部完整的数据包，交给 sink
     */
    void readAvailable(NetworkBuffer buffer) throws DataFormatException {
        while (true) {
            var frameStart = buffer.readIndex();
            var length = readFrameLength(buffer);
            if (length < 0 || buffer.readableBytes() < length) {
                // 帧还没有到齐：保留已经读到的部分，缓冲区放不下整帧时扩大
                var required = buffer.readIndex() - frameStart + Math.max(length, 0);
                buffer.readIndex(frameStart);
                buffer.compact();
                if (required > buffer.capacity()) {
                    buffer.resize(required);
                }
                return;
            }
            var frame = buffer.slice(buffer.readIndex(), length, 0, length);
            buffer.advanceRead(length);
            var packet = readFrame(frame);
            state = PacketVanilla.nextServerState(packet, state);
            switch (packet) {
                case SetCompressionPacket(int threshold) -> {
                    compressed = threshold >= 0;
                    compressionThreshold = threshold;
                }
                case JoinGamePacket join -> enterDimension(join.playerSpawnInfo());
                case RespawnPacket respawn -> enterDimension(respawn.playerSpawnInfo());
                default -> {
                }
            }
            sink.accept(packet);
        }
    }

    /**
     * @return 帧长度；长度还没有读全时为 -1
     * @throws DataFormatException 长度超过 3 个字节，或者为 0
     */
    private static int readFrameLength(NetworkBuffer buffer) throws DataFormatException {
        var length = 0;
        for (int i = 0; i < MAX_FRAME_LENGTH_BYTES; i++) {
            if (buffer.readableBytes() == 0) {
                return -1;
            }
            var b = buffer.read(NetworkBuffer.BYTE);
            length |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                if (length == 0) {
                    throw new DataFormatException("frame length cannot be zero");
                }
                return length;
            }
        }
        throw new DataFormatException("frame length wider than 21 bits");
    }

    private ServerPacket readFrame(NetworkBuffer frame) throws DataFormatException {
        var registries = sink.registries();
        frame.registries(registries);
        if (!compressed) {
            return readPacket(frame);
        }
        var declared = frame.read(NetworkBuffer.VAR_INT);
        if (declared == 0) {
            return readPacket(frame);
        }
        if (declared < 0) {
            throw new DataFormatException("negative decompressed length " + declared);
        }
        // 客户端不检查压缩阈值与解压后的长度上限。原版把数据解压到声明长度的缓冲区，只要求写满它：数据更长时其余部分被忽略。
        // NetworkBuffer.decompress 在数据超出输出缓冲区时出错，与原版不同，所以直接使用 Inflater
        var input = new byte[(int) frame.readableBytes()];
        frame.copyTo(frame.readIndex(), input, 0, input.length);
        var output = new byte[declared];
        int inflated;
        try {
            inflater.setInput(input);
            inflated = inflater.inflate(output);
        } finally {
            inflater.reset();
        }
        if (inflated != declared) {
            throw new DataFormatException("decompressed " + inflated + " bytes, but the declared length is " + declared);
        }
        return readPacket(NetworkBuffer.wrap(output, 0, declared, registries));
    }

    private ServerPacket readPacket(NetworkBuffer payload) throws DataFormatException {
        var id = payload.read(NetworkBuffer.VAR_INT);
        ServerPacket packet = state == ConnectionState.PLAY && id == CHUNK_DATA_ID
                ? readChunkData(payload)
                : PacketVanilla.SERVER_PACKET_PARSER.parse(state, id, payload);
        if (payload.readableBytes() != 0) {
            throw new DataFormatException(packet.getClass().getSimpleName() + " (0x" + Integer.toHexString(id) + ") has "
                    + payload.readableBytes() + " bytes left after reading");
        }
        return packet;
    }

    /**
     * Chunk Data and Update Light：格式与 {@code ChunkDataPacket.SERIALIZER} 相同，方块实体自己读取。
     * 方块实体的方块取区段中该位置的方块；该位置的方块没有同一类型的方块实体时（包括超出维度高度），这条数据被忽略（原版客户端行为）
     */
    // FIXME(Minestom M10): ChunkData 读取方块实体时用 Block.fromKey(方块实体类型的名称) 查找方块，名称与方块不同的类型
    //  （sign、hanging_sign、banner、skull、mob_spawner、brushable_block、shelf 等）被直接丢弃，其余取同名方块的默认状态。
    //  这里自己读取方块实体，方块由区段调色板中该位置的方块决定，NBT 附加到这个方块上，见 MINESTOM-BUG-REPORT.md
    private ChunkDataPacket readChunkData(NetworkBuffer buffer) {
        var chunkX = buffer.read(NetworkBuffer.INT);
        var chunkZ = buffer.read(NetworkBuffer.INT);
        var heightmaps = buffer.read(HEIGHTMAPS);
        var data = buffer.read(NetworkBuffer.BYTE_ARRAY);
        var count = buffer.read(NetworkBuffer.VAR_INT);
        var entries = new ArrayList<RawBlockEntity>(count);
        for (int i = 0; i < count; i++) {
            var xz = buffer.read(NetworkBuffer.BYTE);
            var y = buffer.read(NetworkBuffer.SHORT);
            var type = Objects.requireNonNull(buffer.read(BlockEntityType.NETWORK_TYPE), "unknown block entity type");
            // 方块实体没有发给客户端的 NBT 时，服务端写入 TAG_END
            var nbt = buffer.read(NetworkBuffer.NBT);
            entries.add(new RawBlockEntity((xz >> 4) & 15, y, xz & 15, type, nbt));
        }
        var light = buffer.read(LightData.NETWORK_TYPE);
        return new ChunkDataPacket(chunkX, chunkZ, new ChunkData(heightmaps, data, blockEntities(data, entries)), light);
    }

    private Map<Integer, Block> blockEntities(byte[] data, ArrayList<RawBlockEntity> entries) {
        var result = new HashMap<Integer, Block>();
        var inHeight = entries.stream().filter(entry -> entry.y() >= minY && entry.y() < minY + height).toList();
        if (inHeight.isEmpty()) {
            return result;
        }
        var highestSection = inHeight.stream().mapToInt(entry -> (entry.y() - minY) >> 4).max().orElseThrow();
        var palettes = SectionDecoder.blockPalettes(data, highestSection + 1);
        for (var entry : inHeight) {
            var stateId = palettes[(entry.y() - minY) >> 4].get(entry.x(), entry.y() & 15, entry.z());
            var block = Objects.requireNonNull(Block.fromStateId(stateId));
            if (!entry.type().equals(block.blockEntityType())) {
                continue;
            }
            result.put(CoordConversion.chunkBlockIndex(entry.x(), entry.y(), entry.z()),
                    entry.nbt() instanceof CompoundBinaryTag compound ? block.withNbt(compound) : block);
        }
        return result;
    }

    private void enterDimension(PlayerSpawnInfo spawnInfo) {
        Registries registries = sink.registries();
        var type = Objects.requireNonNull(registries.dimensionType().get(spawnInfo.dimensionType()),
                () -> "unknown dimension type id " + spawnInfo.dimensionType());
        minY = type.minY();
        height = type.height();
    }

    /**
     * 压缩阈值；没有启用压缩时为负数
     */
    public int compressionThreshold() {
        return compressionThreshold;
    }

    public long lastReadNanos() {
        return lastReadNanos;
    }

    /**
     * 按原始数据读出的一个方块实体，坐标为区块内坐标（y 为世界坐标）
     */
    private record RawBlockEntity(int x, int y, int z, BlockEntityType type, BinaryTag nbt) {
    }
}
