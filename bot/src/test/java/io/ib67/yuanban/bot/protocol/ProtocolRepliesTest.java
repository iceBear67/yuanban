package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.ResourcePackPolicy;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.observe.WorldEntryCause;
import io.ib67.yuanban.bot.transport.Transport;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundTransferPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.clientbound.ClientboundCookieRequestPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchStartPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolRepliesTest {
    private static final Duration TIMEOUT = TestServer.TIMEOUT;

    private static void withConfiguredBot(Consumer<BotOptions.BotOptionsBuilder> options, BotTest test) {
        try (var runtime = BotRuntime.create()) {
            var peer = new TestPeer();
            var bot = TestServer.configuredBot(runtime, peer, "replies", options);
            test.run(runtime, peer, bot);
        }
    }

    @Test
    void pingAndCookies() {
        withConfiguredBot(_ -> {
        }, (_, peer, _) -> {
            var mark = peer.mark();
            peer.push(new ClientboundPingPacket(17));
            assertEquals(17, peer.awaitSent(mark, ServerboundPongPacket.class, TIMEOUT).getId());

            mark = peer.mark();
            peer.push(new ClientboundCookieRequestPacket(Key.key("missing")));
            var missing = peer.awaitSent(mark, ServerboundCookieResponsePacket.class, TIMEOUT);
            assertEquals(Key.key("missing"), missing.getKey());
            assertNull(missing.getPayload());

            mark = peer.mark();
            peer.push(new ClientboundStoreCookiePacket(Key.key("token"), new byte[]{1, 2, 3}));
            peer.push(new ClientboundCookieRequestPacket(Key.key("token")));
            assertArrayEquals(new byte[]{1, 2, 3}, peer.awaitSent(mark, ServerboundCookieResponsePacket.class, TIMEOUT).getPayload());
        });
    }

    @Test
    void resourcePackAcceptAndDecline() {
        withConfiguredBot(_ -> {
        }, (_, peer, _) -> {
            var id = UUID.randomUUID();
            var mark = peer.mark();
            peer.push(new ClientboundResourcePackPushPacket(id, "https://example.invalid/pack.zip", "", false, null));
            peer.barrier();
            var statuses = peer.sentSince(mark, ServerboundResourcePackPacket.class).stream()
                    .map(ServerboundResourcePackPacket::getStatus).toList();
            assertEquals(List.of(ResourcePackStatus.ACCEPTED, ResourcePackStatus.DOWNLOADED, ResourcePackStatus.SUCCESSFULLY_LOADED), statuses);
        });
        withConfiguredBot(options -> options.resourcePackPolicy(ResourcePackPolicy.DECLINE), (_, peer, _) -> {
            var mark = peer.mark();
            peer.push(new ClientboundResourcePackPushPacket(UUID.randomUUID(), "https://example.invalid/pack.zip", "", false, null));
            peer.barrier();
            var statuses = peer.sentSince(mark, ServerboundResourcePackPacket.class).stream()
                    .map(ServerboundResourcePackPacket::getStatus).toList();
            assertEquals(List.of(ResourcePackStatus.DECLINED), statuses);
        });
    }

    @Test
    void chunkBatchAndTeleport() {
        withConfiguredBot(_ -> {
        }, (_, peer, bot) -> {
            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            peer.push(new ClientboundChunkBatchStartPacket());
            peer.push(new ClientboundChunkBatchFinishedPacket(9));
            var received = peer.awaitSent(mark, ServerboundChunkBatchReceivedPacket.class, TIMEOUT);
            assertTrue(received.getDesiredChunksPerTick() > 0);

            mark = peer.mark();
            // 坐标与朝向全部为相对值
            peer.push(new ClientboundPlayerPositionPacket(5, 1, 0, 0, 0, 0, 0, 0, 0,
                    PositionElement.X, PositionElement.Y, PositionElement.Z, PositionElement.Y_ROT, PositionElement.X_ROT));
            peer.barrier();
            var reply = peer.sentSince(mark);
            var confirm = assertInstanceOf(ServerboundAcceptTeleportationPacket.class, reply.getFirst());
            assertEquals(5, confirm.getId());
            var position = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, reply.get(1));
            assertEquals(1.5, position.getX(), 1e-9);
            assertFalse(position.isOnGround());
            assertFalse(position.isHorizontalCollision());
            assertEquals(1.5, bot.view().player().position().x(), 1e-9);
        });
    }

    @Test
    void transferReconnectsAndKeepsCookies() {
        var second = new TestPeer();
        Transport transfer = (username, inbound) -> second.open(username, inbound);
        withConfiguredBot(options -> options.transportResolver((host, port) -> {
            assertEquals("other.invalid", host);
            assertEquals(25566, port);
            return transfer;
        }), (_, peer, bot) -> {
            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            var pending = bot.moveForward(100);
            // 等移动开始执行之后再 Transfer（请求晚于同一轮中的入站数据包入队）
            peer.awaitSent(mark, ServerboundPlayerInputPacket.class, ServerboundPlayerInputPacket::isForward, TIMEOUT);
            peer.push(new ClientboundStoreCookiePacket(Key.key("session"), new byte[]{9}));
            peer.push(new ClientboundTransferPacket("other.invalid", 25566));
            var handshake = second.awaitSent(0, ClientIntentionPacket.class, TIMEOUT);
            assertEquals(HandshakeIntent.TRANSFER, handshake.getIntent());
            second.awaitSent(0, ServerboundHelloPacket.class, TIMEOUT);
            assertTrue(peer.closedByBot());
            // 世界重建：移动队列中的动作被取消
            assertFalse(pending.await(TIMEOUT));
            TestServer.configure(second, "replies");
            var secondMark = second.mark();
            second.push(new ClientboundCookieRequestPacket(Key.key("session")));
            assertArrayEquals(new byte[]{9}, second.awaitSent(secondMark, ServerboundCookieResponsePacket.class, TIMEOUT).getPayload());
            TestServer.join(second, new Pos(0.5, 0, 0.5));
            assertEquals(BotStatus.PLAY, bot.status());
            assertEquals(WorldEntryCause.TRANSFER, bot.view().world().cause());
        });
    }

    @FunctionalInterface
    private interface BotTest {
        void run(BotRuntime runtime, TestPeer peer, Bot bot);
    }
}
