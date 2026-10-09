package io.ib67.yuanban.agent.workspace;

import java.util.Arrays;

/**
 * supervisor 不能及时回应脚本的 attention、fault 或 question 时，脚本声明的本地处理方式。
 */
public enum Fallback {
    /**
     * 暂停这个计划（它改变世界的调用等到恢复），等待用户
     */
    PAUSE_ON_UNKNOWN_FAULT("pause-on-unknown-fault"),
    /**
     * 停止这个计划
     */
    STOP_ON_UNKNOWN_FAULT("stop-on-unknown-fault"),
    /**
     * 不处理，脚本自己决定
     */
    CONTINUE("continue");

    private final String wire;

    Fallback(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Fallback fromWire(String wire) {
        return Arrays.stream(values()).filter(value -> value.wire.equals(wire)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown fallback " + wire));
    }
}
