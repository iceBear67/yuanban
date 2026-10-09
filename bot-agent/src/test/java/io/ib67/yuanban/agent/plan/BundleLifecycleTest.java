package io.ib67.yuanban.agent.plan;

import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.bot.FixtureBotFacade;
import io.ib67.yuanban.agent.bot.FixtureWorld;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.message.MessageType;
import io.ib67.yuanban.agent.script.ScriptLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlanBundle 的结构化生命周期：策略 replan、暂停与 recovery、取消顺序、preflight、supervisor 失败、嵌套 bundle 与安全点替换。
 * bot 为 fixture 外观，脚本在真实的受限 Context 中运行。
 */
class BundleLifecycleTest {
    private static final String TARGET = "{\"target\": {\"x\": 10.5, \"y\": 64, \"z\": 0.5}}";
    private static final String SUPERVISOR = """
            {"roles": ["supervisor"], "capabilities": ["observe.*"], "description": "test supervisor",
             "messagesIn": ["signal", "drain", "stop"], "messagesOut": ["fault", "attention"]}
            """;

    @TempDir
    Path dir;
    private PlanStack stack;

    private PlanStack stack(String actions) {
        var facade = new FixtureBotFacade(new FixtureWorld(Json.parseObject("{\"ground\": {\"y\": 63, \"name\": \"minecraft:stone\"}}")),
                Json.parse(actions).getAsJsonArray(), null, null);
        stack = new PlanStack(dir, facade, AgentProfile.defaults(), ScriptLimits.DEFAULT);
        return stack;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stack != null) {
            stack.close();
        }
    }

    @Test
    void supervisorFaultMatchingThePolicyReplans() throws Exception {
        var stack = stack("[{\"tool\": \"locomotion.moveTo\", \"outcome\": \"completed\", \"delayMs\": 5000}]");
        var supervisor = stack.draft("replanner", """
                export async function main(api, input) {
                    while (true) {
                        const message = await api.inbox.next({types: ['signal', 'stop', 'drain']});
                        if (message.type !== 'signal') {
                            return {kind: message.type};
                        }
                        if (message.topic === 'primary_tool_start') {
                            api.events.fault({kind: 'stalled', reason: 'test'});
                        }
                    }
                }
                """, SUPERVISOR);
        var bundle = stack.bundle("{\"primary\": \"travel@1\", \"input\": " + TARGET + ", \"supervisor\": \"" + supervisor
                + "\", \"policy\": {\"on\": [\"stalled\"], \"action\": \"replan\"}}");
        var result = bundle.done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.REPLAN, result.status(), Json.write(result.toJson()) + "\n" + stack.published);
        assertEquals(PlanResult.Status.STOPPED, Objects.requireNonNull(result.primary()).status());
        assertNull(stack.leases.owner());
    }

    @Test
    void supervisorPausesThePrimaryRunsRecoveryAndResumes() throws Exception {
        var stack = stack("""
                [{"tool": "locomotion.moveTo", "outcome": "completed", "delayMs": 3000},
                 {"tool": "locomotion.moveForward", "outcome": "completed", "effects": {"player": {"position": {"x": 2.0}}}},
                 {"tool": "locomotion.moveTo", "outcome": "completed"}]
                """);
        var supervisor = stack.draft("pauser", """
                export async function main(api, input) {
                    let recovered = null;
                    let done = false;
                    while (true) {
                        const message = await api.inbox.next({types: ['signal', 'stop', 'drain']});
                        if (message.type === 'stop') {
                            return {kind: 'stopped'};
                        }
                        if (message.type === 'drain') {
                            return {kind: 'drained', recovered};
                        }
                        if (message.topic === 'primary_tool_start' && !done) {
                            done = true;
                            await api.bundle.pausePrimary({reason: 'test'});
                            const result = await api.bundle.recover({module: 'recovery-unstuck@1', input: {}});
                            recovered = result.value;
                            await api.bundle.resumePrimary({});
                        }
                    }
                }
                """, SUPERVISOR);
        var bundle = stack.bundle("{\"primary\": \"travel@1\", \"input\": " + TARGET + ", \"supervisor\": \"" + supervisor + "\"}");
        var result = bundle.done().get(15, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, result.status(), Json.write(result.toJson()));
        assertEquals("arrived", Objects.requireNonNull(result.primary()).value().getAsJsonObject().get("kind").getAsString(),
                Json.write(result.toJson()) + "\n" + stack.published);
        var supervisorValue = Objects.requireNonNull(result.supervisor()).value().getAsJsonObject();
        assertEquals("drained", supervisorValue.get("kind").getAsString());
        assertEquals("moved", supervisorValue.getAsJsonObject("recovered").get("kind").getAsString());
    }

    @Test
    void cancellingStopsSupervisorAndPrimaryAndReleasesTheLease() throws Exception {
        var stack = stack("[{\"tool\": \"locomotion.moveTo\", \"outcome\": \"completed\", \"delayMs\": 10000}]");
        var bundle = stack.bundle("{\"primary\": \"travel@1\", \"input\": " + TARGET + ", \"supervisor\": \"movement-watchdog@1\"}");
        Thread.sleep(300);
        assertEquals(bundle.primary().id(), stack.leases.owner());
        bundle.cancel("test");
        var result = bundle.done().get(8, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.CANCELLED, result.status());
        assertEquals(PlanResult.Status.STOPPED, Objects.requireNonNull(result.primary()).status());
        assertEquals(PlanResult.Status.STOPPED, Objects.requireNonNull(result.supervisor()).status());
        assertNull(stack.leases.owner());
        assertTrue(stack.published(MessageType.RESULT).stream().anyMatch(event -> event.source().equals("bundle:" + bundle.id())));
    }

    @Test
    void preflightResultsBecomeBundleFacts() throws Exception {
        var stack = stack("[{\"tool\": \"locomotion.moveTo\", \"outcome\": \"completed\", \"delayMs\": 1000}]");
        var supervisor = stack.draft("checker", """
                export async function main(api, input) {
                    let safe = null;
                    while (true) {
                        const message = await api.inbox.next({types: ['signal', 'stop', 'drain']});
                        if (message.type !== 'signal') {
                            return {kind: message.type, safe};
                        }
                        if (message.topic === 'primary_tool_start' && safe === null) {
                            const result = await api.bundle.preflight({module: 'terrain-preflight@1', input: {target: input.primaryInput.target}});
                            safe = result.value.safe;
                        }
                    }
                }
                """, SUPERVISOR);
        var bundle = stack.bundle("{\"primary\": \"travel@1\", \"input\": " + TARGET + ", \"supervisor\": \"" + supervisor + "\"}");
        var result = bundle.done().get(15, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, result.status(), Json.write(result.toJson()));
        assertEquals(1, result.facts().size());
        assertTrue(Objects.requireNonNull(result.supervisor()).value().getAsJsonObject().get("safe").getAsBoolean());
        assertEquals(1, stack.published(MessageType.NEED_PREFLIGHT).size());
    }

    @Test
    void aFailingSupervisorIsReportedAndThePrimaryContinues() throws Exception {
        var stack = stack("[{\"tool\": \"locomotion.moveTo\", \"outcome\": \"completed\", \"delayMs\": 500}]");
        var supervisor = stack.draft("broken", "export async function main(api, input) { throw new Error('broken supervisor'); }", SUPERVISOR);
        var bundle = stack.bundle("{\"primary\": \"travel@1\", \"input\": " + TARGET + ", \"supervisor\": \"" + supervisor + "\"}");
        var result = bundle.done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, result.status());
        assertEquals(PlanResult.Status.FAILED, Objects.requireNonNull(result.supervisor()).status());
        assertTrue(stack.published(MessageType.FAULT).stream().anyMatch(event -> "supervisor_failed".equals(event.topic())));
    }

    @Test
    void aPrimaryCanRunANestedBundleWithTheDelegatedLease() throws Exception {
        var stack = stack("[{\"tool\": \"locomotion.moveTo\", \"outcome\": \"completed\"}]");
        var nester = stack.draft("nester", """
                export async function main(api, input) {
                    const nested = await api.bundle.run({primary: 'travel@1', input: {target: input.target}});
                    return {nested: nested.status, kind: nested.primary.value.kind};
                }
                """, "{\"roles\": [\"primary\"], \"capabilities\": [\"observe.*\"], \"description\": \"test\"}");
        var bundle = stack.bundle("{\"primary\": \"" + nester + "\", \"input\": " + TARGET + "}");
        var result = bundle.done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, result.status(), Json.write(result.toJson()));
        var value = Objects.requireNonNull(result.primary()).value().getAsJsonObject();
        assertEquals("COMPLETED", value.get("nested").getAsString());
        assertEquals("arrived", value.get("kind").getAsString());
        assertNull(stack.leases.owner());
    }

    @Test
    void aCrashedPrimaryResumesFromItsLatestCheckpoint() throws Exception {
        var stack = stack("[]");
        var module = stack.draft("crasher", """
                export async function main(api, input) {
                    const restored = api.state();
                    if (restored !== null) {
                        return {restored: restored.step, marked: api.ledger.has('side-effect')};
                    }
                    api.ledger.mark('side-effect', {done: true});
                    api.checkpoint('after-side-effect', {step: 3});
                    throw new Error('crash after the checkpoint');
                }
                """, "{\"roles\": [\"primary\"], \"capabilities\": [\"observe.*\"], \"description\": \"test\"}");
        var first = stack.bundle("{\"primary\": \"" + module + "\"}").done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.FAULT, first.status());
        var lineage = Objects.requireNonNull(first.primary()).planId();
        var resumed = stack.bundle("{\"primary\": \"" + module + "\", \"resumeLineage\": \"" + lineage + "\"}")
                .done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, resumed.status(), Json.write(resumed.toJson()));
        var value = Objects.requireNonNull(resumed.primary()).value().getAsJsonObject();
        assertEquals(3, value.get("restored").getAsInt());
        assertTrue(value.get("marked").getAsBoolean());
    }

    @Test
    void thePrimaryIsReplacedAtACheckpoint() throws Exception {
        var stack = stack("[]");
        var source = """
                export async function main(api, input) {
                    const restored = api.state();
                    if (restored !== null) {
                        return {restored: restored.count, version: api.self.version};
                    }
                    api.checkpoint('started', {count: 7});
                    while (!api.cancelled()) {
                        await api.sleep(100);
                    }
                    return {kind: 'stopped'};
                }
                """;
        var manifest = "{\"roles\": [\"primary\"], \"capabilities\": [\"observe.*\"], \"description\": \"test\"}";
        var first = stack.draft("counter", source, manifest);
        var second = stack.draft("counter", source, manifest);
        var bundle = stack.bundle("{\"primary\": \"" + first + "\"}");
        var deadline = System.currentTimeMillis() + 5000;
        while (Objects.requireNonNull(bundle.primary()).lastCheckpoint() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        var old = Objects.requireNonNull(bundle.primary());
        bundle.replacePrimary(stack.workspace.load(second), Objects.requireNonNull(old.lastCheckpoint()));
        var result = bundle.done().get(10, TimeUnit.SECONDS);
        assertEquals(BundleResult.Status.COMPLETED, result.status(), Json.write(result.toJson()));
        var value = Objects.requireNonNull(result.primary()).value().getAsJsonObject();
        assertEquals(7, value.get("restored").getAsInt());
        assertEquals(2, value.get("version").getAsInt());
    }
}
