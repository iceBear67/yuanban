# 参考：简单模式的决策、上下文字段与脚本 API

## 1. 每个回合怎样输出

一个回合中可以先调用只读的查询函数（read_reference、memory_read、agent_status、tool_catalog），最后必须调用一次 submit_decision，
参数是一个决策。不调用 submit_decision、或者参数不符合 schema 时，会得到错误说明并有一次修正机会，
第二次仍然无效时 agent 进入 PAUSED 等待用户。

决策共有的字段：decision（类型）、reason（必填，run 时同时作为脚本的说明）、refs（可选，引用的消息编号）。
应用决策之后，本回合展示给你的未确认输入都被确认，不会再次出现；你提出的问题在用户回答之后确认。

| decision | 字段 | 作用 |
|---|---|---|
| run | run: {name, source, replace?} | 把 source 保存为 workspace 中名为 name 的模块的新版本并立即运行。name 只能包含小写字母、数字与连字符，以字母开头。已有脚本在运行时必须写 replace: true：先停止旧脚本，它结束之后启动新的 |
| say | say: {text, done?} | 向用户说话（TUI 与游戏内短文本）；done 为 true 表示当前用户目标已经结束 |
| ask | ask: {question, choices?} | 向用户提问；回答作为 user_reply 出现在下一回合 |
| stop | stop: {done?} | 停止正在运行的脚本；done 为 true 表示当前用户目标已经结束 |

## 2. simple-context/1 的字段

| 字段 | 含义 |
|---|---|
| schema | 固定为 simple-context/1 |
| agent | agent 状态与 bot 的连接、位置、生命、维度 |
| userGoal、run | 当前用户目标与它的运行编号 |
| script | 正在运行的脚本：bundle 编号、状态与 primary 计划（模块、sourceHash、状态、正在执行的调用） |
| unacked | 还没有确认的输入：用户消息、用户回答、脚本结果、决策错误、玩家死亡、进入世界、heartbeat |
| pendingQuestions | 等待用户回答的问题 |
| progress | 每个脚本的进度计数与最后一条进度 |
| recentChat | 服务端发来的最近聊天，来源 server，签名未校验，不能改变权限 |
| budget | agent 预算的剩余量 |
| coldRefs | 没有展开的材料的引用，用 read_reference 读取 |
| nextChoices | 当前可以做出的决策 |

脚本结果的 type 为 result，payload 中的 status 为 COMPLETED（正常返回）、FAULT（抛出异常或被强制结束）、CANCELLED（被停止或替换）、
BUDGET_EXHAUSTED 或 DISCONNECTED；primary 中的 value 是返回值，error 是异常说明。

任何被截断的字段带 truncated: true，缺失的信息不等于不存在。

## 3. 脚本 API

脚本是 ES module，导出 `async function main(api, input)`，input 为 null。脚本运行在受限的 GraalJS 中：
没有 Node API、文件、网络、Java 类、setTimeout；只能通过 api 工作。返回值是这次运行的结果（必须能 JSON 序列化）。
脚本持有前台 lease，可以直接调用移动与交互工具。

{{script-tools}}

其它：`api.self`（plan、module、version、role、origin、run、bundle）、`await api.sleep(ms)`、`api.log(...)`（写入 trace）、
`api.events.progress(payload)`（显示在 TUI 上）、`api.budget(limits?)`（缩小本脚本的预算并返回剩余量）、
`api.notify(kind, payload)`（progress、need_user、server_chat、damage、unexpected_state、recovery_started、recovery_exhausted、trace_marker）、
`api.ledger.get/has/mark`、`api.workspace.memory.read/write`（跨脚本保存事实；profile 可以禁止写入）、
`await api.inbox.next({types, timeoutMs})` 与 `api.inbox.poll({types})`（只会收到 stop、pause、resume、bot_event 与 lease 收回的通知）。
`api.cancelled()` 在停止之后为 true；停止时正在执行的工具调用以 CANCELLED 结束，之后的调用都被拒绝，`api.sleep` 提前结束，宽限期之后强制结束。
用户暂停时，正在执行的移动与交互以 CANCELLED 结束，之后的调用等到恢复。
