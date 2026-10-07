package io.ib67.yuanban.bot;

/**
 * bot 的连接状态。
 */
public enum BotStatus {
    /**
     * 正在登录（握手与 Login 阶段）
     */
    LOGIN,
    /**
     * 配置阶段
     */
    CONFIGURATION,
    /**
     * 游戏阶段
     */
    PLAY,
    /**
     * 连接已经结束，见 {@link Bot#disconnectReason()} 与 {@link Bot#failure()}
     */
    DISCONNECTED
}
