package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotFailureException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.transport.Transport;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.KnownPack;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFlowTest {
    private static final KnownPack MINECRAFT_CORE = new KnownPack(Key.MINECRAFT_NAMESPACE, "core",
            MinecraftCodec.CODEC.getMinecraftVersion());

    @Test
    void loginConfigurationAndJoin() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = runtime.connect(BotOptions.builder().username("alice").transport(peer).build());
            var handshake = peer.awaitSent(0, ClientIntentionPacket.class, TestServer.TIMEOUT);
            assertEquals(MinecraftCodec.CODEC.getProtocolVersion(), handshake.getProtocolVersion());
            assertEquals(HandshakeIntent.LOGIN, handshake.getIntent());
            var loginStart = peer.awaitSent(0, ServerboundHelloPacket.class, TestServer.TIMEOUT);
            assertEquals("alice", loginStart.getUsername());
            assertEquals(ProtocolMachine.offlineUuid("alice"), loginStart.getProfileId());

            TestServer.configure(peer, "alice");
            var sent = peer.sent();
            // 登录成功之后：Login Acknowledged、brand、Client Information
            var ack = indexOf(sent, ServerboundLoginAcknowledgedPacket.class);
            var brand = assertInstanceOf(ServerboundCustomPayloadPacket.class, sent.get(ack + 1));
            assertEquals(Key.key("brand"), brand.getChannel());
            assertEquals("vanilla", MinecraftTypes.readString(Unpooled.wrappedBuffer(brand.getData())));
            var settings = assertInstanceOf(ServerboundClientInformationPacket.class, sent.get(ack + 2));
            assertEquals(2, settings.getRenderDistance());
            assertFalse(settings.isTextFilteringEnabled());
            // 语言为原版的格式：语言与国家用下划线连接，全部小写
            assertEquals("en_us", settings.getLocale());
            // Known Packs：只回应 minecraft:core 且版本相同的一项
            var known = peer.sentSince(0, ServerboundSelectKnownPacks.class).getFirst();
            assertEquals(List.of(MINECRAFT_CORE), known.getKnownPacks());
            assertEquals(1, peer.sentSince(0, ServerboundFinishConfigurationPacket.class).size());

            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            assertEquals(1, peer.sentSince(0, ServerboundPlayerLoadedPacket.class).size());
            assertTrue(bot.loaded().await(Duration.ofSeconds(5)));
            assertTrue(bot.joined().await(Duration.ofSeconds(5)));
            assertEquals(BotStatus.PLAY, bot.status());
            assertEquals(TestServer.ENTITY_ID, bot.view().player().entityId());

            var pending = bot.moveForward(100);
            peer.push(new ClientboundDisconnectPacket(Component.text("bye")));
            peer.close(null);
            var thrown = assertThrows(BotDisconnectedException.class, () -> pending.await(Duration.ofSeconds(5)));
            assertEquals(Component.text("bye"), thrown.reason());
            assertEquals(BotStatus.DISCONNECTED, bot.status());
            assertEquals(Component.text("bye"), bot.disconnectReason());
            // 断开之后提交动作立即抛出异常，查询仍可读取断开时的模型
            assertThrows(BotDisconnectedException.class, () -> bot.moveForward(1));
            assertEquals(TestServer.ENTITY_ID, bot.view().player().entityId());
        }
    }

    @Test
    void knownPacksOnlyAnswersMatchingCore() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            runtime.connect(BotOptions.builder().username("bob").transport(peer).build());
            peer.awaitSent(0, ServerboundHelloPacket.class, TestServer.TIMEOUT);
            peer.push(new ClientboundLoginFinishedPacket(TestServer.profile("bob"), new UUID(0, 0)));
            var mark = peer.mark();
            peer.push(new ClientboundSelectKnownPacks(List.of(
                    new KnownPack(Key.MINECRAFT_NAMESPACE, "core", "1.0"),
                    new KnownPack("example", "pack", MinecraftCodec.CODEC.getMinecraftVersion()),
                    MINECRAFT_CORE)));
            var answer = peer.awaitSent(mark, ServerboundSelectKnownPacks.class, TestServer.TIMEOUT);
            assertEquals(List.of(MINECRAFT_CORE), answer.getKnownPacks());
        }
    }

    @Test
    void packetsBeforeTheHandshakeEndTheBot() {
        var peer = new TestPeer();
        try (var runtime = BotRuntime.create()) {
            // 不按顺序交出数据包的进程内连接：打开连接时就交出 Login Finished，这时 bot 还没有发出握手
            Transport early = (username, inbound) -> {
                var connection = peer.open(username, inbound);
                peer.push(new ClientboundLoginFinishedPacket(TestServer.profile("eager"), new UUID(0, 0)));
                return connection;
            };
            var bot = runtime.connect(BotOptions.builder().username("eager").transport(early).build());
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            var failure = assertInstanceOf(IllegalStateException.class, bot.failure());
            assertEquals("received ClientboundLoginFinishedPacket before the handshake was sent on this connection",
                    failure.getMessage());
            assertEquals(BotStatus.DISCONNECTED, bot.status());
            assertTrue(peer.sent().isEmpty());
        }
        // 排队中的握手在 bot 结束之后不再执行，这条连接被关闭
        assertTrue(peer.closedByBot());
    }

    @Test
    void packetsOfAnotherStageEndTheBot() {
        // 配置阶段收到游戏阶段的数据包
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = runtime.connect(BotOptions.builder().username("early").transport(peer).build());
            peer.awaitSent(0, ServerboundHelloPacket.class, TestServer.TIMEOUT);
            peer.push(new ClientboundLoginFinishedPacket(TestServer.profile("early"), new UUID(0, 0)));
            peer.push(new ClientboundSystemChatPacket(Component.text("too early"), false));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertEquals("received ClientboundSystemChatPacket in CONFIGURATION", bot.failure().getMessage());
        }
        // 游戏阶段收到登录阶段的数据包：状态不会退回配置阶段
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.joinedBot(runtime, peer, "late", new Pos(0.5, 0, 0.5));
            peer.push(new ClientboundLoginFinishedPacket(TestServer.profile("late"), new UUID(0, 0)));
            assertThrows(BotFailureException.class, () -> bot.await(_ -> false, TestServer.TIMEOUT));
            assertEquals("received ClientboundLoginFinishedPacket in PLAY", bot.failure().getMessage());
        }
    }

    private static int indexOf(List<?> packets, Class<?> type) {
        for (int i = 0; i < packets.size(); i++) {
            if (type.isInstance(packets.get(i))) {
                return i;
            }
        }
        throw new AssertionError(type.getSimpleName() + " not sent: " + packets);
    }
}
