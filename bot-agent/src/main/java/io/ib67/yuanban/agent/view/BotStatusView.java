package io.ib67.yuanban.agent.view;

import org.jetbrains.annotations.Nullable;

/**
 * bot 的连接与加载状态。
 *
 * @param status           {@code LOGIN}、{@code CONFIGURATION}、{@code PLAY} 或 {@code DISCONNECTED}
 * @param loaded           自上次进入世界以来是否已经发送 Player Loaded
 * @param inWorld          是否在世界中
 * @param movementSuspended 移动是否暂停
 * @param disconnectReason 连接结束的原因（纯文本）；没有结束时为 null
 * @param failure          bot 因异常结束时的异常说明；没有时为 null
 * @param ticks            bot 已经执行的 tick 数
 */
public record BotStatusView(String status, boolean loaded, boolean inWorld, boolean movementSuspended,
                            @Nullable String disconnectReason, @Nullable String failure, long ticks) {
}
