package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.registry.DimensionInfo;
import io.ib67.yuanban.bot.registry.SyncedRegistries;
import io.ib67.yuanban.bot.registry.SyncedRegistriesBuilder;
import io.ib67.yuanban.bot.registry.TagTable;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundUpdateTagsPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 属于一条连接，按数据包到达顺序维护数据包到达调度线程之前需要的信息：
 * 同步注册表、标签、当前维度的高度与最低 y。调用不会并发（见 {@link io.ib67.yuanban.bot.transport.InboundSink}）。
 */
public final class DecodeContext {
    private final SyncedRegistriesBuilder builder = new SyncedRegistriesBuilder();
    private final SectionDecoder sections;
    private TagTable tags = TagTable.EMPTY;
    private @Nullable SyncedRegistries registries;
    private @Nullable DimensionInfo dimension;

    public DecodeContext(SectionPool pool) {
        this.sections = new SectionDecoder(pool);
    }

    /**
     * 处理一个数据包，返回随它交给调度线程的附加数据
     *
     * @return {@link DecodedChunk}、{@link TagTable}、{@link ConfigurationFinished} 或 null
     * @throws UnsupportedOperationException 收到 Encryption Request（本库不支持加密）
     */
    public @Nullable Object process(MinecraftPacket packet) {
        return switch (packet) {
            case ClientboundHelloPacket _ ->
                    throw new UnsupportedOperationException("the server requested encryption, which is not supported");
            case ClientboundLoginFinishedPacket _, ClientboundStartConfigurationPacket _ -> {
                builder.startConfiguration();
                yield null;
            }
            case ClientboundRegistryDataPacket registryData -> {
                var previous = builder.registryNames();
                if (builder.accept(registryData)) {
                    // 重新配置期间收到任何 Registry Data，之前的同步注册表与标签全部作废
                    tags = tags.without(previous);
                }
                yield null;
            }
            case ClientboundUpdateTagsPacket tagsPacket -> {
                tags = tags.apply(tagsPacket);
                yield tags;
            }
            case ClientboundFinishConfigurationPacket _ -> {
                var built = builder.build();
                registries = built;
                yield new ConfigurationFinished(built, tags);
            }
            case ClientboundLoginPacket join -> {
                enterDimension(join.getCommonPlayerSpawnInfo());
                yield null;
            }
            case ClientboundRespawnPacket respawn -> {
                enterDimension(respawn.getCommonPlayerSpawnInfo());
                yield null;
            }
            case ClientboundLevelChunkWithLightPacket chunk -> {
                var current = Objects.requireNonNull(dimension, "chunk data before Login (play)");
                yield sections.decode(chunk, current.minY(), current.height(), registries().biomeCount());
            }
            default -> null;
        };
    }

    private SyncedRegistries registries() {
        return Objects.requireNonNull(registries, "no registries before Finish Configuration");
    }

    private void enterDimension(PlayerSpawnInfo spawnInfo) {
        dimension = registries().dimensionType(spawnInfo.getDimension());
    }

    /**
     * Finish Configuration 时构建的注册表与此刻的标签
     */
    public record ConfigurationFinished(SyncedRegistries registries, TagTable tags) {
    }
}
