package io.ib67.yuanban.agent.tui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.Agent;
import io.ib67.yuanban.agent.AgentEvent;
import io.ib67.yuanban.agent.AgentIntent;
import io.ib67.yuanban.agent.ingress.CommandParser;
import io.ib67.yuanban.agent.ingress.UserCommand;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.notify.Notification;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 游戏外的 TUI：JLine 3 处理输入行、历史与终端能力，底部是自己绘制的状态面板（JLine {@link Status}），通知打印在输入行上方。
 * 只通过 agent 的事件流与状态快照获取信息，不直接读取 bot，也不在渲染线程上等待动作。
 * <p>
 * 输入：目标文本；{@code /stop}、{@code /pause}、{@code /resume}、{@code /status}、{@code /reply <编号> <回答>}、
 * {@code /trace}、{@code /quit}。Ctrl-C 等同 /stop，Ctrl-D 退出。
 */
public final class AgentTui implements AutoCloseable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Agent agent;
    private final Terminal terminal;
    private final LineReader reader;
    private final @Nullable Status status;
    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("agent-tui-panel").factory());
    private volatile @Nullable Flow.Subscription subscription;

    public AgentTui(Agent agent, Path historyFile) {
        this.agent = agent;
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot open the terminal", e);
        }
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("bot-agent")
                .variable(LineReader.HISTORY_FILE, historyFile)
                .build();
        this.status = Status.getStatus(terminal);
    }

    /**
     * 阻塞到 /quit 或 Ctrl-D
     */
    public void run() {
        agent.events().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                AgentTui.this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AgentEvent event) {
                onEvent(event);
            }

            @Override
            public void onError(Throwable throwable) {
                print(styled("事件流出错：" + throwable, AttributedStyle.RED));
            }

            @Override
            public void onComplete() {
            }
        });
        refresher.scheduleAtFixedRate(this::refreshPanel, 0, 500, TimeUnit.MILLISECONDS);
        print(styled("bot-agent " + agent.id() + "，trace " + agent.traceFile().toAbsolutePath()
                + "。输入目标，或 /stop /pause /resume /status /reply /trace /quit", AttributedStyle.CYAN));
        while (true) {
            String line;
            try {
                line = reader.readLine("agent> ");
            } catch (UserInterruptException e) {
                agent.command(new UserCommand(UserCommand.Kind.STOP, null, null, "tui"));
                continue;
            } catch (EndOfFileException e) {
                return;
            }
            if (line.strip().equals("/quit")) {
                return;
            }
            switch (CommandParser.tui(line)) {
                case CommandParser.Parsed.Goal(var goal) -> agent.submit(new AgentIntent(goal, AgentIntent.Source.TUI, null))
                        .thenAccept(run -> print(styled("已提交 " + run.id(), AttributedStyle.CYAN)));
                case CommandParser.Parsed.Command(var command) -> agent.command(command);
                case CommandParser.Parsed.Invalid(var reason) -> print(styled(reason, AttributedStyle.YELLOW));
            }
        }
    }

    @Override
    public void close() {
        refresher.shutdownNow();
        var current = subscription;
        if (current != null) {
            current.cancel();
        }
        if (status != null) {
            status.close();
        }
        try {
            terminal.close();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot close the terminal", e);
        }
    }

    private void onEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.Notified(var notification) -> print(format(notification));
            case AgentEvent.StatusChanged(var old, var now) -> {
                print(styled("状态 " + old + " → " + now, AttributedStyle.BLUE));
                refreshPanel();
            }
            case AgentEvent.RunEnded(var run, var outcome) -> print(styled("运行 " + run + " 结束：" + outcome.status()
                    + (outcome.summary() == null ? "" : "，" + outcome.summary()), AttributedStyle.CYAN));
            case AgentEvent.BundleStarted(var bundle, var spec) -> print(styled("启动 " + bundle + "：" + Json.string(spec, "primary")
                    + " / " + Json.string(spec, "supervisor"), AttributedStyle.CYAN));
            case AgentEvent.BundleEnded(var bundle, var result) -> print(styled(bundle + " 结束：" + Json.string(result, "status")
                    + (Json.string(result, "reason") == null ? "" : "，" + Json.string(result, "reason")), AttributedStyle.CYAN));
            case AgentEvent.Terminated(var now, var reason) -> print(styled("agent 结束：" + now + (reason == null ? "" : "，" + reason),
                    AttributedStyle.RED));
            default -> {
            }
        }
    }

    private AttributedString format(Notification notification) {
        var color = switch (notification.kind()) {
            case SAY, RESULT -> AttributedStyle.GREEN;
            case NEED_USER, RECOVERY_EXHAUSTED -> AttributedStyle.YELLOW;
            case FAULT, DAMAGE, UNEXPECTED_STATE -> AttributedStyle.RED;
            case ATTENTION, LEASE -> AttributedStyle.MAGENTA;
            case SERVER_CHAT -> AttributedStyle.CYAN;
            case STATUS -> AttributedStyle.BLUE;
            default -> AttributedStyle.WHITE;
        };
        return styled(notification.kind().wire() + " " + notification.text(), color);
    }

    private static AttributedString styled(String text, int color) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.faint()).append(LocalTime.now().format(TIME)).append(' ')
                .style(AttributedStyle.DEFAULT.foreground(color)).append(text)
                .toAttributedString();
    }

    private void print(AttributedString line) {
        reader.printAbove(line);
    }

    private void refreshPanel() {
        if (status == null) {
            return;
        }
        var snapshot = agent.snapshot();
        var bot = object(snapshot.get("bot"));
        var foreground = object(snapshot.get("foreground"));
        var primary = foreground == null ? null : object(foreground.get("primary"));
        var lease = object(snapshot.get("lease"));
        var budget = object(snapshot.get("budget"));
        var line1 = "agent " + Json.string(snapshot, "agent") + " │ " + Json.string(snapshot, "status") + " │ bot "
                + (bot == null ? "-" : Json.string(bot, "username") + " " + Json.string(bot, "status")
                + " " + position(bot.get("position")) + " hp " + bot.get("health") + " " + Json.string(bot, "dimension"));
        var line2 = "目标 " + (Json.string(snapshot, "userGoal") == null ? "-" : Json.string(snapshot, "userGoal"));
        var line3 = "前台 " + (foreground == null ? "-" : Json.string(foreground, "bundle") + " " + Json.string(foreground, "status")
                + (primary == null ? "" : " primary " + Json.string(primary, "module") + " " + Json.string(primary, "status")
                + " 执行中 " + primary.getAsJsonArray("inFlight").size()))
                + " │ lease " + (lease == null || lease.get("chain") == null ? "-" : lease.get("chain").toString());
        var line4 = "预算 " + (budget == null ? "-" : "动作 " + budget.get("actions") + " 模型 " + budget.get("modelCalls")
                + " token " + budget.get("tokens")) + " │ 待回答 " + size(snapshot.get("pendingQuestions")) + " │ trace " + Json.string(snapshot, "trace");
        status.update(List.of(panel(line1), panel(line2), panel(line3), panel(line4)));
    }

    private static AttributedString panel(String text) {
        return new AttributedString(text, AttributedStyle.DEFAULT.inverse());
    }

    private static @Nullable JsonObject object(@Nullable JsonElement element) {
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    private static String position(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return "-";
        }
        var position = element.getAsJsonObject();
        return String.format("%.1f,%.1f,%.1f", position.get("x").getAsDouble(), position.get("y").getAsDouble(),
                position.get("z").getAsDouble());
    }

    private static int size(@Nullable JsonElement element) {
        return element == null || !element.isJsonArray() ? 0 : element.getAsJsonArray().size();
    }
}
