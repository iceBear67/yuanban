package io.ib67.yuanban.agent.ingress;

import org.jetbrains.annotations.Nullable;

/**
 * 用户命令，来自 TUI（{@code /stop}）或游戏内聊天（{@code !agent stop}）。
 *
 * @param id     reply 的问题编号；其余为 null
 * @param text   reply 的回答；其余为 null
 * @param source {@code tui} 或 {@code game:<UUID>}
 */
public record UserCommand(Kind kind, @Nullable String id, @Nullable String text, String source) {
    public enum Kind {
        /**
         * 停止当前脚本并清理等待中的动作
         */
        STOP,
        PAUSE,
        RESUME,
        STATUS,
        /**
         * 回答脚本或模型的问题
         */
        REPLY,
        /**
         * 显示 trace 文件
         */
        TRACE
    }
}
