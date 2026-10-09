package io.ib67.yuanban.agent;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.ingress.UserCommand;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * 一个运行在 bot 上的 agent。
 */
public interface Agent {
    String id();

    AgentStatus status();

    /**
     * 提交用户目标；AgentKernel 接受之后完成
     */
    CompletionStage<AgentRun> submit(AgentIntent intent);

    /**
     * 用户停止：取消前台 bundle 与 background plan，清理等待中的动作
     */
    void stop();

    /**
     * stop、pause、resume、status、reply、trace
     */
    void command(UserCommand command);

    /**
     * 状态、通知、运行、bundle 与模型回合事件。订阅者处理过慢时丢弃事件，不阻塞 agent
     */
    Flow.Publisher<AgentEvent> events();

    /**
     * 当前的完整状态（bundle、计划、lease、预算、等待中的问题）
     */
    JsonObject snapshot();

    Path traceFile();
}
