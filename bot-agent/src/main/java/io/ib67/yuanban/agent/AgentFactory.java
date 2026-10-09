package io.ib67.yuanban.agent;

/**
 * 创建 agent。
 */
public interface AgentFactory {
    AgentRuntime runtime();

    /**
     * 连接 bot 并启动 agent
     *
     * @throws java.io.UncheckedIOException             连接或 workspace、trace 文件失败
     * @throws IllegalArgumentException                 profile 中的 background plan 不存在或不能作为 background 启动
     */
    Agent create(AgentOptions options);
}
