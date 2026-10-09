package io.ib67.yuanban.agent.ingress;

import java.util.Locale;

/**
 * 解析用户输入：TUI 中以 {@code /} 开头的是命令，游戏内 {@code !agent} 之后的第一个词是命令；其余文本是目标。
 * 命令：stop、pause、resume、status、trace、reply &lt;编号&gt; &lt;回答&gt;。
 */
public final class CommandParser {
    private CommandParser() {
    }

    public sealed interface Parsed {
        record Goal(String text) implements Parsed {
        }

        record Command(UserCommand command) implements Parsed {
        }

        record Invalid(String reason) implements Parsed {
        }
    }

    /**
     * TUI 的一行输入
     */
    public static Parsed tui(String line) {
        var trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return new Parsed.Invalid("empty input");
        }
        if (!trimmed.startsWith("/")) {
            return new Parsed.Goal(trimmed);
        }
        return command(trimmed.substring(1), "tui", true);
    }

    /**
     * 游戏内 {@code !agent} 之后的文本
     *
     * @param sender 发送者的 UUID
     */
    public static Parsed game(String rest, String sender) {
        var trimmed = rest.strip();
        if (trimmed.isEmpty()) {
            return new Parsed.Invalid("usage: !agent <goal> | stop | status | reply <id> <text>");
        }
        return command(trimmed, "game:" + sender, false);
    }

    private static Parsed command(String text, String source, boolean strict) {
        var parts = text.split("\\s+", 3);
        var word = parts[0].toLowerCase(Locale.ROOT);
        return switch (word) {
            case "stop" -> simple(UserCommand.Kind.STOP, source);
            case "pause" -> simple(UserCommand.Kind.PAUSE, source);
            case "resume" -> simple(UserCommand.Kind.RESUME, source);
            case "status" -> simple(UserCommand.Kind.STATUS, source);
            case "trace" -> simple(UserCommand.Kind.TRACE, source);
            case "reply" -> parts.length < 3
                    ? new Parsed.Invalid("reply needs a question id and an answer")
                    : new Parsed.Command(new UserCommand(UserCommand.Kind.REPLY, parts[1], parts[2], source));
            default -> strict ? new Parsed.Invalid("unknown command /" + word) : new Parsed.Goal(text);
        };
    }

    private static Parsed simple(UserCommand.Kind kind, String source) {
        return new Parsed.Command(new UserCommand(kind, null, null, source));
    }
}
