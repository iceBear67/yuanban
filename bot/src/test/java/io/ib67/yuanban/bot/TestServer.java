package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.registry.SyncedRegistries;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.game.KnownPack;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 测试中由测试线程扮演服务端时使用的数据包脚本。注册表与标签全部取自 MCProtocolLib 自带的原版数据
 * （{@link MinecraftProtocol#loadNetworkCodec()}、{@link MinecraftProtocol#loadNetworkTags()}），
 * 转换方法与 MCProtocolLib 的 {@code ServerListener} 相同。
 */
public final class TestServer {
    public static final int ENTITY_ID = 42;
    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final Key OVERWORLD = Key.key("overworld");
    public static final Key THE_NETHER = Key.key("the_nether");

    private static final NbtMap NETWORK_CODEC = MinecraftProtocol.loadNetworkCodec();
    private static final NbtMap NETWORK_TAGS = MinecraftProtocol.loadNetworkTags();

    private TestServer() {
    }

    /**
     * 原版的一个同步注册表，按编号排列
     *
     * @throws IllegalArgumentException 原版没有这个同步注册表
     */
    public static List<RegistryEntry> registryEntries(Key registry) {
        for (var value : NETWORK_CODEC.values()) {
            var tag = (NbtMap) value;
            if (Key.key(tag.getString("type")).equals(registry)) {
                return entries(tag);
            }
        }
        throw new IllegalArgumentException("unknown registry " + registry);
    }

    /**
     * 原版同步注册表中条目的编号
     *
     * @throws IllegalArgumentException 原版没有这个条目
     */
    public static int registryId(Key registry, Key entry) {
        var entries = registryEntries(registry);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(entry)) {
                return i;
            }
        }
        throw new IllegalArgumentException("registry " + registry + " has no entry " + entry);
    }

    public static int overworldId() {
        return registryId(SyncedRegistries.DIMENSION_TYPE, OVERWORLD);
    }

    public static int netherId() {
        return registryId(SyncedRegistries.DIMENSION_TYPE, THE_NETHER);
    }

    public static int biomeCount() {
        return registryEntries(SyncedRegistries.BIOME).size();
    }

    private static List<RegistryEntry> entries(NbtMap registry) {
        var values = registry.getList("value", NbtType.COMPOUND);
        var entries = new RegistryEntry[values.size()];
        for (var value : values) {
            entries[value.getInt("id")] = new RegistryEntry(Key.key(value.getString("name")), value.getCompound("element"));
        }
        return List.of(entries);
    }

    /**
     * 全部同步注册表的 Registry Data
     *
     * @param omitData 为 true 时条目不带数据：原版注册表的条目全部来自 minecraft:core，服务端在客户端声明知道这个包时省略数据
     */
    public static List<ClientboundRegistryDataPacket> registryData(boolean omitData) {
        var packets = new ArrayList<ClientboundRegistryDataPacket>();
        for (var value : NETWORK_CODEC.values()) {
            var tag = (NbtMap) value;
            var entries = entries(tag);
            if (omitData) {
                entries = entries.stream().map(entry -> new RegistryEntry(entry.getId(), null)).toList();
            }
            packets.add(new ClientboundRegistryDataPacket(Key.key(tag.getString("type")), entries));
        }
        return packets;
    }

    /**
     * 原版全部标签的 Update Tags
     */
    public static ClientboundUpdateTagsPacket tags() {
        var tags = new HashMap<Key, Map<Key, int[]>>();
        for (var entry : NETWORK_TAGS.entrySet()) {
            var registry = (NbtMap) entry.getValue();
            var registryTags = new HashMap<Key, int[]>();
            for (var tag : registry.keySet()) {
                registryTags.put(Key.key(tag), registry.getIntArray(tag));
            }
            tags.put(Key.key(entry.getKey()), registryTags);
        }
        return new ClientboundUpdateTagsPacket(tags);
    }

    public static GameProfile profile(String username) {
        return new GameProfile(UUID.nameUUIDFromBytes(username.getBytes()), username);
    }

    /**
     * 等到 bot 在这条连接上发出 Login Start（服务端在收到 Login Start 之后才回应），
     * 然后 Login Finished 到 Finish Configuration，等到 bot 回应 Finish Configuration
     */
    public static void configure(TestPeer peer, String username) {
        // 每个 TestPeer 只对应一条连接，Login Start 从第一个发出的数据包开始找
        peer.awaitSent(0, ServerboundHelloPacket.class, TIMEOUT);
        var mark = peer.mark();
        peer.push(new ClientboundLoginFinishedPacket(profile(username), new UUID(0, 0)));
        peer.push(new ClientboundSelectKnownPacks(List.of(
                new KnownPack(Key.MINECRAFT_NAMESPACE, "core", MinecraftCodec.CODEC.getMinecraftVersion()))));
        peer.push(registryData(false));
        peer.push(tags());
        peer.push(new ClientboundFinishConfigurationPacket());
        peer.awaitSent(mark, ServerboundFinishConfigurationPacket.class, TIMEOUT);
    }

    public static ClientboundLoginPacket joinPacket(GameMode gameMode) {
        var spawnInfo = new PlayerSpawnInfo(overworldId(), OVERWORLD, 0, gameMode, null, false, false, null, 0, 63);
        return new ClientboundLoginPacket(ENTITY_ID, false, new Key[]{OVERWORLD, THE_NETHER}, 20, 2, 2,
                false, true, false, spawnInfo, false, false);
    }

    /**
     * 重生到下界：维度名称改变，世界重新创建
     *
     * @param copyData Respawn 的 Data kept：0x01 保留属性修饰符，0x02 保留元数据
     */
    public static ClientboundRespawnPacket netherRespawn(GameMode gameMode, byte copyData) {
        var spawnInfo = new PlayerSpawnInfo(netherId(), THE_NETHER, 0, gameMode, null, false, false, null, 0, 32);
        return new ClientboundRespawnPacket(spawnInfo, (copyData & 0x02) != 0, (copyData & 0x01) != 0);
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
        peer.push(new ClientboundPlayerPositionPacket(1, spawn.x(), spawn.y(), spawn.z(), 0, 0, 0, spawn.yaw(), spawn.pitch()));
        peer.push(new ClientboundSetChunkCacheCenterPacket(spawn.chunkX(), spawn.chunkZ()));
        for (int x = spawn.chunkX() - 1; x <= spawn.chunkX() + 1; x++) {
            for (int z = spawn.chunkZ() - 1; z <= spawn.chunkZ() + 1; z++) {
                var builder = flatChunk();
                chunk.accept(x, z, builder);
                peer.push(builder.packet(x, z, biomeCount()));
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
