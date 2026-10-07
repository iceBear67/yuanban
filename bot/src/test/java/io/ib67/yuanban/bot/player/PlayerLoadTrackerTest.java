package io.ib67.yuanban.bot.player;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLoadTrackerTest {
    private static final long START = 1_000_000_000L;
    private static final long AFTER_TIMEOUT = START + TimeUnit.SECONDS.toNanos(30) + 1;

    @Test
    void waitsForLevelChunksLoadStartWithoutTimingOut() {
        var tracker = new PlayerLoadTracker();
        tracker.start(START);
        // 没有收到 Game Event 13：条件满足、超过 30 秒都不发送
        assertFalse(tracker.tick(true, AFTER_TIMEOUT));
        assertFalse(tracker.loaded());
        tracker.chunksLoadStarted();
        assertTrue(tracker.tick(true, AFTER_TIMEOUT));
        assertTrue(tracker.loaded());
        assertFalse(tracker.tick(true, AFTER_TIMEOUT));
    }

    @Test
    void sendsWhenReadyOrAfterThirtySeconds() {
        var tracker = new PlayerLoadTracker();
        tracker.start(START);
        tracker.chunksLoadStarted();
        assertFalse(tracker.tick(false, START + TimeUnit.SECONDS.toNanos(30)));
        assertFalse(tracker.loaded());
        // 超时之后条件不满足也发送 Player Loaded（原版 LevelLoadTracker）
        assertTrue(tracker.tick(false, AFTER_TIMEOUT));
        assertTrue(tracker.loaded());

        // Respawn 重新开始等待，超时从这次开始计算
        tracker.start(AFTER_TIMEOUT);
        assertFalse(tracker.loaded());
        tracker.chunksLoadStarted();
        assertFalse(tracker.tick(false, AFTER_TIMEOUT + TimeUnit.SECONDS.toNanos(1)));
        assertTrue(tracker.tick(true, AFTER_TIMEOUT + TimeUnit.SECONDS.toNanos(1)));
    }

    @Test
    void ignoresLevelChunksLoadStartOutsideAWorld() {
        var tracker = new PlayerLoadTracker();
        // 开始等待之前收到的 Game Event 13 被忽略
        tracker.chunksLoadStarted();
        tracker.start(START);
        assertFalse(tracker.tick(true, START));
        tracker.chunksLoadStarted();
        tracker.reset();
        // 离开世界：不再等待
        assertFalse(tracker.tick(true, START));
        assertFalse(tracker.loaded());
    }
}
