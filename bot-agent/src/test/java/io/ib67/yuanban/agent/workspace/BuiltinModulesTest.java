package io.ib67.yuanban.agent.workspace;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.script.ScriptEngine;
import io.ib67.yuanban.agent.script.ScriptLimits;
import io.ib67.yuanban.agent.trace.Redactor;
import io.ib67.yuanban.agent.trace.TraceStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每个内置模块在自己的 fixture 中通过 module.test。
 */
class BuiltinModulesTest {
    @TempDir
    static Path dir;
    private static TraceStore trace;
    private static ScriptEngine engine;
    private static ScheduledExecutorService timer;
    private static Workspace workspace;
    private static ModuleTester tester;

    @BeforeAll
    static void setUp() {
        var clock = InstantSource.system();
        trace = TraceStore.open(dir.resolve("agent.jsonl"), "test", "fixture", Redactor.none(), clock, new JsonObject());
        engine = new ScriptEngine();
        timer = Executors.newScheduledThreadPool(2, Thread.ofPlatform().daemon(true).factory());
        workspace = Workspace.open(dir.resolve("workspace"), trace, clock);
        workspace.installBuiltins();
        tester = new ModuleTester(workspace, engine, ScriptLimits.DEFAULT, timer, clock, AgentProfile.defaults());
    }

    @AfterAll
    static void tearDown() {
        timer.shutdownNow();
        engine.close();
        trace.close();
    }

    static List<String> modules() {
        return Workspace.builtinModules().stream().map(ModuleRef::toString).toList();
    }

    @ParameterizedTest
    @MethodSource("modules")
    void passesItsFixtures(String module) {
        var report = tester.test(workspace.load(ModuleRef.parse(module)));
        assertTrue(report.get("passed").getAsBoolean(), Json.PRETTY.toJson(report));
    }
}
