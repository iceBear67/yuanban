package io.ib67.yuanban.bot.observe;

/**
 * 进入一个世界的原因。
 */
public enum WorldEntryCause {
    /**
     * 一个连接上的第一个 Login (play)
     */
    JOIN,
    /**
     * 重新配置之后的 Login (play)
     */
    RECONFIGURATION,
    /**
     * Respawn：死亡后重生与换维度都是这个值
     */
    RESPAWN,
    /**
     * Transfer 之后新连接上的 Login (play)
     */
    TRANSFER
}
