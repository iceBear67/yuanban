package io.ib67.yuanban.bot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * bot 的连接已经结束：服务端断开、{@link Bot#disconnect()} 或 {@link BotRuntime#close()}。
 * 另一种情况是 Transfer 关闭的旧连接在写出数据包之前断开：只有这些数据包所属的「写出即执行完毕」的动作
 * （一次性请求、{@code attack}、{@code interact}、{@code useItem} 的结束）以它结束，bot 继续在新连接上登录。
 * 异常信息为断开原因的纯文本；Transfer 的情况下说明连接在写出之前关闭。
 */
public final class BotDisconnectedException extends RuntimeException {
    private final Component reason;

    public BotDisconnectedException(Component reason) {
        super(PlainTextComponentSerializer.plainText().serialize(reason));
        this.reason = reason;
    }

    public Component reason() {
        return reason;
    }
}
