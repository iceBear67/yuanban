package io.ib67.yuanban.agent.bot;

import java.time.Duration;

/**
 * 已经注册的条件等待。
 */
public interface PendingWait {
    /**
     * 等到条件成立、超时、取消或连接结束；超时时关闭等待
     */
    WaitOutcome await(Duration timeout);

    void cancel();
}
