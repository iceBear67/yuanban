# story 模块文档

`story` 是基于 [Minestom](https://github.com/Minestom/Minestom)、`bot` 客户端库、JUnit 5 与 Kotest Matcher 构建的游戏特性集成测试框架。它将测试组织为**可运行的玩家故事（User Stories）**，由逻辑客户端（Bot）扮演玩家进入服务端执行动作，并在服务端与客户端之间提供高精度的 Tick 级确定性状态断言与同步点机制。

源码位于 [`story/src/main/kotlin/io/ib67/yuanban/story`](../../story/src/main/kotlin/io/ib67/yuanban/story)，测试位于 [`story/src/test/kotlin/io/ib67/yuanban/story`](../../story/src/test/kotlin/io/ib67/yuanban/story)。

---

## 核心设计思想

1. **测试即需求（Executable Specification）**
   故事直接陈述业务行为，用领域词汇描述「谁、在什么场景下、做了什么、观察到什么结果」。被测模块的内部实现类不直接侵入测试代码，因此同一套故事可以约束与重构任何底层实现。

2. **双端确定性断言（Dual-Side Verification）**
   大多数游戏状态断言默认为**同步事实（`SyncedFact`）**，既校验 Minestom 服务端数据，又校验玩家客户端（Bot）接收并解析数据包后的实际模型。杜绝「服务端数据改了但没有发包同步到客户端」的逻辑漏洞。

3. **基于标记的 Tick 级同步机制（Mark & Barrier）**
   不使用不可靠的固定时长 sleep，也不进行高频盲轮询。通过服务端在 Tick 结束时向连接插入特殊序号的 Ping 标记数据包，精确保证客户端模型推进至该 Tick 的网络状态，提供 `barrier` 强同步与 `ExpectationRun` 确定性断言。

4. **单进程共享服务端与高并发并行**
   在同一个 JVM 内启动唯一的 Minestom 服务端进程并保持单调 Tick 推进，多个故事依靠动态路由与隔离 Instance 并行执行。同时支持 `@IsolatedJvm` 单独隔离进程运行，防止静态状态污染。

---

## 文档目录

| 文档 | 核心内容 |
|---|---|
| [核心原语教程](tutorial.md) | **系统化心智模型**：从零拆解舞台、角色、动作、时间、事实与断言等全部核心测试原语，剖析设计哲学与综合实战 |
| [快速上手](getting-started.md) | Gradle 依赖与配置、第一个故事、角色与场景搭建、执行动作、编写期望、测试运行与报告解读 |
| [架构参考](architecture.md) | 总体分层架构、`SharedServer` 单例生命周期、`StoryExtension` 拦截链路、测试类型划分（general / isolated） |
| [同步机制与 Tick 检查](synchronization-and-ticks.md) | Minestom Tick 调度周期、`ServerTickMonitorEvent`、负数 Ping 标记（Mark）、`barrier` 同步点、`ExpectationRun` 算法、故事时钟 |
| [事实与期望](facts-and-expectations.md) | `ServerFact` / `ClientFact` / `SyncedFact`、Kotest Matcher 集成、`expect` 时限模式（Within / Throughout）、缺失客户端与未加载区块处理 |
| [角色与世界](actors-and-world.md) | `WorldActor` 与 `WorldSpec`、`Spot` 相对坐标系、`PlayerActor` 与 `PlayerSetup`、`EntityActor`、`WindowActor` 容器交互 |
| [原版词汇表](vanilla-vocabulary.md) | `vanilla` 包下的完整原版动作与事实参考：移动、方块放置与破坏、物品使用与手部操作、战斗、聊天、窗口操作、生命周期、交互距离与先决条件 |
| [词汇扩展指南](extending-vocabulary.md) | 如何为业务模块编写专用 DSL：`testFixtures` 约定、上下文参数 `context(story: StoryScope)`、扩展函数、领域 Actor、自定义事实与断言 |
| [线程模型与运行时](threading-and-runtime.md) | 测试线程、服务端线程与 Bot 调度线程的约束、`StoryEnv` 接口、`onServer` / `everyTickEnd`、并发分支 `simultaneously`、异常捕获与清理流程 |
| [隔离测试与服务端准备](isolated-and-setup.md) | `ServerSetup` 接口与 `StoryDefaults`、全局参数 `yuanban.story.setup`、`@IsolatedJvm` 机制与 Gradle `isolatedTest` 任务配置 |

---

## 阅读建议

- **初学者与编写业务测试**：
  [核心原语教程](tutorial.md) → [快速上手](getting-started.md) → [角色与世界](actors-and-world.md) → [原版词汇表](vanilla-vocabulary.md) → [事实与期望](facts-and-expectations.md) → [词汇扩展指南](extending-vocabulary.md)。

- **排查测试偶发失败与同步问题**：
  [同步机制与 Tick 检查](synchronization-and-ticks.md) → [线程模型与运行时](threading-and-runtime.md) → [隔离测试与服务端准备](isolated-and-setup.md)。

- **扩展或重构 story 框架本身**：
  [架构参考](architecture.md) → [同步机制与 Tick 检查](synchronization-and-ticks.md) → [线程模型与运行时](threading-and-runtime.md) → [隔离测试与服务端准备](isolated-and-setup.md)。

---

## 关联模块

- [`bot`](../bot/README.md)：Minecraft 26.2 客户端协议库与客户端世界状态模型，故事中的玩家由其驱动。
- [`TESTING-OPUS.md`](../../TESTING-OPUS.md)：story 模块的原始架构与详细设计备忘。
- [`TESTING-OPUS-OBSERVABILITY.md`](../../TESTING-OPUS-OBSERVABILITY.md)：调试服务端（Debug Server）与可观测性设计方案。
