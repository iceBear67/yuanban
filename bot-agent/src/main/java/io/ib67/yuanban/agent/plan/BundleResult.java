package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PlanBundle 生命周期的结束情况。只表示生命周期结束，游戏效果仍由 observer 与 postcondition 判断。
 *
 * @param facts  preflight 返回的事实与风险
 * @param data   replan、fault 附带的数据；没有时为 null
 */
public record BundleResult(String bundleId, Status status, @Nullable PlanResult primary, @Nullable PlanResult supervisor,
                           List<JsonElement> facts, @Nullable String reason, @Nullable JsonElement data) {
    public enum Status {
        /**
         * primary 正常返回
         */
        COMPLETED,
        /**
         * primary 抛出异常、被强制结束，或者按策略以 fault 结束
         */
        FAULT,
        /**
         * supervisor 或策略要求重新规划
         */
        REPLAN,
        /**
         * 被取消（用户停止、上层取消、supervisor 取消）
         */
        CANCELLED,
        /**
         * supervisor 的 fault 按策略 fail，或者 supervisor 失败且策略为 fail
         */
        FAILED,
        BUDGET_EXHAUSTED,
        DISCONNECTED
    }

    public BundleResult {
        facts = List.copyOf(facts);
    }

    public JsonObject toJson() {
        return Json.object("bundle", bundleId, "status", status.name(), "primary", primary == null ? null : primary.toJson(),
                "supervisor", supervisor == null ? null : supervisor.toJson(), "facts", Json.array(facts),
                "reason", reason, "data", data);
    }
}
