package io.ib67.yuanban.agent.bot;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

/**
 * 一个动作的结束情况。
 *
 * @param cancelReason 取消原因的名称（{@code NO_PATH} 等，见 {@link CancelReasons}）；没有被取消时为 null
 * @param error        连接结束、bot 因异常结束、observer 的回调抛出异常（{@code ObserverFailureException}）时的异常；其余为 null
 * @param observation  observer 的结论；没有 observer 时为 null
 */
public record ActionOutcome(Status status, @Nullable String cancelReason, @Nullable Throwable error,
                            @Nullable JsonElement observation) {
    public enum Status {
        /**
         * bot 把这个动作的执行过程走完了；不表示游戏效果达成
         */
        COMPLETED,
        CANCELLED,
        DISCONNECTED,
        FAILED,
        TIMEOUT
    }
}
