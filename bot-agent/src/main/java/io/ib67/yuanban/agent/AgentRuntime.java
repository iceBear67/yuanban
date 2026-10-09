package io.ib67.yuanban.agent;

import io.ib67.yuanban.agent.model.ModelProvider;
import io.ib67.yuanban.agent.script.ScriptEngine;
import io.ib67.yuanban.agent.tool.ToolBackend;
import io.ib67.yuanban.bot.BotRuntime;
import io.ib67.yuanban.bot.RuntimeOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 持有一个 {@link BotRuntime}、全部 agent 共用的 GraalJS Engine 与计时线程。
 * 关闭顺序：停止 agent（取消脚本与动作、关闭 observer、断开 bot）→ 关闭 bot runtime → 关闭模型客户端 → 关闭 Engine。
 */
public final class AgentRuntime implements AgentFactory, AutoCloseable {
    private final BotRuntime botRuntime;
    private final ScriptEngine engine;
    private final ScheduledExecutorService timer;
    private final List<AgentImpl> agents = new ArrayList<>();
    private boolean closed;

    private AgentRuntime(BotRuntime botRuntime) {
        this.botRuntime = botRuntime;
        this.engine = new ScriptEngine();
        this.timer = Executors.newScheduledThreadPool(2, Thread.ofPlatform().daemon(true).name("agent-timer-", 0).factory());
    }

    public static AgentRuntime create() {
        return new AgentRuntime(BotRuntime.create());
    }

    public static AgentRuntime create(RuntimeOptions options) {
        return new AgentRuntime(BotRuntime.create(options));
    }

    @Override
    public AgentRuntime runtime() {
        return this;
    }

    @Override
    public synchronized Agent create(AgentOptions options) {
        if (closed) {
            throw new IllegalStateException("the agent runtime is closed");
        }
        var agent = AgentImpl.live(this, options);
        agents.add(agent);
        return agent;
    }

    /**
     * 确定性重放：不连接服务器，工具结果来自 backend（{@link io.ib67.yuanban.agent.tool.RecordedBackend}），
     * 模型输出来自 options 中的模型（{@link io.ib67.yuanban.agent.model.ReplayModelProvider}）；不启用游戏内入口
     */
    public synchronized Agent replay(AgentOptions options, ToolBackend backend) {
        if (closed) {
            throw new IllegalStateException("the agent runtime is closed");
        }
        var agent = AgentImpl.replay(this, options, backend);
        agents.add(agent);
        return agent;
    }

    public BotRuntime botRuntime() {
        return botRuntime;
    }

    ScriptEngine engine() {
        return engine;
    }

    ScheduledExecutorService timer() {
        return timer;
    }

    @Override
    public void close() {
        List<AgentImpl> closing;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            closing = List.copyOf(agents);
        }
        for (var agent : closing) {
            agent.close();
        }
        botRuntime.close();
        Set<ModelProvider> models = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var agent : closing) {
            models.add(agent.model());
        }
        models.forEach(ModelProvider::close);
        timer.shutdownNow();
        engine.close();
    }
}
