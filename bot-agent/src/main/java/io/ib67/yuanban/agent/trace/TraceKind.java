package io.ib67.yuanban.agent.trace;

import java.util.Locale;

/**
 * trace 记录的种类。一次运行的主要顺序为
 * {@code header → intent → model_request_meta → model_response_meta → script_start → tool_start → observation* → tool_end
 * → notify* → script_end → terminal_state}，其余种类穿插其中。
 */
public enum TraceKind {
    HEADER,
    /**
     * 用户目标与命令
     */
    INTENT,
    MODEL_REQUEST_META,
    /**
     * 模型的回应：决策、工具调用与 token 用量；重放时从这里取得模型输出
     */
    MODEL_RESPONSE_META,
    SCRIPT_START,
    TOOL_START,
    /**
     * observer 的结论与工具读取的快照摘要
     */
    OBSERVATION,
    TOOL_END,
    NOTIFY,
    SCRIPT_END,
    /**
     * agent 的结束状态
     */
    TERMINAL_STATE,
    /**
     * 一条 envelope
     */
    MESSAGE,
    /**
     * 一条消息交给了脚本
     */
    DELIVERED,
    /**
     * 一条消息被去重、替换或因容量丢弃
     */
    DROPPED,
    /**
     * AgentKernel 确认了一条消息
     */
    ACK,
    LEASE,
    BUDGET,
    COMPACTION,
    CHECKPOINT,
    /**
     * agent 状态变化
     */
    STATE,
    BUNDLE,
    /**
     * 脚本的标准输出与标准错误
     */
    SCRIPT_OUTPUT,
    /**
     * 异常的堆栈，由其它记录按编号引用
     */
    STACK,
    /**
     * workspace 中模块的保存、测试与弃用
     */
    MODULE,
    /**
     * 连接与 bot 生命周期
     */
    BOT;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TraceKind fromWire(String wire) {
        return valueOf(wire.toUpperCase(Locale.ROOT));
    }
}
