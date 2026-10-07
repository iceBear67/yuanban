package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.transport.Transport;
import io.ib67.yuanban.bot.transport.TransportResolver;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minestom.server.entity.MainHand;
import net.minestom.server.message.ChatMessageType;
import net.minestom.server.network.player.ClientSettings;

import java.time.Duration;
import java.util.Locale;

/**
 * 单个 bot 的选项。
 */
@Getter
@Accessors(fluent = true)
@Builder
public final class BotOptions {
    /**
     * 玩家名称，离线 UUID 由 {@code OfflinePlayer:<名称>} 生成
     */
    @NonNull
    private final String username;
    /**
     * 第一次连接使用的 Transport
     */
    @NonNull
    private final Transport transport;
    /**
     * 处理 Transfer 时为新地址选择 Transport
     */
    @NonNull
    @Builder.Default
    private final TransportResolver transportResolver = TransportResolver.TCP;

    /**
     * TCP 连接在任何阶段距离上次读到数据超过这个时间时关闭连接；{@link Duration#ZERO} 表示不检测。
     * 默认 30 秒，与原版客户端的 {@code ReadTimeoutHandler(30)} 相同
     */
    @NonNull
    @Builder.Default
    private final Duration readTimeout = Duration.ofSeconds(30);

    @NonNull
    @Builder.Default
    private final LoginPluginHandler loginPluginHandler = LoginPluginHandler.NOT_UNDERSTOOD;
    @NonNull
    @Builder.Default
    private final ResourcePackPolicy resourcePackPolicy = ResourcePackPolicy.ACCEPT;
    /**
     * 为 false 时不回应 Code of Conduct，服务端在收到接受之前不继续配置
     */
    @Builder.Default
    private final boolean acceptCodeOfConduct = true;
    /**
     * 配置阶段发送的 {@code minecraft:brand}
     */
    @NonNull
    @Builder.Default
    private final String brand = "vanilla";

    // Client Information 的内容

    @NonNull
    @Builder.Default
    private final Locale locale = Locale.US;
    @Builder.Default
    private final int viewDistance = 2;
    @NonNull
    @Builder.Default
    private final ChatMessageType chatMode = ChatMessageType.FULL;
    @Builder.Default
    private final boolean chatColors = true;
    @Builder.Default
    private final byte skinParts = 0x7F;
    @NonNull
    @Builder.Default
    private final MainHand mainHand = MainHand.RIGHT;
    @Builder.Default
    private final boolean allowServerListings = true;
    @NonNull
    @Builder.Default
    private final ClientSettings.ParticleSetting particleSetting = ClientSettings.ParticleSetting.ALL;

    /**
     * 聊天日志保存的最大条数
     */
    @Builder.Default
    private final int chatLogSize = 256;

    /**
     * Client Information 数据包的内容，文本过滤为 false
     */
    public ClientSettings clientSettings() {
        return new ClientSettings(locale, (byte) viewDistance, chatMode, chatColors, skinParts, mainHand,
                false, allowServerListings, particleSetting);
    }
}
