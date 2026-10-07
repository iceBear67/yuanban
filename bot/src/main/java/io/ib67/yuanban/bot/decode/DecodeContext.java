package io.ib67.yuanban.bot.decode;

import io.ib67.yuanban.bot.registry.ClientRegistries;
import io.ib67.yuanban.bot.registry.ClientRegistriesBuilder;
import io.ib67.yuanban.bot.registry.TagTable;
import io.ib67.yuanban.bot.registry.VanillaReference;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.TagsPacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.configuration.RegistryDataPacket;
import net.minestom.server.network.packet.server.login.EncryptionRequestPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.StartConfigurationPacket;
import net.minestom.server.network.packet.server.play.data.PlayerSpawnInfo;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 属于入站线程，按数据包到达顺序维护数据包到达调度线程之前需要的信息：
 * 注册表、标签、当前维度的高度与最低 y。
 */
public final class DecodeContext {
    private final ClientRegistriesBuilder builder = new ClientRegistriesBuilder();
    private final SectionDecoder sections;
    private TagTable tags = TagTable.EMPTY;
    /**
     * TCP 连接的写线程也读取它（{@link #registries()}）
     */
    private volatile @Nullable ClientRegistries registries;
    private int height;

    public DecodeContext(SectionPool pool) {
        this.sections = new SectionDecoder(pool);
    }

    /**
     * 解码数据包使用的注册表：收到 Finish Configuration 之后为按服务端编号构建的注册表，在此之前为原版注册表
     */
    public Registries registries() {
        var built = registries;
        return built != null ? built : VanillaReference.get();
    }

    /**
     * 处理一个数据包，返回随它交给调度线程的附加数据
     *
     * @return {@link DecodedChunk}、{@link TagTable}、{@link ConfigurationFinished} 或 null
     * @throws UnsupportedOperationException 收到 Encryption Request（本库不支持加密）
     */
    public @Nullable Object process(ServerPacket packet) {
        return switch (packet) {
            case EncryptionRequestPacket _ ->
                    throw new UnsupportedOperationException("the server requested encryption, which is not supported");
            case LoginSuccessPacket _, StartConfigurationPacket _ -> {
                builder.startConfiguration();
                yield null;
            }
            case RegistryDataPacket registryData -> {
                var previous = builder.registryNames();
                if (builder.accept(registryData)) {
                    // 重新配置期间收到任何 Registry Data，之前的同步注册表与标签全部作废
                    tags = tags.without(previous);
                }
                yield null;
            }
            case TagsPacket tagsPacket -> {
                tags = tags.apply(tagsPacket);
                var built = registries;
                if (built != null) {
                    built.createTags(tags);
                }
                yield tags;
            }
            case FinishConfigurationPacket _ -> {
                var built = builder.build(tags);
                registries = built;
                yield new ConfigurationFinished(built, tags);
            }
            case JoinGamePacket join -> {
                enterDimension(join.playerSpawnInfo());
                yield null;
            }
            case RespawnPacket respawn -> {
                enterDimension(respawn.playerSpawnInfo());
                yield null;
            }
            case ChunkDataPacket chunk -> sections.decode(chunk, height / 16, registries().biome().size());
            default -> null;
        };
    }

    private void enterDimension(PlayerSpawnInfo spawnInfo) {
        var type = Objects.requireNonNull(registries().dimensionType().get(spawnInfo.dimensionType()),
                () -> "unknown dimension type id " + spawnInfo.dimensionType());
        height = type.height();
    }

    /**
     * Finish Configuration 时构建的注册表与此刻的标签
     */
    public record ConfigurationFinished(ClientRegistries registries, TagTable tags) {
    }
}
