package io.ib67.yuanban.agent.trace;

import io.ib67.yuanban.agent.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactorTest {
    @Test
    void sensitiveKeysAreReplaced() {
        var redacted = Redactor.none().redact(Json.object("apiKey", "secret-value", "Authorization", "Bearer abc",
                "name", "bot")).getAsJsonObject();
        assertTrue(redacted.get("apiKey").getAsString().startsWith("[REDACTED:"));
        assertTrue(redacted.get("Authorization").getAsString().startsWith("[REDACTED:"));
        assertEquals("bot", redacted.get("name").getAsString());
    }

    @Test
    void secretsAndTokensInsideStringsAreReplaced() {
        var redactor = new Redactor(List.of("hunter2-password"), List.of(Pattern.compile("(?i)/login \\S+")));
        var text = redactor.redact("key sk-abcdefghijklmnop, bearer xyz.123, hunter2-password, /login s3cret, "
                + "url https://user:pass@example.com/path");
        assertFalse(text.contains("sk-abcdefghijklmnop"));
        assertFalse(text.contains("xyz.123"));
        assertFalse(text.contains("hunter2-password"));
        assertFalse(text.contains("s3cret"));
        assertFalse(text.contains("user:pass"));
        assertTrue(text.contains("example.com/path"));
    }

    @Test
    void theSameValueHasTheSameHash() {
        var redactor = Redactor.none();
        assertEquals(redactor.redact(Json.object("token", "same")), redactor.redact(Json.object("token", "same")));
    }
}
