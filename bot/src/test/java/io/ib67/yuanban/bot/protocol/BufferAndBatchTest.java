package io.ib67.yuanban.bot.protocol;

import net.minestom.server.network.packet.server.common.KeepAlivePacket;
import net.minestom.server.network.packet.server.play.BundlePacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferAndBatchTest {
    private static InboundPacket packet(long id) {
        return new InboundPacket(new KeepAlivePacket(id), null);
    }

    @Test
    void bundleIsReleasedAtTheClosingDelimiter() {
        var buffer = new BundleBuffer();
        assertEquals(1, buffer.accept(packet(1)).size());
        assertNull(buffer.accept(new InboundPacket(new BundlePacket(), null)));
        assertNull(buffer.accept(packet(2)));
        assertNull(buffer.accept(packet(3)));
        var bundle = buffer.accept(new InboundPacket(new BundlePacket(), null));
        assertEquals(2, bundle.size());
        assertEquals(new KeepAlivePacket(2), bundle.getFirst().packet());
    }

    @Test
    void bundleLargerThan4096PacketsIsRejected() {
        var buffer = new BundleBuffer();
        buffer.accept(new InboundPacket(new BundlePacket(), null));
        for (int i = 0; i < BundleBuffer.MAX_BUNDLE_SIZE; i++) {
            buffer.accept(packet(i));
        }
        assertThrows(IllegalStateException.class, () -> buffer.accept(packet(-1)));
    }

    @Test
    void chunkBatchAnswerIsTwentyFiveOverMillisecondsPerChunk() {
        var tracker = new ChunkBatchTracker();
        tracker.start(0);
        // 10 毫秒 5 个区块：每区块 2 毫秒
        assertEquals(12.5f, tracker.finish(10_000_000, 5));
        tracker.start(100_000_000);
        // 再一批每区块 8 毫秒：平均 5 毫秒
        assertEquals(5f, tracker.finish(100_000_000 + 32_000_000, 4));
    }

    @Test
    void cookiesAreLimitedToFiveKibibytes() {
        var cookies = new CookieStore();
        cookies.store("minecraft:a", new byte[CookieStore.MAX_SIZE]);
        assertEquals(CookieStore.MAX_SIZE, cookies.get("minecraft:a").length);
        assertThrows(IllegalArgumentException.class, () -> cookies.store("minecraft:b", new byte[CookieStore.MAX_SIZE + 1]));
    }
}
