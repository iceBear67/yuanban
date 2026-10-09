package io.ib67.yuanban.agent.cli;

import io.ib67.yuanban.agent.AgentRuntime;
import io.ib67.yuanban.agent.json.Json;
import io.ib67.yuanban.agent.replay.TraceReplay;
import io.ib67.yuanban.agent.tui.AgentTui;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 命令行入口。
 * <pre>
 * bot-agent &lt;配置文件&gt;                                        连接服务器并打开 TUI
 * bot-agent replay &lt;trace.jsonl&gt; &lt;workspace&gt; &lt;输出目录&gt;     确定性重放并比较
 * </pre>
 */
public final class AgentMain {
    private AgentMain() {
    }

    public static void main(String[] args) {
        if (args.length == 4 && args[0].equals("replay")) {
            var report = TraceReplay.run(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Duration.ofSeconds(5));
            System.out.println(Json.PRETTY.toJson(report.toJson()));
            System.exit(report.matches() ? 0 : 1);
            return;
        }
        if (args.length != 1) {
            System.err.println("usage: bot-agent <config.json> | bot-agent replay <trace.jsonl> <workspace> <output directory>");
            System.exit(2);
            return;
        }
        var config = Path.of(args[0]);
        var options = AgentConfig.load(config);
        try (var runtime = AgentRuntime.create()) {
            var agent = runtime.create(options);
            try (var tui = new AgentTui(agent, options.workspace().resolve("tui-history"))) {
                tui.run();
            }
        }
    }
}
