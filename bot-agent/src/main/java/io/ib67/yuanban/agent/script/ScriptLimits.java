package io.ib67.yuanban.agent.script;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Duration;

/**
 * 自治脚本的资源限制。
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class ScriptLimits {
    public static final ScriptLimits DEFAULT = builder().build();

    /**
     * 一轮同步执行（启动、或者一次结算之后的 JS 执行）的最长时间；超过时视为忙循环，强制结束脚本
     */
    @Builder.Default
    private final Duration maxTurn = Duration.ofSeconds(2);
    /**
     * 一轮最多执行的语句数；超过时 Context 被取消
     */
    @Builder.Default
    private final long maxStatementsPerTurn = 20_000_000;
    /**
     * 同时等待中的工具调用、消息与定时器
     */
    @Builder.Default
    private final int maxPendingCalls = 64;
    /**
     * 脚本一生的工具调用总数
     */
    @Builder.Default
    private final long maxToolCalls = 100_000;
    /**
     * 一次结算交给脚本的 JSON 的最大字节数
     */
    @Builder.Default
    private final int maxResultBytes = 512 * 1024;
    /**
     * 事件、通知、检查点数据的最大字节数
     */
    @Builder.Default
    private final int maxPayloadBytes = 64 * 1024;
    /**
     * 写入 trace 的一行脚本输出的最大字节数
     */
    @Builder.Default
    private final int maxLogLineBytes = 4096;
    /**
     * 停止时等脚本自己返回的时间，之后强制结束
     */
    @Builder.Default
    private final Duration stopGrace = Duration.ofSeconds(3);
    /**
     * 脚本的最长运行时间；没有限制时为 {@link Duration#ZERO}
     */
    @Builder.Default
    private final Duration maxWallTime = Duration.ZERO;
    /**
     * 收件箱中不可丢弃的消息的上限
     */
    @Builder.Default
    private final int mailboxCapacity = 256;
    /**
     * 收件箱中信号的上限，超过时丢弃最早的
     */
    @Builder.Default
    private final int signalCapacity = 512;
    /**
     * api.sleep 的上限
     */
    @Builder.Default
    private final Duration maxSleep = Duration.ofMinutes(10);
    /**
     * 脚本线程的栈大小，限制最大调用深度：超过时脚本得到 RangeError
     */
    @Builder.Default
    private final long threadStackBytes = 4L * 1024 * 1024;
}
