package io.ib67.yuanban.agent.script;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.bot.FixtureBotFacade;
import io.ib67.yuanban.agent.bot.FixtureWorld;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.plan.PlanStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受限 GraalJS Context 与 ToolGateway 的边界，通过 module.test 在 fixture 中真实运行脚本。
 */
class ScriptSandboxTest {
    private static final String PRIMARY = """
            {"roles": ["primary"], "capabilities": ["observe.*", "locomotion.*", "external.chat"], "description": "test",
             "messagesOut": ["progress"]}
            """;

    @TempDir
    Path dir;
    private PlanStack stack;

    @BeforeEach
    void setUp() {
        var limits = ScriptLimits.DEFAULT.toBuilder().maxTurn(Duration.ofMillis(800)).maxPendingCalls(8).build();
        stack = new PlanStack(dir, new FixtureBotFacade(new FixtureWorld(new JsonObject()), null, null, null), AgentProfile.defaults(), limits);
    }

    @AfterEach
    void tearDown() throws Exception {
        stack.close();
    }

    private void assertPasses(JsonObject report) {
        assertTrue(report.get("passed").getAsBoolean(), Json.PRETTY.toJson(report));
    }

    @Test
    void hostAccessIsNotAvailable() {
        assertPasses(stack.test("probe", """
                export async function main(api) {
                    return {java: typeof Java, polyglot: typeof Polyglot, require: typeof require, host: typeof globalThis.__host,
                            setTimeout: typeof setTimeout, process: typeof process};
                }
                """, PRIMARY, """
                [{"name": "probe", "expect": {"status": "COMPLETED", "result": {"java": "undefined", "polyglot": "undefined",
                  "require": "undefined", "host": "undefined", "setTimeout": "undefined", "process": "undefined"}}}]
                """));
    }

    @Test
    void busyLoopsAreKilled() {
        assertPasses(stack.test("busy", """
                export async function main(api) {
                    await api.sleep(10);
                    let x = 0;
                    for (;;) { x++; }
                }
                """, PRIMARY, """
                [{"name": "busy", "expect": {"status": "KILLED"}}]
                """));
    }

    @Test
    void undeclaredEventsAndRepeatedRequestsThrow() {
        assertPasses(stack.test("undeclared", """
                export async function main(api) {
                    api.events.attention({kind: 'x'});
                }
                """, PRIMARY, """
                [{"name": "undeclared", "expect": {"status": "FAILED"}}]
                """));
        assertPasses(stack.test("twice", """
                export async function main(api) {
                    const request = api.observe.playerSnapshot();
                    await request;
                    await request;
                }
                """, PRIMARY, """
                [{"name": "twice", "expect": {"status": "FAILED"}}]
                """));
    }

    @Test
    void gatewayRejectsInvalidArgumentsAndMissingCapabilities() {
        assertPasses(stack.test("gateway", """
                export async function main(api) {
                    const nan = await api.attempt(api.observe.block({x: NaN, y: 64, z: 0}));
                    const huge = await api.attempt(api.observe.block({x: 1e12, y: 64, z: 0}));
                    const unknown = await api.attempt(api.observe.blocks({x: 0, y: 0, z: 0}, {x: 100, y: 100, z: 100}));
                    const denied = await api.attempt(api.actions.attack(5));
                    return {nan: nan.kind, huge: huge.kind, unknown: unknown.kind, denied: denied.kind};
                }
                """, PRIMARY, """
                [{"name": "gateway", "expect": {"status": "COMPLETED", "result": {"nan": "INVALID_ARGUMENT", "huge": "INVALID_ARGUMENT",
                  "unknown": "INVALID_ARGUMENT", "denied": "PERMISSION_DENIED"}}}]
                """));
    }

    @Test
    void readOnlyRolesCannotChangeTheWorld() {
        assertPasses(stack.test("readonly", """
                export async function main(api) {
                    const moved = await api.attempt(api.actions.moveTo({x: 3, y: 64, z: 0}));
                    return {kind: moved.kind};
                }
                """, """
                {"roles": ["preflight"], "capabilities": ["*"], "description": "test"}
                """, """
                [{"name": "readonly", "expect": {"status": "COMPLETED", "result": {"kind": "PERMISSION_DENIED"}}}]
                """));
        assertPasses(stack.test("background-without-lease", """
                export async function main(api) {
                    const looked = await api.attempt(api.actions.lookAt({x: 3, y: 64, z: 0}));
                    return {kind: looked.kind};
                }
                """, """
                {"roles": ["background"], "capabilities": ["observe.*", "locomotion.lookAt"], "description": "test",
                 "inputSchema": {"type": ["object", "null"]}}
                """, """
                [{"name": "no-lease", "role": "background", "expect": {"status": "COMPLETED", "result": {"kind": "LEASE_REQUIRED"}}}]
                """));
    }

    @Test
    void sideEffectsAreIdempotentAndDryRunIntercepts() {
        assertPasses(stack.test("idempotent", """
                export async function main(api) {
                    const first = await api.attempt(api.actions.chat('hi', {idempotencyKey: 'greet'}));
                    const second = await api.attempt(api.actions.chat('hi', {idempotencyKey: 'greet'}));
                    return {first: first.kind, second: second.kind, ledger: api.ledger.has('greet')};
                }
                """, PRIMARY, """
                [{"name": "idempotent", "actions": [{"tool": "external.chat", "outcome": "completed"}],
                  "expect": {"status": "COMPLETED", "result": {"first": "OK", "second": "DUPLICATE_SUPPRESSED", "ledger": true},
                             "allActionsUsed": true}},
                 {"name": "dry-run", "dryRun": true,
                  "expect": {"status": "COMPLETED", "result": {"first": "WOULD_EXECUTE", "second": "WOULD_EXECUTE"}}}]
                """));
    }

    @Test
    void pendingCallsAreLimited() {
        assertPasses(stack.test("pending", """
                export async function main(api) {
                    const sleeps = [];
                    for (let i = 0; i < 12; i++) {
                        sleeps.push(api.sleep(1000));
                    }
                    await Promise.all(sleeps);
                }
                """, PRIMARY, """
                [{"name": "pending", "expect": {"status": "FAILED"}}]
                """));
    }

    @Test
    void draftsBecomeTestedOnlyWhenEveryFixturePasses() {
        var passing = stack.test("passing", "export async function main(api) { return 1; }", PRIMARY,
                "[{\"name\": \"ok\", \"expect\": {\"status\": \"COMPLETED\", \"result\": 1}}]");
        assertEquals("tested", passing.get("status").getAsString());
        var failing = stack.test("failing", "export async function main(api) { return 2; }", PRIMARY,
                "[{\"name\": \"wrong\", \"expect\": {\"status\": \"COMPLETED\", \"result\": 1}}]");
        assertEquals("draft", failing.get("status").getAsString());
    }
}
