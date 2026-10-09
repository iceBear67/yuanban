package io.ib67.yuanban.agent.plan;

import java.util.Locale;

/**
 * 计划在 bundle 或 agent 中的角色。角色在运行中不能改变。
 */
public enum PlanRole {
    /**
     * 立即执行的主要计划，持有 capability lease
     */
    PRIMARY(true),
    /**
     * 只读监听 fault、超时、无进展与异常状态，可以暂停 primary、请求 preflight 与 recovery
     */
    SUPERVISOR(false),
    /**
     * 一次性的只读检查，返回结构化的事实与风险
     */
    PREFLIGHT(false),
    /**
     * 处理已分类 fault 的短期计划，从暂停的 primary 处取得委托的 lease
     */
    RECOVERY(true),
    /**
     * 人格驱动的长期计划，默认只读；只在调度器授予的空闲窗口中以短期 lease 执行允许的低风险动作
     */
    BACKGROUND(true);

    private final boolean mayWrite;

    PlanRole(boolean mayWrite) {
        this.mayWrite = mayWrite;
    }

    /**
     * 这个角色取得 lease 之后可以调用改变世界的工具
     */
    public boolean mayWrite() {
        return mayWrite;
    }

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlanRole fromWire(String wire) {
        return valueOf(wire.toUpperCase(Locale.ROOT));
    }
}
