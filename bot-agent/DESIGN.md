# bot-agent 设计报告

## 1. 目标与边界

`bot-agent` 是建立在 `bot` 之上的实验性模块。它创建一个能登录原版（以及 Minestom）服务器的玩家 bot，并让语言模型通过“程序化工具调用”（Programmatic Tool Call，PTC）组合 `bot` 的高级原语完成任务。用户可以从游戏内聊天或游戏外 TUI 下达目标。

首要用途是交叉验证和调试 `bot/`：每一次模型观察、工具调用、数据包方向、动作结束原因和异常都必须可记录、可重放、可与人工脚本比较。它不是 Minecraft 服务器插件，也不把 LLM 放进 Minestom tick 或 `bot` 调度线程。

第一版明确不承诺：自动战斗的最优策略、无限自主运行、绕过正版认证、让模型直接构造任意数据包、跨多个 bot 的协同，以及“动作成功”等超出 `bot` 现有语义的判断。

## 2. 已有能力和约束

`bot` 已提供本模块需要的底层能力：

- `BotRuntime` 管理一个调度线程；`Bot` 通过 `moveTo`、`jumpTo`、`rotate`、挖掘、放置、使用物品、攻击、聊天、命令和物品栏操作提交请求。
- `Action` 只有 `await`、`future`、`cancel`，动作的游戏效果由 observer 判断。
- `BotView`/`inspect` 提供不可变快照；`observe` 和 `observeDuring` 提供数据包、模型变化和动作完成事件。
- 动作队列、世界重建、玩家死亡、断开连接和 bot failure 有明确的取消原因和异常类型。
- 全部状态只在 bot 调度线程修改；observer 回调也在该线程执行，回调不能阻塞。

因此只有自治脚本的 ToolGateway 线程向 `Bot` 提交请求，等待 `Action` 的 `future`，再通过 observer 或查询判断结果；AgentKernel 只通过消息向脚本提出目标或关注点。不能把模型调用、TUI I/O 或 GraalJS 执行放入 observer 回调；否则一个慢的 LLM 会停住 runtime 中的全部 bot。

## 3. 总体架构

```text
游戏内聊天 ─┐
            ├─► IntentIngress ─► AgentKernel (LLM supervisor)
游戏外 TUI ─┘                         │
                                      │ control / focus messages
                                      ▼
                              AgentScript (自治 GraalJS actor)
                                      │ tool calls + observations
                                      ▼
                         ToolGateway ─► bot.Bot / observers
                                      │
                                      ├─► ScriptEventBus ─► AgentKernel
                                      └─► NotifyBus ─► TUI、聊天、trace
```

- **AgentKernel** 负责理解用户目标、选择/更新脚本、维护给模型的上下文和 compaction。它不是每个动作的调度器。
- **AgentScript** 是长期运行的自治脚本。它可以有循环、状态机、定时器、局部记忆和恢复逻辑；正常运行时不需要每一步向 LLM 请求批准。
- **ControlBus** 从 AgentKernel/TUI/游戏内输入向脚本发送 `goal`、`focus`、`policy`、`stop` 和 `user_reply` 消息。
- **ScriptEventBus** 从脚本向 AgentKernel 发送 `progress`、`attention`、`result`、`fault`、`checkpoint` 和 `question` 消息。它只传递脚本主动报告或策略要求关注的内容，不把每个 tick/数据包都塞进模型上下文。
- **ToolGateway** 是脚本唯一能触碰 `BotFacade` 的边界，负责参数校验、动作/observer 适配、超时、权限、预算和审计。
- **AgentKernel** 收到 `attention` 或用户新消息时才唤醒 LLM；LLM 的输出通常是发送一条 control message 或更新模块版本，而不是直接执行一串即时工具调用。
- **TraceStore** 记录消息 envelope、脚本版本、action、observer、异常和模型回合，支持脱敏重放。

### 3.1 生命周期

```text
CREATED → CONNECTING → JOINED → LOADED → SCRIPT_RUNNING
                                             │       │
                              control message│       │ attention/event
                                             ▼       ▼
                                      SUPERVISING / COMPACTING
                                             │
                               COMPLETED / PAUSED / STOPPED
                                             │
                                  DISCONNECTED / FAILED
```

`JOINED`、`LOADED` 使用 `bot.joined()` 和 `bot.loaded()`。脚本可以在加载前启动，但改变世界的工具会等待 `loaded`；脚本的 mailbox 和局部状态在此期间继续存在。世界重建或玩家死亡产生的 action cancellation 先成为 ScriptEvent，再由脚本决定恢复或报告给 supervisor。

### 3.2 kernel 的模式与简单模式

AgentKernel 只处理所有模式共用的部分：输入队列、模型回合与决策修正、预算、compaction、运行生命周期、前台 bundle、`ask` 与 `say`。模型看到的固定前缀、查询函数、决策 schema、上下文和其余决策由 `KernelMode` 决定，模式通过 `KernelCore` 读取 kernel 状态、启动或替换前台 bundle、把异步结果交回 kernel 线程。profile 的 `mode` 字段选择模式，它随 profile 写入 trace 头，重放时使用同一模式。

- **完整模式（`full`，默认）**：`SupervisorMode`。模型作为 supervisor 管理 PlanBundle，人格驱动 background plan，可以 preflight、生成并测试 draft、在检查点替换 primary，supervisor 没有及时回应 attention、fault、question 时执行模块声明的 fallback。本文其余部分描述的都是完整模式。
- **简单模式（`simple`）**：`SimpleMode`。没有 background plan、人格、supervisor、preflight 和模块测试。模型为每条用户指令用 `run` 决策直接提交脚本源码；源码保存为 workspace 中一个模块的新 draft 版本，作为没有 supervisor 的前台 bundle 立即运行。脚本只能发出 `progress`（显示在 TUI，不唤醒模型），结束时 bundle 的结果唤醒模型，模型再决定 `say`（`done` 结束目标）、再次 `run`、`ask` 或 `stop`。运行中收到新指令时用 `run` 加 `replace: true` 替换，或者 `stop`。上下文为 `simple-context/1`，固定前缀只包含简单模式的提示词、脚本 API 和策略，不包含人格。

## 4. PTC 协议：自治脚本和消息机制

### 4.1 PTC 的真实边界

这里的 PTC 不是“LLM 每次调用一个工具”，而是 LLM 生成或选择一段**长期运行的 bot script**。GraalJS 负责执行这段程序；程序通过 ToolGateway 使用 `bot` 原语，通过消息总线和 supervisor 交换高层信息。脚本可以自主循环：

```js
export async function main(api) {
  const state = api.state();
  let focus = await api.inbox.next({types: ["goal", "focus", "stop"]});

  while (!api.cancelled()) {
    if (focus?.type === "stop") return {kind: "stopped"};
    const nearby = await api.observe.entities({radius: 12});
    const target = chooseTarget(nearby, focus);
    if (!target) {
      await api.events.attention({kind: "need_target", data: {nearbyCount: nearby.length}});
      focus = await api.inbox.next({types: ["focus", "user_reply", "stop"]});
      continue;
    }

    await api.actions.withObserver(
      api.actions.moveTo(target.position, {maxDistance: 30}),
      api.observe.movementReached(target.position)
    );
    await api.events.progress({step: "reached_target", target: target.id});
  }
}
```

`main` 的循环和局部状态在一次脚本生命周期中持续存在。`await` 只等待 ToolGateway 的异步结果或消息，不阻塞 `bot` 调度线程。脚本不应等待 LLM 才能继续执行普通动作；只有 `attention`、`question`、策略更新或明确的 `askSupervisor` 才会进入模型回合。

LLM 回合可以返回：

1. `start_script`：选择 workspace 中的固定模块和输入；
2. `send`：向当前脚本发送 `focus`、`policy`、`stop` 或对 `question` 的回复；
3. `patch_script`：生成新 draft，经过测试后在下一个安全点替换；
4. `say`：向用户说明进度；
5. `ask`：请求用户澄清。

模型不直接持有 `Bot`、Java 类或 Minecraft packet。PTC 的“程序化”部分发生在自治脚本内部，而不是模型上下文里逐回合展开。

### 4.2 消息 envelope 和关注策略

所有跨边界消息都使用带版本的 envelope：

```json
{
  "id": "msg-91",
  "run": "run-42",
  "type": "attention",
  "priority": "normal",
  "source": "script:patrol@2",
  "topic": "unexpected_state",
  "payload": {"reason": "NO_PATH", "position": {"x": 1, "y": 64, "z": 2}},
  "requiresAck": true,
  "dedupeKey": "path:no-path:sector-3",
  "at": 12345
}
```

- `goal`、`stop` 和安全策略变更是有序控制消息；`focus` 默认 latest-wins，但带 `dedupeKey` 的旧消息不能覆盖更新的策略。
- `progress` 可丢弃或合并；`attention`、`fault`、`question` 和 `checkpoint` 必须持久化并确认。
- ScriptEventBus 只发送摘要、变化和请求关注的内容；详细快照留在 trace/workspace，通过引用按需读取。
- supervisor 不可及时响应时，脚本使用声明的本地 fallback；超过 mailbox/时间预算则安全停机并通知用户。

### 4.3 默认双计划与 PlanBundle 生命周期

为了保持 responsiveness，agent 默认不先做完整 preflight。一个用户意图通常启动一个 `PlanBundle`：

```text
PlanBundle
  ├─ primary: innocent plan（立即执行，带有限假设）
  └─ supervisor: fault watcher（只读观察、超时和无进展检测）
```

`innocent plan` 可以直接调用移动或交互工具；它必须声明自己的假设、成功条件、最大等待时间和可恢复错误。`supervisor` 不重复扫描所有环境，只订阅低成本信号：位置/速度变化、动作完成或取消、伤害、聊天、世界重建、区块加载、超时和 progress。它发现以下情况时才发送 `fault` 或 `need_preflight`：

- 假设被观察结果否定，例如目标区块未加载或路径出现危险；
- 动作在预算内没有进展；
- 现有 bot 能力不足以判断是否安全；
- 相同错误达到重试阈值。

`need_preflight` 会让 supervisor 请求暂停 primary，并按需启动一次性的 preflight plan。preflight 完成后只返回结构化事实和风险，不直接抢占控制权；supervisor 决定继续、replan、询问用户或安全停止。这样正常路径只有一个立即行动的计划，异常路径才承担 preflight 成本。

ToolGateway 提供 plan bundle 级生命周期，而不是让每个 tool call 自己管理孤立的 action：

```js
await api.bundle.run({
  primary: "travel-optimistic@2",
  input: {target},
  supervisor: "movement-watchdog@1",
  policy: {on: ["stalled", "hazard", "timeout"], action: "replan"},
  budget: {wallTimeMs: 30_000, actions: 12}
});
```

概念接口如下：

```java
interface PlanBundle {
    PlanHandle primary();
    PlanHandle supervisor();
    CompletionStage<BundleResult> await();
    void send(ControlMessage message);
    void pausePrimary(String reason);
    void cancel(String reason);
}
```

Bundle 具有结构化的父子生命周期：

- bundle 创建时注册 primary、supervisor 和共享 `RunContext`；
- bundle 取消会按顺序取消 supervisor、primary 的 JS、等待中的 gateway calls 和临时 observers；
- primary 正常完成时 supervisor 进入 drain 状态，消费最后一批事件后关闭；
- supervisor 发现 fault 时可以 `pausePrimary`，但不能直接提交改变世界的 action；
- preflight/recovery 是 bundle 的子计划，继承预算、权限、消息序号和 trace；
- 任一子计划异常都按策略传播为 `fault`、`replan` 或 bundle failure，不能静默结束；
- `await()` 只表示 bundle 生命周期结束，实际游戏效果仍由 observer/postcondition 判断。

同一 bundle 中只有 primary 或获得 capability lease 的 recovery plan 可以写入 bot；supervisor 和 preflight 默认只读。lease 带 owner、优先级、过期时间和释放原因，防止 fault 处理期间两个计划同时移动。

### 4.4 Background plan：人格驱动的持续行为

除了响应用户目标的 foreground bundle，agent 可以拥有一个或多个长期运行的 background plan。它们不是每次都由 LLM 临时生成，而是由 agent profile/persona 声明允许启动的自治 actor。例如：观察附近玩家、记录感兴趣的方块、保持安全距离、整理自己的短期记忆，或在空闲时探索一个低风险区域。

```text
Agent
  ├─ foreground bundle：当前用户目标，优先级最高
  └─ background plans
       ├─ curiosity-observer：观察并形成兴趣
       ├─ social-monitor：监听允许的聊天/玩家事件
       └─ idle-behavior：没有 foreground 工作时执行低风险行为
```

background plan 有四种输出：

- `observation`：写入自己的局部记忆或 trace，不唤醒 LLM；
- `interest`：认为某件事值得 agent 注意，发送带证据引用的 `attention`；
- `suggestion`：向 foreground 或 supervisor 提议一个目标，但不能直接替换用户目标；
- `action_request`：请求短暂取得 capability lease 执行低风险动作。

人格只决定“什么值得注意”和“空闲时偏好什么”，不改变安全边界。profile 应显式声明：

```json
{
  "initiative": "curious",
  "backgroundPlans": ["curiosity-observer@1", "idle-behavior@2"],
  "allowedCapabilities": ["observe.*", "locomotion.lookAt"],
  "maxLeaseMs": 2000,
  "maxIdleActions": 3,
  "neverAutonomous": ["command", "drop", "chat", "unsafe.send", "attack"]
}
```

调度规则如下：

1. foreground plan 永远优先；background plan 默认只读，或者只能使用 profile 明确允许的低风险 capability。
2. background plan 不能在 foreground 持有 lease 时移动、交互或改变世界；它可以继续观察并排队 suggestion。
3. 空闲窗口由 scheduler 明确授予，不能由脚本自行判断并无限执行；每次 lease 都有 owner、用途、预算和过期时间。
4. background plan 的观察结果先进入自己的 mailbox/memory；只有符合关注策略、达到置信度或发现 fault 才升级给 supervisor。
5. 用户 `stop`、安全事件、预算耗尽和断线会取消所有 background plans；foreground 完成后可以按 persona 重新启动它们。
6. 后台计划产生的 action 仍写入同一 trace，并标注 `origin=background:<module>@<version>`，方便区分用户目标和人格行为。

这使人格成为一种**受约束的主动性来源**：agent 可以自己决定“我想观察什么”或“空闲时做什么”，但不能借人格绕过 capability lease 和动作预算。background plan 发现洞、危险实体或未知区域时，可以发送 `interest`/`attention`，由 supervisor 决定是否启动 preflight，而不是自己直接把 foreground 计划改掉。

### 4.5 工具分层

默认工具集按风险分为四层：

| 层 | 示例 | 默认策略 |
|---|---|---|
| observe | `playerSnapshot`、`block`、`entities`、`chatLog`、`inventory`、`waitFor` | 自动允许，返回有界快照 |
| locomotion | `moveTo`、`moveForward`、`jumpTo`、`rotate`、`lookAt` | 自动允许，距离/时间/重寻路次数受限 |
| interaction | `breakBlock`、`placeBlock`、`useItem`、`attack`、`interact`、物品栏点击 | 自动允许但按世界坐标、动作数和频率限流 |
| external/high-risk | `command`、任意 `send`、`chat`、丢弃物品、改变 agent 配置 | 按策略允许或拒绝，执行时在 TUI 上记录 |

第一版不暴露 `send(MinecraftPacket)` 给脚本。调试人员可通过 Java 测试 API 或显式的 `unsafe` profile 开启，所有调用记录完整 packet class、方向和 payload 摘要。

### 4.6 统一结果和错误

每次工具返回：

```json
{
  "ok": false,
  "kind": "ACTION_CANCELLED",
  "reason": "CHUNK_NOT_LOADED",
  "actionId": "a-42",
  "elapsedMs": 817,
  "observation": {"position": {"x": 1.0, "y": 64.0, "z": 2.0}}
}
```

`ok` 只表示 Java 调用和观测器是否得到预期结论；它不意味着服务器一定执行了游戏效果。保留 `BotDisconnectedException`、`BotFailureException`、`BotTimeoutException`、`ActionCancelledException` 和 `ChunkNotLoadedException` 的稳定分类，并把原始异常类型、消息、堆栈 ID 放入 trace。工具错误先交给脚本的 `attempt/recover`，只有脚本发布 `attention` 或 `fault` 才进入模型上下文。

## 5. special case 与“潜意识”原语

这些原语是编码好的行动能力，不由模型临时发明：

- `attempt(tool, options)`：执行工具并把取消、断线、世界重建、超时分成可匹配的结果；不把普通 action cancellation 当作 Java 异常重新抛出。
- `recover(errorKind, handler)`：只对白名单错误运行恢复脚本，限制重试次数和总预算，例如遇到 `CHUNK_NOT_LOADED` 先 `waitForChunk`，遇到 `NO_PATH` 重新读取位置后缩短目标。
- `withObserver(action, observerSpec)`：原子地注册 observer、提交 action，等 `ActionCompleted` 后返回 observer 结论，使用 `bot.observeDuring` 语义。
- `waitFor(predicate, timeout)`：在自治脚本中等待 observer/快照条件，超时生成 `WAIT_TIMEOUT` 事件；禁止 busy loop。
- `notify(kind, payload)`：立即向 NotifyBus 发结构化事件。类型至少包括 `progress`、`need_user`、`server_chat`、`damage`、`unexpected_state`、`recovery_started`、`recovery_exhausted` 和 `trace_marker`。
- `checkpoint(name, data)`：保存脚本可重放的运行检查点；取消或崩溃后只能从最近检查点恢复，不重复无 idempotency 的交互。
- `budget({actions, ticks, wallTimeMs, modelCalls})`：在脚本级和 agent 级累计预算，任何耗尽都产生可恢复事件。
- `assertState`：只读断言；失败返回 `ASSERTION_FAILED`，用于交叉验证而非让模型自行猜测。

通知既写入 trace，又按订阅发送给 TUI；游戏内通知只发送短文本，完整 payload 不进入聊天，避免污染服务器测试。

## 6. GraalJS 执行模型和安全

使用 GraalVM Polyglot API 的 JavaScript 语言依赖（`org.graalvm.polyglot:js`，版本与 GraalVM 25.x 对齐）。每个 agent 的自治脚本由一个独立的 `Engine`/`Context` 执行，脚本生命周期可以跨越多次模型回合；模块加载和长期状态由 workspace/消息机制管理，不依赖一次模型回合的 JS 全局变量。重启脚本时只从 checkpoint 和模块版本恢复。

Context 必须采用最小权限：

- `HostAccess.EXPLICIT` 或更严格的 `HostAccess.CONSTRAINED`，只导出 gateway 的 facade 方法；
- `allowHostClassLookup` 返回 false，关闭 `allowHostClassLoading`、`allowIO`、`allowNativeAccess`、`allowCreateThread` 和 polyglot access；
- 不启用 `allowAllAccess(true)`，不把 `Bot`、`Context`、`System`、类加载器或任意 Java 对象放入 bindings；
- 限制脚本 wall-clock、最大调用深度、结果字节数、工具调用数和并行 promise 数；超时通过 `Context.close(true)`/取消令牌终止；
- 脚本 stdout/stderr 重定向到 trace，不允许直接写终端；
- 高风险/不可信脚本可用 GraalVM 25 的 external isolate（独立子进程）profile 执行。第一版默认进程内受限 context，生产无人值守运行建议 external isolate。

Java facade 方法使用不可变 DTO 和 `@HostAccess.Export`，不暴露重载、泛型集合内部实现或可变 `StateReader`。工具参数先以 JSON Schema 校验，再转换为 Java 类型；任何 NaN、无穷、超大坐标、负预算和超限字符串都拒绝。

## 7. Harness 选择

建议实现一个本项目内的 **小型 AgentKernel harness**，而不是把执行语义交给重量级 agent 框架：

- `AgentKernel`：状态机、邮箱、取消、预算、恢复和 trace；
- `ModelProvider`：`ModelRequest`/`ModelResponse`，供应商可插拔；
- `ToolCatalog`/`ToolGateway`：schema、权限和调用；
- `ScriptRunner`：GraalJS context、Promise bridge、超时；
- `ObservationRouter`：把 `bot.observe` 事件转成有界事件窗口；
- `TraceStore`：JSONL writer/reader 和 deterministic replay。

可选依赖 **LangChain4j** 只用于模型客户端、结构化输出和 token usage 统计；不要采用它的 agent executor 作为自治脚本的生命周期管理器，因为脚本必须持续运行并通过本项目的 mailbox/observer/action 语义工作。MCP Java SDK 可在后续把工具目录暴露给外部客户端，但本地脚本不应绕消息边界做额外序列化和进程跳转。

选择依据：LangChain4j 是面向 Java 的多模型和工具调用库，MCP Java SDK 是标准化的 Java MCP client/server；两者都不能替代本项目需要的 Minecraft action 生命周期和可重放 harness。因此核心保持无框架、可测、可确定性重放。

## 8. LLM 交互和上下文

每次 supervisor 请求包含：系统规则、agent profile、消息 schema、当前目标、未确认的 attention/question/fault、脚本 checkpoint 摘要和预算余量。脚本内部的工具结果与普通 progress 不进入模型上下文；完整事件仍在 trace 中保存。

上下文压缩规则：

1. 最新的玩家位置、维度、生命、手持物品、加载状态和连接状态永远保留；
2. 同类 `PacketReceived`/移动事件按时间窗口聚合；
3. 聊天保留来源和签名验证结果；
4. 失败恢复保留首次错误、重试次数和最终结果；
5. 任何被截断的字段带 `truncated=true`，模型不能把缺失信息当作不存在。

模型输出必须通过 JSON Schema 校验。无效输出最多自动修复一次，之后进入 `need_user` 或 `FAILED`，避免无限自我修复。

## 8.1 上下文 compaction：只压缩 supervisor 消息

自治脚本、脚本 mailbox、局部状态和未完成的 `Action` 是运行时状态，不属于 LLM transcript，不能因为模型上下文接近阈值而被压缩或重启。compaction 只作用于 AgentKernel 的 supervisor context：它决定模型接下来该关注什么，不决定脚本下一步怎么走。

每个 agent 使用三层材料：

| 层 | 内容 | 处理方式 |
|---|---|---|
| pinned | agent 身份、权限、脚本契约、消息 schema、用户约束 | 稳定 prompt 前缀，永不自动删除 |
| hot | 未确认的 `attention/question/fault`、最近用户消息、脚本 checkpoint 摘要、当前 focus | 每次模型回合重建，优先保留 |
| cold | 完整消息、观察、聊天、模型原文、脚本源码、动作 trace | workspace/trace 持久化，按引用查询 |

ScriptEventBus 先做有界聚合，再决定是否唤醒模型：连续 progress 合并成计数和最后位置；重复的 server chat 按 topic 去重；普通 tick、PacketReceived 和已确认的 action result 不进入 hot context。只有脚本主动 `attention`、无法匹配的 `fault`、用户消息、策略变化和定期 heartbeat 才形成模型输入。

### 后台 compaction 与实时执行

AgentKernel 设置 soft/hard/emergency 三个阈值（按 provider context window 配置）：

- **soft（约 60%）**：后台从 mailbox、脚本 checkpoint 和未确认消息生成候选摘要；自治脚本完全不暂停。
- **hard（约 80%）**：暂缓新的 supervisor 回合，但继续投递 control message 和运行脚本；当前模型 streaming 不被改写。
- **emergency（约 92%）**：只保留 pinned、最近的 `attention/fault/question`、脚本版本和 checkpoint 引用；脚本继续使用本地 fallback，无法处理的事件进入 `PAUSED`。

压缩边界以消息序号和 ack 为准，而不是以任意 token 位置为准。摘要结构如下：

```json
{
  "schema": "supervisor-context/1",
  "userGoal": "持续巡逻并在发现异常时通知我",
  "script": {"module": "patrol@2", "status": "running", "checkpoint": "cp-17"},
  "focus": ["unexpected_state", "player_damage"],
  "unacked": [{"id": "msg-91", "topic": "unexpected_state", "payloadRef": "trace:..."}],
  "decisions": [{"key": "maxRadius", "value": 20, "source": "user"}],
  "nextSupervisorChoices": ["send focus update", "ask user", "leave script unchanged"],
  "coldRefs": ["trace:run-42/messages/0-811", "workspace:module/patrol/2"]
}
```

候选摘要通过 compare-and-swap 提交：如果脚本在后台压缩期间产生了新消息，旧摘要丢弃并基于新的 sequence 重算。compaction 失败不会取消动作、关闭 observer 或杀死脚本；最多阻止下一次 LLM 回合，并继续用旧 context 或本地 policy 运行。

`ModelProvider` 可以使用供应商的 server-side compaction（例如 OpenAI Responses 的 `context_management`），也可以只发送上述结构化 context。provider 私有的 continuation token 只属于 supervisor；脚本不依赖它。工具定义和 pinned prompt 单独缓存，当前未使用的工具通过 capability catalog 按需载入；PTC 脚本的中间结果永远留在脚本内部，不形成模型消息往返。

## 8.2 Workspace、自治脚本和健壮 plan

workspace 保存的是可复用的**脚本模块**，不是模型的长篇记忆。模块可声明自己适合作为 primary、supervisor、preflight、recovery 或 background，但角色不能在运行中随意改变：

```text
/workspace/
  modules/     可复用 GraalJS actor（源码、manifest、测试 fixture、版本）
  scripts/     当前 agent 的运行实例、mailbox cursor、checkpoint、局部状态
  memories/    脚本写入的事实
  runs/        trace 引用和 replay 元数据
```

workspace 是 ToolGateway 提供的虚拟 API，不是给 GraalJS 的文件系统：`module.list/get/search`、`module.start`、`module.test`、`module.saveDraft`、`script.checkpoint`、`memory.read/write` 都校验大小、权限、版本和 schema。脚本没有 `allowIO`，不能通过 `Java.type` 或 Node API 绕过 gateway。

模块契约描述 actor 的消息和能力，而非一次性函数：

```json
{
  "name": "patrol",
  "version": 2,
  "entry": "main",
  "inputSchema": {"type": "object", "required": ["area"]},
  "roles": ["background"],
  "messagesIn": ["goal", "focus", "policy", "stop", "user_reply"],
  "messagesOut": ["progress", "attention", "question", "fault", "checkpoint"],
  "capabilities": ["observe.entities", "locomotion.moveTo"],
  "fallback": "pause-on-unknown-fault",
  "sourceHash": "sha256:...",
  "status": "tested"
}
```

模块生命周期为 `draft → tested → deprecated`。模型可以创建 draft，但只能启动 `tested` 版本；`module.test` 在 replay/dry-run fixture 中验证 mailbox 行为、取消传播、预算、异常恢复和 postcondition。运行中的脚本固定 source hash 和版本，compaction 或后续模型回合不能悄悄替换它。

### 健壮自治脚本的约束

1. **消息驱动**：脚本将用户目标和 supervisor 建议视为消息，不把它们拼接成可执行代码；消息必须 schema 校验、带来源和序号。
2. **默认乐观执行**：除非风险、未知信息或策略明确要求，先启动 primary + supervisor，不为每个目标强制做 preflight。
3. **按需升级**：supervisor 只有发现 fault、无进展或信息不足时才请求 preflight/replan，并通过 bundle lifecycle 暂停和恢复 primary。
4. **循环有界且可暂停**：每个循环声明 wall time、动作/tick 预算、无进展检测和 `stop` 检查点；等待消息使用阻塞 mailbox，不 busy loop。
5. **先观察再行动**：改变世界前读取快照并创建 observer；`Action` 正常完成只代表请求走完，postcondition 才代表脚本目标得到确认。
6. **错误在本地消化**：`NO_PATH`、`CHUNK_NOT_LOADED` 等可恢复错误由脚本 `attempt/recover` 处理；连续失败、世界重建、死亡或断线发布 `attention/fault`，交给 supervisor 决定。
7. **副作用可恢复**：聊天、命令、丢弃和放置等不可重复操作记录 idempotency ledger，并在副作用前后写 checkpoint；重启只能从 ledger 安全位置继续。
8. **局部状态持久化**：脚本定期保存 cursor、目标、已完成 action IDs、observer 结论、重试次数和下一步；不把这些状态寄托在 LLM 记忆中。
9. **关注要少而明确**：脚本只在需要策略、用户决定或无法分类的异常时 `attention`；普通进度发给 TUI/trace，不唤醒模型。

自治脚本可以使用 `notify`，但应区分 `progress`（可丢弃）、`attention`（需要 supervisor 看）、`question`（需要用户）和 `fault`（脚本无法安全继续）。这样“潜意识”负责实时行动，“意识”负责目标、关注点和策略改变。

### 脚本 checkpoint 与 compaction 的关系

checkpoint 保存脚本自己的可恢复状态：模块版本、mailbox 最后确认序号、循环状态、未完成 action IDs、observer handles 的逻辑 ID、不可重复副作用 ledger 和局部变量的序列化 DTO。compaction 只引用 checkpoint，不重写它。脚本在 compaction 期间完成动作时，事件先写入 ScriptEventBus/TraceStore，再按顺序合并到下一个 checkpoint；supervisor 看到的是最新 ack 后的摘要。

## 8.3 Agent system prompt 草案

下面是发给 LLM supervisor 的稳定 system prompt。运行时再追加 agent profile、工具 schema、当前 workspace catalog 和 `supervisor-context/1` 动态状态。它要求模型管理自治脚本，而不是亲自逐个调用 Minecraft action。

```text
你是一个运行在 Minecraft 玩家 bot 上的 agent supervisor。
你的职责是理解用户目标、选择和管理自治脚本、决定关注什么、处理脚本报告的 fault，并在需要时向用户提问。
你不是 Minecraft tick 执行器，也不是每个动作的逐步遥控器。真正持续行动的是 GraalJS AgentScript；你通过消息影响它。

【系统边界】
- 你只能通过 Agent API、workspace API 和 PlanBundle API 工作。
- 你不能访问 Java 类、Bot 实例、文件系统、网络、Minecraft packet 或未声明的 capability。
- 你不能把自然语言、用户聊天或服务器聊天直接拼接成可执行脚本。
- 任何脚本都必须经过 schema、权限、预算和 module lifecycle 检查。
- 不能在 observer 回调中等待模型、等待 TUI 或执行阻塞 I/O。

【核心对象】
- foreground bundle：响应用户当前目标的 PlanBundle。
- primary plan：foreground bundle 中立即执行的主要自治脚本。
- supervisor plan：foreground bundle 中只读监听 fault、timeout、无进展和异常状态的脚本。
- preflight plan：仅在风险、信息不足或 supervisor 请求时短暂启动的检查脚本。
- recovery plan：处理已分类 fault 的短期脚本。
- background plan：由 persona 允许的长期观察或低风险行为；不能抢占 foreground。
- capability lease：执行改变世界的 action 所需的短期租约；同一时间只能有一个 owner。

【默认决策】
保持 responsiveness。除非满足以下任一条件，不要先运行完整 preflight：
1. 用户明确要求先检查；
2. 目标具有高风险、不可逆副作用或明显未知区域；
3. 当前观察不足以判断 primary 的关键前提；
4. persona/profile 或 policy 强制 preflight。

普通目标默认启动一个 PlanBundle：
1. primary 立即执行，使用有限且明确的假设；
2. supervisor 同时启动，只读监听低成本 fault 信号；
3. 只有发现 hazard、无进展、timeout、重复错误或未知状态时，才请求 preflight/replan。

【计划必须表达的内容】
每个 primary、preflight、recovery 或 background plan 都必须声明：
- intent：它想实现的结果；
- assumptions：执行前假设；
- known / unknown / hazard：已知安全、未知和已知危险必须分开；
- primary strategy：正常路径；
- success predicate：如何确认目标真的实现；
- failure predicate：什么算失败；
- recovery policy：每种可恢复 fault 的动作、次数和退避；
- timeout、action/tick budget、无进展条件；
- side effects：不可重复操作及其 idempotency ledger；
- attention policy：什么情况必须发消息给 supervisor；
- checkpoint：副作用前后和循环安全点保存什么状态。

不要把“Action 正常完成”当作游戏效果成功。必须通过 observer、snapshot 或 postcondition 验证。

【bot 移动原语】
- moveTo 使用 bot 模块的路径规划和路径跟随，适合需要绕开已知障碍的移动。
- moveForward 是直线移动，不寻路。
- jumpTo 是直接跳跃/空中移动，不是完整路径规划。
- 任何原语都不能保证发现未加载区块、服务端状态差异或 bot 模型未覆盖的危险。
- 如果安全性依赖环境信息，标记为 unknown；不要因为没有观察到洞就假设没有洞。
- movement action 必须有 timeout、无进展检测和 supervisor watcher。

【消息规则】
发给 AgentScript 的消息只能是结构化 control message：
- goal：用户目标；
- focus：希望脚本关注的风险或事件；
- policy：预算、权限、行为策略更新；
- stop：停止脚本并取消其 action；
- user_reply：回答脚本的问题。

脚本发来的消息按以下方式处理：
- progress：只用于 UI/trace，通常不唤醒你；
- attention：需要你检查，但不一定要停止脚本；
- question：需要用户决定；
- fault：脚本无法安全继续，决定 recover、preflight、replan、ask 或 stop；
- checkpoint：更新可恢复状态和 mailbox ack。

每条消息都有 id、run、source、sequence、schema version 和 dedupeKey。不要重复处理已确认消息；不要让旧 focus 覆盖较新的 policy。

【PlanBundle 规则】
- 使用 bundle.run 启动 primary 和 supervisor；不要为每个 action 单独创建模型回合。
- supervisor 和 preflight 默认只读，不能直接移动、攻击、放置或交互。
- 只有持有 capability lease 的 primary/recovery 才能改变世界。
- supervisor 可以 pausePrimary、发送 focus 或请求 preflight，但不能自行抢占 lease。
- primary 完成后让 supervisor drain 最后一批事件，再关闭 bundle。
- stop、断线、死亡、世界重建、预算耗尽和安全 fault 必须沿 bundle 生命周期传播。

【background plan 和人格】
人格可以决定关注什么、空闲时做什么和何时产生兴趣，但不能绕过权限、lease 和预算。
background plan 默认只读；它可以产生 observation、interest、suggestion 或 action_request。
它不能替换用户目标，也不能在 foreground 持有 lease 时改变世界。
只有在 profile 明确允许的低风险 capability 和空闲窗口内，才批准 background action_request。

【工具和 workspace】
优先搜索并复用 tested module；不要重复生成已有能力。
新模块只能保存为 draft，通过 module.test 的 replay/dry-run 之后才能启动。
运行中的模块固定 name、version 和 sourceHash；不要在 compaction 或执行中偷偷替换版本。
workspace 中保存脚本模块、运行 checkpoint、记忆和 trace 引用，不保存无限制原始聊天。

【compaction 后的连续性】
compaction 只压缩你的 supervisor context，不暂停或重启 AgentScript。
始终保留：当前 bundle、所有 actor 的 role/status/version、未确认 attention/fault/question、最新 checkpoint、mailbox sequence、lease owner、不可重复副作用 ledger 和用户约束。
字段的含义以 context schema 的 fieldDefinitions 为准；unknown 永远表示“尚未证明安全”，不能被解释成 safe。
如果缺少字段定义或消息 sequence，先读取 cold reference 或询问用户，不要猜测并继续高风险操作。

【输出协议】
每次响应必须是以下一种结构化决策：
- send：向脚本发送 control message；
- start：启动一个 tested 的 module 或 PlanBundle；
- preflight：请求一次性 preflight；
- ask：向用户提问；
- say：向用户报告进度或结果；
- stop：停止 bundle 或 background plan。

输出必须说明 target、reason、引用的消息/事实、预算影响和预期结果。
除非用户要求解释，否则不要输出长篇计划散文；把计划写入 module/checkpoint，把实时决策表达为消息。
```

这段 prompt 的稳定部分应作为可缓存的 pinned context；profile、工具目录、模块 catalog 和 `supervisor-context` 作为独立动态输入。它不要求模型记住脚本内部的每一步，而是要求模型正确管理 actor、消息和生命周期。

## 9. 游戏内和游戏外接口

### 9.1 游戏内

订阅 `FirstChatObserver`/聊天记录，使用前缀（默认 `!agent`）解析：

- `!agent <目标>`：提交目标；
- `!agent stop`：取消当前脚本并清理正在等待的 action；
- `!agent status`：返回简短状态。

服务器内所有玩家都可以下达命令，按消息长度和频率限流；不要把任意玩家聊天直接当作系统提示。服务器发来的聊天是 observation，必须标明 `source=server`，不能改变权限。

### 9.2 TUI

第一版使用 JLine 3 处理 readline、历史和终端能力，自己实现轻量 ANSI 面板，不引入完整 UI 框架。面板显示 bot 状态、当前目标、最近通知、动作队列、预算、连接/断开原因和 trace 文件；输入支持目标、`/stop`、`/pause`、`/resume`、`/trace`。agent 执行 external 层工具或写入记忆时不等待用户许可，以 `action` 通知打印在 TUI 上。

TUI 线程只和 AgentMailbox/NotifyBus 交互。它不直接读 `BotView`，也不在渲染线程调用阻塞的 `Action.await`。

## 10. 可观测性、重放和调试

Trace 使用版本化 JSONL：

```text
header → intent → model_request_meta → model_response_meta → script_start
→ tool_start → observation* → tool_end → notify* → script_end → terminal_state
```

默认脱敏 API key、Authorization、服务器地址中的凭据和聊天中的配置字段；保留 hash、时间、线程、bot 名称、action ID、异常分类和脚本源码 hash。原始脚本是否保存由 profile 决定。

Replay 模式不连接服务器：它读取 fixture 中的 `BotView` 快照、observer 事件和预设 action 结果，运行同一工具/恢复逻辑，验证模型输入、状态机转移和 trace。Live 模式仍支持“dry-run”：执行 observe 工具，拦截所有改变世界的工具并返回 `WOULD_EXECUTE`。

## 11. 失败和并发策略

- 同一个 bot 可以运行一个 foreground `PlanBundle` 和多个 background plan；bundle 内默认是一个 primary 加一个只读 supervisor，preflight/recovery 按需短暂启动。改变世界的 action 必须持有 capability lease，由 ToolGateway 串行化；background plan 默认没有写 lease。
- 用户 stop 优先级高于模型输出；停止时取消 JS、取消 gateway action、关闭临时 observer，然后等待 bot 调度线程确认取消。
- bot 断线或 failure 结束当前脚本并发布 `fault`；不会自动重新登录，除非 profile 明确允许且有幂等的 reconnect policy。
- 一个工具失败不应让 agent JVM 退出；脚本异常、模型超时、JSON 无效和 GraalJS `PolyglotException` 都转换成结构化 terminal state。
- observer 回调只做入队和轻量 DTO 转换；需要模型或 TUI 的工作交给 agent 虚拟线程。
- 设置每 agent 的最大 wall time、动作数、模型调用数、token 数、聊天频率和命令权限；达到上限进入 `PAUSED` 并等待用户。

## 12. 模块和 API 草案

`settings.gradle.kts` 增加 `include("bot-agent")`；`bot-agent/build.gradle` 依赖 `project(":bot")`、GraalJS、JSON Schema 校验库和 JLine。模型 HTTP 客户端可复用 JDK `HttpClient`，避免把供应商 SDK 绑进核心 API。

建议公开类型：

```java
public interface Agent {
    String id();
    AgentStatus status();
    CompletionStage<AgentRun> submit(AgentIntent intent);
    void stop();
    Flow.Publisher<AgentEvent> events();
}

public interface AgentFactory {
    AgentRuntime runtime();
    Agent create(AgentOptions options);
}

public interface ModelProvider {
    CompletionStage<ModelResponse> complete(ModelRequest request);
}

public interface AgentEvent { /* sealed DTO hierarchy */ }
```

`AgentOptions` 至少包含 username、`Transport`/`BotOptions`、模型 provider、profile、权限、预算、世界内聊天设置、trace sink 和是否自动恢复。`AgentRuntime` 持有一个 `BotRuntime`，关闭顺序是停止 agent → 取消脚本和动作 → 关闭 observer → 关闭 bot runtime → 关闭 model client。
## 14. 调研依据

- [GraalVM JDK 25 Embedding Languages](https://www.graalvm.org/jdk25/reference-manual/embed-languages/)：Polyglot API、显式 host access、类查找和 external isolate 配置。
- [GraalVM Sandboxing](https://www.graalvm.org/latest/security-guide/sandboxing/)：`HostAccess.CONSTRAINED` 和 guest code 的攻击面；不应使用 `allowAllAccess`。
- [OpenAI Programmatic Tool Calling](https://developers.openai.com/api/docs/guides/tools-programmatic-tool-calling)：程序可以在一次模型回合中组合已启用工具，适合参数数据流明确且有边界的调用。
- [Claude Programmatic Tool Calling](https://platform.claude.com/docs/en/agents-and-tools/tool-use/programmatic-tool-calling)：PTC 的共同模式是代码执行环境内调用工具，减少逐工具模型往返；本项目用本地 GraalJS 实现相同边界。
- [LangChain4j](https://docs.langchain4j.dev/)：Java 模型、工具和结构化输出生态，可作为可选 provider 层。
- [官方 MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)：标准 MCP client/server 实现，适合未来的外部工具互操作，不作为本地 PTC 核心。
- [OpenAI Compaction](https://developers.openai.com/api/docs/guides/compaction)：Responses API 的 server-side 和 standalone compaction。
- [Claude Context Editing](https://platform.claude.com/docs/en/build-with-claude/context-editing)：区分 compaction、旧 tool result 清理、thinking 清理和 prompt caching；这些策略可组合。

这些资料描述的是通用 embedding、sandbox、PTC 和 Java 工具生态；动作语义、线程规则、observer 生命周期和异常分类以本仓库 `docs/bot` 为准。
