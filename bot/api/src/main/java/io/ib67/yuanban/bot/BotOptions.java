package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.transport.Transport;
import io.ib67.yuanban.bot.transport.TransportResolver;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /**
     * 发送时转换为原版的语言代码格式：语言与国家用下划线连接，全部小写（例如 {@code en_us}）；没有国家时只有语言
     */
    @NonNull
    @Builder.Default
    private final Locale locale = Locale.US;
    @Builder.Default
    private final int viewDistance = 2;
    @NonNull
    @Builder.Default
    private final ChatVisibility chatMode = ChatVisibility.FULL;
    @Builder.Default
    private final boolean chatColors = true;
    /**
     * 显示的皮肤部分，默认全部
     */
    @NonNull
    @Builder.Default
    private final Set<SkinPart> skinParts = Set.of(SkinPart.values());
    @NonNull
    @Builder.Default
    private final HandPreference mainHand = HandPreference.RIGHT_HAND;
    @Builder.Default
    private final boolean allowServerListings = true;
    @NonNull
    @Builder.Default
    private final ParticleStatus particleSetting = ParticleStatus.ALL;

    /**
     * 聊天日志保存的最大条数
     */
    @Builder.Default
    private final int chatLogSize = 256;

    /**
     * Client Information 数据包，文本过滤为 false
     */
    public ServerboundClientInformationPacket clientInformation() {
        var country = locale.getCountry();
        var language = country.isEmpty() ? locale.getLanguage() : locale.getLanguage() + "_" + country;
        return new ServerboundClientInformationPacket(language.toLowerCase(Locale.ROOT), viewDistance, chatMode, chatColors,
                List.copyOf(skinParts), mainHand, false, allowServerListings, particleSetting);
    }
}
