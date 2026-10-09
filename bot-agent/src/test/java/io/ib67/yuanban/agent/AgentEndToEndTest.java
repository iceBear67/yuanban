package io.ib67.yuanban.agent;

import io.ib67.yuanban.agent.ingress.GameChatOptions;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.live.LiveServer;
import io.ib67.yuanban.agent.model.OpenAiChatProvider;
import io.ib67.yuanban.agent.replay.TraceReplay;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceReader;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.transport.TcpTransport;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端：用户目标 → AgentKernel → 模型决策 → PlanBundle → bot 在 Minestom 服务端中移动，然后对这次运行的 trace 做确定性重放。
 * 需要真实的模型：设置 {@code BOT_AGENT_MODEL_API_KEY}，可选 {@code BOT_AGENT_MODEL_BASE_URL}、{@code BOT_AGENT_MODEL}。
 */
@EnabledIfEnvironmentVariable(named = "BOT_AGENT_MODEL_API_KEY", matches = ".+")
class AgentEndToEndTest {
    @TempDir
    Path dir;

    @Test
    void aUserGoalMovesTheBotAndTheTraceReplays() throws Exception {
        var server = LiveServer.get();
        var model = model();
        var profile = AgentProfile.fromJson(Json.object("backgroundPlans", List.of(), "heartbeatMs", 0));
        var workspace = dir.resolve("workspace");
        var options = AgentOptions.builder()
                .bot(BotOptions.builder().username("e2e").transport(new TcpTransport("127.0.0.1", server.port)).build())
                .model(model)
                .profile(profile)
                .workspace(workspace)
                .traceDirectory(dir.resolve("traces"))
                .chat(GameChatOptions.DISABLED)
                .secrets(List.of(System.getenv("BOT_AGENT_MODEL_API_KEY")))
                .build();
        Path trace;
        try (var runtime = AgentRuntime.create()) {
            var agent = runtime.create(options);
            trace = agent.traceFile();
            var deadline = System.currentTimeMillis() + 30_000;
            while (agent.status() != AgentStatus.LOADED && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(AgentStatus.LOADED, agent.status());
            var run = agent.submit(new AgentIntent("走到坐标 x=6.5 y=64 z=3.5，到达之后告诉我并结束这个目标。",
                    AgentIntent.Source.API, null)).toCompletableFuture().get(10, TimeUnit.SECONDS);
            var outcome = run.outcome().toCompletableFuture().get(5, TimeUnit.MINUTES);
            assertEquals(AgentRun.Outcome.Status.COMPLETED, outcome.status(), Json.write(outcome.toJson()));
            var position = Objects.requireNonNull(server.player("e2e")).getPosition();
            assertTrue(position.distance(new Vec(6.5, 64, 3.5)) < 2.5, "server position " + position);
        }
        // 重放从记录开始时的 workspace 开始：模块相同，没有这次运行写下的检查点与账本
        var replayWorkspace = dir.resolve("replay-workspace");
        copy(workspace.resolve("modules"), replayWorkspace.resolve("modules"));
        var report = TraceReplay.run(trace, replayWorkspace, dir.resolve("replay-traces"), Duration.ofSeconds(5));
        assertTrue(report.backendMismatches().isEmpty(), Json.PRETTY.toJson(report.toJson()));
        assertTrue(report.matches(), Json.PRETTY.toJson(report.toJson()));
    }

    /**
     * 简单模式：模型直接写脚本并运行；只有 primary，没有 supervisor 与 background plan
     */
    @Test
    void simpleModeWritesAndRunsAScript() throws Exception {
        var server = LiveServer.get();
        var profile = AgentProfile.fromJson(Json.object("mode", "simple", "heartbeatMs", 0));
        var options = AgentOptions.builder()
                .bot(BotOptions.builder().username("e2e_simple").transport(new TcpTransport("127.0.0.1", server.port)).build())
                .model(model())
                .profile(profile)
                .workspace(dir.resolve("simple-workspace"))
                .traceDirectory(dir.resolve("simple-traces"))
                .chat(GameChatOptions.DISABLED)
                .secrets(List.of(System.getenv("BOT_AGENT_MODEL_API_KEY")))
                .build();
        Path trace;
        try (var runtime = AgentRuntime.create()) {
            var agent = runtime.create(options);
            trace = agent.traceFile();
            var deadline = System.currentTimeMillis() + 30_000;
            while (agent.status() != AgentStatus.LOADED && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertEquals(AgentStatus.LOADED, agent.status());
            var run = agent.submit(new AgentIntent("走到坐标 x=6.5 y=64 z=3.5，到达之后告诉我并结束这个目标。",
                    AgentIntent.Source.API, null)).toCompletableFuture().get(10, TimeUnit.SECONDS);
            var outcome = run.outcome().toCompletableFuture().get(5, TimeUnit.MINUTES);
            assertEquals(AgentRun.Outcome.Status.COMPLETED, outcome.status(), Json.write(outcome.toJson()));
            var position = Objects.requireNonNull(server.player("e2e_simple")).getPosition();
            assertTrue(position.distance(new Vec(6.5, 64, 3.5)) < 2.5, "server position " + position);
        }
        var recorded = TraceReader.read(trace);
        var bundles = recorded.ofKind(TraceKind.BUNDLE).stream().filter(record -> "started".equals(Json.string(record, "event"))).toList();
        assertFalse(bundles.isEmpty());
        bundles.forEach(record -> assertNull(Json.string(record, "supervisor"), Json.write(record)));
        recorded.ofKind(TraceKind.SCRIPT_START).forEach(record -> assertEquals("primary", Json.string(record, "role"), Json.write(record)));
        // 重放从空的 workspace 开始：重新安装内置模块，模型的脚本保存为相同的版本
        var report = TraceReplay.run(trace, dir.resolve("simple-replay-workspace"), dir.resolve("simple-replay-traces"),
                Duration.ofSeconds(5));
        assertTrue(report.backendMismatches().isEmpty(), Json.PRETTY.toJson(report.toJson()));
        assertTrue(report.matches(), Json.PRETTY.toJson(report.toJson()));
    }

    private static OpenAiChatProvider model() {
        return new OpenAiChatProvider(OpenAiChatProvider.Options.builder()
                .baseUrl(System.getenv("BOT_AGENT_MODEL_BASE_URL"))
                .apiKey(System.getenv("BOT_AGENT_MODEL_API_KEY"))
                .model(Objects.requireNonNullElse(System.getenv("BOT_AGENT_MODEL"), "gpt-5-mini"))
                .build());
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (var file : files.toList()) {
                var target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }
}
