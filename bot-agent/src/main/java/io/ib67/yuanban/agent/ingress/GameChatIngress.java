package io.ib67.yuanban.agent.ingress;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentIntent;
import io.ib67.yuanban.agent.bot.Signal;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.kernel.AgentKernel;
import io.ib67.yuanban.agent.notify.NotifyBus;
import io.ib67.yuanban.agent.notify.NotifyKind;
import io.ib67.yuanban.agent.tool.RateLimiter;
import io.ib67.yuanban.agent.tool.ToolGateway;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceStore;

import java.time.InstantSource;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * 游戏内输入：订阅聊天信号，在自己的虚拟线程上解析（信号回调只把聊天放进队列）。
 * <ul>
 *     <li>以前缀（默认 {@code !agent}）开头的玩家聊天：任何玩家都可以下达，限制长度与每分钟次数，解析为目标或命令；</li>
 *     <li>其余聊天：作为观察（来源标为 server，签名未校验）交给 AgentKernel 与 TUI，不能改变权限，也不会成为系统提示。</li>
 * </ul>
 */
public final class GameChatIngress implements AutoCloseable {
    private final GameChatOptions options;
    private final AgentKernel kernel;
    private final GameChatNotifier notifier;
    private final NotifyBus notifications;
    private final TraceStore trace;
    private final Supplier<String> self;
    private final RateLimiter limiter;
    private final LinkedBlockingQueue<JsonObject> queue = new LinkedBlockingQueue<>();
    private final AutoCloseable subscription;
    private final Thread worker;

    /**
     * @param self bot 自己的 UUID（忽略自己发出的聊天）；不在世界中时为 null
     */
    public GameChatIngress(GameChatOptions options, ToolGateway gateway, AgentKernel kernel, GameChatNotifier notifier,
                           NotifyBus notifications, TraceStore trace, InstantSource clock, Supplier<String> self) {
        this.options = options;
        this.kernel = kernel;
        this.notifier = notifier;
        this.notifications = notifications;
        this.trace = trace;
        this.self = self;
        this.limiter = new RateLimiter(clock);
        this.subscription = gateway.subscribeSignals(this::onSignal);
        this.worker = Thread.ofVirtual().name("agent-game-ingress").start(this::drain);
    }

    @Override
    public void close() throws Exception {
        subscription.close();
        worker.interrupt();
    }

    private void onSignal(Signal signal) {
        if (signal.topic().equals("chat")) {
            queue.add(signal.data());
        }
    }

    private void drain() {
        while (true) {
            JsonObject line;
            try {
                line = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            handle(line);
        }
    }

    private void handle(JsonObject line) {
        var text = Json.requireString(line, "text");
        var sender = Json.string(line, "sender");
        if (sender != null && sender.equals(self.get())) {
            return;
        }
        var prefix = options.prefix();
        var isCommand = options.enabled() && (text.equals(prefix) || text.startsWith(prefix + " "));
        if (!isCommand) {
            kernel.serverChat(line);
            notifications.publish(NotifyKind.SERVER_CHAT, "server", (Json.string(line, "senderName") == null ? ""
                    : "<" + Json.string(line, "senderName") + "> ") + text, line);
            return;
        }
        if (!"PLAYER".equals(Json.string(line, "kind")) || sender == null) {
            reject(line, "not a player chat message");
            return;
        }
        if (text.length() > options.maxLength()) {
            reject(line, "longer than " + options.maxLength() + " characters");
            notifier.reply("命令太长");
            return;
        }
        if (!limiter.tryAcquire("game:" + sender, options.commandsPerMinute(), 60_000)) {
            reject(line, "rate limited");
            notifier.reply("命令太频繁");
            return;
        }
        trace.append(TraceKind.INTENT, Json.object("event", "game_command", "sender", sender, "text", text));
        switch (CommandParser.game(text.substring(prefix.length()), sender)) {
            case CommandParser.Parsed.Goal(var goal) -> kernel.submit(new AgentIntent(goal, AgentIntent.Source.GAME_CHAT, sender))
                    .thenAccept(run -> notifier.reply("收到目标 " + run.id()));
            case CommandParser.Parsed.Command(var command) -> {
                kernel.command(command);
                if (command.kind() == UserCommand.Kind.STATUS) {
                    notifier.reply(kernel.status().name());
                }
            }
            case CommandParser.Parsed.Invalid(var reason) -> notifier.reply(reason);
        }
    }

    private void reject(JsonObject line, String reason) {
        trace.append(TraceKind.INTENT, Json.object("event", "game_command_rejected", "reason", reason, "line", line));
    }
}
