package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.BotDisconnectedException;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.common.ClientPluginMessagePacket;
import net.minestom.server.network.packet.client.common.ClientSettingsPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerLoadedPacket;
import net.minestom.server.network.packet.server.common.DisconnectPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFlowTest {
    @Test
    void loginConfigurationAndJoin() {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = runtime.connect(BotOptions.builder().username("alice").transport(peer).build());
            var handshake = peer.awaitSent(0, ClientHandshakePacket.class, TestServer.TIMEOUT);
            assertEquals(MinecraftServer.PROTOCOL_VERSION, handshake.protocolVersion());
            assertEquals(ClientHandshakePacket.Intent.LOGIN, handshake.intent());
            var loginStart = peer.awaitSent(0, ClientLoginStartPacket.class, TestServer.TIMEOUT);
            assertEquals("alice", loginStart.username());
            assertEquals(ProtocolMachine.offlineUuid("alice"), loginStart.profileId());

            TestServer.configure(peer, "alice");
            var sent = peer.sent();
            // 登录成功之后：Login Acknowledged、brand、Client Information
            var ack = indexOf(sent, ClientLoginAcknowledgedPacket.class);
            assertInstanceOf(ClientPluginMessagePacket.class, sent.get(ack + 1));
            var brand = (ClientPluginMessagePacket) sent.get(ack + 1);
            assertEquals("minecraft:brand", brand.channel());
            assertEquals("vanilla", NetworkBuffer.wrap(brand.data(), 0, brand.data().length).read(NetworkBuffer.STRING));
            var settings = assertInstanceOf(ClientSettingsPacket.class, sent.get(ack + 2));
            assertEquals(2, settings.settings().viewDistance());
            assertTrue(!settings.settings().enableTextFiltering());
            // Known Packs：只回应 minecraft:core 且版本相同的一项
            var known = peer.sentSince(0, ClientSelectKnownPacksPacket.class).getFirst();
            assertEquals(List.of(SelectKnownPacksPacket.MINECRAFT_CORE), known.entries());
            assertEquals(1, peer.sentSince(0, ClientFinishConfigurationPacket.class).size());

            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            assertEquals(1, peer.sentSince(0, ClientPlayerLoadedPacket.class).size());
            assertTrue(bot.loaded().await(Duration.ofSeconds(5)));
            assertTrue(bot.joined().await(Duration.ofSeconds(5)));
            assertEquals(BotStatus.PLAY, bot.status());
            assertEquals(TestServer.ENTITY_ID, bot.view().player().entityId());

            var pending = bot.moveForward(100);
            peer.push(new DisconnectPacket(Component.text("bye")));
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
            peer.awaitSent(0, ClientLoginStartPacket.class, TestServer.TIMEOUT);
            peer.push(new net.minestom.server.network.packet.server.login.LoginSuccessPacket(
                    TestServer.profile(java.util.UUID.randomUUID(), "bob", List.of()), new java.util.UUID(0, 0)));
            var mark = peer.mark();
            peer.push(new SelectKnownPacksPacket(List.of(
                    new SelectKnownPacksPacket.Entry("minecraft", "core", "1.0"),
                    new SelectKnownPacksPacket.Entry("example", "pack", MinecraftServer.VERSION_NAME),
                    SelectKnownPacksPacket.MINECRAFT_CORE)));
            var answer = peer.awaitSent(mark, ClientSelectKnownPacksPacket.class, TestServer.TIMEOUT);
            assertEquals(List.of(SelectKnownPacksPacket.MINECRAFT_CORE), answer.entries());
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
