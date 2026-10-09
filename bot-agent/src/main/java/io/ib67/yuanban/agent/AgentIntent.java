package io.ib67.yuanban.agent;

import org.jetbrains.annotations.Nullable;

/**
 * 用户提交的目标。
 *
 * @param sender 游戏内输入时为发送者的 UUID；其余为 null
 */
public record AgentIntent(String text, Source source, @Nullable String sender) {
    public enum Source {
        TUI,
        GAME_CHAT,
        API
    }

    public AgentIntent {
        if (text.isBlank()) {
            throw new IllegalArgumentException("an intent needs text");
        }
    }
}
