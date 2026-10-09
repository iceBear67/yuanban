package io.ib67.yuanban.agent.ingress;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * 游戏内输入与通知的设置。
 */
@Getter
@Accessors(fluent = true)
@Builder
public final class GameChatOptions {
    public static final GameChatOptions DISABLED = builder().enabled(false).build();

    @Builder.Default
    private final boolean enabled = true;
    @NonNull
    @Builder.Default
    private final String prefix = "!agent";
    /**
     * 一条命令消息的最大字符数
     */
    @Builder.Default
    private final int maxLength = 256;
    /**
     * 每名玩家每分钟的命令上限
     */
    @Builder.Default
    private final int commandsPerMinute = 10;
    /**
     * 是否把通知以短文本发到游戏聊天（种类由 profile 的 inGameNotify 决定）
     */
    @Builder.Default
    private final boolean notifyInGame = true;
    /**
     * 两条游戏内通知之间至少相隔的毫秒数
     */
    @Builder.Default
    private final long notifyIntervalMs = 1500;
    /**
     * 游戏内通知的最大字符数
     */
    @Builder.Default
    private final int notifyMaxLength = 200;
}
