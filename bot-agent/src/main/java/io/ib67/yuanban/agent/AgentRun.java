package io.ib67.yuanban.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * 一个用户目标的运行。
 */
public interface AgentRun {
    /**
     * {@code run-<n>}
     */
    String id();

    AgentIntent intent();

    /**
     * 运行结束时完成：模型以 done 结束、用户停止、新的目标取代它、或者 agent 结束
     */
    CompletionStage<Outcome> outcome();

    /**
     * @param summary 结束时对用户说的话；没有时为 null
     * @param result  最后一个前台 bundle 的结果；没有时为 null
     */
    record Outcome(Status status, @Nullable String summary, @Nullable JsonElement result) {
        public enum Status {
            COMPLETED,
            STOPPED,
            SUPERSEDED,
            FAILED
        }

        public JsonObject toJson() {
            return Json.object("status", status.name(), "summary", summary, "result", result);
        }
    }
}
