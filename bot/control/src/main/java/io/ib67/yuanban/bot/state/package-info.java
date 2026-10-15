/**
 * 协议状态机与移动、手部交互两个控制器共用的每个 bot 的状态：汇总全部模型的 {@link io.ib67.yuanban.bot.state.BotState}、
 * 本轮待发送的数据包、Store Cookie 保存的数据、动作的结束与取消，以及数据包中的向量与 Minestom 坐标之间的转换。
 */
@ApiStatus.Internal
package io.ib67.yuanban.bot.state;

import org.jetbrains.annotations.ApiStatus;
