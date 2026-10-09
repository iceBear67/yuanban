# bot-agent

建立在 `bot` 之上的实验性 agent：一个玩家 bot，由语言模型通过自治脚本（GraalJS）组合 `bot` 的原语完成任务。用户从游戏内聊天（`!agent ...`）或游戏外 TUI 下达目标。设计见 [DESIGN.md](DESIGN.md)。

## 运行

```bash
./gradlew :bot-agent:installDist
OPENAI_API_KEY=... bot-agent/build/install/bot-agent/bin/bot-agent agent.json
```

`agent.json`（相对路径相对于配置文件所在的目录；API key 只从环境变量读取）：

```json
{
  "server": {"host": "127.0.0.1", "port": 25565},
  "username": "agent",
  "workspace": "agent-workspace",
  "traceDirectory": "agent-traces",
  "profile": {"persona": "谨慎的建筑助手", "backgroundPlans": ["curiosity-observer@1"]},
  "model": {"baseUrl": null, "apiKeyEnv": "OPENAI_API_KEY", "model": "gpt-5", "contextWindow": 200000},
  "budget": {"actions": 5000, "modelCalls": 500, "tokens": 5000000},
  "chat": {"enabled": true, "prefix": "!agent"},
  "redactPatterns": ["(?i)/login \\S+"]
}
```

- 模型通过 OpenAI 官方 Java SDK 的 Chat Completions 接口调用；`baseUrl` 指向兼容这个接口的服务时同样可用。
- `profile` 可以是文件路径或对象，字段与默认值见 [`AgentProfile`](src/main/java/io/ib67/yuanban/agent/AgentProfile.java)。
- 简单模式：`"profile": {"mode": "simple"}`。模型为每条指令直接编写脚本并立即运行，没有 supervisor、background plan、人格、preflight 和模块测试（DESIGN.md 3.2）。
- TUI 输入目标文本，或 `/stop`、`/pause`、`/resume`、`/status`、`/reply <编号> <回答>`、`/trace`、`/quit`；Ctrl-C 等同 `/stop`。
- 游戏内任何玩家都可以使用：`!agent <目标>`、`!agent stop`、`!agent status`、`!agent reply <编号> <回答>`。
- agent 执行 external 层工具（聊天、命令、丢弃物品、修改配置）或写入记忆时不等待用户许可，TUI 上打印 `action` 通知作为记录。
- 确定性重放：`bot-agent replay <trace.jsonl> <记录开始时的 workspace 副本> <输出目录>`，比较模型输入、决策、每个计划的工具调用与结束状态。

## 代码结构

| 包 | 内容 | DESIGN.md |
|---|---|---|
| `agent` | `Agent`、`AgentFactory`、`AgentRuntime`、`AgentOptions`、`AgentProfile`、`AgentStatus`、`AgentEvent`、组装（`AgentImpl`） | 3.1、4.4、12 |
| `agent.kernel` | `AgentKernel`（输入队列、唤醒模型、应用决策、确认、预算）、`KernelMode` 与 `KernelCore`、`SupervisorMode`（完整模式：supervisor 决策、fallback、background plan）、`SimpleMode`（简单模式）、`ContextManager`（pinned/hot/cold 与 compaction）、决策 schema、模型可调用的查询函数、system prompt 与参考文档 | 3、3.2、8、8.1、8.3 |
| `agent.model` | `ModelProvider`、`OpenAiChatProvider`、从 trace 重放的 `ReplayModelProvider` | 7、8 |
| `agent.plan` | `PlanActor`（自治脚本与全部主机方法）、`PlanBundle`（primary/supervisor/preflight/recovery 的生命周期与策略）、`PlanManager`、`BackgroundScheduler` | 4.1、4.3、4.4、11 |
| `agent.script` | 受限 GraalJS `ScriptEngine`、每个脚本独占线程的 `ScriptRunner`、导出的 `ScriptHost`、前导脚本 `prelude.js`（`api` 对象） | 4.1、5、6 |
| `agent.tool` | `ToolGateway`、`ToolCatalog`、条件与 observer 描述、`LeaseManager`、`BudgetScope`、`ToolPolicy`、实时/fixture 后端与记录重放后端 | 4.5、4.6、5、11 |
| `agent.bot` | `BotFacade`：包装真实 bot 的 `LiveBotFacade` 与按 fixture 运行的 `FixtureBotFacade` | 2、10 |
| `agent.view` | 给脚本与模型看的不可变快照 | 6、8 |
| `agent.message` | envelope、收件箱（dedupeKey 与 latest-wins）、`ScriptEventBus` | 4.2 |
| `agent.workspace` | 模块、manifest 与生命周期、检查点、幂等账本、记忆、`ModuleTester`（module.test） | 8.2 |
| `agent.trace` | 版本化 JSONL trace、脱敏、读取与引用 | 10 |
| `agent.notify` | `NotifyBus` | 5、9 |
| `agent.ingress`、`agent.tui`、`agent.cli` | 游戏内输入与通知、JLine TUI、命令行与配置 | 9 |
| `agent.replay` | `TraceReplay` | 7、10 |

## 内置模块

安装到 workspace 时为 tested，每个模块带有 fixture，`module.test` 全部通过（`BuiltinModulesTest`）：

| 模块 | 角色 | 作用 |
|---|---|---|
| `travel@1` | primary、recovery | 移动到目标并用 movementReached 确认；本地处理未加载区块、阻挡与找不到路径 |
| `movement-watchdog@1` | supervisor | 无进展、受伤、死亡、世界重建时发出 fault 或 attention |
| `terrain-preflight@1` | preflight | 目标附近的危险方块、落差、遮挡与路线上未加载的区块 |
| `recovery-unstuck@1` | recovery | 四个方向直线移动或跳跃脱困 |
| `follow-player@1` | primary | 跟随一名玩家直到停止 |
| `patrol@1` | primary | 按路点巡逻，按检查点继续 |
| `mine-block@1` | primary | 挖掉一个方块，用 observer 与断言确认 |
| `announce@1` | primary | 发送一条聊天（带幂等键） |
| `curiosity-observer@1` | background | 观察附近实体，产生 interest |
| `idle-behavior@1` | background | 在空闲窗口申请 lease，看向附近的玩家 |

## 测试

```bash
./gradlew :bot-agent:test
```

- 单元测试：收件箱、能力、脱敏、条件、lease、预算、决策 schema、命令解析、上下文压缩、trace。
- 脚本与计划：`ScriptSandboxTest`、`BundleLifecycleTest`、`BuiltinModulesTest` 在真实的受限 Context 中运行脚本，bot 为 fixture 外观。
- `LivePlanTest` 在测试 JVM 中启动 Minestom 服务端，bot 通过 TCP 加入，在服务端一侧检查移动、取消与挖掘的效果。
- `AgentEndToEndTest` 需要真实的模型，设置 `BOT_AGENT_MODEL_API_KEY`（可选 `BOT_AGENT_MODEL_BASE_URL`、`BOT_AGENT_MODEL`）时运行：用户目标经过模型启动 bundle 让 bot 移动，然后对这次的 trace 做确定性重放。

## 已知限制

- bot 不校验聊天签名，聊天记录中的签名结果标为 `UNVERIFIED`。
