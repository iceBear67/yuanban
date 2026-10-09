package io.ib67.yuanban.agent.bot;

import java.time.Duration;

/**
 * 已经提交的动作。
 */
public interface PendingAction {
    /**
     * 等到动作结束（以及 observer 关闭）。超时时取消动作、关闭 observer，结果为 {@link ActionOutcome.Status#TIMEOUT}
     */
    ActionOutcome await(Duration timeout);

    /**
     * 取消动作；对已结束的动作不做任何事
     */
    void cancel();
}
