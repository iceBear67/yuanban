package io.ib67.yuanban.agent.tool;

import java.util.Locale;

/**
 * 工具按风险分层。
 */
public enum ToolLayer {
    /**
     * 只读，自动允许，返回有界快照
     */
    OBSERVE,
    /**
     * 移动与朝向，自动允许，距离与时间受限
     */
    LOCOMOTION,
    /**
     * 挖掘、放置、使用物品、攻击、交互、物品栏点击，自动允许但按坐标、动作数与频率限流
     */
    INTERACTION,
    /**
     * 聊天、命令、丢弃物品、改变 agent 配置，按策略允许或拒绝，执行时发出 action 通知
     */
    EXTERNAL,
    /**
     * 任意数据包，只在 unsafe profile 中可用
     */
    UNSAFE;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
