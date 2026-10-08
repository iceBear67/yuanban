# bot 模块文档

`bot` 是 Minecraft: Java Edition 26.2（协议号 776）的客户端库。集成测试用它创建逻辑上的玩家，让玩家加入服务端、执行动作，并观察服务端的反应。数据包的编解码与 TCP 连接由 [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) 完成；物理计算、寻路、坐标与静态方块数据使用 [Minestom](https://github.com/Minestom/Minestom)。

源码位于 [`bot/src/main/java/io/ib67/yuanban/bot`](../../bot/src/main/java/io/ib67/yuanban/bot)，测试位于 [`bot/src/test/java/io/ib67/yuanban/bot`](../../bot/src/test/java/io/ib67/yuanban/bot)。

## 目录

| 文档 | 内容 |
|---|---|
| [快速上手](getting-started.md) | 创建运行时与 bot、等待加载、提交动作、查询模型、注册 observer、暂停移动 |
| [架构参考](architecture.md) | 分层、各个包与类的职责、线程、入站与出站的数据流、一轮调度与一次 tick 的顺序、模型的组成、外部库的使用范围 |
| [线程与调度](threading.md) | 调度线程的循环、tick 周期、线程封闭、跨线程查询、阻塞等待与推进函数、`BotRuntime` 的关闭 |
| [连接与解码](connection.md) | `Transport` 接口、TCP 连接的入站与出站、读取超时、进程内连接的约定、解码上下文、区段解码与共享 |
| [协议状态机](protocol.md) | 登录与配置、任意阶段的自动应答、传送的计算、Transfer、重新配置、Login (play) 与 Respawn、聊天签名相关的状态、Bundle |
| [注册表与静态数据](registries-and-data.md) | 同步注册表、标签表、原版注册表数据、物品与属性与实体类型的静态数据、物品的比较 |
| [世界模型](world-model.md) | `BotState` 的组成、区块与区段共享、世界快照、实体与包围盒、乘坐关系、本地玩家、物品栏、聊天日志、界面状态、时间与世界边框 |
| [动作](actions.md) | `Action` 句柄、三类队列、取消原因、朝向的归属、各个移动动作、手部交互动作、一次性请求 |
| [移动模拟与骑乘](movement.md) | 每 tick 的移动计算、疾跑与 Player Input、移动包、寻路与路径跟随、服务端施加的位置与速度、暂停移动、骑乘 |
| [查询与观察](observation.md) | `BotView` 与 `inspect`、观察记录、`Observer` 与 `Subscription`、observer 的生命周期、`WorldBoundObserver`、内置 observer、`observeDuring`、等待方法 |
| [错误处理](errors.md) | 异常类型、bot 因异常结束的过程、连接结束的过程、不支持的输入 |
| [测试](testing.md) | 测试方式、`TestPeer` 与 `TestServer`、各测试类的范围 |
| [限制与待办](limitations.md) | 不实现的功能、不覆盖的移动、对 Minestom 的绕过方法、待办 T1 到 T4 |

## 阅读顺序

- **使用本库编写测试**：[快速上手](getting-started.md) → [动作](actions.md) → [查询与观察](observation.md) → [错误处理](errors.md) → [限制与待办](limitations.md)。
- **修改本库**：[架构参考](architecture.md) → [线程与调度](threading.md) → [连接与解码](connection.md) → [协议状态机](protocol.md) → [世界模型](world-model.md) → [动作](actions.md) → [移动模拟与骑乘](movement.md) → [查询与观察](observation.md) → [测试](testing.md)。

## 其它资料

- [`bot/README.md`](../../bot/README.md)：模块的用法说明，以及待办表格。
- [`bot/DESIGN.md`](../../bot/DESIGN.md)：逐条的设计依据，包括引用的 minecraft.wiki 修订版本，以及每一处「原版客户端行为」对应的原版方法。
- [`MINESTOM-BUG-REPORT.md`](../../MINESTOM-BUG-REPORT.md)：本库绕过的 Minestom 问题 M12。
