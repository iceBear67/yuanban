package io.ib67.yuanban.agent.bot;

import org.jetbrains.annotations.Nullable;

/**
 * 条件等待的结束情况。
 *
 * @param error 连接结束、bot 因异常结束、条件求值抛出异常时的异常；其余为 null
 */
public record WaitOutcome(Status status, @Nullable Throwable error) {
    public enum Status {
        MET,
        TIMEOUT,
        CANCELLED,
        DISCONNECTED,
        FAILED
    }
}
