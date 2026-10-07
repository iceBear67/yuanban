package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.Bot;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.ResourcePackPolicy;
import io.ib67.yuanban.bot.TestPeer;
import io.ib67.yuanban.bot.TestServer;
import io.ib67.yuanban.bot.transport.Transport;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.network.packet.client.common.ClientCookieResponsePacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.common.ClientResourcePackStatusPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientChunkBatchReceivedPacket;
import net.minestom.server.network.packet.client.play.ClientInputPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.server.common.CookieRequestPacket;
import net.minestom.server.network.packet.server.common.CookieStorePacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.common.ResourcePackPushPacket;
import net.minestom.server.network.packet.server.common.TransferPacket;
import net.minestom.server.network.packet.server.play.ChunkBatchFinishedPacket;
import net.minestom.server.network.packet.server.play.ChunkBatchStartPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
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
            peer.push(new PingPacket(17));
            assertEquals(17, peer.awaitSent(mark, ClientPongPacket.class, TIMEOUT).id());

            mark = peer.mark();
            peer.push(new CookieRequestPacket("minecraft:missing"));
            var missing = peer.awaitSent(mark, ClientCookieResponsePacket.class, TIMEOUT);
            assertEquals("minecraft:missing", missing.key());
            assertNull(missing.value());

            mark = peer.mark();
            peer.push(new CookieStorePacket("minecraft:token", new byte[]{1, 2, 3}));
            peer.push(new CookieRequestPacket("minecraft:token"));
            assertArrayEquals(new byte[]{1, 2, 3}, peer.awaitSent(mark, ClientCookieResponsePacket.class, TIMEOUT).value());
        });
    }

    @Test
    void resourcePackAcceptAndDecline() {
        withConfiguredBot(_ -> {
        }, (_, peer, _) -> {
            var id = UUID.randomUUID();
            var mark = peer.mark();
            peer.push(new ResourcePackPushPacket(id, "https://example.invalid/pack.zip", "", false, null));
            peer.barrier();
            var statuses = peer.sentSince(mark, ClientResourcePackStatusPacket.class).stream()
                    .map(ClientResourcePackStatusPacket::status).toList();
            assertEquals(List.of(ResourcePackStatus.ACCEPTED, ResourcePackStatus.DOWNLOADED, ResourcePackStatus.SUCCESSFULLY_LOADED), statuses);
        });
        withConfiguredBot(options -> options.resourcePackPolicy(ResourcePackPolicy.DECLINE), (_, peer, _) -> {
            var mark = peer.mark();
            peer.push(new ResourcePackPushPacket(UUID.randomUUID(), "https://example.invalid/pack.zip", "", false, null));
            peer.barrier();
            var statuses = peer.sentSince(mark, ClientResourcePackStatusPacket.class).stream()
                    .map(ClientResourcePackStatusPacket::status).toList();
            assertEquals(List.of(ResourcePackStatus.DECLINED), statuses);
        });
    }

    @Test
    void chunkBatchAndTeleport() {
        withConfiguredBot(_ -> {
        }, (_, peer, bot) -> {
            TestServer.join(peer, new Pos(0.5, 0, 0.5));
            var mark = peer.mark();
            peer.push(new ChunkBatchStartPacket());
            peer.push(new ChunkBatchFinishedPacket(9));
            var received = peer.awaitSent(mark, ClientChunkBatchReceivedPacket.class, TIMEOUT);
            assertTrue(received.targetChunksPerTick() > 0);

            mark = peer.mark();
            peer.push(new PlayerPositionAndLookPacket(5, new Vec(1, 0, 0), Vec.ZERO, 0, 0,
                    RelativeFlags.COORD | RelativeFlags.VIEW));
            peer.barrier();
            var reply = peer.sentSince(mark);
            var confirm = assertInstanceOf(ClientTeleportConfirmPacket.class, reply.getFirst());
            assertEquals(5, confirm.teleportId());
            var position = assertInstanceOf(ClientPlayerPositionAndRotationPacket.class, reply.get(1));
            assertEquals(1.5, position.position().x(), 1e-9);
            assertFalse(position.onGround());
            assertFalse(position.horizontalCollision());
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
            peer.awaitSent(mark, ClientInputPacket.class, ClientInputPacket::forward, TIMEOUT);
            peer.push(new CookieStorePacket("minecraft:session", new byte[]{9}));
            peer.push(new TransferPacket("other.invalid", 25566));
            var handshake = second.awaitSent(0, ClientHandshakePacket.class, TIMEOUT);
            assertEquals(ClientHandshakePacket.Intent.TRANSFER, handshake.intent());
            second.awaitSent(0, ClientLoginStartPacket.class, TIMEOUT);
            assertTrue(peer.closedByBot());
            // 世界重建：移动队列中的动作被取消
            assertFalse(pending.await(TIMEOUT));
            TestServer.configure(second, "replies");
            var secondMark = second.mark();
            second.push(new CookieRequestPacket("minecraft:session"));
            assertArrayEquals(new byte[]{9}, second.awaitSent(secondMark, ClientCookieResponsePacket.class, TIMEOUT).value());
            TestServer.join(second, new Pos(0.5, 0, 0.5));
            assertEquals(BotStatus.PLAY, bot.status());
            assertEquals(io.ib67.yuanban.bot.observe.WorldEntryCause.TRANSFER, bot.view().world().cause());
        });
    }

    @FunctionalInterface
    private interface BotTest {
        void run(BotRuntime runtime, TestPeer peer, Bot bot);
    }
}
