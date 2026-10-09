package io.ib67.yuanban.agent.tool;

import io.ib67.yuanban.agent.json.JsonSchema;

import java.time.Duration;

/**
 * 一个工具。
 *
 * @param worldChanging   需要 capability lease，由网关串行执行
 * @param sideEffect      有副作用：dry-run 时被拦截；改变世界的工具都有副作用，改变 agent 配置也有
 * @param acceptsObserver 可以通过 {@code withObserver} 带一个 observer
 * @param maxTimeout      参数 {@code timeoutMs} 的上限
 */
public record ToolSpec(String name, ToolLayer layer, String description, JsonSchema schema, boolean worldChanging,
                       boolean sideEffect, boolean acceptsObserver, Duration defaultTimeout, Duration maxTimeout,
                       ToolExecutor executor) {
    @FunctionalInterface
    public interface ToolExecutor {
        /**
         * 在工具调用的虚拟线程上执行，参数已经通过 schema 校验
         */
        ToolResult execute(ToolContext context);
    }
}
