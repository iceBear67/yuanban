# 架构参考

[目录](README.md) · 上一篇：[快速上手](getting-started.md) · 下一篇：[线程与调度](threading.md)

本篇目录：

- [总体结构](#总体结构)
- [包与职责](#包与职责)
- [公开接口与内部实现](#公开接口与内部实现)
- [核心对象](#核心对象)
- [线程](#线程)
- [入站数据流](#入站数据流)
- [出站数据流](#出站数据流)
- [一轮调度](#一轮调度)
- [一次 tick](#一次-tick)
- [模型的组成](#模型的组成)
- [世界与玩家的重建](#世界与玩家的重建)
- [外部库的使用范围](#外部库的使用范围)
- [设计原则](#设计原则)

## 总体结构

```
测试代码
  │  BotRuntime.connect / Bot 的动作方法 / view() 与 inspect() / observe() / suspendMovement()
  ▼
调度线程（每个 BotRuntime 一个，Scheduler.loop）
  │  每一轮按创建顺序处理每个 bot（BotInstance.round）：
  │    observer 注册与关闭 → 应用入站数据包 → 执行排队的请求 → 一次性请求 → 到期时 tick → 发送 → StateUpdated
  │
  ├── ProtocolMachine        应用数据包，写入自动应答，产生观察记录
  ├── BotState               世界模型：ClientWorld、LocalPlayer、InventoryModel、ChatModel、HudModel 等
  ├── MovementController     移动队列、移动模拟（Kinematics）、移动包（MovementSender）
  ├── HandController         手部交互队列、方块交互序号（BlockSequence）
  ├── 一次性请求队列          BotInstance.oneShots
  ├── ObservationDispatcher  把观察记录投递给订阅的 observer
  └── Outbox                 本轮待发送的数据包
        │                                        ▲
        │ List<MinecraftPacket>                  │ InboundPacket（数据包与附加数据）
        ▼                                        │
ClientConnection.send                     InboundSink（BotInstance.Sink）
        │                                   └── DecodeContext：注册表、标签、维度、区段解码与共享
        ▼                                        ▲
TcpConnection：在 Netty 事件循环上编码、分帧、压缩、写出；读取、解压、解码
或外部模块实现的 Transport / ClientConnection
        │                                        ▲
        └──────────────── 服务端 ────────────────┘
```

bot 的全部状态只在调度线程上修改。连接一侧的线程只负责解码和准备附加数据，把结果放进 bot 的入站队列；测试线程只把请求放进队列。三者之间用并发队列与 `LockSupport.unpark` 交接。

## 包与职责

根包为 `io.ib67.yuanban.bot`，源码目录 [`bot/src/main/java/io/ib67/yuanban/bot`](../../bot/src/main/java/io/ib67/yuanban/bot)。

| 包 | 主要类 | 职责 | 详细说明 |
|---|---|---|---|
| 根包 | `BotRuntime`、`RuntimeOptions`、`Bot`、`BotOptions`、`BotStatus`、`Action`、`ObservedAction`、`InventoryActions`、`MoveOptions`、`MovementMode`、`LoginPluginHandler`、`ResourcePackPolicy`、各异常类 | 公开入口与选项 | [快速上手](getting-started.md)、[动作](actions.md)、[错误处理](errors.md) |
| [`scheduler`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler) | `Scheduler`、`BotInstance`、`BotState`、`BotTicker`、`ActionImpl`、`StateReaderImpl`、`BotViewImpl`、`ObserverHandleImpl`、`ObservedActionImpl`、`Waits`、`WaitPump` | 调度线程、`Bot` 的实现、tick 顺序、阻塞等待 | [线程与调度](threading.md) |
| [`transport`](../../bot/src/main/java/io/ib67/yuanban/bot/transport) | `Transport`、`ClientConnection`、`InboundSink`、`TransportResolver`、`TcpTransport`、`TcpConnection`；包内可见的 `BotClientSession`、`LastReadHandler`、`NettyGroup` | 连接接口与 TCP 实现 | [连接与解码](connection.md) |
| [`decode`](../../bot/src/main/java/io/ib67/yuanban/bot/decode) | `DecodeContext`、`SectionDecoder`、`SectionPool`、`DecodedChunk` | 数据包交给调度线程之前的处理 | [连接与解码](connection.md#解码上下文) |
| [`protocol`](../../bot/src/main/java/io/ib67/yuanban/bot/protocol) | `ProtocolMachine`、`Outbox`、`InboundPacket`、`BundleBuffer`、`ChunkBatchTracker`、`CookieStore`、`PacketDirection`、`Vectors` | 协议状态机、自动应答、待发送数据包、数据包方向 | [协议状态机](protocol.md) |
| [`registry`](../../bot/src/main/java/io/ib67/yuanban/bot/registry) | `SyncedRegistries`、`SyncedRegistriesBuilder`、`DimensionInfo`、`TagTable`、`VanillaNetworkCodec` | 服务端同步的注册表与标签 | [注册表与静态数据](registries-and-data.md) |
| [`data`](../../bot/src/main/java/io/ib67/yuanban/bot/data) | `ItemData`、`Items`、`AttributeData`、`EntityTypeData`、`BlockStates`、`MinestomDataFiles` | 取自 `net.minestom:data` 的静态数据 | [注册表与静态数据](registries-and-data.md#静态数据) |
| [`world`](../../bot/src/main/java/io/ib67/yuanban/bot/world) | `ClientWorld`、`ChunkColumn`、`SectionRef`、`SharedSection`、`WorldSnapshot`、`BlockEntityStore`、`WorldClocks`、`TickState`、`WorldBorderState`、`Weather` | 世界模型 | [世界模型](world-model.md) |
| [`entity`](../../bot/src/main/java/io/ib67/yuanban/bot/entity) | `EntityStore`、`ClientEntity`、`EntityDimensions` | 世界中的其它实体与包围盒 | [世界模型 · 实体](world-model.md#实体) |
| [`player`](../../bot/src/main/java/io/ib67/yuanban/bot/player) | `LocalPlayer`、`AttributeMap`、`EffectMap`、`Abilities`、`PlayerLoadTracker`、`Interpolation` | 本地玩家 | [世界模型 · 本地玩家](world-model.md#本地玩家) |
| [`inventory`](../../bot/src/main/java/io/ib67/yuanban/bot/inventory) | `InventoryModel`、`Window`、`ContainerSlots`、`InventoryActionsImpl` | 物品栏模型与物品栏操作 | [世界模型 · 物品栏](world-model.md#物品栏)、[动作 · 一次性请求](actions.md#一次性请求) |
| [`chat`](../../bot/src/main/java/io/ib67/yuanban/bot/chat) | `ChatModel`、`LastSeenTracker`、`SignatureCache`、`Signature` | 聊天日志、已读跟踪、签名缓存 | [协议状态机 · 聊天与命令](protocol.md#聊天与命令) |
| [`hud`](../../bot/src/main/java/io/ib67/yuanban/bot/hud) | `HudModel` | Boss Bar、标题、动作栏、玩家列表、计分板 | [世界模型 · 界面状态](world-model.md#界面状态) |
| [`movement`](../../bot/src/main/java/io/ib67/yuanban/bot/movement) | `MovementController`、`MovementAction` 与四个子类、`MoveIntent`、`Kinematics`、`PathPlanner`、`PathFollower`、`RotationArbiter`、`Rotation`、`MovementSender`、`SineTable`、`RidingSeat`、`VehicleControl` | 移动队列、移动模拟、寻路跟随、朝向、骑乘 | [移动模拟与骑乘](movement.md) |
| [`interaction`](../../bot/src/main/java/io/ib67/yuanban/bot/interaction) | `HandController`、`HandAction` 与五个子类、`BlockSequence`、`DigSpeed`、`BlockPredicates`、`NbtCompare`、`Directions` | 手部交互队列 | [动作 · 手部交互动作](actions.md#手部交互动作) |
| [`view`](../../bot/src/main/java/io/ib67/yuanban/bot/view) | `BotView`、`StateReader`、各快照 record、`ActiveEffect`、`EntityMeta`、`EntityMetaIndex`、`ChatLine`、`ChatKind` | 只读的查询接口与快照 | [查询与观察 · 查询](observation.md#查询) |
| [`observe`](../../bot/src/main/java/io/ib67/yuanban/bot/observe) | `Observation`、`Observer`、`Subscription`、`ObserverContext`、`ObserverHandle`、`CloseReason`、`WorldBoundObserver`、`WorldInfo`、`WorldEntryCause`、`WorldRecord`、`WorldClosed`、`Region` | 观察的公开接口 | [查询与观察](observation.md) |
| [`observe.builtin`](../../bot/src/main/java/io/ib67/yuanban/bot/observe/builtin) | `Observers` 与各内置 observer、`MovementCriteria`、`Records`、各记录 record | 内置 observer | [查询与观察 · 内置 observer](observation.md#内置-observer) |
| [`observe.dispatch`](../../bot/src/main/java/io/ib67/yuanban/bot/observe/dispatch) | `ObservationDispatcher`、`SubscriptionIndex`、`ObserverEntry`、`ObserverContextImpl`、`ObservationHost`、`RegistrationCheck`、`ObserverFailure` | 观察记录的投递与订阅索引 | [查询与观察 · 投递](observation.md#投递) |

## 公开接口与内部实现

- 公开的包：根包、`view`、`observe`、`observe.builtin`、`transport`。
- 其余包（`scheduler`、`decode`、`protocol`、`registry`、`data`、`world`、`entity`、`player`、`inventory`、`chat`、`hud`、`movement`、`interaction`、`observe.dispatch`）在 `package-info.java` 中整体标注 `@ApiStatus.Internal`。其中的类大多是 `public`，因为包之间互相调用。
- 值类型使用 record，选项类使用 Lombok `@Builder`（`BotOptions`、`RuntimeOptions`、`MoveOptions`），可空性使用 JetBrains annotations 标注。
- 公开接口直接使用 MCProtocolLib 与 Minestom 的类型，本库不包装它们，见[外部库的使用范围](#外部库的使用范围)。

## 核心对象

一个 `BotRuntime` 持有一个 `Scheduler`；`Scheduler` 持有调度线程与按创建顺序排列的 `BotInstance` 列表。每个 `BotInstance`（`Bot` 的实现，同时实现 `ObservationHost`）持有：

| 字段 | 类型 | 说明 |
|---|---|---|
| `state` | `BotState` | 这个 bot 的全部模型，只在调度线程上读写 |
| `protocol` | `ProtocolMachine` | 协议状态机，全部连接共用一个 |
| `movement` | `MovementController` | 移动队列与移动步骤 |
| `hand` | `HandController` | 手部交互队列与手部步骤 |
| `dispatcher` | `ObservationDispatcher` | 观察记录的投递 |
| `reader`、`view` | `StateReaderImpl`、`BotViewImpl` | 调度线程上直接读取的 `StateReader`；跨线程查询的 `BotView` |
| `inbound` | `ConcurrentLinkedQueue<Object>` | 入站队列，元素为 `Received(generation, InboundPacket)` 或 `Closed(generation, error)` |
| `observerTasks`、`requests` | `ConcurrentLinkedQueue<Runnable>` | 其它线程交来的 observer 注册与关闭、其余请求（动作入队、取消、查询、新连接的接入） |
| `oneShots` | `ArrayDeque<OneShot>` | 一次性请求队列 |
| `unfinished` | `Set<ActionImpl>` | 全部未结束的动作，连接结束时逐个结束 |
| `connection`、`generation` | `ClientConnection`、`int` | 当前连接与连接的代数；每打开一条连接代数加一，旧连接交来的数据包与关闭通知按代数丢弃 |
| `bundles` | `BundleBuffer` | 当前连接的 bundle 缓存 |

`BotState` 持有 `Outbox`（本轮待发送的数据包）与 `ObservationDispatcher` 的引用，所以 `ProtocolMachine`、两个控制器、`InventoryActionsImpl` 都只依赖 `BotState` 写入数据包与产生观察记录。

## 线程

| 线程 | 数量 | 职责 |
|---|---|---|
| 调度线程（平台线程，守护线程，名为 `yuanban-bot-scheduler-<序号>`） | 每个 `BotRuntime` 一个 | 修改全部 bot 的状态：应用数据包、执行请求、tick、注册与关闭 observer、调用 observer 回调 |
| Netty 事件循环（守护线程，名称以 `yuanban-bot-netty` 开头） | JVM 中全部 TCP 连接共用一组（`NettyGroup`），每条连接固定在其中一个线程上 | 读取、分帧、解压、解码数据包，调用 `InboundSink`；编码并写出调度线程交来的数据包 |
| 寻路线程（虚拟线程，名为 `yuanban-bot-path`） | 每次寻路一个 | 在世界快照上计算路径 |
| Transfer 线程（虚拟线程，名为 `yuanban-bot-transfer`） | 每次 Transfer 一个 | 打开新连接 |

外部模块实现的 `Transport` 在它自己决定的线程上调用 `InboundSink`，调用必须串行。细节见[线程与调度](threading.md)。

## 入站数据流

1. **连接线程**（TCP 为 Netty 事件循环）：MCProtocolLib 的管线按当前入站阶段分帧、解压、解码，得到 `MinecraftPacket`（[连接与解码 · 入站](connection.md#入站)）。
2. `InboundSink.accept`（[`BotInstance.Sink`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/BotInstance.java)）调用这条连接的 `DecodeContext.process`，得到附加数据：Chunk Data 附带解码出的 `DecodedChunk`，Update Tags 附带更新后的 `TagTable`，Finish Configuration 附带 `ConfigurationFinished(SyncedRegistries, TagTable)`，其余为 null（[连接与解码 · 解码上下文](connection.md#解码上下文)）。
3. `Sink` 把 `Received(代数, InboundPacket)` 放进 bot 的入站队列，唤醒调度线程。
4. **调度线程**在一轮调度中取出入站队列的全部内容，丢弃旧代数的条目，交给 `BundleBuffer`：bundle 之外的数据包单独成批，bundle 在结束分隔符处整批交出。
5. `ProtocolMachine.handle` 逐个应用数据包：修改 `BotState`，在 `Outbox` 中写入应答，按变化的顺序产生模型层的观察记录，最后产生 `PacketReceived`。
6. 每批处理完后，`ObservationDispatcher.flush` 按产生顺序把观察记录投递给订阅的 observer。

## 出站数据流

1. `ProtocolMachine`、`MovementController`、`HandController`、一次性请求只把数据包写入 `Outbox`；在写出时执行完毕的动作通过 `Outbox.completeWhenWritten` 记在同一批中。
2. 一轮调度的最后，`BotInstance.flushOutbox` 取出整批数据包，一次交给 `ClientConnection.send`，然后为每个数据包产生 `PacketSent`。
3. `TcpConnection.send` 在通道的事件循环上执行一个任务：依次编码写入，遇到改变协议阶段的数据包时立即切换出站阶段，最后 flush 一次（[连接与解码 · 出站](connection.md#出站)）。
4. `send` 返回的 future 完成时，这一批中写出即执行完毕的动作执行完毕（一次性请求、`attack`、`interact`、`useItem` 的结束）。

## 一轮调度

`Scheduler.loop` 每一轮按 bot 的创建顺序调用 `BotInstance.round(now)`，然后等待到最早的 tick 或读取超时到期，或者被唤醒。`round` 的顺序：

1. 处理排队中的 observer 注册与关闭（`observerTasks`），投递产生的观察记录。
2. 取出入站队列，逐批应用数据包，每批之后投递观察记录。
3. 完成上一轮在调度线程上调用、已经发生过的 `joined()` 与 `loaded()`。
4. 执行排队中的请求（`requests`：动作入队、取消、查询、新连接的接入等），投递观察记录。
5. 更新实际的暂停状态，检查 TCP 连接的读取超时。
6. bot 处于游戏阶段并且已经加载时，按提交顺序执行一次性请求。
7. tick 到期时执行一次 tick（`BotTicker.tick`）。
8. 把 `Outbox` 中的数据包一次交给连接，投递 `PacketSent`。
9. 本轮状态有变化时产生并投递 `StateUpdated`。

`round` 中抛出的任何异常都结束这个 bot（[错误处理](errors.md)），调度线程继续处理其它 bot。详见[线程与调度 · 调度循环](threading.md#调度循环)。

## 一次 tick

[`BotTicker.tick`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/BotTicker.java) 的顺序与原版客户端 `Minecraft.tick` 对应：

1. tick 计数加一；物品冷却与药水效果的剩余时间减一；`TickState` 决定这个 tick 是否正常运行。
2. 快捷栏同步：本地的快捷栏槽位与已经发送的不同时发送 Set Held Item。
3. 加载判断：`PlayerLoadTracker` 条件满足时发送 Player Loaded，完成 `loaded()`，产生 `PlayerLoaded`。
4. 已经加载时，手部交互队列推进一步。
5. 其它实体执行每 tick 的实体步骤（马类的人立动画）。
6. 已经加载时，移动队列推进一步：决定朝向、疾跑、Player Input，执行移动模拟或骑乘，发送移动包。
7. 这个 tick 正常运行时，世界边框推进一步，World Age 加一，时钟按新的 World Age 推进。
8. 发送 Client Tick End。
9. 产生 `BotTicked`。

每一步产生的观察记录在这一步完成后投递，observer 回调中读到的模型已经包含这一步的结果。手部步骤在移动步骤之前，所以手部数据包排在同一 tick 的移动包之前；需要对准目标的手部动作因此在开始的 tick 只确定朝向，下一个 tick 才发送数据包（[动作 · 先看向目标](actions.md#先看向目标)）。

## 模型的组成

```
BotState（调度线程独占）
├── BotOptions、Outbox、ObservationDispatcher 的引用
├── 连接的信息         服务端 brand、Server Links、Feature Flags
├── SyncedRegistries   Finish Configuration 时由解码上下文构建，不可变
├── TagTable           不可变，每次 Update Tags 换成新的实例
├── WorldClocks        属于连接，每次 Finish Configuration 时重新创建
├── ClientWorld        Login (play) 或维度名称改变的 Respawn 时创建；Start Configuration 与 Transfer 时清除
│   ├── 维度名称、维度类型编号、最低 y、高度
│   ├── 区块：Long2ObjectMap<ChunkColumn>，每列为 SectionRef 数组
│   ├── BlockEntityStore（方块实体的 NbtMap）
│   ├── EntityStore（ClientEntity，按编号排列）
│   ├── World Age、TickState、WorldBorderState、Weather
│   └── 难度、默认出生点、服务端视距、模拟距离、加载区域中心
├── WorldInfo          当前世界的编号（epoch）、维度、进入原因
├── LocalPlayer        新的连接或重新配置之后的 Login (play)，以及 Respawn 时重新创建
├── InventoryModel     随 LocalPlayer 重新创建
├── PlayerLoadTracker  加载判断
├── ChatModel          聊天日志、已读跟踪、签名缓存
├── HudModel           界面状态
├── CookieStore        跨 Transfer 保留
└── tick 计数
```

各部分的内容见[世界模型](world-model.md)。

## 世界与玩家的重建

| 事件 | 世界 | 本地玩家与物品栏 | 移动与手部队列 | 方块交互序号 | 其它 |
|---|---|---|---|---|---|
| Start Configuration、Transfer（离开世界） | 清除，bot 不在任何世界中 | 保留到下一次 Login (play) | 以 `WORLD_REBUILT` 取消，不发送清理数据包 | 归零，丢弃等待中的确认 | 加载状态重置 |
| 不在世界中时收到 Login (play) | 新建 | 新建 | 不取消（都是在没有世界时提交的） | 归零 | 已读跟踪、签名缓存、聊天全局编号重新开始；挖掘延迟与已发送的快捷栏槽位归零 |
| 在世界中再次收到 Login (play) | 新建 | 沿用，只做原版 `resetPos` | 以 `WORLD_REBUILT` 取消，不发送清理数据包 | 归零 | 同上 |
| Respawn，维度名称改变 | 新建 | 新建，按 Data kept 保留部分数据 | 以 `WORLD_REBUILT` 取消，执行中的动作照常清理 | 归零 | 打开的窗口先关闭 |
| Respawn，维度名称不变 | 保留区块、方块实体与其它实体 | 同上 | 同上 | 不变 | 同上 |
| 玩家死亡 | 不变 | 不变 | 以 `PLAYER_DIED` 取消 | 不变 | |
| Finish Configuration | | | | | 注册表、标签换成新的，时钟重新创建 |

每次进入世界（Login (play) 与 Respawn）都产生 `WorldEntered`，`WorldInfo.epoch` 加一。细节见[协议状态机 · Login (play) 与 Respawn](protocol.md#login-play-与-respawn)。

## 外部库的使用范围

依赖声明在 [`bot/build.gradle`](../../bot/build.gradle)：

| 依赖 | 方式 | 用途 |
|---|---|---|
| `org.geysermc.mcprotocollib:protocol:26.2-20260907.143312-19` | `api`，排除 `com.nukkitx.fastutil` | 数据包的编解码、分帧、压缩与 Netty 连接；数据包、`ItemStack`、`DataComponents`、各种协议枚举（`EntityType`、`GameMode`、`Hand`、`EquipmentSlot`、`Direction`、`Pose`、`Effect`、`AttributeType`、`ContainerType` 等）。排除的 jar 中的类与 fastutil 同名 |
| `net.minestom:minestom:2026.10.05-26.2` | `api` | 只使用物理（`PhysicsUtils`、`CollisionUtils`、`BoundingBox`、`Shape`、`WorldBorder`、`Aerodynamics`、`PhysicsResult`）、寻路（`PathGenerator`、`GroundNodeGenerator`、`PNode`）、坐标（`Point`、`Pos`、`Vec`、`CoordConversion`）与静态方块数据（`Block`、`Block.Getter`）。不使用它的网络代码、注册表、`MinecraftServer` 的进程状态与物品系统 |
| `it.unimi.dsi:fastutil:8.5.19` | `api` | MCProtocolLib 的公开类型使用 fastutil |
| `net.minestom:data:26.2-rv3` | `implementation` | 原版 26.2 导出的 JSON：物品编号与默认组件、属性的取值范围、实体类型的尺寸与乘客挂点 |
| `com.github.ben-manes.caffeine:caffeine:3.3.0` | `implementation` | 区段池与已注册 observer 的弱引用缓存 |

其余约定：

- NBT 使用 Cloudburst 的 `NbtMap`，注册表条目的名称使用 adventure 的 `Key`，文本使用 adventure 的 `Component`，三者随 MCProtocolLib 带入。
- 两个库都有的概念使用 MCProtocolLib 的类型，公开接口也是。
- MCProtocolLib 的 `EntityType`、`BlockEntityType`、`Effect`、`BuiltinSound`、`AttributeType.Builtin`、`ParticleType`、`ContainerType` 的序号与原版注册表编号相同，本库直接按序号与编号互相转换；`EntityTypeData` 与 `AttributeData` 在加载时逐项检查。
- 两个库的服务端逻辑都不作为客户端行为的依据。

## 设计原则

- **依据**：协议行为以 minecraft.wiki 的 Java Edition protocol 页面为准；wiki 没有写明的客户端行为按原版 26.2 客户端实现，代码注释中写明对应的原版方法。引用的 wiki 修订版本列在 [`bot/DESIGN.md`](../../bot/DESIGN.md) 的开头。
- **线程封闭**：模型只在调度线程上读写，修改模型的入口检查当前线程。
- **动作与结果分开**：`Action` 只表示请求的执行过程是否走完，游戏中的效果由 observer 观察；库中没有通用的「成功」概念。
- **就地失败**：出错的位置抛出异常，所属的 bot 结束，异常传到测试代码；不提供替代值。见[错误处理](errors.md)。
- **按需产生观察记录**：模型在产生观察记录之前先查询订阅索引，没有订阅者的类型不创建对象。
- **轻量**：区段在同一个 JVM 的全部 bot 之间按内容共享并写入时复制，光照与高度图在解码时丢弃，加载区域外的区块立即卸载，默认视距为 2。

[目录](README.md) · 上一篇：[快速上手](getting-started.md) · 下一篇：[线程与调度](threading.md)
