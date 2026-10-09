package io.ib67.yuanban.agent;

/**
 * agent 的生命周期：
 * <pre>
 * CREATED → CONNECTING → JOINED → LOADED → SCRIPT_RUNNING
 *                                             │       │
 *                              control message│       │ attention/event
 *                                             ▼       ▼
 *                                      SUPERVISING / COMPACTING
 *                                             │
 *                               COMPLETED / PAUSED / STOPPED
 *                                             │
 *                                  DISCONNECTED / FAILED
 * </pre>
 */
public enum AgentStatus {
    CREATED,
    CONNECTING,
    /**
     * bot 进入了世界（{@code bot.joined()}）
     */
    JOINED,
    /**
     * bot 发送了 Player Loaded（{@code bot.loaded()}）
     */
    LOADED,
    /**
     * 前台 bundle 正在运行
     */
    SCRIPT_RUNNING,
    /**
     * 模型回合进行中
     */
    SUPERVISING,
    /**
     * 正在压缩 supervisor 上下文
     */
    COMPACTING,
    /**
     * 最近的用户目标已经完成，没有前台 bundle
     */
    COMPLETED,
    /**
     * 等待用户：用户暂停、预算耗尽、模型输出连续无效、supervisor 不能处理的事件
     */
    PAUSED,
    /**
     * 用户停止
     */
    STOPPED,
    DISCONNECTED,
    FAILED;

    public boolean terminal() {
        return this == DISCONNECTED || this == FAILED;
    }
}
