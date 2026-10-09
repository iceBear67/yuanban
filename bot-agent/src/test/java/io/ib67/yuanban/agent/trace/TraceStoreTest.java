package io.ib67.yuanban.agent.trace;

import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.message.MessageFactory;
import io.ib67.yuanban.agent.message.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceStoreTest {
    @TempDir
    Path dir;

    @Test
    void recordsAreWrittenInOrderAndRedacted() {
        var file = dir.resolve("trace.jsonl");
        try (var trace = TraceStore.open(file, "agent-1", "bot", new Redactor(List.of("topsecret"), List.of()),
                InstantSource.system(), Json.object("note", "header"))) {
            trace.append(TraceKind.INTENT, Json.object("text", "say topsecret"));
            var seq = trace.appendDurable(TraceKind.STATE, Json.object("event", "durable")).join();
            assertEquals(3, seq);
            assertTrue(trace.stack(new IllegalStateException("boom")).startsWith("stk-"));
        }
        var reader = TraceReader.read(file);
        assertEquals(4, reader.records().size());
        assertEquals("header", reader.records().getFirst().get("kind").getAsString());
        var intent = reader.ofKind(TraceKind.INTENT).getFirst();
        assertFalse(Json.requireString(intent, "text").contains("topsecret"));
        assertEquals("agent-1", Json.requireString(intent, "agent"));
        assertEquals(1, reader.ofKind(TraceKind.STACK).size());
    }

    @Test
    void reservedFieldsAreRejected() {
        try (var trace = TraceStore.open(dir.resolve("t.jsonl"), "a", "b", Redactor.none(), InstantSource.system(), Json.object())) {
            assertThrows(IllegalArgumentException.class, () -> trace.append(TraceKind.STATE, Json.object("kind", "x")));
        }
    }

    @Test
    void referencesResolveRangesAndMessages() {
        var file = dir.resolve("refs.jsonl");
        var messages = new MessageFactory(InstantSource.system());
        var envelope = messages.create("run-1", MessageType.ATTENTION, "script:a@1#plan-1", "x", Json.object());
        try (var trace = TraceStore.open(file, "a", "b", Redactor.none(), InstantSource.system(), Json.object())) {
            trace.append(TraceKind.MESSAGE, Json.object("envelope", envelope.toJson()));
            trace.append(TraceKind.STATE, Json.object("event", "x"));
        }
        var reader = TraceReader.read(file);
        assertEquals(2, reader.resolve("trace:1-2").size());
        assertEquals(1, reader.resolve("trace:msg/" + envelope.id()).size());
        assertThrows(IllegalArgumentException.class, () -> reader.resolve("trace:99"));
        assertThrows(IllegalArgumentException.class, () -> reader.resolve("other:1"));
    }
}
