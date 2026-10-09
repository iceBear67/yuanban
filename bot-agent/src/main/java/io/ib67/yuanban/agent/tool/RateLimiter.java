package io.ib67.yuanban.agent.tool;

import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 滑动窗口限流：一个键在窗口内最多 limit 次。线程安全。
 */
public final class RateLimiter {
    private final InstantSource clock;
    private final Map<String, ArrayDeque<Long>> windows = new HashMap<>();

    public RateLimiter(InstantSource clock) {
        this.clock = clock;
    }

    /**
     * @return 没有超过上限时记一次并返回 true
     */
    public synchronized boolean tryAcquire(String key, int limit, long windowMillis) {
        var now = clock.millis();
        var window = windows.computeIfAbsent(key, _ -> new ArrayDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.pollFirst();
        }
        if (window.size() >= limit) {
            return false;
        }
        window.addLast(now);
        return true;
    }
}
