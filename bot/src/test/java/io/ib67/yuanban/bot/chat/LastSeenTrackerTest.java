package io.ib67.yuanban.bot.chat;

import net.minestom.server.crypto.MessageSignature;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastSeenTrackerTest {
    private static MessageSignature signature(int seed) {
        var bytes = new byte[256];
        Arrays.fill(bytes, (byte) seed);
        bytes[0] = (byte) (seed * 7);
        return new MessageSignature(bytes);
    }

    @Test
    void checksumFollowsTheWikiDescription() {
        assertEquals(1, LastSeenTracker.checksum(List.of()));
        var signatures = List.of(signature(1), signature(2), signature(3));
        int hash = 1;
        for (var signature : signatures) {
            hash = 31 * hash + Arrays.hashCode(signature.signature());
        }
        var expected = (byte) hash == 0 ? 1 : (byte) hash;
        assertEquals(expected, LastSeenTracker.checksum(signatures));
    }

    @Test
    void zeroChecksumBecomesOne() {
        // 找一个使结果为 0 的签名：初值 31 × 1 + h 的低 8 位为 0
        for (int seed = 0; seed < 4096; seed++) {
            var bytes = new byte[256];
            bytes[0] = (byte) seed;
            bytes[1] = (byte) (seed >> 8);
            var candidate = new MessageSignature(bytes);
            if ((byte) (31 + Arrays.hashCode(bytes)) == 0) {
                assertEquals(1, LastSeenTracker.checksum(List.of(candidate)));
                return;
            }
        }
        throw new AssertionError("no signature with a zero checksum found");
    }

    @Test
    void updateReportsNewMessagesAndAcknowledgedWindow() {
        var tracker = new LastSeenTracker();
        tracker.add(signature(1), true);
        tracker.add(signature(1), true);
        tracker.add(signature(2), true);
        var update = tracker.update();
        assertEquals(2, update.messageCount());
        assertEquals(2, update.acknowledged().cardinality());
        // 窗口按从旧到新的顺序排列，最新的两条位于最后
        assertEquals(LastSeenTracker.WINDOW - 2, update.acknowledged().nextSetBit(0));
        assertEquals(LastSeenTracker.checksum(List.of(signature(1), signature(2))), update.checksum());
        assertEquals(0, tracker.update().messageCount());
    }

    @Test
    void ignorePendingOnlyDropsMessagesNotYetSent() {
        var tracker = new LastSeenTracker();
        tracker.add(signature(1), true);
        tracker.add(signature(2), true);
        // 原版 ignorePending：还没有随 Chat Message 告知服务端的条目被去掉，未确认的消息数不变
        tracker.ignorePending(signature(1));
        var update = tracker.update();
        assertEquals(2, update.messageCount());
        assertEquals(1, update.acknowledged().cardinality());
        assertEquals(LastSeenTracker.checksum(List.of(signature(2))), update.checksum());
        // 已经告知过的条目保留
        tracker.ignorePending(signature(2));
        assertEquals(1, tracker.update().acknowledged().cardinality());
    }

    @Test
    void messagesNotShownCountButAreNotSeen() {
        var tracker = new LastSeenTracker();
        tracker.add(signature(1), true);
        // 原版 addPending(signature, false)：计入未确认的消息数，窗口中这个位置为空
        assertTrue(tracker.add(signature(2), false));
        assertFalse(tracker.add(signature(2), true));
        var update = tracker.update();
        assertEquals(2, update.messageCount());
        assertEquals(1, update.acknowledged().cardinality());
        assertEquals(LastSeenTracker.WINDOW - 2, update.acknowledged().nextSetBit(0));
        assertEquals(LastSeenTracker.checksum(List.of(signature(1))), update.checksum());
    }
}
