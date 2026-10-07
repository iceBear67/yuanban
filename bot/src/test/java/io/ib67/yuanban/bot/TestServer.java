package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.registry.VanillaReference;
import net.minestom.server.codec.Codec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerLoadedPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.configuration.RegistryDataPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.UpdateViewPositionPacket;
import net.minestom.server.network.packet.server.play.data.PlayerSpawnInfo;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.registry.Registries;
import net.minestom.server.world.DimensionType;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 测试中由测试线程扮演服务端时使用的数据包脚本。注册表全部取自原版注册表。
 */
public final class TestServer {
    public static final int ENTITY_ID = 42;
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private TestServer() {
    }

    public static Registries vanilla() {
        return VanillaReference.get();
    }

    public static int overworldId() {
        return vanilla().dimensionType().getId(DimensionType.OVERWORLD);
    }

    public static int biomeCount() {
        return vanilla().biome().size();
    }

    /**
     * 全部同步注册表的 Registry Data
     *
     * @param excludeVanilla 为 true 时条目不带 NBT：原版注册表的条目全部来自 minecraft:core，
     *                       与 {@code Registries.registryDataPackets(registries, true)} 的内容相同（那里返回的
     *                       {@code CachedPacket} 需要服务端进程才能取出数据包）
     */
    public static List<ServerPacket> registryData(boolean excludeVanilla) {
        return Registries.registryDataPackets(vanilla(), false).stream()
                .map(packet -> (RegistryDataPacket) Objects.requireNonNull(
                        SendablePacket.extractServerPacket(ConnectionState.CONFIGURATION, packet)))
                .map(packet -> excludeVanilla
                        ? new RegistryDataPacket(packet.registryId(), packet.entries().stream()
                        .map(entry -> new RegistryDataPacket.Entry(entry.id(), null)).toList())
                        : packet)
                .map(ServerPacket.class::cast)
                .toList();
    }

    /**
     * 构造档案：先初始化 {@link Codec}，再构造 {@link GameProfile}
     */
    // FIXME(Minestom M13): GameProfile$Property、Codec、ComponentCodecs、ResolvableProfile 的静态初始化互相依赖，
    //  JVM 中第一个初始化的是 GameProfile（包括 new LoginSuccessPacket）或 ResolvableProfile 时，ResolvableProfile.Partial
    //  读到还没有赋值的 GameProfile.Property.LIST_CODEC，这几个类全部初始化失败。先初始化 Codec 就不会形成这个循环，
    //  见 MINESTOM-BUG-REPORT.md
    public static GameProfile profile(UUID uuid, String name, List<GameProfile.Property> properties) {
        try {
            MethodHandles.lookup().ensureInitialized(Codec.class);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return new GameProfile(uuid, name, properties);
    }

    /**
     * Login Success 到 Finish Configuration，等到 bot 回应 Finish Configuration
     */
    public static void configure(TestPeer peer, String username) {
        var mark = peer.mark();
        peer.push(new LoginSuccessPacket(profile(UUID.nameUUIDFromBytes(username.getBytes()), username, List.of()), new UUID(0, 0)));
        peer.push(new SelectKnownPacksPacket(List.of(SelectKnownPacksPacket.MINECRAFT_CORE)));
        peer.push(registryData(false));
        peer.push(Registries.tagsPacket(vanilla()));
        peer.push(new FinishConfigurationPacket());
        peer.awaitSent(mark, ClientFinishConfigurationPacket.class, TIMEOUT);
    }

    public static JoinGamePacket joinPacket(GameMode gameMode) {
        var spawnInfo = new PlayerSpawnInfo(overworldId(), "minecraft:overworld", 0, gameMode, null, false, false,
                null, 0, 63);
        return new JoinGamePacket(ENTITY_ID, false, List.of("minecraft:overworld", "minecraft:the_nether"), 20, 2, 2,
                false, true, false, spawnInfo, false, false);
    }

    /**
     * 重生到下界：维度名称改变，世界重新创建
     */
    public static RespawnPacket netherRespawn(GameMode gameMode, byte copyData) {
        var spawnInfo = new PlayerSpawnInfo(vanilla().dimensionType().getId(DimensionType.THE_NETHER), "minecraft:the_nether",
                0, gameMode, null, false, false, null, 0, 32);
        return new RespawnPacket(spawnInfo, copyData);
    }

    /**
     * 地面：y 小于 0 为石头，其余为空气
     */
    public static ChunkBuilder flatChunk() {
        return ChunkBuilder.overworld().fill(-64, -1, Block.STONE);
    }

    /**
     * 进入主世界：Login (play)、传送到 spawn、加载区块（中心 ±1）、Game Event 13，等到 bot 发送 Player Loaded
     *
     * @param chunk 为每个区块填写方块
     */
    public static void join(TestPeer peer, Pos spawn, GameMode gameMode, ChunkConsumer chunk) {
        var mark = peer.mark();
        peer.push(joinPacket(gameMode));
        peer.push(new PlayerPositionAndLookPacket(1, spawn, Vec.ZERO, spawn.yaw(), spawn.pitch(), 0));
        peer.push(new UpdateViewPositionPacket(spawn.chunkX(), spawn.chunkZ()));
        for (int x = spawn.chunkX() - 1; x <= spawn.chunkX() + 1; x++) {
            for (int z = spawn.chunkZ() - 1; z <= spawn.chunkZ() + 1; z++) {
                var builder = flatChunk();
                chunk.accept(x, z, builder);
                peer.push(builder.packet(x, z, biomeCount()));
            }
        }
        peer.push(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0));
        peer.awaitSent(mark, ClientPlayerLoadedPacket.class, TIMEOUT);
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
