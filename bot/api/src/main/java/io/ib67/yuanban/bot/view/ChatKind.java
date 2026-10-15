package io.ib67.yuanban.bot.view;

/**
 * 聊天日志中一条消息的来源。
 */
public enum ChatKind {
    /**
     * Player Chat Message
     */
    PLAYER,
    /**
     * Disguised Chat Message
     */
    DISGUISED,
    /**
     * 不带 overlay 的 System Chat Message
     */
    SYSTEM
}
