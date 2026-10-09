package io.ib67.yuanban.agent.notify;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知的种类。前八种也可以由脚本通过 {@code api.notify} 发出。
 */
public enum NotifyKind {
    PROGRESS(true),
    NEED_USER(true),
    /**
     * 服务端发来的聊天（非命令），来源标为 server
     */
    SERVER_CHAT(true),
    DAMAGE(true),
    UNEXPECTED_STATE(true),
    RECOVERY_STARTED(true),
    RECOVERY_EXHAUSTED(true),
    TRACE_MARKER(true),
    /**
     * supervisor 对用户说的话
     */
    SAY(false),
    /**
     * agent 状态变化
     */
    STATUS(false),
    /**
     * agent 自行执行的 external 层工具调用与记忆写入，在 TUI 上留下记录
     */
    ACTION(false),
    ATTENTION(false),
    FAULT(false),
    RESULT(false),
    /**
     * capability lease 的变化
     */
    LEASE(false);

    private static final Map<String, NotifyKind> BY_WIRE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(NotifyKind::wire, Function.identity()));

    private final boolean scriptAllowed;

    NotifyKind(boolean scriptAllowed) {
        this.scriptAllowed = scriptAllowed;
    }

    /**
     * 脚本可以发出这种通知
     */
    public boolean scriptAllowed() {
        return scriptAllowed;
    }

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalArgumentException 没有这个种类
     */
    public static NotifyKind fromWire(String wire) {
        var kind = BY_WIRE.get(wire);
        if (kind == null) {
            throw new IllegalArgumentException("unknown notification kind " + wire);
        }
        return kind;
    }
}
