package io.ib67.yuanban.agent.plan;

import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.message.MessageFactory;
import io.ib67.yuanban.agent.message.ScriptEventBus;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.script.ScriptEngine;
import io.ib67.yuanban.agent.script.ScriptLimits;
import io.ib67.yuanban.agent.tool.LeaseManager;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.TraceStore;
import io.ib67.yuanban.agent.workspace.ModuleTester;
import io.ib67.yuanban.agent.workspace.Workspace;
import org.jetbrains.annotations.Nullable;

import java.time.InstantSource;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 一个 agent（或一次模块测试）中全部计划共用的服务。
 *
 * @param tester    模块测试；在模块测试中运行的计划没有（不能嵌套测试）
 * @param allowDraft 是否允许启动 draft 模块（模块测试与简单模式中为 true）
 */
public record PlanServices(String agentId, TraceStore trace, MessageFactory messages, ScriptEventBus events,
                           NotifyBus notifications, ToolGateway gateway, LeaseManager leases, Workspace workspace,
                           ScriptEngine engine, ScriptLimits limits,
                           ScheduledExecutorService timer, InstantSource clock, AgentProfile profile,
                           @Nullable ModuleTester tester, boolean allowDraft) {
}
