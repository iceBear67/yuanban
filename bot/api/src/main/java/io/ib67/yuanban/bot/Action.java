package io.ib67.yuanban.bot;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 一个动作请求的句柄，只用于等待和取消这个请求。
 * 动作在游戏中产生的效果由 observer 观察。
 */
public interface Action {
    /**
     * 等到动作结束。等待期间执行 {@link RuntimeOptions#waitPump()}。
     *
     * @return 执行完毕返回 true，被取消返回 false
     * @throws BotDisconnectedException 连接结束
     * @throws BotFailureException      bot 因异常结束
     * @throws BotTimeoutException      超时
     * @throws IllegalStateException    在调度线程上调用
     */
    boolean await(Duration timeout);

    /**
     * 执行完毕时正常完成；被取消时以 {@link ActionCancelledException} 完成，异常信息为取消原因；
     * 连接结束或 bot 因异常结束时，以 {@link #await} 抛出的同一个异常完成。
     * 返回的是内部 future 的副本，对它调用 {@code complete}、{@code cancel} 等方法不影响动作。
     */
    CompletableFuture<Void> future();

    /**
     * 取消动作；对已结束的动作不做任何事。
     * 在其它线程调用时在下一轮调度中处理，在调度线程上调用时立即处理。
     */
    void cancel();
}
