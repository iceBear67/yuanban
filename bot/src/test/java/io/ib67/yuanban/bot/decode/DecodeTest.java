package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.ChunkBuilder;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.registry.TagTable;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecodeTest {
    private static final int BIOMES = TestServer.biomeCount();

    /**
     * 经过 MCProtocolLib 的编码与解码，与 TCP 连接收到的数据包相同
     */
    private static ClientboundLevelChunkWithLightPacket roundTrip(ClientboundLevelChunkWithLightPacket packet) {
        var buffer = Unpooled.buffer();
        try {
            packet.serialize(buffer);
            var read = new ClientboundLevelChunkWithLightPacket(buffer);
            assertFalse(buffer.isReadable());
            return read;
        } finally {
            buffer.release();
        }
    }

    private static ClientboundLevelChunkWithLightPacket chunkPacket(byte[] data) {
        var light = new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(), List.of());
        return new ClientboundLevelChunkWithLightPacket(0, 0, data, Map.of(), new BlockEntityInfo[0], light);
    }

    @Test
    void chunkBlockEntitiesTakeTheirBlockFromTheSections() {
        var sign = Block.OAK_SIGN.withProperty("rotation", "4");
        var chest = Block.CHEST.withProperty("facing", "east");
        var text = NbtMap.builder().putString("text", "hello").build();
        var chunk = ChunkBuilder.overworld()
                .set(1, 2, 3, sign).blockEntity(1, 2, 3, BlockEntityType.SIGN, text)
                .set(4, 2, 3, chest).blockEntity(4, 2, 3, BlockEntityType.CHEST, null)
                // 区段中这个位置是石头：方块实体数据被忽略（原版客户端行为）
                .set(5, 2, 3, Block.STONE).blockEntity(5, 2, 3, BlockEntityType.CHEST, null)
                // 方块有方块实体，但类型不同：同样被忽略
                .set(6, 2, 3, Block.CHEST).blockEntity(6, 2, 3, BlockEntityType.BARREL, null)
                // 超出维度高度
                .blockEntity(7, 320, 3, BlockEntityType.CHEST, null);
        var packet = roundTrip(chunk.packet(0, 0, BIOMES));
        var decoded = new SectionDecoder(new SectionPool()).decode(packet, -64, 384, BIOMES);
        // 方块实体没有发给客户端的数据时（TAG_END）为空的 NBT
        assertEquals(Map.of(CoordConversion.chunkBlockIndex(1, 2, 3), text, CoordConversion.chunkBlockIndex(4, 2, 3), NbtMap.EMPTY),
                decoded.blockEntities());
    }

    @Test
    void identicalSectionsAreShared() {
        var pool = new SectionPool();
        var decoder = new SectionDecoder(pool);
        var chunk = ChunkBuilder.overworld().fill(-64, -1, Block.STONE).set(3, 5, 7, Block.GOLD_BLOCK);
        var first = decoder.decode(chunk.packet(0, 0, BIOMES), -64, 384, BIOMES);
        var second = decoder.decode(chunk.packet(1, 0, BIOMES), -64, 384, BIOMES);
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
        var log = Block.OAK_LOG.withProperty("axis", "x");
        var chunk = ChunkBuilder.overworld().fill(-64, -60, Block.DIRT).set(1, 2, 3, log).biome(5);
        var packet = chunk.packet(0, 0, BIOMES);
        var decoded = new SectionDecoder(new SectionPool()).decode(packet, -64, 384, BIOMES);
        assertEquals(log.stateId(), decoded.sections()[4].block(1, 2, 3));
        assertEquals(Block.DIRT.stateId(), decoded.sections()[0].block(15, 4, 15));
        assertEquals(Block.AIR.stateId(), decoded.sections()[0].block(15, 5, 15));
        assertEquals(5, decoded.sections()[23].biome(3, 3, 3));
        assertTrue(decoded.blockEntities().isEmpty());
        // 把解码得到的区段重新写出，与数据包中的字节相同
        var buffer = Unpooled.buffer();
        try {
            for (var section : decoded.sections()) {
                var shared = section.share();
                MinecraftTypes.writeChunkSection(buffer, new ChunkSection(shared.blockCount(), shared.fluidCount(),
                        shared.blocks(), shared.biomes()));
            }
            assertArrayEquals(packet.getChunkData(), ByteBufUtil.getBytes(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void trailingBytesAfterTheSectionsFail() {
        var data = ChunkBuilder.overworld().data(BIOMES);
        var decoder = new SectionDecoder(new SectionPool());
        assertThrows(IllegalStateException.class,
                () -> decoder.decode(chunkPacket(Arrays.copyOf(data, data.length + 1)), -64, 384, BIOMES));
        // 区段数按维度高度计算：数据比维度高度要求的少时读出界
        assertThrows(IndexOutOfBoundsException.class, () -> decoder.decode(chunkPacket(data), -64, 400, BIOMES));
    }

    @Test
    void encryptionRequestsAreNotSupported() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(1024);
        var request = new ClientboundHelloPacket("", keys.generateKeyPair().getPublic(), new byte[4], true);
        assertThrows(UnsupportedOperationException.class, () -> new DecodeContext(new SectionPool()).process(request));
    }

    @Test
    void chunksUseTheDimensionOfTheLastLoginOrRespawn() {
        var context = new DecodeContext(new SectionPool());
        configure(context);
        assertNull(context.process(TestServer.joinPacket(GameMode.SURVIVAL)));
        var overworld = assertInstanceOf(DecodedChunk.class, context.process(ChunkBuilder.overworld().packet(0, 0, BIOMES)));
        assertEquals(24, overworld.sections().length);
        assertNull(context.process(TestServer.netherRespawn(GameMode.SURVIVAL, (byte) 0)));
        var nether = assertInstanceOf(DecodedChunk.class, context.process(new ChunkBuilder(0, 256).packet(0, 0, BIOMES)));
        assertEquals(16, nether.sections().length);
    }

    @Test
    void reconfigurationDropsTheTagsOfTheResentRegistries() {
        var context = new DecodeContext(new SectionPool());
        var first = configure(context);
        var enchantment = Key.key("enchantment");
        var block = Key.key("block");
        assertFalse(first.tags().tagNames(enchantment).isEmpty());
        context.process(new ClientboundStartConfigurationPacket());
        // 重新配置的第一个 Registry Data 让之前的同步注册表与它们的标签作废；方块标签不属于同步注册表，保留
        var chatTypes = TestServer.registryData(false).stream()
                .filter(packet -> packet.getRegistry().equals(Key.key("chat_type")))
                .findFirst().orElseThrow();
        assertNull(context.process(chatTypes));
        var finished = assertInstanceOf(DecodeContext.ConfigurationFinished.class,
                context.process(new ClientboundFinishConfigurationPacket()));
        assertTrue(finished.tags().tagNames(enchantment).isEmpty());
        assertFalse(finished.tags().tagNames(block).isEmpty());
        assertEquals(0, finished.registries().biomeCount());
    }

    /**
     * 完整的配置阶段：Login Finished、全部同步注册表、原版标签、Finish Configuration
     */
    private static DecodeContext.ConfigurationFinished configure(DecodeContext context) {
        assertNull(context.process(new ClientboundLoginFinishedPacket(TestServer.profile("decoder"), new UUID(0, 0))));
        TestServer.registryData(true).forEach(packet -> assertNull(context.process(packet)));
        assertInstanceOf(TagTable.class, context.process(TestServer.tags()));
        var finished = assertInstanceOf(DecodeContext.ConfigurationFinished.class,
                context.process(new ClientboundFinishConfigurationPacket()));
        assertEquals(BIOMES, finished.registries().biomeCount());
        return finished;
    }
}
