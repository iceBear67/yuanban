package io.ib67.yuanban.agent.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.workspace.ModuleRef;
import org.jetbrains.annotations.Nullable;

/**
 * 一个计划的结束情况。
 *
 * @param value  入口函数的返回值；没有正常返回时为 null
 * @param error  抛出的异常或强制结束的原因；正常返回时为 null
 * @param reason 停止的原因；没有被要求停止时为 null
 */
public record PlanResult(String planId, ModuleRef module, PlanRole role, Status status, @Nullable JsonElement value,
                         @Nullable String error, @Nullable String reason) {
    public enum Status {
        /**
         * 入口函数正常返回，没有被要求停止
         */
        COMPLETED,
        /**
         * 被要求停止之后正常返回
         */
        STOPPED,
        /**
         * 入口函数抛出异常
         */
        FAILED,
        /**
         * 被强制结束：停止之后没有及时返回、忙循环、信箱溢出、超出运行时间
         */
        KILLED
    }

    public boolean succeeded() {
        return status == Status.COMPLETED;
    }

    public JsonObject toJson() {
        return Json.object("plan", planId, "module", module.toString(), "role", role.wire(), "status", status.name(),
                "value", value, "error", error, "reason", reason);
    }
}
