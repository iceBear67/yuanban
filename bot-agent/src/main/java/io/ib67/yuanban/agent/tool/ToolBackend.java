package io.ib67.yuanban.agent.tool;

import io.ib67.yuanban.agent.bot.Signal;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 网关执行工具的后端：{@link FacadeBackend} 通过 {@link io.ib67.yuanban.agent.bot.BotFacade} 执行（实时或 fixture），
 * {@link RecordedBackend} 返回 trace 中记录的结果（确定性重放）。校验、权限、lease 与预算在网关中完成，与后端无关。
 */
public interface ToolBackend {
    /**
     * 在工具调用的虚拟线程上执行，阻塞到结束
     */
    ToolResult execute(ToolSpec spec, ToolInvocation invocation, CancelToken cancel, ToolPolicy policy);

    /**
     * sink 可能在 bot 调度线程上被调用，不能阻塞
     */
    AutoCloseable subscribe(Consumer<Signal> sink);

    CompletableFuture<Void> loaded();

    long ticks();

    boolean ended();
}
