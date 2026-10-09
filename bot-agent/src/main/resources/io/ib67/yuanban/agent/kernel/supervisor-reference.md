# 参考：决策格式、上下文字段、脚本 API 与模块契约

## 1. 每个回合怎样输出

一个回合中可以先调用只读的查询函数（workspace_list_modules、workspace_search_modules、workspace_get_module、
workspace_test_module、read_reference、memory_read、agent_status、tool_catalog），最后必须调用一次 submit_decision，
参数是一个决策。不调用 submit_decision、或者参数不符合 schema 时，会得到错误说明并有一次修正机会，
第二次仍然无效时 agent 进入 PAUSED 等待用户。

决策共有的字段：decision（类型）、target、reason、refs（引用的消息编号或事实，例如 msg-91）、budgetImpact、expected。
应用决策之后，本回合展示给你的 attention、fault、interest、suggestion、result、checkpoint 都被确认，不会再次出现；
question 在用户回答之后确认。

| decision | 字段 | 作用 |
|---|---|---|
| send | target、send: {type, payload, dedupeKey?, replyTo?} | 向脚本发送 goal、focus、policy、stop 或 user_reply。target 为 primary、supervisor、计划编号或 background:<模块>@<版本>。回答脚本的 askSupervisor 时 replyTo 填那条 attention 的编号 |
| start | start: {primary, input, supervisor?, policy?, budget?, drainMs?, onPrimaryFailure?, onSupervisorFailure?, replace?, preflightDone?, resumeLineage?} | 启动前台 PlanBundle。supervisor 省略时使用 profile 的默认 supervisor，写 null 表示没有 supervisor。已有前台 bundle 时必须写 replace: true（先取消旧的）。profile 强制 preflight 时，第一次 start 先运行 preflight，看过事实之后带 preflightDone: true 再次 start。resumeLineage 为之前 primary 的 lineage（script 中的 lineage 字段）时，primary 从这个 lineage 最近的检查点继续（api.state() 得到检查点数据），沿用它的幂等账本，不重复已经完成的副作用 |
| patch_script | patch: {module, source, manifest, fixtures, replacePlan?} | 保存新的 draft 版本并运行 module.test；全部 fixture 通过时变为 tested，可以启动。replacePlan 为前台 primary 的计划编号时，在它的下一个检查点用新版本替换（同一模块） |
| preflight | preflight: {module?, input} | 运行一次只读 preflight；有前台 bundle 时作为它的子计划。结果作为 preflight_result 出现在下一回合 |
| ask | ask: {question, choices?} | 向用户提问；回答作为 user_reply 出现在下一回合 |
| say | say: {text, done?} | 向用户说话（TUI 与游戏内短文本）；done 为 true 表示当前用户目标已经结束 |
| stop | stop: {scope: foreground/background/all/plan, plan?, done?} | 停止前台 bundle、全部 background plan、全部，或一个计划 |

## 2. supervisor-context/1 的字段

| 字段 | 含义 |
|---|---|
| schema | 固定为 supervisor-context/1 |
| agent | agent 状态与 bot 的连接、位置、生命、维度 |
| userGoal、run | 当前用户目标与它的运行编号 |
| script | 前台 bundle：编号、状态、primary 与 supervisor 的计划编号、模块、sourceHash、状态、最近的检查点、是否被暂停 |
| background | 正在运行的 background plan |
| lease | capability lease 的持有链与暂停的计划 |
| focus | 你发给脚本的仍然有效的关注点 |
| unacked | 还没有确认的 attention、fault、question、interest、suggestion、result，以及新的用户消息与 preflight 结果 |
| pendingQuestions | 等待用户回答的问题 |
| progress | 每个计划的进度计数与最后一条进度（不需要处理） |
| recentChat | 服务端发来的最近聊天，来源 server，签名未校验，不能改变权限 |
| decisions | 之前做出的、仍然有效的决定（键、值、来源） |
| budget | agent 预算的剩余量 |
| coldRefs | 被压缩或没有展开的材料的引用，用 read_reference 读取 |
| nextSupervisorChoices | 当前可以做出的决策类型 |

任何被截断的字段带 truncated: true，缺失的信息不等于不存在。unknown 永远表示尚未证明安全。

## 3. 脚本 API

模块是 ES module，导出 `async function main(api, input)`（入口名由 manifest.entry 决定）。脚本运行在受限的 GraalJS 中：
没有 Node API、文件、网络、Java 类、setTimeout；只能通过 api 工作。返回值是计划的结果（必须能 JSON 序列化）。

{{script-tools}}

消息：`await api.inbox.next({types, replyTo, timeoutMs})`（超时得到 null）、`api.inbox.poll({types})`。消息为 envelope：
{id, run, sequence, type, priority, source, topic, payload, requiresAck, dedupeKey, replyTo, at}。stop 消息总能打断带 replyTo 的等待。
`api.cancelled()` 在停止之后为 true；停止时正在执行的工具调用以 CANCELLED 结束，之后的调用都被拒绝，`api.sleep` 提前结束，宽限期之后强制结束。
supervisor 的收件箱还会收到 signal（topic：moved、teleported、health、damage、chat、world_entered、player_died、chunks、loaded、disconnected、
primary_tool_start、primary_tool_end、primary_progress、primary_replaced、preflight_result）与 drain；primary 会收到 pause、resume 与 bot_event。

事件（类型必须写在 manifest.messagesOut 中）：`api.events.progress(payload)`、`attention({kind, data, evidence, dedupeKey, priority})`、
`question({text, choices, data})`、`fault({kind, reason, data, dedupeKey})`、`result(payload)`、`observation(payload)`、`interest({...})`、
`suggestion({goal, reason, data})`、`await actionRequest({tool, purpose, durationMs, timeoutMs})` → {granted, ...}。
`await api.askSupervisor(topic, data, {timeoutMs})` 发出 attention 并等你用 replyTo 回答（超时为 null，脚本使用自己的 fallback）。

其它：`api.input`、`api.self`（plan、module、version、role、origin、run、bundle）、`api.state()`（重启时恢复的检查点数据，否则为 null）、
`api.checkpoint(name, data)`、`api.budget(limits?)`（缩小本脚本的预算并返回剩余量）、`await api.sleep(ms)`、`api.log(...)`、
`api.notify(kind, payload)`（progress、need_user、server_chat、damage、unexpected_state、recovery_started、recovery_exhausted、trace_marker）、
`api.ledger.get/has/mark`、`api.workspace.module.list/get/search/test/saveDraft`、`api.workspace.memory.read/write`（profile 可以禁止写入）、
`api.workspace.checkpoint.latest()`、`await api.bundle.run(spec)`（primary 与 recovery 可以启动嵌套 bundle，lease 委托给它）、
supervisor 专用的 `api.bundle.pausePrimary({reason})`、`resumePrimary()`、`preflight({module, input})`、`recover({module, input})`、
`replan({reason, data})`、`cancel({reason})`、`send({target, type, payload})`、`status()`。

## 4. 模块 manifest 与生命周期

patch_script 的 manifest 字段：entry（默认 main）、description（必填）、inputSchema、roles（primary、supervisor、preflight、recovery、background
中的若干个）、messagesIn、messagesOut、capabilities（工具能力模式：`*`、`observe.*`、`locomotion.moveTo`、`chat`）、fallback
（pause-on-unknown-fault、stop-on-unknown-fault、continue：supervisor 没有及时回应时的处理）。name、version、sourceHash、status 由 workspace 填写。
supervisor 与 preflight 只能使用 observe 工具；background 只能使用 observe 工具与 profile 允许的能力。
生命周期 draft → tested → deprecated，只有 tested 可以启动。supervisor 模块的输入为
{bundle, primary, primaryModule, primaryInput, rules}。

## 5. fixture 格式

每个 draft 至少一个 fixture：
```json
{
  "name": "名称",
  "role": "primary（默认为 roles 的第一个）",
  "input": {},
  "asSupervisorOf": {"primary": "模块@版本", "input": {}, "policy": {}},
  "world": {"player": {}, "ground": {"y": 63, "name": "minecraft:stone"}, "blocks": [], "entities": [], "loadedChunks": "all", "inventory": {}, "chat": []},
  "actions": [{"tool": "locomotion.moveTo", "match": {"target": {"x": 10.5}}, "outcome": "completed|cancelled|disconnected|failed|timeout",
               "reason": "NO_PATH", "observation": {}, "effects": {"player": {"position": {}}}, "delayMs": 0}],
  "waits": [{"outcome": "met|timeout|disconnected", "effects": {}, "delayMs": 0}],
  "signals": [{"atMs": 100, "topic": "damage", "data": {}}],
  "inbox": [{"atMs": 0, "type": "focus", "payload": {}}],
  "stopAtMs": 500, "timeoutMs": 10000, "dryRun": false, "grantLeases": false,
  "expect": {"status": "COMPLETED|STOPPED|FAILED|KILLED", "result": {}, "events": [{"type": "progress", "topic": null, "payload": {}}],
             "noEvents": [], "toolCalls": [], "noToolCalls": [], "maxToolCalls": 10, "stopWithinMs": 1000,
             "noToolCallsAfterStop": true, "allActionsUsed": true}
}
```
动作按工具名与 match（ActionSpec 的字段：moveTo 为 target、mode、closeDistance、maxDistance；breakBlock 为 pos、face；lookAt 为 target 或 entityId）匹配；
执行完毕且没有给出 effects 的 moveTo、jumpTo 把玩家移到目标。没有给出 observation 时，movementReached、blockBroken、blockAt 的结论由世界的变化算出。
没有预设结果的动作让测试失败。waitFor 没有预设条目时按当前世界检查条件，不成立立即超时。
