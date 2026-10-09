package io.ib67.yuanban.agent.bot;

import io.ib67.yuanban.agent.tool.Condition;
import io.ib67.yuanban.agent.tool.ObserverSpec;
import io.ib67.yuanban.agent.view.GameView;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ToolGateway 使用的 bot：{@link LiveBotFacade} 包装真实的 {@link io.ib67.yuanban.bot.Bot}，
 * {@link FixtureBotFacade} 按 fixture 给出状态与预设结果，不连接服务器。
 * <p>
 * 会阻塞的方法只在工具调用的虚拟线程上调用，不在 bot 调度线程上调用。
 */
public interface BotFacade {
    String username();

    /**
     * 进入世界时完成；连接结束时以连接结束的异常完成
     */
    CompletableFuture<Void> joined();

    /**
     * 发送 Player Loaded 时完成；连接结束时以连接结束的异常完成
     */
    CompletableFuture<Void> loaded();

    /**
     * 在同一时刻读取多项状态。阻塞
     *
     * @throws io.ib67.yuanban.bot.BotFailureException bot 因异常结束
     */
    <T> T read(Function<GameView, T> reader);

    /**
     * 提交一个动作，observer 不为 null 时在同一个调度任务中注册它（{@code Bot.observeDuring}）
     *
     * @throws io.ib67.yuanban.bot.BotDisconnectedException 连接已经结束
     * @throws io.ib67.yuanban.bot.BotFailureException      bot 因异常结束
     */
    PendingAction start(ActionSpec action, @Nullable ObserverSpec observer);

    /**
     * 注册一个条件，条件成立时等待结束
     */
    PendingWait startWait(Condition condition);

    /**
     * 订阅低成本信号。sink 可能在 bot 调度线程上被调用，不能阻塞
     *
     * @return 关闭时取消订阅
     */
    AutoCloseable subscribe(Consumer<Signal> sink);

    /**
     * bot 已经执行的 tick 数
     */
    long ticks();

    /**
     * 连接是否已经结束
     */
    boolean ended();

    /**
     * 断开连接并释放资源
     */
    void close();
}
