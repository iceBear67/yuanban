package io.ib67.yuanban.bot.state;

import io.ib67.yuanban.bot.ActionCancelledException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 动作在调度线程上的结束与取消。移动与手部交互的动作、{@link Outbox} 通过它结束动作；实现为调度器中的 {@code ActionImpl}。
 */
public interface ActionHandle {
    /**
     * 动作是否已经结束
     */
    boolean isDone();

    /**
     * 设置取消时的清理：由持有这个动作的队列在调度线程上完成清理并调用 {@link #cancelled}
     */
    void canceller(@Nullable Consumer<String> canceller);

    /**
     * 调度线程：请求取消
     */
    @ApiStatus.Internal
    void requestCancel(String reason);

    /**
     * 调度线程：执行完毕
     */
    @ApiStatus.Internal
    void succeed();

    /**
     * 调度线程：以取消结束
     */
    @ApiStatus.Internal
    void cancelled(String reason);

    /**
     * 调度线程：以取消结束，附带原始异常作为 {@link ActionCancelledException} 的 cause
     * （例如寻路计算失败时的原始异常）
     */
    @ApiStatus.Internal
    void cancelled(String reason, Throwable cause);

    /**
     * 调度线程：连接结束或 bot 因异常结束
     */
    @ApiStatus.Internal
    void terminate(RuntimeException exception);
}
