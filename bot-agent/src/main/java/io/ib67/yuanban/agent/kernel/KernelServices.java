package io.ib67.yuanban.agent.kernel;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentEvent;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.message.MessageFactory;
import io.ib67.yuanban.agent.message.ScriptEventBus;
import io.ib67.yuanban.agent.model.ModelProvider;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.plan.PlanManager;
import io.ib67.yuanban.agent.tool.BudgetScope;
import io.ib67.yuanban.agent.tool.LeaseManager;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.TraceStore;
import io.ib67.yuanban.agent.workspace.ModuleTester;
import io.ib67.yuanban.agent.workspace.Workspace;

import java.time.InstantSource;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AgentKernel 使用的服务。
 *
 * @param budget    agent 级预算（模型回合、token、动作、时间）
 * @param botStatus 给模型看的 bot 状态（连接、位置、生命、维度）；在 AgentKernel 线程上调用
 * @param publish   发布 {@link AgentEvent}，不能阻塞
 */
public record KernelServices(String agentId, PlanManager plans, ToolGateway gateway,
                             LeaseManager leases, NotifyBus notifications, ScriptEventBus events,
                             MessageFactory messages, Workspace workspace, ModuleTester tester, TraceStore trace,
                             ModelProvider model, AgentProfile profile, BudgetScope budget, InstantSource clock,
                             ScheduledExecutorService timer, Supplier<JsonObject> botStatus, Consumer<AgentEvent> publish) {
}
