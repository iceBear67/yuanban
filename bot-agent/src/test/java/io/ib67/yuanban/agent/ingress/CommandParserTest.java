package io.ib67.yuanban.agent.ingress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommandParserTest {
    @Test
    void tuiCommandsAndGoals() {
        var stop = assertInstanceOf(CommandParser.Parsed.Command.class, CommandParser.tui("/stop"));
        assertEquals(UserCommand.Kind.STOP, stop.command().kind());
        var reply = assertInstanceOf(CommandParser.Parsed.Command.class, CommandParser.tui("/reply msg-3 去左边那个"));
        assertEquals("msg-3", reply.command().id());
        assertEquals("去左边那个", reply.command().text());
        assertEquals("走到 10 64 10", assertInstanceOf(CommandParser.Parsed.Goal.class, CommandParser.tui(" 走到 10 64 10 ")).text());
        assertInstanceOf(CommandParser.Parsed.Invalid.class, CommandParser.tui("/teleport"));
    }

    @Test
    void gameCommandsAndGoals() {
        var stop = assertInstanceOf(CommandParser.Parsed.Command.class, CommandParser.game(" stop", "uuid"));
        assertEquals(UserCommand.Kind.STOP, stop.command().kind());
        assertEquals("game:uuid", stop.command().source());
        assertEquals("follow me", assertInstanceOf(CommandParser.Parsed.Goal.class, CommandParser.game(" follow me", "uuid")).text());
        assertInstanceOf(CommandParser.Parsed.Invalid.class, CommandParser.game("  ", "uuid"));
    }
}
