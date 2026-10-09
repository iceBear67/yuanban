package io.ib67.yuanban.agent.tool;

import java.util.List;
import java.util.Map;

/**
 * 网关执行的 agent 级策略，来自 agent profile 与 {@code AgentOptions}。
 *
 * @param external                      external 层各工具的处理方式；没有列出的工具允许执行
 * @param unsafe                        是否开放 {@code unsafe.send}
 * @param neverAutonomous               background plan 永远不能调用的工具
 * @param chatPerMinute                 {@code external.chat} 每分钟的上限
 * @param allowedCommands               {@code external.command} 允许的命令前缀（不带斜线）；{@code *} 表示全部
 * @param interactionsPerSecond         interaction 层每秒的调用上限
 * @param interactionsPerBlockPerMinute 同一个方块坐标每分钟的 interaction 调用上限
 * @param maxMoveDistance               移动目标与玩家的最大水平距离，也是 moveTo 寻路距离的上限
 * @param dryRun                        拦截全部有副作用的工具，返回 WOULD_EXECUTE
 */
public record ToolPolicy(Map<String, Risk> external, boolean unsafe, Capabilities neverAutonomous, int chatPerMinute,
                         List<String> allowedCommands, int interactionsPerSecond, int interactionsPerBlockPerMinute,
                         double maxMoveDistance, boolean dryRun) {
    public enum Risk {
        ALLOW,
        DENY
    }

    public ToolPolicy {
        external = Map.copyOf(external);
        allowedCommands = List.copyOf(allowedCommands);
    }

    public Risk risk(String tool) {
        return external.getOrDefault(tool, Risk.ALLOW);
    }

    public boolean commandAllowed(String command) {
        return allowedCommands.stream().anyMatch(prefix -> prefix.equals("*") || command.equals(prefix)
                || command.startsWith(prefix + " "));
    }

    public ToolPolicy withDryRun(boolean dryRun) {
        return new ToolPolicy(external, unsafe, neverAutonomous, chatPerMinute, allowedCommands, interactionsPerSecond,
                interactionsPerBlockPerMinute, maxMoveDistance, dryRun);
    }
}
