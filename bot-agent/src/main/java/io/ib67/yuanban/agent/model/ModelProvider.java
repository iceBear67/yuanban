package io.ib67.yuanban.agent.model;

import java.util.concurrent.CompletionStage;

/**
 * 模型供应商。实现可以使用供应商的服务端上下文压缩，AgentKernel 也只发送结构化的上下文，不依赖它。
 */
public interface ModelProvider extends AutoCloseable {
    /**
     * 用于 trace 的名称，例如 {@code openai-chat:gpt-5}
     */
    String name();

    /**
     * 上下文窗口的 token 数，用于 compaction 的阈值
     */
    int contextWindow();

    CompletionStage<ModelResponse> complete(ModelRequest request);

    @Override
    default void close() {
    }
}
