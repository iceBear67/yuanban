package io.ib67.yuanban.agent.kernel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionsTest {
    @Test
    void validDecisions() {
        assertTrue(Decisions.validate("""
                {"decision": "start", "reason": "go", "refs": ["msg-1"],
                 "start": {"primary": "travel@1", "input": {"target": {"x": 1, "y": 64, "z": 1}}, "supervisor": "movement-watchdog@1",
                           "policy": {"on": ["stalled"], "action": "replan"}, "budget": {"actions": 12}}}
                """).isEmpty());
        assertTrue(Decisions.validate("""
                {"decision": "send", "target": "primary", "reason": "focus", "refs": [],
                 "send": {"type": "focus", "payload": {"player": "alice"}}}
                """).isEmpty());
        assertTrue(Decisions.validate("""
                {"decision": "say", "reason": "done", "refs": [], "say": {"text": "到了", "done": true}}
                """).isEmpty());
    }

    @Test
    void invalidDecisions() {
        assertFalse(Decisions.validate("{\"decision\": \"send\", \"reason\": \"x\", \"refs\": []}").isEmpty());
        assertFalse(Decisions.validate("{\"decision\": \"teleport\", \"reason\": \"x\", \"refs\": []}").isEmpty());
        assertFalse(Decisions.validate("{\"decision\": \"say\", \"reason\": \"x\", \"refs\": [], \"say\": {\"text\": \"\"}}").isEmpty());
        assertFalse(Decisions.validate("""
                {"decision": "send", "target": "primary", "reason": "x", "refs": [], "send": {"type": "progress", "payload": {}}}
                """).isEmpty());
        assertFalse(Decisions.validate("not json").isEmpty());
    }

    @Test
    void simpleDecisions() {
        assertTrue(Decisions.validate("""
                {"decision": "run", "reason": "走到目标点",
                 "run": {"name": "walk-to", "source": "export async function main(api) { return {ok: true}; }"}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertTrue(Decisions.validate("""
                {"decision": "run", "reason": "换一个脚本", "refs": ["msg-2"],
                 "run": {"name": "walk-to", "source": "export async function main(api) {}", "replace": true}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertTrue(Decisions.validate("""
                {"decision": "say", "reason": "done", "say": {"text": "到了", "done": true}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertTrue(Decisions.validate("""
                {"decision": "stop", "reason": "用户要求停止", "stop": {"done": true}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertFalse(Decisions.validate("""
                {"decision": "start", "reason": "x", "start": {"primary": "travel@1"}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertFalse(Decisions.validate("""
                {"decision": "stop", "reason": "x", "stop": {"scope": "background"}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertFalse(Decisions.validate("""
                {"decision": "run", "reason": "x", "run": {"name": "walk-to"}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
        assertFalse(Decisions.validate("""
                {"decision": "run", "reason": "x", "run": {"name": "Walk To", "source": "x"}}
                """, Decisions.SIMPLE_SCHEMA).isEmpty());
    }
}
