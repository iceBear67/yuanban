package io.ib67.yuanban.bot.protocol;

import io.ib67.yuanban.bot.BotStatus;
import io.ib67.yuanban.bot.interaction.HandController;
import io.ib67.yuanban.bot.movement.MovementController;
import io.ib67.yuanban.bot.state.BotState;
import net.kyori.adventure.text.Component;

/**
 * 协议状态机所在的 bot 提供给它的内容：模型、两个控制器，以及由数据包触发的连接状态转移、进入世界、Transfer 与断开。
 * 全部方法在调度线程上调用。
 */
public interface ProtocolHost {
    BotState state();

    MovementController movement();

    HandController hand();

    String username();

    BotStatus status();

    /**
     * 协议状态机改变连接状态：Login Finished、Finish Configuration、Start Configuration
     *
     * @throws IllegalStateException 不允许的转移
     */
    void status(BotStatus status);

    /**
     * Login (play) 处理完成
     */
    void markJoined();

    /**
     * 进入世界（Login (play) 与 Respawn，包括同一维度内保留世界的 Respawn）：第一次 tick 在一个间隔之后
     */
    void enteredWorld();

    /**
     * 服务端断开：记录原因，bot 进入 DISCONNECTED；observer 以 DISCONNECTED 关闭，
     * 未结束的动作以 {@link io.ib67.yuanban.bot.BotDisconnectedException} 结束
     */
    void disconnected(Component reason);

    /**
     * Transfer：关闭当前连接，用 TransportResolver 得到新 Transport，以握手意图 TRANSFER 重新登录，保留 CookieStore
     */
    void transfer(String host, int port);
}
