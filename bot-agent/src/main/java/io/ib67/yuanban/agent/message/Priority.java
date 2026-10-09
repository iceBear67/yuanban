package io.ib67.yuanban.agent.message;

import java.util.Locale;

/**
 * 消息的优先级。
 */
public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Priority fromWire(String wire) {
        return valueOf(wire.toUpperCase(Locale.ROOT));
    }
}
