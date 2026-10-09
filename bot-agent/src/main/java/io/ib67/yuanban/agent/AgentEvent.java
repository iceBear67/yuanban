package io.ib67.yuanban.agent;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.notify.Notification;
import org.jetbrains.annotations.Nullable;

/**
 * agent 发布的事件，经 {@link Agent#events()} 订阅。
 */
public sealed interface AgentEvent {
    record StatusChanged(AgentStatus old, AgentStatus now) implements AgentEvent {
    }

    record Notified(Notification notification) implements AgentEvent {
    }

    record RunStarted(String runId, AgentIntent intent) implements AgentEvent {
    }

    record RunEnded(String runId, AgentRun.Outcome outcome) implements AgentEvent {
    }

    record BundleStarted(String bundleId, JsonObject spec) implements AgentEvent {
    }

    record BundleEnded(String bundleId, JsonObject result) implements AgentEvent {
    }

    /**
     * 一次模型回合
     *
     * @param decision 应用的决策；回合没有得到有效决策时为 null
     */
    record ModelTurn(long turn, @Nullable JsonObject decision, long inputTokens, long outputTokens) implements AgentEvent {
    }

    record Terminated(AgentStatus status, @Nullable String reason) implements AgentEvent {
    }
}
