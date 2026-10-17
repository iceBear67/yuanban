package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.state.CookieStore;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDelimiterPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferAndBatchTest {
    private static InboundPacket packet(long id) {
        return new InboundPacket(new ClientboundKeepAlivePacket(id), null);
    }

    /**
     * Bundle Delimiter 没有字段，MCProtocolLib 只提供从缓冲区解码的构造方法
     */
    private static InboundPacket delimiter() {
        return new InboundPacket(new ClientboundDelimiterPacket(Unpooled.EMPTY_BUFFER), null);
    }

    @Test
    void bundleIsReleasedAtTheClosingDelimiter() {
        var buffer = new BundleBuffer();
        assertEquals(1, buffer.accept(packet(1)).size());
        assertNull(buffer.accept(delimiter()));
        assertNull(buffer.accept(packet(2)));
        assertNull(buffer.accept(packet(3)));
        var bundle = buffer.accept(delimiter());
        assertEquals(2, bundle.size());
        assertEquals(new ClientboundKeepAlivePacket(2), bundle.getFirst().packet());
    }

    @Test
    void bundleLargerThan4096PacketsIsRejected() {
        var buffer = new BundleBuffer();
        buffer.accept(delimiter());
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
        cookies.store(Key.key("a"), new byte[CookieStore.MAX_SIZE]);
        assertEquals(CookieStore.MAX_SIZE, cookies.get(Key.key("a")).length);
        assertThrows(IllegalArgumentException.class, () -> cookies.store(Key.key("b"), new byte[CookieStore.MAX_SIZE + 1]));
    }
}
