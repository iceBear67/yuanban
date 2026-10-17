package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.testing.TestPeer;
import io.ib67.yuanban.bot.transport.TransportResolver;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 选项的默认值与 Client Information 的内容。
 */
class BotOptionsTest {
    private static BotOptions defaults() {
        return BotOptions.builder().username("alice").transport(new TestPeer()).build();
    }

    @Test
    void onlyTheUsernameAndTransportAreRequired() {
        var options = defaults();
        assertEquals("alice", options.username());
        assertEquals(Duration.ofSeconds(30), options.readTimeout());
        assertEquals("vanilla", options.brand());
        assertEquals(256, options.chatLogSize());
        assertSame(TransportResolver.TCP, options.transportResolver());
        assertSame(LoginPluginHandler.NOT_UNDERSTOOD, options.loginPluginHandler());
        assertSame(ResourcePackPolicy.ACCEPT, options.resourcePackPolicy());
        // 默认接受 Code of Conduct，否则服务端不会继续配置
        assertEquals(true, options.acceptCodeOfConduct());
    }

    @Test
    void clientInformationUsesTheVanillaLanguageFormat() {
        var packet = defaults().clientInformation();
        assertEquals("en_us", packet.getLocale());
        assertEquals(2, packet.getRenderDistance());
        assertSame(ChatVisibility.FULL, packet.getChatVisibility());
        assertEquals(true, packet.isUseChatColors());
        assertEquals(Set.of(SkinPart.values()).stream().sorted().toList(),
                packet.getVisibleParts().stream().sorted().toList());
        assertSame(HandPreference.RIGHT_HAND, packet.getMainHand());
        // 文本过滤为 false
        assertFalse(packet.isTextFilteringEnabled());
        assertEquals(true, packet.isAllowsListing());
        assertSame(ParticleStatus.ALL, packet.getParticleStatus());
    }

    @Test
    void aLocaleWithoutACountrySendsOnlyTheLanguage() {
        var options = BotOptions.builder().username("alice").transport(new TestPeer())
                .locale(Locale.forLanguageTag("ja")).build();
        assertEquals("ja", options.clientInformation().getLocale());
    }

    @Test
    void theLanguageAndCountryAreLowercased() {
        // 语言与国家用下划线连接，全部小写
        var options = BotOptions.builder().username("alice").transport(new TestPeer())
                .locale(new Locale("ZH", "CN")).build();
        assertEquals("zh_cn", options.clientInformation().getLocale());
        var reversed = BotOptions.builder().username("alice").transport(new TestPeer())
                .locale(new Locale("pt", "BR")).build();
        assertEquals("pt_br", reversed.clientInformation().getLocale());
    }

    @Test
    void clientInformationReflectsTheChosenOptions() {
        var options = BotOptions.builder().username("alice").transport(new TestPeer())
                .viewDistance(8)
                .chatMode(ChatVisibility.SYSTEM)
                .chatColors(false)
                .skinParts(Set.of(SkinPart.CAPE))
                .mainHand(HandPreference.LEFT_HAND)
                .allowServerListings(false)
                .particleSetting(ParticleStatus.MINIMAL)
                .build();
        var packet = options.clientInformation();
        assertEquals(8, packet.getRenderDistance());
        assertSame(ChatVisibility.SYSTEM, packet.getChatVisibility());
        assertFalse(packet.isUseChatColors());
        assertEquals(List.of(SkinPart.CAPE), packet.getVisibleParts());
        assertSame(HandPreference.LEFT_HAND, packet.getMainHand());
        assertFalse(packet.isAllowsListing());
        assertSame(ParticleStatus.MINIMAL, packet.getParticleStatus());
    }

    @Test
    void theLanguageCodeIsNotAffectedByTheDefaultLocale() {
        // 使用 ROOT 转换大小写：土耳其语环境下 I 的小写仍然是 i
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            var options = BotOptions.builder().username("alice").transport(new TestPeer())
                    .locale(new Locale("HI", "IN")).build();
            assertEquals("hi_in", options.clientInformation().getLocale());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
