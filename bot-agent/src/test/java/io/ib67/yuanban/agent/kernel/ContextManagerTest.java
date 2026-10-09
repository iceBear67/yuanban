package io.ib67.yuanban.agent.kernel;

import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.model.ModelMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextManagerTest {
    private static ContextManager.Turn turn(long number, String text) {
        var messages = List.<ModelMessage>of(new ModelMessage.User(text), new ModelMessage.Assistant("ok", List.of()));
        return new ContextManager.Turn(number, messages, ContextManager.count(text), number * 10, number * 10 + 5);
    }

    @Test
    void pressureFollowsTheThresholds() {
        var context = new ContextManager(1000, "pinned", List.of());
        assertEquals(ContextManager.Pressure.NORMAL, context.pressure(599));
        assertEquals(ContextManager.Pressure.SOFT, context.pressure(600));
        assertEquals(ContextManager.Pressure.HARD, context.pressure(800));
        assertEquals(ContextManager.Pressure.EMERGENCY, context.pressure(920));
    }

    @Test
    void commitFoldsOldTurnsAndKeepsTheRecentOnes() {
        var context = new ContextManager(10_000, "pinned", List.of());
        for (var i = 1; i <= 5; i++) {
            context.append(turn(i, "turn " + i));
        }
        assertEquals(3, context.compactable());
        var candidate = new ContextManager.Candidate(Json.object("schema", "supervisor-context/1"), 3, 42);
        assertTrue(context.commit(candidate, 42));
        assertEquals(2, context.turns().size());
        assertEquals(4, context.turns().getFirst().number());
        assertTrue(context.hasSummary());
        var messages = context.messages("current", false);
        assertTrue(messages.stream().anyMatch(message -> message.text().contains("compaction")));
        assertEquals("current", messages.getLast().text());
    }

    @Test
    void aCandidateBuiltBeforeNewInputIsDiscarded() {
        var context = new ContextManager(10_000, "pinned", List.of());
        for (var i = 1; i <= 4; i++) {
            context.append(turn(i, "turn " + i));
        }
        var candidate = new ContextManager.Candidate(Json.object("schema", "supervisor-context/1"), 2, 7);
        assertFalse(context.commit(candidate, 8));
        assertEquals(4, context.turns().size());
        assertFalse(context.hasSummary());
    }

    @Test
    void emergencyKeepsOnlyThePinnedPrefixAndTheCurrentInput() {
        var context = new ContextManager(10_000, "pinned", List.of());
        context.catalog("catalog");
        context.append(turn(1, "old"));
        var messages = context.messages("current", true);
        assertEquals(2, messages.size());
        assertEquals("pinned", messages.getFirst().text());
    }
}
