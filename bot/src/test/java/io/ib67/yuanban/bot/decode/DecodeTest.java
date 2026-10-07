package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.ChunkBuilder;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.registry.VanillaReference;
import io.ib67.yuanban.bot.transport.InboundSink;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.PacketWriting;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.KeepAlivePacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.login.LoginPluginRequestPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.data.ChunkData;
import net.minestom.server.network.packet.server.play.data.LightData;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.registry.Registries;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecodeTest {
    /**
     * 用 TcpInbound 读出全部字节，返回读出的数据包；读到末尾以外的异常照常抛出
     */
    private static List<ServerPacket> read(byte[] bytes) throws Exception {
        var received = new ArrayList<ServerPacket>();
        var inbound = new TcpInbound(Channels.newChannel(new ByteArrayInputStream(bytes)), new InboundSink() {
            @Override
            public void accept(ServerPacket packet) {
                received.add(packet);
            }

            @Override
            public Registries registries() {
                return VanillaReference.get();
            }

            @Override
            public void closed(@Nullable Throwable error) {
            }
        });
        try {
            inbound.run();
        } catch (EOFException e) {
            return received;
        }
        throw new AssertionError("the channel did not end");
    }

    private static byte[] bytes(Consumer<NetworkBuffer> writer) {
        var buffer = NetworkBuffer.resizableBuffer(256, VanillaReference.get());
        writer.accept(buffer);
        var bytes = new byte[(int) buffer.writeIndex()];
        buffer.copyTo(0, bytes, 0, bytes.length);
        return bytes;
    }

    /**
     * 启用压缩之后的一帧：声明的解压长度，然后是 zlib 压缩的数据包（编号与内容）
     */
    private static byte[] compressedFrame(byte[] payload, int declaredLength) {
        var deflater = new Deflater();
        deflater.setInput(payload);
        deflater.finish();
        var compressed = new byte[payload.length + 1024];
        var compressedLength = deflater.deflate(compressed);
        deflater.end();
        var body = bytes(buffer -> {
            buffer.write(NetworkBuffer.VAR_INT, declaredLength);
            buffer.write(NetworkBuffer.RAW_BYTES, Arrays.copyOf(compressed, compressedLength));
        });
        return bytes(buffer -> {
            buffer.write(NetworkBuffer.VAR_INT, body.length);
            buffer.write(NetworkBuffer.RAW_BYTES, body);
        });
    }

    private static byte[] loginPluginRequestPayload(LoginPluginRequestPacket packet) {
        var id = PacketVanilla.SERVER_PACKET_PARSER.login().packetInfo(LoginPluginRequestPacket.class).id();
        return bytes(buffer -> {
            buffer.write(NetworkBuffer.VAR_INT, id);
            buffer.write(LoginPluginRequestPacket.SERIALIZER, packet);
        });
    }

    @Test
    void compressedPacketRightAfterSetCompressionInTheSameRead() throws Exception {
        var threshold = 16;
        var profile = TestServer.profile(UUID.randomUUID(), "compressed_name",
                List.of(new GameProfile.Property("textures", "x".repeat(200), null)));
        var bytes = bytes(buffer -> {
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new SetCompressionPacket(threshold), 0);
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new LoginSuccessPacket(profile, new UUID(0, 0)), threshold);
            PacketWriting.writeFramedPacket(buffer, ConnectionState.CONFIGURATION, new KeepAlivePacket(7), threshold);
        });
        var received = new ArrayList<ServerPacket>();
        var inbound = new TcpInbound(Channels.newChannel(new ByteArrayInputStream(bytes)), new InboundSink() {
            @Override
            public void accept(ServerPacket packet) {
                received.add(packet);
            }

            @Override
            public Registries registries() {
                return VanillaReference.get();
            }

            @Override
            public void closed(@Nullable Throwable error) {
            }
        });
        assertThrows(EOFException.class, inbound::run);
        assertEquals(List.of(new SetCompressionPacket(threshold), new LoginSuccessPacket(profile, new UUID(0, 0)), new KeepAlivePacket(7)),
                received);
        assertEquals(threshold, inbound.compressionThreshold());
    }

    @Test
    void loginPacketsLargerThanTheServerLimitAreRead() throws Exception {
        // Minestom 的 PacketReading 在 LOGIN 阶段限制为 8192 字节，解压后限制为 2097151 字节；原版客户端只限制帧长度为 21 位
        var large = new byte[20_000];
        large[19_999] = 42;
        var huge = new byte[3_000_000];
        huge[2_999_999] = 7;
        var hugePayload = loginPluginRequestPayload(new LoginPluginRequestPacket(2, "test:huge", huge));
        var bytes = bytes(buffer -> {
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new LoginPluginRequestPacket(1, "test:large", large), 0);
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new SetCompressionPacket(256), 0);
            buffer.write(NetworkBuffer.RAW_BYTES, compressedFrame(hugePayload, hugePayload.length));
        });
        var received = read(bytes);
        assertEquals(3, received.size());
        var first = assertInstanceOf(LoginPluginRequestPacket.class, received.get(0));
        assertArrayEquals(large, first.data());
        var third = assertInstanceOf(LoginPluginRequestPacket.class, received.get(2));
        assertArrayEquals(huge, third.data());
    }

    @Test
    void framesFollowTheVanillaLimits() {
        // 长度 VarInt 超过 3 个字节
        assertThrows(DataFormatException.class, () -> read(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01, 0x00}));
        // 长度为 0
        assertThrows(DataFormatException.class, () -> read(new byte[]{0x00}));
        // 读完数据包之后还有剩余字节
        var id = PacketVanilla.SERVER_PACKET_PARSER.login().packetInfo(SetCompressionPacket.class).id();
        var payload = bytes(buffer -> {
            buffer.write(NetworkBuffer.VAR_INT, id);
            buffer.write(NetworkBuffer.VAR_INT, 64);
            buffer.write(NetworkBuffer.BYTE, (byte) 0);
        });
        var trailing = bytes(buffer -> {
            buffer.write(NetworkBuffer.VAR_INT, payload.length);
            buffer.write(NetworkBuffer.RAW_BYTES, payload);
        });
        assertThrows(DataFormatException.class, () -> read(trailing));
    }

    @Test
    void decompressedLengthMustMatchTheDeclaredLength() {
        var payload = loginPluginRequestPayload(new LoginPluginRequestPacket(3, "test:short", new byte[100]));
        var bytes = bytes(buffer -> {
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new SetCompressionPacket(16), 0);
            buffer.write(NetworkBuffer.RAW_BYTES, compressedFrame(payload, payload.length + 10));
        });
        assertThrows(DataFormatException.class, () -> read(bytes));
    }

    @Test
    void decompressedDataLongerThanTheDeclaredLengthIsCut() throws Exception {
        // 原版把数据解压到声明长度的缓冲区，只要求写满它：数据更长时只取声明长度的部分
        var payload = loginPluginRequestPayload(new LoginPluginRequestPacket(4, "test:long", new byte[100]));
        var longer = Arrays.copyOf(payload, payload.length + 10);
        Arrays.fill(longer, payload.length, longer.length, (byte) 7);
        var bytes = bytes(buffer -> {
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new SetCompressionPacket(16), 0);
            buffer.write(NetworkBuffer.RAW_BYTES, compressedFrame(longer, payload.length));
        });
        var received = read(bytes);
        var packet = assertInstanceOf(LoginPluginRequestPacket.class, received.getLast());
        assertEquals(4, packet.messageId());
        assertArrayEquals(new byte[100], packet.data());
    }

    @Test
    void chunkBlockEntitiesTakeTheirBlockFromTheSections() throws Exception {
        var sign = Block.OAK_SIGN.withProperty("rotation", "4");
        var chest = Block.CHEST.withProperty("facing", "east");
        var text = CompoundBinaryTag.builder().putString("text", "hello").build();
        var chunk = ChunkBuilder.overworld()
                .set(1, 2, 3, sign).blockEntity(1, 2, 3, sign.withNbt(text))
                .set(4, 2, 3, chest).blockEntity(4, 2, 3, chest)
                // 区段中这个位置是石头：方块实体数据被忽略（原版客户端行为）
                .set(5, 2, 3, Block.STONE).blockEntity(5, 2, 3, Block.CHEST);
        var registries = VanillaReference.get();
        var profile = TestServer.profile(UUID.randomUUID(), "decoder", List.of());
        var bytes = bytes(buffer -> {
            PacketWriting.writeFramedPacket(buffer, ConnectionState.LOGIN, new LoginSuccessPacket(profile, new UUID(0, 0)), 0);
            PacketWriting.writeFramedPacket(buffer, ConnectionState.CONFIGURATION, new FinishConfigurationPacket(), 0);
            PacketWriting.writeFramedPacket(buffer, ConnectionState.PLAY, TestServer.joinPacket(GameMode.SURVIVAL), 0);
            PacketWriting.writeFramedPacket(buffer, ConnectionState.PLAY, chunk.packet(0, 0, registries.biome().size()), 0);
        });
        var received = read(bytes);
        var packet = assertInstanceOf(ChunkDataPacket.class, received.getLast());
        var blockEntities = packet.chunkData().blockEntities();
        // 告示牌的方块实体类型名称与方块名称不同，Minestom 的 ChunkData 会把它丢弃
        assertEquals(sign.withNbt(text), blockEntities.get(CoordConversion.chunkBlockIndex(1, 2, 3)));
        assertEquals(chest.withNbt(CompoundBinaryTag.empty()), blockEntities.get(CoordConversion.chunkBlockIndex(4, 2, 3)));
        assertEquals(2, blockEntities.size());
        var decoded = new SectionDecoder(new SectionPool()).decode(packet, 24, registries.biome().size());
        assertEquals(text, decoded.blockEntities().get(CoordConversion.chunkBlockIndex(1, 2, 3)));
    }

    @Test
    void objectChunksKeepOnlyTheNbtSentToClients() {
        // 对象级连接拿到的是服务端区块中的方块：只保留 BlockHandler 声明发给客户端的标签，与序列化之后相同
        var handler = new BlockHandler() {
            @Override
            public Collection<Tag<?>> getBlockEntityTags() {
                return List.of(Tag.String("CustomName"));
            }

            @Override
            public Key getKey() {
                return Key.key("test:chest");
            }
        };
        var nbt = CompoundBinaryTag.builder().putString("CustomName", "box").putString("Secret", "server only").build();
        var chest = Block.CHEST.withHandler(handler).withNbt(nbt);
        var chunk = ChunkBuilder.overworld().set(2, 0, 2, Block.CHEST).blockEntity(2, 0, 2, chest);
        var decoded = new SectionDecoder(new SectionPool()).decode(chunk.packet(0, 0, TestServer.biomeCount()), 24, TestServer.biomeCount());
        assertEquals(CompoundBinaryTag.builder().putString("CustomName", "box").build(),
                decoded.blockEntities().get(CoordConversion.chunkBlockIndex(2, 0, 2)));
    }

    @Test
    void identicalSectionsAreShared() {
        var pool = new SectionPool();
        var decoder = new SectionDecoder(pool);
        var biomeCount = TestServer.biomeCount();
        var chunk = ChunkBuilder.overworld().fill(-64, -1, Block.STONE).set(3, 5, 7, Block.GOLD_BLOCK);
        var first = decoder.decode(chunk.packet(0, 0, biomeCount), 24, biomeCount);
        var second = decoder.decode(chunk.packet(1, 0, biomeCount), 24, biomeCount);
        for (int i = 0; i < 24; i++) {
            assertSame(first.sections()[i].share(), second.sections()[i].share(), "section " + i);
        }
        // 四个全是石头的区段、一个带金块的区段、其余全是空气：三种不同的内容
        assertSame(first.sections()[0].share(), first.sections()[3].share());
        assertNotSame(first.sections()[3].share(), first.sections()[4].share());
        assertEquals(3, pool.size());
        assertEquals(Block.GOLD_BLOCK.stateId(), first.sections()[4].block(3, 5, 7));
        assertEquals(Block.STONE.stateId(), first.sections()[0].block(0, 0, 0));
    }

    @Test
    void sectionBytesRoundTrip() {
        var biomeCount = TestServer.biomeCount();
        var chunk = ChunkBuilder.overworld().set(1, 2, 3, Block.OAK_LOG.withProperty("axis", "x"));
        var packet = chunk.packet(0, 0, biomeCount);
        var decoded = new SectionDecoder(new SectionPool()).decode(packet, 24, biomeCount);
        assertEquals(Block.OAK_LOG.withProperty("axis", "x").stateId(), decoded.sections()[4].block(1, 2, 3));
        assertArrayEquals(chunk.data(biomeCount), packet.chunkData().data());
        assertTrue(decoded.blockEntities().isEmpty());
    }

    @Test
    void directBiomePalettesUseTheVanillaBitCount() {
        // 64 个生物群系：原版直接调色板为 ceillog2(64) = 6 位、7 个 long；Minestom 的 Palette.biomeSerializer 算出 7 位（M20）
        var biomeCount = 64;
        var ids = new int[64];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i;
        }
        // 一个区段中超过 8 种生物群系时使用直接调色板；方块为单值调色板（空气）
        var vanilla = NetworkBuffer.makeArray(buffer -> {
            buffer.write(NetworkBuffer.SHORT, (short) 0);
            buffer.write(NetworkBuffer.SHORT, (short) 0);
            buffer.write(Palette.BLOCK_SERIALIZER, Palette.blocks());
            buffer.write(NetworkBuffer.RAW_BYTES, ChunkBuilder.vanillaDirectBiomes(6, ids));
        });
        var decoded = new SectionDecoder(new SectionPool()).decode(chunkPacket(vanilla), 1, biomeCount);
        assertEquals(1 + 4 * 2 + 16 * 3, decoded.sections()[0].biome(1, 3, 2));
        assertEquals(63, decoded.sections()[0].biome(3, 3, 3));
        // Minestom 服务端为 64 个生物群系写出 7 位的直接调色板，原版客户端读错，bot 因异常结束
        var minestomBiomes = Palette.biomes();
        minestomBiomes.setAll((x, y, z) -> x + 4 * z + 16 * y);
        var minestom = NetworkBuffer.makeArray(buffer -> {
            buffer.write(NetworkBuffer.SHORT, (short) 0);
            buffer.write(NetworkBuffer.SHORT, (short) 0);
            buffer.write(Palette.BLOCK_SERIALIZER, Palette.blocks());
            buffer.write(Palette.biomeSerializer(biomeCount), minestomBiomes);
        });
        // 两个数量、方块的单值调色板（位数与值）各占 2 个字节，之后是生物群系调色板的位数
        assertEquals(7, minestom[6]);
        assertThrows(IllegalArgumentException.class, () -> new SectionDecoder(new SectionPool()).decode(chunkPacket(minestom), 1, biomeCount));
    }

    private static ChunkDataPacket chunkPacket(byte[] data) {
        var light = new LightData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(), List.of());
        return new ChunkDataPacket(0, 0, new ChunkData(Map.of(), data, Map.of()), light);
    }
}
