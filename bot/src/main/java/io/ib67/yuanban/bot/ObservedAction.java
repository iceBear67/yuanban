package io.ib67.yuanban.bot;

import io.ib67.yuanban.bot.observe.Observer;
import io.ib67.yuanban.bot.observe.ObserverHandle;

import java.time.Duration;

/**
 * {@link Bot#observeDuring} 的结果：一个动作，以及只在这个动作执行期间观察的 observer。
 */
public interface ObservedAction<O extends Observer> {
    Action action();

    ObserverHandle<O> handle();

    /**
     * 等到动作结束并且 observer 关闭，返回 observer，之后任何线程都可以直接读取它的状态。
     * 动作被取消时照常返回。等待期间执行 {@link RuntimeOptions#waitPump()}；超时时不关闭 observer，也不取消动作。
     *
     * @throws BotDisconnectedException 连接结束
     * @throws BotFailureException      bot 因异常结束
     * @throws BotTimeoutException      超时
     */
    O await(Duration timeout);
}
