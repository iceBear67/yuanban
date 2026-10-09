package io.ib67.yuanban.agent.replay;

import com.google.gson.JsonObject;
import io.ib67.yuanban.agent.AgentIntent;
import io.ib67.yuanban.agent.AgentOptions;
import io.ib67.yuanban.agent.AgentProfile;
import io.ib67.yuanban.agent.AgentRuntime;
import io.ib67.yuanban.agent.ingress.UserCommand;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.model.ReplayModelProvider;
import io.ib67.yuanban.agent.tool.BudgetLimits;
import io.ib67.yuanban.agent.tool.RecordedBackend;
import io.ib67.yuanban.agent.trace.TraceKind;
import io.ib67.yuanban.agent.trace.TraceReader;
import io.ib67.yuanban.bot.BotOptions;
import io.ib67.yuanban.bot.transport.ClientConnection;
import io.ib67.yuanban.bot.transport.InboundSink;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 确定性重放：读取一次运行的 trace，不连接服务器，按记录的时间重新提交用户目标与命令，工具结果与模型输出取自记录，
 * 运行同一套网关、恢复、计划与 kernel 逻辑，然后比较两份 trace 的模型输入、决策、每个计划的工具调用与结束状态。
 */
public final class TraceReplay {
    /**
     * @param differences          不一致的地方；完全一致时为空
     * @param backendMismatches    工具调用与记录不一致的地方
     * @param unusedModelResponses 没有用到的模型回应数
     */
    public record Report(boolean matches, List<String> differences, List<String> backendMismatches, int unusedModelResponses,
                         Path replayTrace) {
        public JsonObject toJson() {
            return Json.object("matches", matches, "differences", differences, "backendMismatches", backendMismatches,
                    "unusedModelResponses", unusedModelResponses, "replayTrace", replayTrace.toAbsolutePath().toString());
        }
    }

    private TraceReplay() {
    }

    /**
     * @param workspace      重放使用的 workspace：记录开始时 workspace 的副本（模块版本与源码 hash 一致，检查点与幂等账本是开始时的状态）
     * @param traceDirectory 重放的 trace 写入的目录
     * @param margin         记录的时长之后再等待的时间
     */
    public static Report run(Path recorded, Path workspace, Path traceDirectory, Duration margin) {
        var reader = TraceReader.read(recorded);
        var header = reader.records().getFirst();
        var start = header.get("t").getAsLong();
        var end = reader.records().getLast().get("t").getAsLong();
        var profile = AgentProfile.fromJson(header.getAsJsonObject("profile"));
        var model = new ReplayModelProvider(reader);
        var backend = new RecordedBackend(reader);
        var options = AgentOptions.builder()
                .bot(BotOptions.builder().username(Json.requireString(header, "bot")).transport(TraceReplay::refuse).build())
                .model(model)
                .profile(profile)
                .workspace(workspace)
                .traceDirectory(traceDirectory)
                .budget(BudgetLimits.fromJson(header.getAsJsonObject("budget")))
                .build();
        Path replayTrace;
        var timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("trace-replay").factory());
        try (var runtime = AgentRuntime.create()) {
            var agent = runtime.replay(options, backend);
            replayTrace = agent.traceFile();
            for (var record : reader.ofKind(TraceKind.INTENT)) {
                var at = record.get("t").getAsLong() - start;
                if (record.has("text") && record.has("source") && !record.has("event") && !record.has("command")) {
                    var intent = new AgentIntent(Json.requireString(record, "text"),
                            AgentIntent.Source.valueOf(Json.requireString(record, "source")), Json.string(record, "sender"));
                    timer.schedule(() -> agent.submit(intent), at, TimeUnit.MILLISECONDS);
                } else if (record.has("command")) {
                    var command = new UserCommand(UserCommand.Kind.valueOf(Json.requireString(record, "command")),
                            Json.string(record, "id"), Json.string(record, "text"), Json.requireString(record, "source"));
                    timer.schedule(() -> agent.command(command), at, TimeUnit.MILLISECONDS);
                }
            }
            Thread.sleep(end - start + margin.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while replaying " + recorded, e);
        } finally {
            timer.shutdownNow();
            backend.close();
        }
        var replayed = TraceReader.read(replayTrace);
        var differences = new ArrayList<String>();
        compare("model input", contextHashes(reader), contextHashes(replayed), differences);
        compare("decision", decisions(reader), decisions(replayed), differences);
        var recordedCalls = toolCalls(reader);
        var replayedCalls = toolCalls(replayed);
        for (var key : recordedCalls.keySet()) {
            compare("tool calls of " + key, recordedCalls.get(key), replayedCalls.getOrDefault(key, List.of()), differences);
        }
        for (var key : replayedCalls.keySet()) {
            if (!recordedCalls.containsKey(key)) {
                differences.add("plan " + key + " only exists in the replay: " + replayedCalls.get(key));
            }
        }
        compare("terminal state", terminal(reader), terminal(replayed), differences);
        return new Report(differences.isEmpty() && backend.mismatches().isEmpty(), differences, backend.mismatches(),
                model.remaining(), replayTrace);
    }

    private static List<String> contextHashes(TraceReader trace) {
        return trace.ofKind(TraceKind.MODEL_REQUEST_META).stream().map(record -> Json.requireString(record, "contextHash")).toList();
    }

    private static List<String> decisions(TraceReader trace) {
        return trace.ofKind(TraceKind.STATE).stream().filter(record -> "turn_done".equals(Json.string(record, "event")))
                .map(record -> Json.write(record.get("decision"))).toList();
    }

    private static Map<String, List<String>> toolCalls(TraceReader trace) {
        var calls = new LinkedHashMap<String, List<String>>();
        for (var record : trace.ofKind(TraceKind.TOOL_START)) {
            calls.computeIfAbsent(Json.requireString(record, "planKey"), _ -> new ArrayList<>()).add(Json.requireString(record, "tool"));
        }
        return calls;
    }

    private static List<String> terminal(TraceReader trace) {
        return trace.ofKind(TraceKind.TERMINAL_STATE).stream().map(record -> Json.requireString(record, "status")).toList();
    }

    private static void compare(String what, List<String> recorded, List<String> replayed, List<String> differences) {
        var size = Math.max(recorded.size(), replayed.size());
        for (var i = 0; i < size; i++) {
            var left = i < recorded.size() ? recorded.get(i) : null;
            var right = i < replayed.size() ? replayed.get(i) : null;
            if (!Objects.equals(left, right)) {
                differences.add(what + " #" + (i + 1) + ": recorded " + left + ", replayed " + right);
            }
        }
    }

    /**
     * 重放不打开连接；这个 Transport 只为满足 {@link BotOptions} 的要求
     */
    private static ClientConnection refuse(String username, InboundSink inbound) {
        throw new IllegalStateException("a replay does not connect to a server");
    }
}
