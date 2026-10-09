package io.ib67.yuanban.agent.tool;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * 通过校验与权限检查之后交给后端执行的调用。
 *
 * @param ordinal 这个计划的第几次调用（按 planKey 计数，从 1 开始），重放时用于对应记录
 */
public record ToolInvocation(String callId, String planId, String planKey, String tool, JsonObject args,
                             @Nullable ObserverSpec observer, Duration timeout, long ordinal) {
    public ToolInvocation {
        args = args.deepCopy();
    }

    @Override
    public JsonObject args() {
        return args.deepCopy();
    }
}
