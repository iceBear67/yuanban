package io.ib67.yuanban.agent.tool;

import io.ib67.yuanban.agent.bot.BotFacade;
import io.ib67.yuanban.agent.bot.Signal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 通过 {@link BotFacade} 执行工具。允许重新连接之后换成新的 facade：已有的订阅转到新的 facade 上。
 */
public final class FacadeBackend implements ToolBackend {
    private final AgentConfigurator configurator;
    private final Map<Consumer<Signal>, AutoCloseable> subscriptions = new ConcurrentHashMap<>();
    private volatile BotFacade facade;

    public FacadeBackend(BotFacade facade, AgentConfigurator configurator) {
        this.facade = facade;
        this.configurator = configurator;
    }

    public BotFacade facade() {
        return facade;
    }

    /**
     * 换成新的 facade（重新连接之后）
     */
    public synchronized void replace(BotFacade next) {
        facade = next;
        for (var entry : subscriptions.entrySet()) {
            closeQuietly(entry.getValue());
            entry.setValue(next.subscribe(entry.getKey()));
        }
    }

    @Override
    public ToolResult execute(ToolSpec spec, ToolInvocation invocation, CancelToken cancel, ToolPolicy policy) {
        return spec.executor().execute(new ToolContext(facade, invocation, cancel, configurator, policy));
    }

    @Override
    public synchronized AutoCloseable subscribe(Consumer<Signal> sink) {
        subscriptions.put(sink, facade.subscribe(sink));
        return () -> {
            var subscription = subscriptions.remove(sink);
            if (subscription != null) {
                subscription.close();
            }
        };
    }

    @Override
    public CompletableFuture<Void> loaded() {
        return facade.loaded();
    }

    @Override
    public long ticks() {
        return facade.ticks();
    }

    @Override
    public boolean ended() {
        return facade.ended();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            throw new IllegalStateException("failed to close a signal subscription", e);
        }
    }
}
