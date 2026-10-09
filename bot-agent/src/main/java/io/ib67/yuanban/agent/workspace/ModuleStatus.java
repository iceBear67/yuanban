package io.ib67.yuanban.agent.workspace;

import java.util.Locale;

/**
 * 模块的生命周期：{@code draft → tested → deprecated}。只有 tested 的模块可以启动。
 */
public enum ModuleStatus {
    DRAFT,
    TESTED,
    DEPRECATED;

    public boolean startable() {
        return this == TESTED;
    }

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ModuleStatus fromWire(String wire) {
        return valueOf(wire.toUpperCase(Locale.ROOT));
    }
}
