package io.ib67.yuanban.agent;

import io.ib67.yuanban.agent.ingress.GameChatOptions;
import io.ib67.yuanban.agent.model.ModelProvider;
import io.ib67.yuanban.agent.script.ScriptLimits;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.bot.BotOptions;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 一个 agent 的选项。
 */
@Getter
@Accessors(fluent = true)
@Builder
public final class AgentOptions {
    /**
     * bot 的用户名与 Transport 等
     */
    @NonNull
    private final BotOptions bot;
    @NonNull
    private final ModelProvider model;
    @NonNull
    @Builder.Default
    private final AgentProfile profile = AgentProfile.defaults();
    /**
     * workspace 目录：模块、脚本检查点、记忆与运行记录
     */
    @NonNull
    private final Path workspace;
    /**
     * trace 文件所在的目录
     */
    @NonNull
    private final Path traceDirectory;
    /**
     * agent 级预算：最大运行时间、动作数、模型回合数与 token 数；达到上限时进入 PAUSED 并等待用户
     */
    @NonNull
    @Builder.Default
    private final BudgetLimits budget = new BudgetLimits(5_000L, null, null, 500L, 5_000_000L);
    @NonNull
    @Builder.Default
    private final GameChatOptions chat = GameChatOptions.builder().build();
    @NonNull
    @Builder.Default
    private final ScriptLimits scriptLimits = ScriptLimits.DEFAULT;
    /**
     * 写入 trace 之前替换的字面值（API key、服务器密码等）
     */
    @NonNull
    @Builder.Default
    private final List<String> secrets = List.of();
    /**
     * 写入 trace 之前替换的额外模式（例如聊天中的登录命令）
     */
    @NonNull
    @Builder.Default
    private final List<Pattern> redactPatterns = List.of();
    @NonNull
    @Builder.Default
    private final InstantSource clock = InstantSource.system();
}
