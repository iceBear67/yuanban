package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.testing.ChunkBuilder;
import io.ib67.yuanban.bot.testing.TestPeer;
import io.ib67.yuanban.bot.testing.VanillaServer;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.KnownPack;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 测试中由测试线程扮演服务端时使用的数据包脚本：在 {@link VanillaServer} 的注册表、标签与世界数据包上
 * 补上配置阶段与进入世界的流程。需要运行 bot 的测试都在这个模块中。
 */
public final class TestServer {
    /**
     * Login (play) 给出的本地玩家实体编号
     */
    public static final int ENTITY_ID = 42;
    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final Key OVERWORLD = VanillaServer.OVERWORLD;

    private static final KnownPack MINECRAFT_CORE = new KnownPack(Key.MINECRAFT_NAMESPACE, "core",
            MinecraftCodec.CODEC.getMinecraftVersion());

    private TestServer() {
    }

    // 原版数据与世界数据包，转交给 data 模块的测试夹具

    public static List<RegistryEntry> registryEntries(Key registry) {
        return VanillaServer.registryEntries(registry);
    }

    public static int registryId(Key registry, Key entry) {
        return VanillaServer.registryId(registry, entry);
    }

    public static int biomeCount() {
        return VanillaServer.biomeCount();
    }

    public static List<ClientboundRegistryDataPacket> registryData(boolean omitData) {
        return VanillaServer.registryData(omitData);
    }

    public static ClientboundUpdateTagsPacket tags() {
        return VanillaServer.tags();
    }

    public static GameProfile profile(String username) {
        return VanillaServer.profile(username);
    }

    public static ClientboundLoginPacket joinPacket(GameMode gameMode) {
        return VanillaServer.joinPacket(gameMode);
    }

    public static ClientboundRespawnPacket netherRespawn(GameMode gameMode, byte copyData) {
        return VanillaServer.netherRespawn(gameMode, copyData);
    }

    public static ChunkBuilder flatChunk() {
        return VanillaServer.flatChunk();
    }

    /**
     * 等到 bot 在这条连接上发出 Login Start（服务端在收到 Login Start 之后才回应），
     * 然后 Login Finished 到 Finish Configuration，等到 bot 回应 Finish Configuration
     */
    public static void configure(TestPeer peer, String username) {
        peer.awaitSent(0, ServerboundHelloPacket.class, TIMEOUT);
        var mark = peer.mark();
        peer.push(new ClientboundLoginFinishedPacket(VanillaServer.profile(username), new UUID(0, 0)));
        peer.push(new ClientboundSelectKnownPacks(List.of(MINECRAFT_CORE)));
        peer.push(VanillaServer.registryData(false));
        peer.push(VanillaServer.tags());
        peer.push(new ClientboundFinishConfigurationPacket());
        peer.awaitSent(mark, ServerboundFinishConfigurationPacket.class, TIMEOUT);
    }

    /**
     * 进入主世界：Login (play)、传送到 spawn、加载区块（中心 ±1）、Game Event 13，等到 bot 发送 Player Loaded
     *
     * @param chunk 为每个区块填写方块
     */
    public static void join(TestPeer peer, Pos spawn, GameMode gameMode, ChunkConsumer chunk) {
        var mark = peer.mark();
        peer.push(VanillaServer.joinPacket(gameMode));
        peer.push(new ClientboundPlayerPositionPacket(1, spawn.x(), spawn.y(), spawn.z(), 0, 0, 0, spawn.yaw(), spawn.pitch()));
        peer.push(new ClientboundSetChunkCacheCenterPacket(spawn.chunkX(), spawn.chunkZ()));
        for (int x = spawn.chunkX() - 1; x <= spawn.chunkX() + 1; x++) {
            for (int z = spawn.chunkZ() - 1; z <= spawn.chunkZ() + 1; z++) {
                var builder = VanillaServer.flatChunk();
                chunk.accept(x, z, builder);
                peer.push(builder.packet(x, z, VanillaServer.biomeCount()));
            }
        }
        peer.push(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));
        peer.awaitSent(mark, ServerboundPlayerLoadedPacket.class, TIMEOUT);
    }

    public static void join(TestPeer peer, Pos spawn) {
        join(peer, spawn, GameMode.SURVIVAL, (_, _, _) -> {
        });
    }

    /**
     * 新建 bot、完成配置、进入平地世界
     */
    public static Bot joinedBot(BotRuntime runtime, TestPeer peer, String username, Pos spawn) {
        var bot = runtime.connect(BotOptions.builder().username(username).transport(peer).build());
        configure(peer, username);
        join(peer, spawn);
        return bot;
    }

    public static Bot configuredBot(BotRuntime runtime, TestPeer peer, String username, Consumer<BotOptions.BotOptionsBuilder> options) {
        var builder = BotOptions.builder().username(username).transport(peer);
        options.accept(builder);
        var bot = runtime.connect(builder.build());
        configure(peer, username);
        return bot;
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(int chunkX, int chunkZ, ChunkBuilder chunk);
    }
}
