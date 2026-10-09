package io.ib67.yuanban.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitiesTest {
    @Test
    void patterns() {
        var capabilities = Capabilities.of(List.of("observe.*", "locomotion.lookAt", "chat"));
        assertTrue(capabilities.allows("observe.entities"));
        assertTrue(capabilities.allows("locomotion.lookAt"));
        assertTrue(capabilities.allows("external.chat"));
        assertFalse(capabilities.allows("locomotion.moveTo"));
        assertFalse(capabilities.allows("interaction.attack"));
    }

    @Test
    void allAndNone() {
        assertTrue(Capabilities.ALL.allows("unsafe.send"));
        assertFalse(Capabilities.NONE.allows("observe.block"));
    }

    @Test
    void intersectionAndDifference() {
        var declared = Capabilities.of(List.of("observe.*", "locomotion.*", "external.chat"));
        var background = declared.and(Capabilities.of(List.of("observe.*", "locomotion.lookAt", "chat")))
                .without(Capabilities.of(List.of("chat", "attack")));
        assertTrue(background.allows("observe.block"));
        assertTrue(background.allows("locomotion.lookAt"));
        assertFalse(background.allows("locomotion.moveTo"));
        assertFalse(background.allows("external.chat"));
    }
}
