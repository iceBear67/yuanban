package io.ib67.yuanban.agent.ingress;

import io.ib67.yuanban.agent.notify.Notification;
import io.ib67.yuanban.agent.notify.NotifyBus;

import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 把通知以短文本发到游戏聊天：只发送 profile 的 inGameNotify 中的种类与直接的回复，完整 payload 不进入聊天，避免污染服务器测试。
 * 发送在自己的虚拟线程上排队，按间隔限流，超长截断。
 */
public final class GameChatNotifier implements AutoCloseable {
    private static final String PREFIX = "[agent] ";

    private final GameChatOptions options;
    private final Set<String> kinds;
    private final Consumer<String> chat;
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final AutoCloseable subscription;
    private final Thread worker;

    /**
     * @param kinds 发到游戏中的通知种类（线上名称）
     * @param chat  发送一条聊天（bot 的 chat 请求）
     */
    public GameChatNotifier(GameChatOptions options, Set<String> kinds, NotifyBus notifications, Consumer<String> chat) {
        this.options = options;
        this.kinds = Set.copyOf(kinds);
        this.chat = chat;
        this.subscription = notifications.subscribe(this::onNotification);
        this.worker = Thread.ofVirtual().name("agent-game-notify").start(this::drain);
    }

    /**
     * 直接回复（例如 !agent status），不受种类限制
     */
    public void reply(String text) {
        if (options.enabled() && options.notifyInGame()) {
            queue.add(text);
        }
    }

    @Override
    public void close() throws Exception {
        subscription.close();
        worker.interrupt();
    }

    private void onNotification(Notification notification) {
        if (options.enabled() && options.notifyInGame() && kinds.contains(notification.kind().wire())) {
            queue.add(notification.text());
        }
    }

    private void drain() {
        while (true) {
            String text;
            try {
                text = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            var line = (PREFIX + text.replace('\n', ' '));
            if (line.length() > options.notifyMaxLength()) {
                line = line.substring(0, options.notifyMaxLength() - 1) + "…";
            }
            chat.accept(line);
            try {
                Thread.sleep(options.notifyIntervalMs());
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
