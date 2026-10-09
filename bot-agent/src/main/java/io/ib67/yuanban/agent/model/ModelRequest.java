package io.ib67.yuanban.agent.model;

import java.util.List;

/**
 * 一次模型请求。
 *
 * @param requireToolCall 要求模型调用一个函数（Chat Completions 的 {@code tool_choice: required}）
 */
public record ModelRequest(List<ModelMessage> messages, List<ToolDefinition> tools, boolean requireToolCall,
                           int maxOutputTokens) {
    public ModelRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
