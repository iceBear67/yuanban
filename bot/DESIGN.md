# bot 设计

## 依据

**协议行为**以 minecraft.wiki 的 *Java Edition protocol* 系列页面为准。wiki 当前页面已更新到 26.3（协议号 777），本库对应 26.2（协议号 776），因此引用以下修订版本：

| 页面 | 修订号 | 时间 |
|---|---|---|
| Java Edition protocol/Packets | 3786274 | 2026-09-20 |
| Java Edition protocol/FAQ | 3769958 | 2026-09-11 |
| Java Edition protocol/Registries | 3749652 | 2026-08-29 |
| Java Edition protocol/Chunk format | 3661930 | 2026-07-07 |
| Java Edition protocol/Inventory | 3201379 | 2025-10-14 |
| Java Edition protocol/Chat | 3624820 | 2026-06-13 |

- **游戏机制的数值**取自 wiki 的 Transportation、Jumping、Breaking、Commands/tick 页面。
- **wiki 没有写明的客户端行为**按原版 26.2 客户端实现，文中标注「原版客户端行为」。
- **实现基础**：
  - MCProtocolLib `org.geysermc.mcprotocollib:protocol:26.2-20260907.143312-19`（`feature/26.2` 分支的提交 `964619f`，协议号 776）完成数据包的编解码、分帧、压缩与 Netty 连接，并提供数据包、物品、组件与各种协议枚举的类型。
  - Minestom `net.minestom:minestom:2026.10.05-26.2`（提交 `fcd35c05`）只用于物理、寻路、坐标与静态方块数据，不使用它的网络代码、注册表、`MinecraftServer` 的进程状态与物品系统。
  - `net.minestom:data:26.2-rv3` 是原版 26.2 导出的 JSON，提供物品的编号与默认组件、属性的取值范围、实体类型的尺寸（6.1）。
  - 两个库的服务端逻辑都不作为客户端行为的依据。
- MCProtocolLib 中以下枚举的序号与原版注册表编号相同（逐项核对过），本库直接按序号与编号互相转换：`EntityType`（158 项）、`BlockEntityType`（49 项）、`Effect`（40 项）、`BuiltinSound`（1968 项）、`AttributeType.Builtin`（40 项）、`ParticleType`（125 项）、`ContainerType`（25 项）。`ParticleType` 中有两个常量名与原版名称不同，所以只按编号转换。

**已确定的决策**：

| 方面 | 决策 |
|---|---|
| 连接的服务端 | 原版系服务端与 Minestom。本库提供 TCP 连接；进程内连接由外部模块实现 `Transport` 提供（4.3） |
| 不实现的功能 | 正版验证、聊天签名 |
| 原版服务端的移动检查 | 通过它的优先级低 |
| 协议、数据包、物品、组件 | MCProtocolLib：`MinecraftPacket` 与各数据包类、`ItemStack`（空槽位为 null）、`DataComponents` 与各组件类型 |
| 两个库都有的概念 | 用 MCProtocolLib 的类型，公开接口也是：`EntityType`、`GameMode`、`Hand`、`HandPreference`、`EquipmentSlot`、`Direction`、`Pose`、`Effect`、`AttributeType`、`AttributeModifier`、`ModifierOperation`、`Particle`、`Sound`、`SoundCategory`、`ContainerType`、`Difficulty`、`ChatVisibility`、`ParticleStatus`、`SkinPart`、`PositionElement`、`EntityMetadata`、`ObjectData`、`BlockEntityType`、`BossBarColor`、`BossBarDivision`、`ScoreboardPosition`、`ScoreType`、`EntityEvent` |
| NBT | Cloudburst 的 `NbtMap` |
| 注册表条目的名称 | adventure 的 `Key` |
| 保留的 Minestom 内容 | 物理：`PhysicsUtils`、`CollisionUtils`、`BoundingBox`、`Shape`、`WorldBorder`、`Aerodynamics`、`PhysicsResult`；寻路：`PathGenerator`、`GroundNodeGenerator`、`PNode`；坐标：`Point`、`Pos`、`Vec`、`CoordConversion`；静态方块数据：`Block`、`Block.Getter`（不带 NBT）。坐标与 `Block` 出现在公开接口中 |
| 物品的编号与默认组件、属性的默认值与取值范围、实体类型的尺寸 | 取自 `net.minestom:data` 的 JSON（6.1） |
| 方块实体的 NBT | 单独保存与读取（`blockEntity`），不附加到 `Block` 上（7.1） |
| 物品的比较 | 按原始补丁比较（待办 T3，6.1） |
| 动作请求 | 按类别分队列执行：移动、手部交互、一次性请求 |
| `moveTo` | 使用 Minestom 的寻路（只依赖 `Block.Getter`，可以在 bot 的世界模型上运行）；另外提供直线移动 `moveForward`、跳跃 `jumpTo`、转向 `rotate` 与 `lookAt` |
| 移动模拟 | 使用 Minestom 的 `PhysicsUtils` |
| 公开接口 | 不提供按键输入，不提供时钟，只提供 movement suspension；暂停只停止速度与位置的计算，朝向照常更新和发送 |
| 调度 | 每个 `BotRuntime` 一个调度线程，统一调度全部 bot |
| Action | 只有 `await`、`future`、`cancel` 三个方法：`await` 在执行完毕时返回 true、被取消时返回 false，连接结束或 bot 因异常结束时抛出异常；取消原因作为 `future` 的异常给出；不返回值；动作在游戏中产生的效果由 observer 观察 |
| 接收变化 | 统一用 `observe`：用户构造 observer 交给 bot，observer 选择订阅的观察记录类型（数据包或模型变化），`observe` 只负责更新 observer 的状态，没有单独的事件接口 |
| observer 的关闭 | 由 `observe` 返回的句柄关闭，或由 observer 自己关闭；世界重建时继续观察，连接断开时关闭；提供按世界自动切换状态的抽象类 `WorldBoundObserver` |
| 成功的判断 | 库中没有通用的「成功」概念：用户检查 observer 的状态决定后续动作；能自行判断的 observer 在得出结论时关闭自己，并通过它自己的方法提供结论 |

**记入待办的问题**：以下问题本库不处理，代码中标注 `TODO(T<编号>)`，`bot/README.md` 的「待办」表格登记同样的内容：

| 编号 | 内容 | 现在的处理 |
|---|---|---|
| T1 | MCProtocolLib 的 `DataPalette.index` 对 4×4×4 的生物群系调色板算错下标（`y << 3 \| z << 1 \| x`，原版为 `y << 4 \| z << 2 \| x`） | 不处理，生物群系照常用 `DataPalette.get` 读取（7.1） |
| T2 | MCProtocolLib 的 `ClientboundPlayerChatPacket` 在 PARTIALLY_FILTERED 时不读取过滤标记的 BitSet | 不支持，收到时 bot 以 `UnsupportedOperationException` 结束（7.7） |
| T3 | 原版解码物品时去掉补丁中与默认组件相同的条目，MCProtocolLib 保留原始补丁 | 物品按原始补丁比较，没有补丁与空补丁视为相同（6.1） |
| T4 | `can_break` 中针对方块实体组件的条件（exact components 与 component predicates） | 遇到时抛出 `UnsupportedOperationException`（8.5） |

---

## 第一部分：需要修改的已有内容

**1. `bot/build.gradle`**

```groovy
dependencies {
    api("org.geysermc.mcprotocollib:protocol:26.2-20260907.143312-19") {
        exclude group: "com.nukkitx.fastutil"
    }
    api("net.minestom:minestom:2026.10.05-26.2")
    api("it.unimi.dsi:fastutil:8.5.19")
    implementation("net.minestom:data:26.2-rv3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.0")
}
```

- **MCProtocolLib 与 Minestom 用 `api`**：公开接口直接使用 MCProtocolLib 的数据包、物品与各种枚举类型，以及 Minestom 的坐标与 `Block`；adventure 的 `Component`、`Key` 与 Cloudburst 的 `NbtMap` 随 MCProtocolLib 带入。MCProtocolLib 的仓库 `repo.opencollab.dev/main/` 已在根目录 `build.gradle.kts` 中声明。
- **排除 `com.nukkitx.fastutil`**：MCProtocolLib 依赖的这些 jar 中的类位于 `it.unimi.dsi.fastutil` 包，与 fastutil 8.5.19 同名。
- **fastutil 用 `api`**：MCProtocolLib 的公开类型使用 fastutil（`HolderSet.getHolders()` 返回 `IntList`，点击窗口的数据包使用 `Int2ObjectMap`），排除上面的 jar 之后使用者要从本库得到它。
- **显式声明 `net.minestom:data`**：Minestom 以 `implementation` 依赖它，它不在本库的编译类路径上。
- **caffeine**：区段共享使用弱引用缓存。
- Minestom 要求 Java 25，与根项目的工具链一致。

**2. 根目录 `README.md` 与 `MINESTOM-BUG-REPORT.md`**

本库只绕过 Minestom 的一个限制：7.4 的实体包围盒与 9.6 中骑乘位置使用的实体尺寸与挂点（M12，`net.minestom:data` 的实体类型数据中没有幼年与姿势的尺寸）。README 的 *Minestom workarounds* 表格与 `MINESTOM-BUG-REPORT.md` 中 bot 的条目只有这一项。MCProtocolLib 的问题不在这两个文件中，按上文记入待办。

**3. `bot/README.md`**

第一句写明数据包由 MCProtocolLib 处理；新增「待办」表格，登记 T1 到 T4。

---

## 第二部分：新增内容

### 1. 结构

```
测试代码 ──► Bot API：提交动作请求（返回 Action）、查询快照、observe、movement suspension
                │                                       ▲
                ▼                                       │ 观察记录（数据包 / 模型变化）
        ┌─────────────── 调度线程（每个 BotRuntime 一个）───────────────┐
        │  对每个 bot：应用收到的数据包 → 执行一次性请求 → 到期时执行 tick  │
        │  ProtocolMachine   世界模型   MovementController   HandController │
        │  ObservationDispatcher：把产生的观察记录投递给已订阅的 observer   │
        └────────────────────────────────────────────────────────────────┘
                ▲ 已解码的数据包与附加数据            │ 待发送的 MinecraftPacket
                │                                    ▼
        入站（TCP：Netty 事件循环；           出站（TCP：Netty 事件循环；
        其它 Transport：由实现决定的线程，    其它 Transport：由实现决定）
        调用不并发）                                  │
        MCProtocolLib：字节 → MinecraftPacket         │
        DecodeContext：注册表、区段解码与共享          ▼
                ▲                         TcpTransport / 外部模块实现的 Transport
                └────────────────────────────────────┘
```

### 2. 包结构

根包 `io.ib67.yuanban.bot`。

- 公开类型位于根包、`view`、`observe`、`observe.builtin`、`transport`，其余包整体标注 `@ApiStatus.Internal`。
- 值类型使用 record，选项类使用 Lombok `@Builder`，可空性使用 JetBrains annotations 标注。

```
io.ib67.yuanban.bot
├── BotRuntime, RuntimeOptions          调度线程、全部 bot 的生命周期、全局 movement suspension
├── Bot, BotOptions, BotStatus          单个 bot 的公开入口
├── LoginPluginHandler, ResourcePackPolicy
│                                       BotOptions 中的 Login Plugin Request 回应与资源包回应方式
├── Action, ObservedAction              动作请求的句柄；动作与 observer 的组合（11.8）
├── InventoryActions                    物品栏操作（8.6）
├── MoveOptions, MovementMode           移动动作的参数
├── 异常                                ActionCancelledException（含各取消原因的常量）、BotFailureException、
│                                       BotDisconnectedException、BotTimeoutException、ChunkNotLoadedException、
│                                       ObserverFailureException
├── view/        BotView、StateReader、各类快照 record、ActiveEffect、EntityMeta、EntityMetaIndex（7.4）
├── observe/     Observation（sealed）及其子类型、Observer、Subscription、ObserverContext、
│                ObserverHandle、CloseReason、WorldBoundObserver、WorldInfo、WorldEntryCause、
│                WorldRecord、WorldClosed、Region
├── observe/builtin/
│                Observers（内置 observer 的工厂方法）、各内置 observer、MovementCriteria 及各记录 record
├── observe/dispatch/
│                ObservationDispatcher、SubscriptionIndex、ObserverEntry、ObserverContextImpl、
│                ObservationHost、RegistrationCheck
├── transport/   Transport、ClientConnection、InboundSink、TransportResolver、TcpTransport、TcpConnection；
│                包内可见的 BotClientSession、LastReadHandler、NettyGroup（4.2）
├── scheduler/   Scheduler、BotInstance（Bot 的实现）、BotState、BotTicker、ActionImpl、StateReaderImpl、
│                BotViewImpl、ObserverHandleImpl、ObservedActionImpl、Waits、WaitPump
├── protocol/    ProtocolMachine、Outbox、InboundPacket、BundleBuffer、ChunkBatchTracker、CookieStore、
│                PacketDirection（数据包类的方向，11.3）、Vectors（MCProtocolLib 与 Minestom 的坐标转换）
├── decode/      DecodeContext、SectionDecoder、SectionPool、DecodedChunk
├── registry/    SyncedRegistries、SyncedRegistriesBuilder、DimensionInfo、VanillaNetworkCodec、TagTable
├── data/        ItemData、Items、AttributeData、EntityTypeData、BlockStates、MinestomDataFiles（6.1）
├── world/       ClientWorld、ChunkColumn、SectionRef、SharedSection、WorldSnapshot、
│                BlockEntityStore、WorldClocks、TickState、WorldBorderState、Weather
├── entity/      EntityStore、ClientEntity、EntityDimensions
├── player/      LocalPlayer、AttributeMap、EffectMap、Abilities、PlayerLoadTracker、Interpolation
├── movement/    MovementController、MovementAction 及 MoveToAction、MoveForwardAction、JumpToAction、
│                RotateAction、MoveIntent、Rotation、Kinematics、PathPlanner、PlayerNodeGenerator、PathFollower、
│                RotationArbiter、MovementSender、SineTable、RidingSeat、VehicleControl
├── interaction/ HandController、HandAction、DigTask、DigSpeed、UseItemOnTask、UseItemTask、
│                AttackTask、AttackReach、InteractTask、BlockSequence、Directions、BlockPredicates、NbtCompare（8.5）
├── inventory/   InventoryModel、Window、InventoryActionsImpl、ContainerSlots（7.6）
├── chat/        ChatModel、LastSeenTracker、SignatureCache、Signature
└── hud/         HudModel
```

### 3. 线程与调度

#### 3.1 线程

| 线程 | 数量 | 职责 |
|---|---|---|
| 调度线程（平台线程，名为 `yuanban-bot-scheduler-<序号>`） | 每个 `BotRuntime` 一个 | 修改全部 bot 的状态：应用数据包、执行请求、tick、注册与关闭 observer、调用 observer 回调 |
| Netty 事件循环（守护线程，名称以 `yuanban-bot-netty` 开头） | 整个 JVM 中全部 TCP 连接共用一组（`NettyGroup`，线程数为 Netty 的默认值），每条连接固定在其中一个线程上 | 读取、分帧、解压与解码数据包，调用 `InboundSink`（解码上下文、区段解码与共享，结果放进 bot 的入站队列）；编码并写出调度线程交来的数据包。见 4.2 |
| 寻路线程（虚拟线程） | 每次寻路一个，算完结束 | 在世界快照上计算路径，见 8.4 |
| Transfer 线程（虚拟线程） | 每次 Transfer 一个，连接打开后结束 | 打开新连接（TCP 的连接建立会阻塞），见 5.2 |

调度线程为守护线程。`BotRuntime.connect` 在调用线程上打开第一条连接。

外部模块实现的 `Transport` 在它自己决定的线程上调用 `InboundSink`（4.3），解码上下文与区段解码在那里执行。

#### 3.2 调度循环

调度线程循环执行以下步骤，每次称为一轮：

1. **等待**：直到以下任一情况发生：
   - 最早的 bot tick 到期；
   - 任一 bot 的 TCP 连接的读取超时到期（4.2，登录与配置阶段不 tick，调度线程靠它醒来检查超时）；
   - 任一 bot 的入站队列有新数据包；
   - 有新的动作请求、查询、observer 注册或关闭；
   - `BotRuntime` 关闭。

   连接交出数据包的线程（TCP 连接为 Netty 事件循环）与提交请求的线程在放入任务后唤醒调度线程（`LockSupport.unpark`）。
2. **按 bot 的创建顺序处理每个 bot**：
   1. 处理排队中的 observer 注册与关闭（见 11.4）。
   2. 取出入站队列中的全部数据包，按到达顺序交给 `ProtocolMachine` 与世界模型。Bundle 内的数据包一次性处理（见 5.4）。每个数据包（或整个 bundle）处理完后，投递它产生的观察记录（见 11.2）。属于当前连接的数据包先经过两项检查（5.1 的状态机），不满足时按 12 结束 bot：这条连接的握手已经发出（打开连接之后、第 3 步执行握手之前收到的数据包不接受）；数据包属于当前连接状态对应的协议阶段。
   3. 完成上一轮在调度线程上调用、已经发生过的 `joined()`、`loaded()`（11.9），执行排队中的请求（动作入队、取消、查询任务、暂停请求），检查 TCP 的读取超时（4.2），然后在 bot 已经加载时执行排队中的一次性请求（见 8.2）。
   4. tick 到期时执行一次 tick（见 10），tick 产生的观察记录在对应步骤完成后投递。
   5. 把本轮产生的待发送数据包一次交给连接（TCP 连接在事件循环上的一个任务中依次写入，最后一次写出到套接字；wiki FAQ 建议同一 tick 内的数据包一起发送），然后投递 `PacketSent` 观察记录。
   6. 本轮中该 bot 的状态有变化时，投递一条 `StateUpdated` 观察记录。

**tick 周期**：

- 每个 bot 单独维护下一次 tick 的时间，间隔为 `1000 / min(tickRate, 20)` 毫秒。`tickRate` 取自该 bot 收到的 Set Ticking State，默认 20（每个世界重新开始）。依据是 wiki Commands/tick：高于默认值的 tick 速率不影响玩家；冻结时玩家照常运行。
- 只在游戏阶段并且在世界中时 tick；进入世界（Login (play) 与 Respawn，包括同一维度内保留世界的 Respawn，它们都产生 `WorldEntered`）之后的第一次 tick 在一个间隔之后，处理这个数据包的那一轮不 tick。从离开世界（Start Configuration、Transfer）到下一次 Login (play)，bot 不在任何世界中（5.1），这期间（包括重新配置的配置阶段，以及 Finish Configuration 之后、Login (play) 之前）不 tick。
- 每轮最多执行一次 tick。调度线程落后超过 5 个 tick 时，丢弃积压，下一次 tick 时间改为当前时间，不追赶。

时钟只存在于调度器内部，不对外提供。

#### 3.3 线程封闭与跨线程访问

- 世界模型、动作队列、协议状态只在调度线程上读写；修改模型的入口（`ProtocolMachine` 的数据包处理与登录开始，两个控制器的入队、tick、全部取消，方块交互确认）检查当前线程，在其它线程调用时立即抛出 `IllegalStateException`。
- 调度线程退出之后（`BotRuntime.close()` 返回之后）模型不再变化，查询与读取 observer 状态直接在调用线程上执行。
- 调度线程退出时还在队列中的任务，以及在它最后一轮之后才入队的任务（查询、注册与关闭 observer、动作请求、Transfer 线程打开的新连接），由调度线程在退出时、或者由之后提交任务的线程在调用线程上执行：这时 bot 都已经结束，等待这些任务的线程得到结果或者异常，之后才打开的连接被关闭。`BotRuntime.connect` 在打开连接期间 runtime 开始关闭时，断开这个 bot 并抛出 `IllegalStateException`。
- 查询方法（`Bot.view()` 下的方法）在其它线程调用时，作为查询任务提交给调度线程并等待结果，返回不可变快照；在调度线程上调用时直接执行。
- 动作方法只把请求放进队列，立即返回 `Action`。
- 会阻塞的方法（`Action.await`、`ObservedAction.await`、`Bot.await`、`ObserverHandle.awaitClose`、跨线程查询）在调度线程上调用时抛出 `IllegalStateException`：调度线程等待自己永远等不到结果。
- observer 的回调在调度线程上执行。回调阻塞会让同一 `BotRuntime` 中的全部 bot 停住，这是使用约定，本库无法检测。

#### 3.4 等待时推进服务端

进程内测试常用手动推进的 Minestom 服务端（例如 `net.minestom:testing` 的 `Env.tick()`）。测试线程阻塞等待 bot 时，服务端如果不推进，等待就会超时。

- `RuntimeOptions.waitPump(Runnable)` 设置推进函数。
- 阻塞等待的方法在等待期间推进服务端，其余时间检查完成状态，让服务端的推进速度与 bot 的 20 TPS 接近。同一个 `BotRuntime` 中所有等待的线程共用推进（`WaitPump`）：推进函数在任何时刻最多由一个线程执行（手动推进的服务端不是线程安全的），两次执行的开始时间至少相隔 50 毫秒；到了推进的时间，由正在等待的线程中先检查到的一个在它自己的线程上执行，其余线程只检查完成状态。所以多个线程同时等待时，整体的推进间隔仍为 50 毫秒。推进函数抛出的异常传给执行它的等待线程。
- 没有设置推进函数时，等待方法只检查完成状态。

### 4. 连接

#### 4.1 Transport 接口

```java
public interface Transport {
    /// 打开连接。inbound 接收连接交出的数据包与连接关闭通知（TCP 连接在 Netty 事件循环上交出）
    ClientConnection open(String username, InboundSink inbound);
}

public interface ClientConnection {
    /// 握手包中的服务端地址与端口
    String serverAddress();
    int serverPort();

    /// 只由调度线程调用；本轮的数据包一次交出，保持顺序，全部是发往服务端的数据包。
    /// 返回的 future 在数据包交给连接之后完成（TCP 连接在写进套接字之后）；连接在写出之前关闭时以异常完成
    CompletableFuture<Void> send(List<MinecraftPacket> packets);

    /// 关闭连接，不等待对端读取：已经交出的数据包按顺序写出，写到连接不能再立即接受数据为止，没有写出的部分被丢弃
    /// （原版客户端关闭 Netty 通道的行为），然后关闭；之后 InboundSink.closed 被调用一次
    void close();
}

public interface InboundSink {
    /// 按到达顺序交出一个来自服务端的数据包。调用不会并发：TCP 连接在 Netty 事件循环上调用，其它实现必须串行调用
    void accept(MinecraftPacket packet);

    /// 连接关闭，只调用一次。error 为连接上的异常（解码失败、写出失败、accept 抛出的异常、套接字错误），
    /// 正常关闭（对端关闭或本地调用 close）时为 null
    void closed(@Nullable Throwable error);
}
```

- 本库的实现为 `TcpTransport`；进程内连接等其它方式由使用者实现（4.3）。
- bot 给每条连接一个 `InboundSink`：它调用 `DecodeContext`（4.4），把数据包与附加数据放进 bot 的入站队列。Transfer 之后旧连接的数据包与关闭通知被忽略。
- `ProtocolMachine` 只面对 `MinecraftPacket` 对象，全部连接共用同一个协议状态机。数据包类没有区分方向的类型，`PacketDirection` 按 MCProtocolLib 的 `MinecraftCodec.CODEC` 中任一协议阶段的注册判断方向（11.3）。
- `TransportResolver` 的接口为 `Transport resolve(String host, int port)`，处理 Transfer 时使用。默认返回 `TcpTransport`，测试可以把特定地址映射到别的 Transport。

#### 4.2 TCP 连接

`TcpConnection` 使用 MCProtocolLib 的 Netty 管线完成分帧、压缩与编解码。MCProtocolLib 的默认监听器（自动完成登录与配置、自动回应 Keep Alive 等）不启用：协议阶段由 `TcpConnection` 按数据包切换，登录与配置的应答由 bot 的协议状态机产生（5）。

- **建立连接**（`TcpConnection.open(host, port, inbound)`，`TcpTransport.open` 交给它）：
  - 每条连接新建一个 `MinecraftProtocol(MinecraftCodec.CODEC)`，调用 `setUseDefaultListeners(false)`；它保存这条连接的入站与出站阶段。
  - 会话为包内可见的 `BotClientSession extends ClientNetworkSession`：数据包处理执行器为 `Runnable::run`，全部数据包在 Netty 事件循环上按到达顺序处理（MCProtocolLib 把 `shouldRunOnGameThread()` 为 true 的数据包，例如区块数据，交给这个执行器，其余数据包直接在事件循环上处理；执行器使用其它线程时，两者之间的顺序没有保证）；事件循环组为 `NettyGroup`（3.1，按 MCProtocolLib 选择的传输方式创建 epoll、kqueue 或 NIO 的事件循环组；MCProtocolLib 自己的共享事件循环组的延迟创建不是线程安全的，所以本库自己持有一个）；管线中的 `read-timeout` 处理器换成 `LastReadHandler`。
  - 不做 SRV 解析（`ATTEMPT_SRV_RESOLVE` 为 false），远端地址为 `new InetSocketAddress(host, port)`。MCProtocolLib 开启 `TCP_NODELAY`（wiki FAQ：原版客户端与服务端都关闭 Nagle 算法）。
  - 在调用线程上等待连接建立。失败时 `IOException` 包装成 `UncheckedIOException` 抛出，其它异常原样抛出。
- **入站**（Netty 事件循环）：
  - MCProtocolLib 的管线按当前的入站阶段分帧、解压并解码数据包，数据包读完之后还有剩余字节时出错。
  - 收到 Set Compression 时立即调用 `setCompression`：阈值不小于 0 时为 `new CompressionConfig(阈值, new ZlibCompression(), false)`（最后一个参数与原版客户端的 `validateDecompressed` 相同，为 false），否则关闭压缩。同一次读取中紧跟的数据包已经是压缩格式，MCProtocolLib 按通道属性中的压缩设置逐帧处理，所以下一帧就按新的设置解压。
  - 然后调用 `InboundSink.accept`，由它做后续处理（4.4）并放进 bot 的入站队列。
  - 交给 `InboundSink` 之后立即切换入站阶段：Login Success 与 Start Configuration 之后为配置阶段，Finish Configuration 之后为游戏阶段。MCProtocolLib 在解码出这些终止数据包时停止从套接字读取，切换之后（`switchInboundState`）恢复；同一次读取中已经收到的后续帧在切换之后才解码，所以按新的阶段解码。
  - `LastReadHandler` 位于管线的最前面，每次读到数据时以 volatile 记录 `System.nanoTime()`。读取超时由调度线程检查（`TcpConnection.lastReadNanos()`）：任何阶段距离上次读到数据超过 `BotOptions.readTimeout`（默认 30 秒，与原版客户端 `Connection` 中的 `ReadTimeoutHandler(30)` 相同；0 表示不检测）时关闭连接。MCProtocolLib 自己的读取超时以整数秒计，所以换掉。登录与配置阶段不 tick，调度线程按超时到期的时间醒来（3.2）。
  - 解码失败与 `InboundSink.accept` 抛出的异常由 MCProtocolLib 以这个异常断开会话，连接以这个异常关闭；对端关闭为正常关闭。
- **出站**（`send(List<MinecraftPacket>)`）：
  - 已经开始关闭（调用过 `close()`）时，返回以 `ClosedChannelException` 完成的 future。
  - 否则在通道的事件循环上执行一个任务：通道已经不活动时以 `ClosedChannelException` 完成；否则对每个数据包调用 `channel.write`（编码在调用中按当前的出站阶段完成），每写入一个改变阶段的数据包之后立即切换阶段，最后 flush 一次。
  - 阶段的切换：Handshake 之后出站与入站都为登录阶段（服务端从这时开始按登录阶段回应），Login Acknowledged 之后出站为配置阶段，Finish Configuration 之后出站为游戏阶段，Acknowledge Configuration 之后出站为配置阶段。
  - 返回的 future 在最后一个数据包写进套接字时完成。任何一个写出失败（编码失败、套接字错误）时，先以这个异常完成 future，然后在没有开始关闭时关闭通道，连接以这个异常关闭（之后的关闭通知带有它）：关闭通道时，同一批中还没有写出的数据包以 `ClosedChannelException` 失败，future 先完成，所以带有的是第一个失败的原因。写出失败因此一定结束连接。
  - `MinecraftProtocol` 的阶段只在事件循环上读写。MCProtocolLib 的 `switchOutboundState` 与 `disconnect` 会等待通道上的操作完成，所以调度线程上不调用它们。
- **关闭**：按原版客户端的行为。原版 `Connection.send` 把每个数据包作为任务交给 Netty 的事件循环（套接字不能接受的部分留在 `ChannelOutboundBuffer` 中等待可写），`disconnect` 调用 `channel.close()`，这个关闭任务排在之前的发送任务之后；Netty 关闭通道时丢弃 `ChannelOutboundBuffer` 中还没有写进套接字的数据（`AbstractChannel.AbstractUnsafe.close` 中的 `failFlushed`），已经在套接字发送缓冲区中的数据由操作系统照常发出。`close()` 标记开始关闭，然后调用 `channel.close()`，关闭任务同样排在之前交出的批次之后。所以：
  - `close()` 之前交出的批次按顺序写出，写到套接字不能再接受数据为止；没有写进套接字的部分以异常完成。
  - 关闭从不等待对端读取：对端长时间不读取、发送缓冲区已满时，`close()` 之后连接随即关闭，套接字被释放。
  - `close()` 之后交出的批次立即以异常完成。
  - 连接因对端关闭或出错而结束时，还没有写完的批次都以异常完成。每个交给连接的批次都有确定的结局。
  - 关闭通知（`InboundSink.closed`）只调用一次：调用过 `close()` 时为 null，否则为断开的原因或者写出失败的异常。
- **加密**：不支持。`DecodeContext` 收到 Encryption Request 时抛出 `UnsupportedOperationException`，连接以这个异常关闭，按 12 结束 bot（正版验证不在本库范围内；wiki Login：离线模式的原版服务端不使用加密）。

#### 4.3 进程内连接

本库不提供进程内连接，由外部模块实现 `Transport` 与 `ClientConnection` 提供：

- 把服务端发出的数据包对象按顺序交给 `InboundSink.accept`，调用必须串行（4.1）；解码上下文与区段解码在调用它的线程上执行（4.4）。
- 把 bot 交出的数据包按顺序交给服务端；`send` 返回的 future 的完成时机、`close()` 与关闭通知遵守 4.1 的约定。
- 读取超时只对 `TcpConnection` 检查（4.2）。
- 手动推进的服务端需要的推进函数见 3.4。

#### 4.4 解码上下文

每条连接一个 `DecodeContext`，在连接调用 `InboundSink.accept` 的线程上（TCP 连接为 Netty 事件循环）按数据包到达顺序执行，调用不并发（4.1）。数据包到达调度线程之前，它维护以下内容：

| 内容 | 用途 | 更新时机 |
|---|---|---|
| `SyncedRegistriesBuilder` 中配置阶段累积的 Registry Data | 构建 `SyncedRegistries` | Registry Data；Login Success 与 Start Configuration 开始一个新的配置阶段（6.2） |
| `TagTable` | 随 Update Tags 与 Finish Configuration 交给调度线程 | Update Tags 按标签逐个替换；配置阶段的第一个 Registry Data 删除之前全部同步注册表的标签（6.3） |
| `SyncedRegistries` | 维度类型的最低 y 与高度、生物群系数量 | Finish Configuration（6.2） |
| 当前维度（`DimensionInfo`：最低 y 与高度） | 区段数量 = 高度 / 16（wiki Chunk format），方块实体所在的区段 | Login (play)、Respawn：按 `PlayerSpawnInfo` 中维度类型的编号从 `SyncedRegistries` 取出 |

`DecodeContext` 为部分数据包准备附加数据，随数据包交给调度线程：Chunk Data 附带解码出的区块（`DecodedChunk`），Update Tags 附带更新后的 `TagTable`，Finish Configuration 附带 `ConfigurationFinished(SyncedRegistries, TagTable)`。收到 Encryption Request 时抛出 `UnsupportedOperationException`（4.2）。

区段解码（`SectionDecoder`）：

1. 对 `ClientboundLevelChunkWithLightPacket.getChunkData()` 依次切出每个区段的原始字节，只跳过、不解码：两个计数共 4 个字节，然后是方块与生物群系两个调色板容器。每个容器先是位数：位数为 0 时是一个 VarInt；位数不超过这种调色板的间接调色板上限（方块 8，生物群系 3）时是调色板长度与各个编号；之后（位数不为 0 时）是按这个位数存放全部条目所需的 long，每个 long 放 `64 / 位数` 个条目，与 MCProtocolLib 的 `BitStorage` 相同。
2. 以字节内容与生物群系数量为键在 `SectionPool` 中查找；命中时直接复用。
3. 未命中时用 `MinecraftTypes.readChunkSection(字节, BlockStates.REGISTRY_SIZE, 生物群系数量)` 解码为方块与生物群系两个 `DataPalette`，构造 `SharedSection` 放入 `SectionPool`。
4. 区段数按维度高度计算，读完这些区段之后数据还有剩余字节时出错。
5. 光照数据与高度图在这一步丢弃。
6. 方块实体的方块由区段中该位置的方块决定：对维度高度范围之内的每个 `BlockEntityInfo`，取区段中该位置的状态，Minestom `Block.fromStateId(状态).blockEntityType()` 的编号等于 `info.getType()` 的序号时才保留；该位置的方块没有同一类型的方块实体时（包括超出维度高度），这条数据被忽略（原版客户端 `LevelChunk.replaceWithPacketData` 的行为）。NBT 为 `info.getNbt()`；方块实体没有发给客户端的 NBT 时服务端写入 TAG_END，这时为空的 `NbtMap`。
7. 结果以 `DecodedChunk`（区块坐标、从维度最低 y 开始的 `SectionRef` 数组、以 `CoordConversion.chunkBlockIndex` 为键的方块实体 NBT）随数据包交给调度线程。

### 5. 协议状态机

`ProtocolMachine` 在调度线程上运行，全部连接共用。

#### 5.1 登录与配置

**连接状态机**：连接状态（`BotStatus`）只按下表改变，其它转移抛出 `IllegalStateException`，按 12 结束 bot（`BotInstance.transition`）。初始状态为 `LOGIN`。

| 从 | 到 | 原因 |
|---|---|---|
| `LOGIN` | `CONFIGURATION` | Login Finished |
| `CONFIGURATION` | `PLAY` | Finish Configuration |
| `PLAY` | `CONFIGURATION` | Start Configuration |
| `CONFIGURATION`、`PLAY` | `LOGIN` | Transfer（离开世界，打开新的连接） |
| `DISCONNECTED` 以外的状态 | `DISCONNECTED` | 连接结束、bot 因异常结束（12） |

- 打开连接分两步：先在调用 `runtime.connect` 的线程上（Transfer 时在一个虚拟线程上）调用 `Transport.open`，把 `InboundSink` 交给连接；之后在调度线程上执行握手（`ProtocolMachine.startLogin` 发送 Handshake 与 Login Start）。握手要求此刻的状态为 `LOGIN`（初始状态，或者 Transfer 设置的），它本身不改变状态。
- 入站数据包在到达时检查（3.2 第 2 步）：
  - 这条连接的握手还没有发出时收到数据包，bot 结束。服务端在收到 Login Start 之前不发送数据，TCP 连接不会出现这种情况；直接把数据包对象交给 `InboundSink` 的进程内连接如果在 bot 发出 Login Start 之前就交来数据包，就会出现。
  - 数据包必须在当前状态对应的协议阶段（`LOGIN` → `ProtocolState.LOGIN`，`CONFIGURATION` → `CONFIGURATION`，`PLAY` → `GAME`）中注册为服务端发往客户端的数据包（`PacketDirection.clientboundIn`，按 `MinecraftCodec.CODEC` 中各阶段的注册判断），否则 bot 结束。TCP 连接由 MCProtocolLib 按当前阶段解码，总是满足；进程内连接交来的数据包没有经过这样的解码，属于其它阶段的数据包在这里被拒绝。改变阶段的数据包不在 bundle 中，按到达顺序应用，所以检查时的状态就是 TCP 连接解码这个数据包时的阶段。
  - 原版客户端遇到这两种情况时数据包无法解码，断开连接。

| 阶段 | 收到 | 发送 | 依据 |
|---|---|---|---|
| 连接建立 | | `ClientIntentionPacket`（协议号取 `MinecraftCodec.CODEC.getProtocolVersion()`，即 776；意图 LOGIN 或 TRANSFER），`ServerboundHelloPacket`（名称、离线 UUID：由 `OfflinePlayer:<名称>` 生成的 UUID v3） | wiki Login |
| Login | Encryption Request | 结束 bot（不支持，4.2） | |
| Login | Set Compression | （TCP 连接在事件循环上已经启用压缩，4.2） | wiki Set Compression |
| Login | Login Plugin Request | `ServerboundCustomQueryAnswerPacket(编号, BotOptions.loginPluginHandler.respond(频道, 数据))`。默认的处理器返回 null，表示未理解，回应包中的数据缺省；必须使用这个两个参数的构造方法，一个参数的构造方法写出空数组，表示理解 | wiki：原版客户端总是回应未理解 |
| Login | Login Success | `ServerboundLoginAcknowledgedPacket`；进入配置阶段后发送 `ServerboundCustomPayloadPacket`（`minecraft:brand`，数据为 `BotOptions.brand` 按协议的 String 格式编码，即 `MinecraftTypes.writeString`）与 `BotOptions.clientInformation()` 给出的 `ServerboundClientInformationPacket`（Client Information） | wiki FAQ 登录流程第 11–14 步 |
| Configuration | Known Packs (clientbound) | `ServerboundSelectKnownPacks`：服务端列出的包中命名空间为 `minecraft`、名称为 `core` 且版本等于 `MinecraftCodec.CODEC.getMinecraftVersion()` 的那一项，其余省略 | wiki Known Packs：回应双方都知道的子集，按服务端顺序 |
| Configuration | Registry Data | 由解码上下文累积，Finish Configuration 时构建注册表（6.2） | wiki Registry Data |
| Configuration / Play | Update Tags | 换成解码上下文更新后的 `TagTable` | wiki Update Tags |
| Configuration | Feature Flags | 保存为 `List<Key>` | |
| Configuration | Clear Chat | 清空聊天日志 | |
| Configuration | Code of Conduct | `ServerboundAcceptCodeOfConductPacket`；`BotOptions.acceptCodeOfConduct` 为 false 时不回应 | wiki：服务端在收到接受之前不继续 |
| Configuration | Finish Configuration | 换成解码上下文构建的 `SyncedRegistries` 与此刻的 `TagTable`，时钟重新开始（7.8），然后发送 `ServerboundFinishConfigurationPacket` | wiki Finish Configuration；时钟为原版客户端行为（进入游戏阶段时新建 `ClientPacketListener` 与它的 `ClientClockManager`） |
| Configuration / Play | Custom Payload | 频道为 `minecraft:brand` 时按 String 格式读出服务端的 brand（`MinecraftTypes.readString`）保存；其余频道不处理 | |
| Configuration / Play | Server Links | 保存为 `List<ServerLink>` | |
| Play | Start Configuration | 未确认的消息数大于 0 时先发送 `ServerboundChatAckPacket`（Acknowledge Message，5.3），然后离开世界（见下文），再发送 `ServerboundConfigurationAcknowledgedPacket`，回到配置阶段 | wiki Start Configuration；Acknowledge Message 与离开世界为原版客户端行为（`ClientPacketListener.handleConfigurationStart` 先调用 `sendChatAcknowledgement`，再调用 `Minecraft.clearClientLevel`） |
| Play | Login (play) | 进入新的世界，本地玩家按下文重新开始 | 原版客户端行为（`ClientPacketListener.handleLogin`） |

**离开世界**（Start Configuration、Transfer）：原版客户端清除世界、本地玩家与游戏模式控制器，不发送任何数据包。

- 移动与手部两条队列中的动作以 `WORLD_REBUILT` 取消，执行中的动作不做清理，不发送清理数据包（例如不发送 Cancelled digging），等待中的方块交互确认被丢弃；
- 世界模型被清除，加载状态重置；
- 到下一次 Login (play) 之前 bot 不在任何世界中：不 tick（3.2），`StateReader.world()` 为 null，`player()`、`block()` 抛出 `IllegalStateException`；这期间提交的移动与手部动作、一次性请求等待进入下一个世界并加载之后执行（8.2）。重新配置的 Finish Configuration 之后、Login (play) 之前同样如此。
- 已经不在任何世界中时（进入第一个世界之前、重新配置期间收到 Transfer），队列中只有这期间提交的动作，它们不被取消。

**Login (play)**：每次都新建世界与游戏模式控制器（挖掘延迟与已经发送的快捷栏槽位归零，方块交互序号从 0 开始），已读跟踪、签名缓存与 Player Chat Message 的全局编号重新开始（原版 `handleLogin` 设置 `nextChatIndex = 0`、新的 `LastSeenMessagesTracker` 与 `MessageSignatureCache`）。

- 不在任何世界中时（新的连接，或者 Start Configuration 之后）创建新的本地玩家，全部状态回到初始值（7.5）：物品栏为空、没有打开的窗口、状态编号为 0，移动包的上一次发送记录、上次发送的 Player Input 与疾跑状态重新开始。
- 已经在世界中时（服务端不经过重新配置再次发送 Login (play)）沿用当前玩家，只做原版 `resetPos`：姿态为站立，速度与俯仰角归零，生命值设为最大生命值；药水效果、属性、元数据、物品栏、偏航角都保留。移动与手部两条队列中的动作以 `WORLD_REBUILT` 取消，不发送清理数据包（原版新建游戏模式控制器）。
- 两种情况都设置实体编号，按游戏模式设置能力（7.5），清除乘坐状态。

**Client Information 的内容**（`BotOptions.clientInformation()`）：均来自 `BotOptions`。

- 语言：`BotOptions.locale`（默认 `Locale.US`）转换为原版的语言代码格式，语言与国家用下划线连接、全部小写，例如 `en_us`；没有国家时只有语言。
- 视距：`BotOptions.viewDistance`，默认 2。
- 聊天模式：`ChatVisibility`，默认 `FULL`；聊天颜色：默认 true。
- 显示的皮肤部分：`Set<SkinPart>`，默认全部 7 项。
- 主手：`HandPreference`，默认 `RIGHT_HAND`。
- 文本过滤：false；是否出现在服务器列表：默认 true。
- 粒子等级：`ParticleStatus`，默认 `ALL`。

#### 5.2 任意阶段的自动应答

收到时在本轮立即回应。数据包处理不受 movement suspension 影响。

| 收到 | 回应 | 依据 |
|---|---|---|
| Keep Alive | 相同 ID 的 Keep Alive | wiki：15 秒内必须回应 |
| Ping | 相同 ID 的 Pong | wiki Ping |
| Cookie Request | 携带 `CookieStore` 中对应数据的 Cookie Response，没有则缺省 | wiki Cookie Request |
| Store Cookie | 写入 `CookieStore`（以 `Key` 为键，每个 bot 一个，跨 Transfer 保留；数据超过 5 KiB 时抛出异常，MCProtocolLib 解码时不检查长度） | wiki Store Cookie |
| Add Resource Pack | `ServerboundResourcePackPacket`：`ResourcePackPolicy.ACCEPT` 时依次发送 `ACCEPTED`、`DOWNLOADED`、`SUCCESSFULLY_LOADED`；`DECLINE` 时发送 `DECLINED`。本库不下载资源包 | 结果码取自 wiki；发送顺序为原版客户端行为 |
| Chunk Batch Start / Finished | Chunk Batch Received，数值见下文 | wiki Chunk Batch Finished |
| Synchronize Player Position | 骑乘时位置、朝向、速度都不改变；不骑乘时按 Teleport Flags 更新位置、朝向、速度（计算见下文）。然后发送 Confirm Teleportation，再发送当前位置与朝向的 Set Player Position and Rotation（on ground 与 pushing against wall 为 false） | wiki Synchronize Player Position；计算与骑乘时的处理为原版客户端行为（`handleMovePlayer`、`PositionMoveRotation.calculateAbsolute`） |
| Transfer | Transfer 之前产生的数据包交给旧连接，然后关闭旧连接（4.2 的关闭：写到套接字不能再立即接受数据为止，其余丢弃）；离开世界（5.1），不发送清理数据包；用 `TransportResolver` 得到新 Transport，在虚拟线程上打开新连接，以握手意图 TRANSFER 重新登录，保留 `CookieStore`。新连接上的第一个数据包是 Handshake。同一个 bundle 中排在 Transfer 之后的数据包属于旧连接，与旧连接之后的数据包一样被忽略 | wiki Transfer；旧连接的处理为原版客户端行为（`handleTransfer` 先关闭连接、再断开处理；关闭之前交给网络层的数据包在 Netty 处理关闭之前尝试写出，没有写进套接字的部分被丢弃，之后不再发送） |
| Disconnect / Disconnect (login) | 记录原因，bot 进入 `DISCONNECTED` | |

**Synchronize Player Position 的计算**（原版 `PositionMoveRotation.calculateAbsolute`）：

- Teleport Flags 为 MCProtocolLib 解码出的 `List<PositionElement>`，计算时转为集合。带 X、Y、Z 标志的坐标为当前坐标加数据包的值，其余取数据包的值；新偏航角为（带 `Y_ROT` 时当前偏航角加）数据包的偏航角，新俯仰角为（带 `X_ROT` 时当前俯仰角加）数据包的俯仰角，再限制到 [-90, 90]，都按 float 计算。
- 带 ROTATE_DELTA 时，先把当前速度绕 x 轴旋转「当前俯仰角 − 新俯仰角」（原版 `Vec3.xRot`：`y' = y·cos + z·sin`，`z' = z·cos − y·sin`），再绕 y 轴旋转「当前偏航角 − 新偏航角」（`Vec3.yRot`：`x' = x·cos + z·sin`，`z' = z·cos − x·sin`）。角度换算成 float 弧度；正弦与余弦取自原版 `Mth.sin`、`Mth.cos` 的 65536 项 float 表（`SIN[i] = (float) Math.sin(i / 10430.378350470453)`，下标为 `(int) ((long) (弧度 × 10430.378350470453) & 65535)`，余弦在转换之前加 16384），由 `SineTable` 实现。Minestom 的 `Vec.rotateAroundX(a)` 等于 `Vec3.xRot(-a)`，方向相反，所以不使用。例如速度 (0, 0, 1)、俯仰角从 0 变成 90 时得到 (0, -1, 0)。
- 带 DELTA_X、DELTA_Y、DELTA_Z 标志的速度分量为旋转后的速度加数据包的值，其余取数据包的值。
- 两个应答为 `ServerboundAcceptTeleportationPacket` 与 `ServerboundMovePlayerPosRotPacket`，不计入移动包的上一次发送记录，着地与顶住墙壁标记不变（9.2）。
- Player Rotation 同样按相对标记计算：`isRelativeY()` 对应偏航角，`isRelativeX()` 对应俯仰角，俯仰角限制到 [-90, 90]。

**Chunk Batch Received 的数值**（`ServerboundChunkBatchReceivedPacket`）：`ChunkBatchTracker` 在调度线程处理 Chunk Batch Start 时记录时间，在 Chunk Batch Finished 时用耗时除以批次中的区块数得到每区块毫秒数，保留最近 15 批，回应 `25 / 平均每区块毫秒数`（wiki Chunk Batch Finished）。耗时为 0 时结果为正无穷，原版服务端与 Minestom 都把它限制到各自的上限。

#### 5.3 聊天与命令

- Chat Message（`ServerboundChatPacket`）不签名（签名缺省），不发送 Player Session，不处理服务端的安全聊天要求。
- Message Count、Acknowledged（20 位）、Checksum 按 wiki Chat Message 一节计算：Checksum 对最近已读消息的签名校验值按 `Arrays.hashCode(byte[])` 的方法计算，转为 byte，结果为 0 时改为 1。`LastSeenTracker` 记录处理过的带签名的 Player Chat Message：显示的消息（7.7）计入已读；没有显示的（完全过滤、发送者不在玩家列表中）只计入未确认的消息数，在 20 条的窗口中占一个空位，不计入已读（原版 `markMessageAsProcessed` 的 `wasShown` 为 false 时 `addPending` 放入空的条目）。未确认的消息超过 64 条时发送 Acknowledge Message，收到 Start Configuration 时未确认的消息数大于 0 也先发送一次（原版客户端行为，`markMessageAsProcessed`、`handleConfigurationStart` 中的 `sendChatAcknowledgement`）。
- 签名保存为 `Signature`（包装 `byte[]` 的 record，`equals` 与 `hashCode` 按数组内容）。按编号引用的签名从 `SignatureCache`（原版 `MessageSignatureCache`，容量 128）中取出，编号为 MCProtocolLib 的 `MessageSignature.getId()`（已经减去 1；带完整签名时为 -1）。Player Chat Message 的已读列表或 Delete Message 引用的编号超出范围、或者缓存中这个编号没有签名时，bot 因异常结束（原版客户端对这两种情况以无效数据包断开连接）。
- Player Chat Message 的全局编号（Global Index）从 0 开始逐条加一，与预期不同时 bot 因异常结束（原版 `handlePlayerChat` 在处理消息之前检查，不连续时以 `multiplayer.disconnect.bad_chat_index` 断开连接）。
- Delete Message 删除的消息还没有随 Chat Message 告知服务端（`LastSeenTracker` 中的条目处于待告知状态）时，把它从跟踪中去掉，之后的 Chat Message 不再把它计入已读；已经告知过的条目不变（原版 `handleDeleteChat` 调用 `LastSeenMessagesTracker.ignorePending`）。
- `LastSeenTracker`、`SignatureCache` 与全局编号在每次 Login (play) 时重新开始（5.1）。
- 消息超过 256 个字符时在提交请求时抛出 `IllegalArgumentException`（wiki：服务端会踢出客户端）。
- 命令用 Chat Command（`ServerboundChatCommandPacket`，不签名），长度上限 32767。

#### 5.4 Bundle

wiki Bundle Delimiter 规定：两个分隔符之间的数据包在同一 tick 内处理，原版客户端不接受超过 4096 个数据包的 bundle。

- `BundleBuffer` 在两个分隔符（`ClientboundDelimiterPacket`）之间缓存数据包，收到结束分隔符时整批交给处理器。
- 超过 4096 个时抛出异常断开连接。
- 调度循环中，入站数据包的处理在 tick 之前完成，一个 bundle 的处理过程中不会插入 tick。

### 6. 注册表与静态数据

#### 6.1 静态数据

静态数据都在类初始化时加载，不需要服务端进程：

- **方块**：Minestom 的静态方块数据（同样读取 `net.minestom:data`）。`Block.fromStateId(int)` 给出状态编号、属性、`collisionShape()`、`outlineShape()`、`friction()`、`speedFactor()`、`jumpFactor()`、`hardness()`、`requiresTool()`、`replaceable()`、`liquid()`、`blockEntityType()`。`BlockStates.REGISTRY_SIZE`（`Block.statesCount()`）是方块状态的总数，决定区块数据中全局调色板的位数，MCProtocolLib 解码区段时需要它。
- **`data` 包**读取 `net.minestom:data` 中的 JSON（`MinestomDataFiles`），其余的 Minestom 代码不参与：
  - **`ItemData`**（`item.json`）：`id(Key)` 给出物品编号，没有这个物品时抛出 `IllegalArgumentException`。`prototype(物品编号, DataComponentType)` 给出物品的默认组件，只转换本库读取的五种：`tool`、`equippable`、`use_effects`、`piercing_weapon`、`attack_range`，其它类型抛出 `IllegalArgumentException`。JSON 是原版编解码器的格式，取默认值的字段被省略，转换时按原版编解码器的默认值补上（见下表）。
  - **`Items`**：`component(物品, 类型)` 给出物品实际的组件：补丁中有这个类型时取补丁中的值（值为 null 表示补丁移除了它），否则取 `ItemData` 的默认组件；空槽位（null）没有任何组件。只支持上面五种与 `can_break`（原版物品都没有这个默认组件，只在补丁中出现）。`sameItemSameComponents` 判断同一物品、组件相同（不比较数量），`sameStack` 再要求数量相同；两个都为 null 时相同。
  - **`AttributeData`**（`attribute.json`）：每个属性的默认值、最小值与最大值。MCProtocolLib 的 `AttributeType.Builtin` 也带有这些数值，但其中 `jump_strength` 的默认值为 0.42，原版为 `(double) 0.42F`，所以使用 JSON 中的数值。
  - **`EntityTypeData`**（`entity_type.json`）：按 MCProtocolLib `EntityType` 的序号索引（加载时逐项检查名称），给出成年默认姿态的宽与高、是否为活体（`packetType` 为 LIVING 或 PLAYER；原版只有活体有属性）、乘客挂点（没有时为 null，由调用方按实体高度计算）。

`ItemData` 转换 JSON 的规则：

| JSON 内容 | 转换为 |
|---|---|
| 方块集合与实体集合 | `"#标签"` 转为 `HolderSet(Key)`；名称或名称列表转为编号列表（方块用 `Block.fromKey(名称).id()`，实体用 MCProtocolLib `EntityType` 的序号） |
| 声音 | 按名称找 `BuiltinSound`（默认组件中的声音都是 `minecraft` 命名空间中注册表的条目），找不到时抛出 `IllegalStateException` |
| 装备槽位名称 | `head`、`chest`、`legs`、`feet`、`mainhand`、`offhand`、`body`、`saddle` 映射到 MCProtocolLib 的 `EquipmentSlot` |
| `asset_id` | MCProtocolLib 中的 `model` |

| 组件 | 省略的字段的默认值（原版编解码器） |
|---|---|
| `tool` | `default_mining_speed` 1.0、`damage_per_block` 1、`can_destroy_blocks_in_creative` true；规则的 `speed` 与 `correct_for_drops` 可以省略，省略时为 null |
| `equippable` | `equip_sound` 为 `item.armor.equip_generic`，`dispensable`、`swappable`、`damage_on_hurt` 为 true，`equip_on_interact`、`can_be_sheared` 为 false，`shearing_sound` 为 `item.shears.snip` |
| `use_effects` | `(false, true, 0.2F)`，与原版 `UseEffects.DEFAULT` 相同 |
| `piercing_weapon` | `deals_knockback` true、`dismounts` false |
| `attack_range` | `min_reach` 0、`max_reach` 3、`min_creative_reach` 0、`max_creative_reach` 5、`hitbox_margin` 0.3、`mob_factor` 1 |

**物品的比较（待办 T3）**：原版解码物品时去掉补丁中与默认组件相同的条目，MCProtocolLib 保留原始补丁。`Items.sameItemSameComponents` 按原始补丁比较（没有补丁与空补丁视为相同），服务端发来多余的条目时结果与原版不同（代码标注 `TODO(T3)`）。挖掘中判断主手物品是否改变（8.5）与物品栏判断槽位是否改变（7.6）都使用它。

#### 6.2 SyncedRegistries

服务端在配置阶段用 Registry Data 发来同步注册表，条目的顺序决定数字编号（wiki Registry Data）。数据包中按编号引用条目的字段（聊天类型、伤害类型、世界时钟、维度类型）由 MCProtocolLib 解码为编号，本库按服务端的顺序把编号换成名称。本库只使用条目名称，以及维度类型的最低 y 与高度，不解码其余的条目数据。

**`SyncedRegistriesBuilder`**（由解码上下文使用，4.4）：

1. **配置阶段的累积**：Login Success 与 Start Configuration 时调用 `startConfiguration()`，开始一个新的配置阶段。同一个配置阶段中收到第一个 Registry Data 时，清空之前的同步注册表，`accept` 返回 true，解码上下文随之删除这些注册表的标签（wiki Update Tags：重新配置期间收到任何 Registry Data，之前的同步注册表与标签全部作废）。之后按注册表名称累积条目列表（名称加可以缺省的 NBT），`registryNames()` 给出当前累积的注册表名称。
2. **构建**（`build()`，收到 Finish Configuration 时）：按服务端顺序记录每个注册表全部条目的名称。条目不带 NBT 时（服务端因为 Known Packs 省略），要求它存在于原版的同一注册表中，取不到时抛出 `IllegalStateException`。维度类型的 `min_y` 与 `height` 取自条目的 NBT，省略时取自原版，得到 `DimensionInfo(名称, 最低 y, 高度)`。

**原版的条目数据**（`VanillaNetworkCodec`）：第一次使用时加载 MCProtocolLib 自带的 `MinecraftProtocol.loadNetworkCodec()`（原版 26.2 全部同步注册表的条目与数据），按注册表名称与条目名称查找。

**`SyncedRegistries`**（不可变）：

- `entries(注册表)`：按编号排列的条目名称，服务端没有发送这个注册表时为空列表；`size(注册表)`。
- `entry(注册表, 编号)` 在编号不在注册表中时为 null；`requireEntry(注册表, 编号)` 这时抛出 `IllegalStateException`（原版解码时出错，客户端断开连接）。
- `biomeCount()`：`worldgen/biome` 的条目数，决定区块数据与 Chunk Biomes 中生物群系全局调色板的位数。
- `dimensionType(编号)`：维度类型的 `DimensionInfo`，编号不存在时抛出 `IllegalStateException`。

用到编号的地方：Player Chat Message 与 Disguised Chat Message 的聊天类型（`CHAT_TYPE`，7.7）、Damage Event 的伤害类型（`DAMAGE_TYPE`，11.2）、Update Time 的世界时钟（`WORLD_CLOCK`，7.8）、Login (play) 与 Respawn 的维度类型（`DIMENSION_TYPE`，4.4、7）。

**限制**：服务端使用自定义数据包时，没有带数据、原版中又不存在的条目让构建失败，bot 结束。

#### 6.3 TagTable

保存 Update Tags 中的全部标签（注册表名称 → 标签名称 → 编号集合，以 `Key` 为名称）。不可变，每次更新返回新的实例：

- `apply(ClientboundUpdateTagsPacket)`：配置阶段与游戏阶段都按标签逐个替换，数据包中没有提到的标签保留（wiki Update Tags：游戏阶段只替换包中提到的标签）。
- `without(注册表名称的集合)`：删除这些注册表的全部标签，重新配置的第一个 Registry Data 时使用（6.2）。
- `contains(注册表, 标签, 编号)`、`tagNames(注册表)`。

客户端自己的判断使用这里的成员，与服务端一致：挖掘速度的工具规则（8.5）、`can_break` 的方块集合（8.5）、脚下方块的墙与栅栏门（9.1）、`equippable` 允许的实体类型（9.6）。方块、实体类型等静态注册表的标签同样按服务端下发的标签判断，服务端自定义的标签照常生效；直接列出的方块与实体按编号判断（方块为 Minestom `Block.id()`，实体为 MCProtocolLib `EntityType` 的序号）。

### 7. 世界模型

```
BotState（调度线程独占）
├── 连接的信息         服务端品牌、Server Links（List<ServerLink>）、Feature Flags（List<Key>）
├── SyncedRegistries / TagTable（不可变，由解码上下文构建）
├── ClientWorld        收到 Login (play)，或维度名称改变的 Respawn 时重新创建；Start Configuration 与 Transfer 时清除；
│   │                  实现 Block.Getter
│   ├── 维度类型编号、维度名称（Key）、minY、height
│   ├── 区块：Long2ObjectMap<ChunkColumn>，每列为 SectionRef 数组
│   ├── BlockEntityStore（方块实体的 NbtMap）
│   ├── EntityStore
│   ├── World Age、TickState、WorldBorderState、Weather
│   └── 难度（MCProtocolLib 的 Difficulty）、默认出生点、服务端视距、模拟距离、加载区域中心
├── WorldClocks        各时钟；属于连接，每次 Finish Configuration 时重新创建（7.8）
├── LocalPlayer        新的连接或 Start Configuration 之后的 Login (play)，以及 Respawn 时重新创建（5.1、7.5）
├── InventoryModel     随 LocalPlayer 重新创建
├── ChatModel
├── HudModel
└── CookieStore        跨连接保留
```

#### 7.1 区块

- **安装**：Chunk Data and Update Light 的区段已由解码上下文解码，调度线程只安装 `SectionRef` 数组与方块实体的 NBT（4.4）。该位置的方块没有同一类型的方块实体时，这条数据在解码时已经被忽略。安装时先删除这个区块列中原有的方块实体数据。
- **方块实体的 NBT**单独保存在 `BlockEntityStore` 中（`NbtMap`，按方块坐标），不附加到 `Block` 上；`ClientWorld.blockEntity(x, y, z)` 读取，没有时为 null，所在区块没有加载时抛出 `ChunkNotLoadedException`。区块卸载时删除其中的方块实体数据；改变方块时，新方块的方块实体类型与旧方块不同则删除这个位置的数据（原版客户端行为）。
- **更新**：Block Update、Update Section Blocks（MCProtocolLib 已经解码成绝对坐标的 `BlockChangeEntry`）、Chunk Biomes、Block Entity Data、Unload Chunk。
  - Block Entity Data 按原版 `handleBlockEntityData` 处理：它只应用到 `level.getBlockEntity(pos, type)` 存在的方块实体，所以只在所在区块已加载、位置在维度高度范围之内、并且该位置的方块有同一类型的方块实体时（Minestom `Block.blockEntityType()` 的编号等于数据包中 `BlockEntityType` 的序号）替换这个位置的 NBT（数据包中没有 NBT 时为空的 `NbtMap`）并产生 `BlockEntityChanged`，其余情况忽略（原版客户端行为）。
  - Chunk Biomes：对区块列中的每个区段依次用 `MinecraftTypes.readDataPalette(数据, PaletteType.BIOME, 生物群系数量)` 读出生物群系调色板，替换这个区段的生物群系；区块没有加载时忽略。
- **生物群系的读取（待办 T1）**：区段中的生物群系照常用 `DataPalette.get` 读取。MCProtocolLib 的 `DataPalette.index` 对 4×4×4 的生物群系调色板算错下标（`y << 3 | z << 1 | x`，原版为 `y << 4 | z << 2 | x`），一个区段中不止一种生物群系时读出的结果错误（代码标注 `TODO(T1)`）。
- **加载区域**：以中心区块为中心、半径为 `max(2, 服务端视距) + 3` 的正方形（原版客户端行为，`ClientChunkCache.calculateStorageRange`），服务端视距取自 Login (play) 与 Set Render Distance。wiki Set Center Chunk 写的宽度为 `2 × 服务端视距 + 7`，服务端视距小于 2 时与原版不同，本库按原版。
  - 区域外的区块数据直接忽略（与原版客户端一致）；
  - 收到 Set Center Chunk 后立即卸载落到区域外的区块（wiki 写明客户端可以这样做）。
- **`ClientWorld.getBlock(x, y, z, condition)`**：
  - 返回 `Block.fromStateId(...)`，不带 NBT，`condition` 不影响结果；
  - 维度高度范围之外返回空气；
  - 未加载的区块抛出 `ChunkNotLoadedException`。

#### 7.2 区段共享与写入时复制

- 同一个 JVM 中所有 bot 共用一个 `SectionPool`：
  - 键为区段的原始字节与生物群系数量（字节数组加哈希值，比较时逐字节对比）；
  - 值为 `SharedSection`（方块 `DataPalette`、生物群系 `DataPalette`、方块数量、流体数量），以 Caffeine 的弱引用值保存，没有 bot 引用时由垃圾回收释放。
- `SectionRef` 持有一个 `SharedSection` 和一个「共享」标记。第一次写入时用 `new DataPalette(原调色板)` 复制出私有副本再写入。共享的 `DataPalette` 永远不被写入。
- 按原始字节去重：MCProtocolLib 的 `DataPalette` 虽然定义了 `equals`，但它与调色板中条目的顺序有关，而且查找之前要先解码。
- 写入方块时不使用 `DataPalette.set` 的返回值（它返回调色板中的下标，不能当作方块状态），也不使用 `ChunkSection.setBlock`（它按这个返回值维护方块数量）：需要旧的状态时先读取。

#### 7.3 世界快照

`ClientWorld.snapshot(中心, 半径)` 返回 `WorldSnapshot implements Block.Getter`，供寻路线程使用：

- 收集范围内各区块的 `SectionRef` 当前持有的 `SharedSection`，并把这些 `SectionRef` 都标记为共享。之后调度线程再写入时会先复制，快照中的 `DataPalette` 因此不会再变化。快照按 `DataPalette.get` 读取方块。
- 范围内（以及范围外）未加载的区块，快照返回 `Block.BARRIER`，寻路把它视为无法通过。
- 复制的只是引用，开销与区段数量成正比。

#### 7.4 实体

`ClientEntity` 保存：

- 编号、UUID、MCProtocolLib 的 `EntityType`、Spawn Entity 的附加数据（`ObjectData`）
- 位置、朝向、头部朝向、速度、是否着地
- 元数据：`Map<Integer, EntityMetadata<?, ?>>`，按下标保存 MCProtocolLib 解码出的条目
- 装备（`EnumMap<EquipmentSlot, ItemStack>`，没有空物品）、属性（`AttributeMap`，7.5）、药水效果（`Map<Effect, ActiveEffect>`）、乘坐关系、拴住它的实体
- 马类（原版 `AbstractHorse`：马、驴、骡、骷髅马、僵尸马、羊驼、行商羊驼、骆驼、骆驼尸壳）的人立动画进度及其上一 tick 的值（原版 `standAnim`、`standAnimO`），供骑乘位置使用（9.6）

**元数据的命名条目**：`view.EntityMetaIndex` 列出本库读取的条目，每项为 `EntityMeta<T>(下标, 默认值, 掩码)`；掩码为 0 时按下标取值，否则取标记字节中的这一位。`EntityMetaIndex.get(元数据, 条目)` 读取，没有收到时返回默认值；`EntitySnapshot.meta(条目)` 相同。下标与 Minestom 的 `MetadataDef` 一致（测试中逐项比对）：

| 常量 | 下标 | 掩码或默认值 |
|---|---|---|
| `HAS_NO_GRAVITY` | 5 | 默认 false |
| `POSE` | 6 | 默认 `Pose.STANDING` |
| `LIVING_HEALTH` | 9 | 默认 1 |
| `ARMOR_STAND_IS_SMALL` | 15 | 掩码 0x01 |
| `AGEABLE_IS_BABY`、`ZOMBIE_IS_BABY`、`ZOGLIN_IS_BABY` | 16 | 默认 false |
| `PIGLIN_IS_BABY` | 17 | 默认 false |
| `CUBE_SIZE`（史莱姆、岩浆怪） | 18 | 默认 1 |
| `HORSE_FLAGS` | 18 | 默认 0 |
| `HORSE_IS_TAME` | 18 | 掩码 0x02 |
| `HORSE_IS_REARING` | 18 | 掩码 0x20 |
| `TAMEABLE_IS_TAMED`（包括鹦鹉螺） | 18 | 掩码 0x04 |
| `HAPPY_GHAST_STAYS_STILL` | 19 | 默认 false |
| `CAMEL_LAST_POSE_CHANGE_TICK` | 20 | 默认 0 |

**药水效果**：`view.ActiveEffect(effect, amplifier, duration, ambient, showParticles, showIcon, blend)`，持续时间 `INFINITE_DURATION`（-1）表示无限。Entity Effect 的四个标记由 MCProtocolLib 解码为布尔值。

**每 tick 的实体步骤**（原版 `ClientLevel.tickEntities`，10 的第 4 步）：所在区块已加载、没有乘坐其它实体的实体执行一步，世界冻结并且不在步进中时（原版 `TickRateManager.runsNormally` 为 false）玩家以外、没有玩家乘客（直接或间接，包括本地玩家）的实体除外；乘客在它的载具之后执行，玩家以外的乘客所在的区块也要已加载。本库只模拟这一步中马类的人立动画：元数据中的人立标记（`HORSE_IS_REARING`）为真时进度向 1 靠近（`standAnim + ((1 − standAnim) × 0.4 + 0.05)`，不超过 1），否则向 0 回落（`standAnim + ((0.8 × standAnim³ − standAnim) × 0.6 − 0.05)`，不低于 0），以 float 计算。本地玩家不在实体模型中，由移动步骤执行。

各数据包按 wiki 的定义处理（目标为本地玩家实体编号时，Synchronize Vehicle Position 按下面 `ClientboundTeleportEntityPacket` 一条中本地玩家的情况处理，其余移动包被忽略，原版客户端行为；速度、元数据、属性、效果写入 `LocalPlayer`）：

- `ClientboundAddEntityPacket`（Spawn Entity）：位置与朝向、头部朝向、速度（`getMovement()`）、附加数据（`ObjectData`）。
- `ClientboundMoveEntityPosPacket`、`ClientboundMoveEntityPosRotPacket`：MCProtocolLib 已经把位移除以 4096，直接加到当前位置上。
- `ClientboundMoveEntityRotPacket`、`ClientboundRotateHeadPacket`。
- `ClientboundEntityPositionSyncPacket`（wiki 中的 Teleport Entity）：绝对位置、朝向、是否着地；不使用数据包中的速度，实体的速度不变（原版 `handleEntityPositionSync`）。本地玩家控制的实体（原版 `isLocalInstanceAuthoritative`，控制条件见 9.6）不改变：原版只更新它之后的相对移动所用的基准位置，本库不记录这个基准位置。
- `ClientboundTeleportEntityPacket`（wiki 中的 Synchronize Vehicle Position）：位置、朝向与速度按 5.2 中 Synchronize Player Position 的计算得出（原版 `handleTeleportEntity` 与 `handleMovePlayer` 都使用 `PositionMoveRotation.calculateAbsolute`）：按 Teleport Flags（`List<PositionElement>`）处理相对字段，俯仰角限制到 [-90, 90]，带 ROTATE_DELTA 时旋转当前速度。
  - 编号是本地玩家自己的（原版本地玩家也在世界的实体中，由 `handleTeleportEntity` 照常处理）：按上面的计算得出新的位置、朝向与速度，速度立即设置。本地玩家所在区块已加载（原版 `isTickingEntity`）或者 X、Y、Z 中有相对字段，并且新位置与当前位置的距离不超过 64 时，位置与朝向在之后的移动计算中插值（原版 `moveOrInterpolateTo`，见 9.1 第 2 步）：正在进行的插值目标相同时继续，否则从当前的位置与朝向开始新的 3 步插值（原版 `InterpolationHandler.interpolateTo`）。其余情况立即设置位置与朝向，正在进行的插值不结束（与原版相同）。然后设置着地标记（原版 `setOnGround`）：顶住墙壁标记不变；着地时按 9.1 第 8 步在当前位置查找支撑方块，这里没有可以退回的位移，找不到并且上一次不是「着地但没有支撑方块」时保留原来的支撑方块；不着地时清除支撑方块。不发送任何数据包。与服务端的其它传送相同，之前 `rotate`、`lookAt` 的朝向不再生效（8.3），清空上一次模拟结果并触发 `moveTo` 重新寻路（8.4、9.2）；立即设置时产生 `PlayerTeleported`，插值时不产生，之后移动计算中的位置变化照常产生 `PlayerMoved`。
- `ClientboundSetEntityMotionPacket`、`ClientboundSetEntityDataPacket`（按下标写入元数据）、`ClientboundUpdateAttributesPacket`、`ClientboundUpdateMobEffectPacket`、`ClientboundRemoveMobEffectPacket`。
- `ClientboundSetEquipmentPacket`：物品为空（null）时从装备表中移除这个槽位，装备表中没有空物品。
- `ClientboundSetEntityLinkPacket`（Link Entities）：`getEntityId()` 是被拴住的实体，`getAttachedToId()` 是拴住它的实体，为 -1 时表示解开。
- `ClientboundSetPassengersPacket`（原版客户端行为，`handleSetEntityPassengersPacket`）：载具不在模型中时整个数据包被忽略。否则载具现有的乘客全部停止乘坐，然后列出的乘客按顺序乘坐它（原版 `Entity.startRiding`，强制乘坐）：
  - 模型中没有的编号被跳过；已经乘坐这个载具的不重复加入；载具为标记或不祥之物生成器（`couldAcceptPassenger` 为 false）时不乘坐；
  - 乘客是载具直接或间接乘坐的实体时不乘坐，所以乘坐关系不会形成循环（Minestom 服务端的 `addPassenger` 只检查直接载具，可以发出形成循环的数据包）；
  - 其余乘客先离开原来的载具（从它的乘客列表中去掉），再加入这个载具乘客列表的末尾；
  - 实体乘坐自己时，原版之后的乘坐计算会无限循环，本库让 bot 因异常结束；
  - 本地玩家在列出的乘客中时，清除记下的被移除载具编号；新登上船或竹筏时（之前没有直接或间接乘坐它），偏航角设为船的偏航角。这两项与乘坐是否成功无关。
- `ClientboundRemoveEntitiesPacket` 移除实体时（原版 `Entity.setRemoved`）：被移除的实体离开它乘坐的载具（从载具的乘客列表中去掉），它的乘客（包括本地玩家）停止乘坐。所以模型中的乘坐关系只指向存在的实体。
- `ClientboundAddEntityPacket` 的编号已有实体时（原版 `handleAddEntity` 调用的 `ClientLevel.addEntity` 先移除同一编号的实体）：先按上一条移除旧实体（乘坐关系同样解除，产生旧实体的 `EntityRemoved`），再加入新的实体（产生 `EntitySpawned`）。这种移除不记下本地玩家乘坐过的载具编号：下一条只适用于 Remove Entities。
- 被移除的实体是本地玩家直接或间接乘坐的实体时，记下它的编号（原版 `handleRemoveEntities` 中的 `removedPlayerVehicleId`）。之后模型中没有这个编号的实体时，对它的 `ClientboundTeleportEntityPacket`（Synchronize Vehicle Position）应用到本地玩家：按 5.2 中 Synchronize Player Position 的计算设置位置、朝向与速度，然后发送当前位置与朝向的 Set Player Position and Rotation（on ground 与 pushing against wall 为 false），不发送 Confirm Teleportation（原版 `handleTeleportEntity`）；与服务端传送相同，产生 `PlayerTeleported`，不计入移动包的上一次发送记录，清空上一次模拟结果并触发 `moveTo` 重新寻路（8.4、9.2）。记下的编号在这个编号的实体重新生成（Spawn Entity）、本地玩家登上任何载具、离开世界（5.1）时清除。
- `ClientboundTakeItemEntityPacket`、`ClientboundEntityEventPacket`、`ClientboundDamageEventPacket`、`ClientboundHurtAnimationPacket` 不改变实体状态，只产生观察记录（见 11.2）。Entity Event 由 MCProtocolLib 解码为 `EntityEvent`，出现不认识的编号时解码失败，bot 结束。

模型中实体（`ClientEntity`）的位置保存服务端最后给出的值，不插值；本地玩家的插值见上面的 `ClientboundTeleportEntityPacket` 一条。

**包围盒**（`EntityDimensions`，原版 `Entity.getDimensions`、`LivingEntity.getDimensions` 与各实体类的 `getDefaultDimensions`），`lookAt(int entityId)`、`attack`、`interact` 按它计算瞄准点：

- 活体以外的实体：实体类型的宽高（`EntityTypeData`，6.1）。
- 活体（原版 `LivingEntity`，按 `EntityTypeData.living` 判断：原版只有活体有属性）：睡觉的姿势为固定尺寸 0.2 × 0.2；其余为默认尺寸乘以 `minecraft:scale` 属性，默认尺寸为固定尺寸（原版 `EntityDimensions.fixed`）时不乘。
- 尺寸与原版 `EntityDimensions` 相同以 float 保存与计算：实体类型的宽高换成 float，`minecraft:scale` 属性先换成 float（原版 `LivingEntity.getScale`）再相乘，最后换成 double 交给 `BoundingBox`。
- 默认尺寸：玩家与人体模型（原版 `Avatar.POSES`）潜行时 0.6 × 1.5，游泳、滑翔、激流冲刺时 0.6 × 0.6，死亡时为固定尺寸 0.2 × 0.2，其余姿势 0.6 × 1.8；其余实体幼年时取原版各实体类的幼年尺寸（`BABY_DIMENSIONS`，例如僵尸一类与村民 0.49 × 0.98、马为成年尺寸乘 0.7、骆驼坐下时 0.95 × 0.425；没有单独定义的乘 0.5），否则取实体类型的宽高。姿势读取元数据 `POSE`。
- 幼年按原版各实体类的 `isBaby` 判断：`AgeableMob` 读取它的幼年标记（`AGEABLE_IS_BABY`），其中史莱姆、岩浆怪、鹦鹉、僵尸鹦鹉螺、青蛙、骆驼尸壳不会是幼年（`canBeABaby` 为 false），史莱姆一类（`AbstractCubeMob`）的尺寸与年龄无关；僵尸一类、猪灵、僵尸疣猪兽读取各自的幼年标记；盔甲架为小型盔甲架。
- 不覆盖其它随状态变化的尺寸：史莱姆一类与幻翼的大小、河豚的膨胀、鲑鱼的大小、成年骆驼坐下、山羊跳跃、北极熊站立、嗅探兽与监守者挖掘、潜影贝、盔甲架的标记、交互实体，以及末影龙、快乐恶魂、潜影贝对 scale 属性的限制（原版 `sanitizeScale`），这些情况取上面的尺寸。

Minestom 的实体类型数据（`net.minestom:data` 的 `entity_type.json`）只有成年实体默认姿态的尺寸（M12，见 9.6），幼年与玩家各姿势的尺寸是本库内置的原版 26.2 取值。

#### 7.5 本地玩家

`LocalPlayer` 保存：

- 实体编号、档案。
- 游戏模式（MCProtocolLib 的 `GameMode`）：取自 Login (play)、Respawn、Game Event 3、自己 UUID 的 Player Info Update（wiki FAQ：旁观模式通过 Player Info Update 告知；自己不在玩家列表中时这项更新被忽略，原版客户端行为；改成旁观模式时竖直速度的处理见 9.2）。Game Event 3 的数值由 MCProtocolLib 按 `GameMode.byId` 解码，不认识的编号为生存模式，与原版相同。
- 能力：飞行、允许飞行、瞬间建造、无敌、可以建造、飞行速度、行走速度。初始值与原版 `Abilities` 相同（飞行速度 0.05，行走速度 0.1，可以建造为 true，其余为 false）。Login (play)、Respawn、Game Event 3 设置游戏模式时按原版 `GameType.updatePlayerAbilities` 设置：创造模式可以飞行、瞬间建造、无敌；旁观模式可以飞行、无敌并且正在飞行，不能瞬间建造；生存与冒险模式这四项都为 false；可以建造等于「不是冒险模式也不是旁观模式」。之后由 Player Abilities 覆盖（它不包含可以建造）。
- 生命、饥饿、饱和度、经验；死亡状态（Set Health 生命值 ≤ 0，或 Combat Death）。
- 元数据：与 7.4 相同的 `Map<Integer, EntityMetadata<?, ?>>`。
- 属性（`AttributeMap`）：自己实体编号的 Update Attributes。每个属性保存基础值与按名称（`Key`）排列的 MCProtocolLib `AttributeModifier`，同名的修饰符替换之前的。取值由本库计算，与 Minestom `AttributeInstance` 的计算相同：基础值加上全部 `ADD`；再对每个 `ADD_MULTIPLIED_BASE` 加上「加法之后的值 × 数值」；再对每个 `ADD_MULTIPLIED_TOTAL` 乘以「1 + 数值」；最后限制到 `AttributeData` 的最小值与最大值（6.1）。
- 药水效果（`EffectMap`：`Map<Effect, ActiveEffect>`，每 tick 剩余时间减一，无限的效果不减）、物品冷却（Set Cooldown，以冷却组的 `Key` 为键，按 tick 递减）、快捷栏槽位。
- 位置、朝向、速度、是否着地、是否顶住墙壁、上一次 `PhysicsResult`、乘坐的实体（Set Passengers 中包含自己时；载具被 Remove Entities 移除、Login (play)、Respawn 时清除）。
- 属性的基础值取原版 `Player.createAttributes`：移动速度为 `(double) 0.1F`（0.10000000149011612），攻击伤害为 1，路径点的发送与接收距离为 6.0E7，其余为 `AttributeData` 中属性的默认值（例如 `jump_strength` 为 `(double) 0.42F`）。其它实体的基础值都为 `AttributeData` 的默认值。Update Attributes 替换列出的属性的基础值与全部修饰符（原版客户端行为）。
- 尺寸取原版 `Avatar.STANDING_DIMENSIONS` 与 `Avatar.POSES` 中潜行的尺寸，以 float 保存与计算：眼睛高度站立 1.62F、潜行 1.27F；包围盒宽 0.6F，高 1.8F（潜行时 1.5F）；都乘以 `minecraft:scale` 属性，属性值先换成 float（原版 `LivingEntity.getScale`、`EntityDimensions.scale`）。包围盒的半宽因此为 `(double) 0.3F`（0.30000001192092896），与原版服务端计算的玩家包围盒相同。按 double 的 0.3 计算时，贴墙的玩家在服务端的包围盒伸进墙内约 1.2E-8：原版服务端检查新碰撞时把包围盒收缩 1.0E-5，不受影响；Paper 26.2 的 `hasNewCollision` 不收缩，判定玩家卡进方块并把玩家传送回去。

**新的本地玩家**：新的连接或 Start Configuration 之后的 Login (play)（5.1），以及 Respawn，都创建新的本地玩家（原版客户端新建 `LocalPlayer`）。新玩家位于原点，偏航角 -180，俯仰角 0，速度为零，不着地、不顶住墙壁，没有乘坐，姿态为站立，存活；元数据为空，没有药水效果，属性为上面的基础值、没有修饰符；生命值 20，饥饿值 20，饱和度 5，经验进度、等级、总经验为 0；能力为初始值再按游戏模式设置；物品栏为空，快捷栏槽位 0，没有打开的窗口，状态编号为 0，没有物品冷却。移动包的上一次发送记录随之重新开始（9.3）。这些模型的重置不产生 `SlotChanged` 等观察记录。

**Respawn**（原版 `handleRespawn`）：

- 维度名称改变时重新创建世界（方块交互序号从 0 开始）；同一维度内保留区块、方块实体与其它实体。两种情况都产生 `WorldEntered`（`cause` 为 `RESPAWN`）。
- 打开着容器窗口时，先发送带该窗口编号的 Close Container（`ServerboundContainerClosePacket`），并在本地关闭窗口（产生 `WindowClosed`）。
- 创建新的本地玩家，沿用实体编号与游戏模式：
  - Data kept 由 MCProtocolLib 解码为两个布尔值：0x02 为 `isKeepMetadata()`，0x01 为 `isKeepAttributeModifiers()`。
  - Data kept 0x02：保留元数据、生命值、速度、偏航角与俯仰角，以及上次发送的 Player Input 与疾跑状态；没有 0x02 时做原版 `resetPos`（姿态为站立，在维度高度范围内从当前高度逐格向上，直到包围盒不与方块的碰撞箱相交，区块未加载的位置没有方块；速度与俯仰角归零，生命值设为最大生命值），偏航角 -180，上次发送的 Player Input 为空、疾跑为 false：下一 tick 只要当前输入不为空就发送 Player Input，正在疾跑时发送 Start sprinting。
  - Data kept 0x01：保留属性的基础值与全部修饰符；没有 0x01：保留每个已有属性的基础值（包括来自 Update Attributes 的值），移除全部修饰符（包括疾跑修饰符）。
  - 其余按上面的新玩家：物品栏为空，快捷栏槽位 0，没有物品冷却与药水效果，饥饿值 20，饱和度 5，经验为 0，能力按游戏模式重新设置。
- 游戏模式控制器不重建：挖掘延迟与已经发送的快捷栏槽位不变。服务端没有发送 Set Held Item 时，下一 tick 因为快捷栏槽位 0 与已经发送的槽位不同而发送 Set Held Item(0)（8.6）。
- 移动与手部两条队列中的动作以 `WORLD_REBUILT` 取消（8.2）。

#### 7.6 物品栏

**窗口与槽位**：

- 窗口 0 为玩家物品栏，槽位含义见 wiki Inventory。
- 槽位中的物品为 MCProtocolLib 的 `ItemStack`，空槽位为 null。槽位是否改变按 `Items.sameStack` 判断（6.1，待办 T3）。
- `ClientboundOpenScreenPacket`（Open Screen）与 `ClientboundMountScreenOpenPacket`（Open Horse Screen）打开其它窗口；窗口类型为 MCProtocolLib 的 `ContainerType`，它的序号即原版的窗口类型编号。
- `ClientboundMountScreenOpenPacket` 按原版 `handleMountScreenOpen` 处理：实体是马类（原版 `AbstractHorse`：马、驴、骡、骷髅马、僵尸马、羊驼、行商羊驼、骆驼、骆驼尸壳）时打开马的物品栏；是鹦鹉螺或僵尸鹦鹉螺（原版 `AbstractNautilus`）时打开鹦鹉螺的物品栏，它只有鞍与身体护甲 2 格，不看数据包中的列数（原版 `NautilusInventoryMenu`）；实体不在模型中或者是其它类型时什么都不做，打开着的窗口保持打开（原版客户端行为）。
- 每种窗口第一个主物品栏槽位的下标（独有槽位数）取 `ContainerSlots.unique(ContainerType)`，测试中逐项与 wiki Inventory 页比对：

  | 窗口类型 | 独有槽位数 |
  |---|---|
  | `GENERIC_9X1` 到 `GENERIC_9X6` | 9、18、27、36、45、54 |
  | `GENERIC_3X3`、`CRAFTER_3x3` | 9 |
  | `ANVIL`、`BLAST_FURNACE`、`FURNACE`、`GRINDSTONE`、`MERCHANT`、`SMOKER`、`CARTOGRAPHY` | 3 |
  | `BEACON`、`LECTERN` | 1 |
  | `BREWING_STAND`、`HOPPER` | 5 |
  | `CRAFTING` | 10 |
  | `ENCHANTMENT`、`STONECUTTER` | 2 |
  | `LOOM`、`SMITHING` | 4 |
  | `SHULKER_BOX` | 27 |

  窗口槽位排列（wiki Inventory）：
  - 一般的窗口：独有槽位，然后主物品栏 27 格，然后快捷栏 9 格；
  - 合成器（`crafter_3x3`）：输入 0 到 8，主物品栏 9 到 35，快捷栏 36 到 44，结果槽位在最后（45）；
  - 讲台：只有书一格，不包含玩家物品栏；
  - 马的物品栏：鞍与身体护甲 2 格，然后每列 3 格，然后主物品栏与快捷栏；鹦鹉螺的物品栏：鞍与身体护甲 2 格，然后主物品栏与快捷栏。

**服务端下发的数据**：

- `ClientboundContainerSetContentPacket`（状态编号、全部槽位、光标物品）、`ClientboundContainerSetSlotPacket`（状态编号、单个槽位）、`ClientboundSetCursorItemPacket`、`ClientboundContainerSetDataPacket`、`ClientboundContainerClosePacket`、`ClientboundSetHeldSlotPacket`、`ClientboundMerchantOffersPacket`（保存在打开的商人窗口上，见 `WindowSnapshot.trades`）。窗口编号与窗口 0 及打开的窗口都不同的数据包被忽略（原版客户端行为）。Set Container Content 的物品数多于窗口的槽位数、Set Container Slot 的槽位不在窗口中时，bot 因异常结束（原版 `AbstractContainerMenu.getSlot` 越界抛出异常，客户端断开连接）。
- `ClientboundContainerClosePacket` 不看窗口编号，关闭打开的窗口（wiki Close Container 写明原版客户端忽略编号；原版 `handleContainerClose` 调用 `clientSideCloseContainer`），`WindowClosed` 给出被关闭的窗口的编号；没有打开的窗口时什么都不做。
- **光标物品**由每个窗口各自记录（原版每个 `AbstractContainerMenu` 各有一个 `carried`）：Set Container Content 的光标物品写入它指向的窗口（窗口 0 的写入窗口 0，即使打开着其它窗口），Set Cursor Item 写入当前窗口（打开的窗口，没有打开窗口时为窗口 0）；新打开的窗口光标为空；关闭窗口之后当前窗口回到窗口 0，光标为窗口 0 的光标。`StateReader.cursor()` 为当前窗口的光标，它改变时（包括打开与关闭窗口使当前窗口改变）产生 `CursorChanged`。
- `ClientboundSetPlayerInventoryPacket`（Set Player Inventory Slot）：wiki 写明它使用玩家物品栏下标、顺序与窗口槽位不同，下标对应关系按原版客户端行为编码：0 到 8 为快捷栏，9 到 35 为主物品栏，36 到 39 依次为脚、腿、胸、头（窗口 0 的槽位 8 到 5），40 为副手（窗口 0 的槽位 45），41、42 为身体护甲与鞍（原版 `Inventory.EQUIPMENT_SLOT_MAPPING`）。身体护甲与鞍在窗口 0 中没有对应的槽位，保存在 `InventoryModel` 中（MCProtocolLib `EquipmentSlot` 的 `BODY` 与 `SADDLE`），由 `StateReader.equipment` 读取（空时为 null），改变时产生 `EntityEquipmentChanged`（实体编号为本地玩家的）。大于 42 的下标被忽略，下标为负数时 bot 因异常结束（原版 `Inventory.setItem`：负数在写入物品列表时越界，客户端断开连接）。
- wiki Set Container Slot 规定：服务端对打开窗口中玩家物品栏部分的修改不会再针对窗口 0 重发。原版客户端的容器与窗口 0 共用同一个玩家物品栏，所以 `InventoryModel` 把一边的主物品栏与快捷栏修改同时写入另一边，两边各产生一条 `SlotChanged`。
- 打开窗口（Open Screen、Open Horse Screen）时，新窗口的主物品栏与快捷栏取窗口 0 的物品（原版客户端的容器菜单直接使用玩家的 `Inventory`，打开时就有这些物品）。窗口刚刚建立，物品没有改变，所以不产生 `SlotChanged`；之后服务端发来的 Set Container Content 中这一部分与已有的物品相同时同样不产生。服务端打开窗口时不一定发送这一部分：Minestom 的 `AbstractInventory.update` 只发送容器自己的槽位。

**点击**：

- `inventory().click(slot, ContainerActionType, ContainerAction)` 发送 `ServerboundContainerClickPacket(窗口编号, 状态编号, 槽位, 模式, 按键, null, Map.of())`：执行时的窗口编号（没有打开窗口时为 0）、这个窗口最近收到的状态编号；光标物品为空（null），变化槽位为空。
- 提交请求时在调用线程上检查，不合法时抛出 `IllegalArgumentException`：按键的类型必须属于这个模式（`CLICK_ITEM` 对应 `ClickItemAction`，`SHIFT_CLICK_ITEM` 对应 `ShiftClickItemAction`，`MOVE_TO_HOTBAR_SLOT` 对应 `MoveToHotbarAction`，`CREATIVE_GRAB_MAX_STACK` 对应 `CreativeGrabAction`，`DROP_ITEM` 对应 `DropItemAction`，`SPREAD_ITEM` 对应 `SpreadItemAction`，`FILL_STACK` 对应 `FillStackAction`）；点击窗口外的两种 `DropItemAction` 要求槽位为 -999。便捷方法 `leftClick`、`rightClick`、`shiftClick`、`swapWithHotbar`、`dropSlot` 按对应的模式与按键构造。
- 状态编号由每个窗口各自记录（原版每个 `AbstractContainerMenu` 各有一个 `stateId`，`handleContainerInput` 发送当前菜单的）：Set Container Content 与 Set Container Slot 只改变它们指向的窗口的状态编号，同步到另一个窗口的槽位修改不改变那个窗口的；新打开的窗口从 0 开始，关闭之后点击窗口 0 时使用窗口 0 自己的状态编号。`WindowSnapshot.stateId` 为这个窗口的状态编号。
- wiki Click Container 写明：服务端把客户端声明的变化叠加到点击前的状态上，与点击结果比较，不一致的槽位用 Set Container Slot 发回；状态编号不一致时发回完整内容。所以点击改变的每个槽位都会由服务端发回，物品栏内容只由服务端数据更新，本库不预测点击结果。
- 点击后光标不为空时，服务端会发回 Set Cursor Item（原版服务端行为）。

#### 7.7 聊天与界面状态

- **`ChatModel`**：Player Chat Message、Disguised Chat Message、不带 overlay 的 System Chat Message 写入有长度上限的日志（`BotOptions.chatLogSize`，默认 256）；Delete Message 从日志中删除对应消息。日志中的每条为 `ChatLine(ChatKind kind, @Nullable UUID sender, @Nullable Key chatType, @Nullable ChatType inlineChatType, @Nullable Component senderName, @Nullable Component targetName, Component message, String plainText)`，`kind` 区分玩家聊天、伪装聊天与系统消息；`chatType`、`inlineChatType`、`senderName`、`targetName` 为玩家聊天与伪装聊天中的聊天类型、发送者名称与目标名称（原版 `ChatType.Bound`；后两者是聊天类型修饰消息时的 sender 与 target 参数，没有目标名称时为 null），聊天类型来自注册表时 `chatType` 为它在 `minecraft:chat_type` 注册表中的名称、`inlineChatType` 为 null，随数据包给出定义时 `chatType` 为 null、`inlineChatType` 为 MCProtocolLib 的 `ChatType` 定义；系统消息的这四项为 null；`message` 不经过聊天类型的修饰；`plainText` 为去掉格式后的文本（adventure 的 `PlainTextComponentSerializer`）。`plainText` 不翻译翻译组件：本库没有语言文件，翻译组件本身与它的翻译参数都不出现在文本中，只保留它的子组件，所以加入消息、死亡消息等由翻译键组成的系统消息的 `plainText` 通常为空字符串；按翻译键匹配这类消息使用 11.7 的 `firstTranslatedChat`。
  - **Player Chat Message 的显示**（原版 `handlePlayerChat` 与 `ChatListener`）：发送者不在玩家列表中时不记录这条消息，改为记录原版代替它显示的 `chat.validation_error`（红色斜体的翻译组件；没有签名，Delete Message 不删除它；原版 `handleChatMessageError`）；完全过滤（FULLY_FILTERED）的消息不记录，也不产生 `ChatReceived`；其余记录未签名内容，没有时为消息正文。没有显示的消息的签名只计入未确认的消息数（5.3）。
  - **部分过滤的消息（待办 T2）**：MCProtocolLib 的 `ClientboundPlayerChatPacket` 在 PARTIALLY_FILTERED 时不读取过滤标记的 BitSet，之后的聊天类型、发送者名称与目标名称都从错误的位置读出。本库不支持这种消息：收到时在使用任何字段之前抛出 `UnsupportedOperationException`，bot 结束（代码标注 `TODO(T2)`）。
  - **聊天类型**：Player Chat Message 与 Disguised Chat Message 的聊天类型字段为「ID or Chat Type」：0 表示数据包随后给出聊天类型的定义，其余为 `minecraft:chat_type` 注册表中的编号加一。MCProtocolLib 解码为 `Holder<ChatType>`：编号已经减去 1，按 `SyncedRegistries.requireEntry(CHAT_TYPE, 编号)` 换成名称，编号不在注册表中时 bot 因异常结束（原版解码时出错，客户端断开连接）；随数据包给出的定义交给 `ChatLine.inlineChatType`。
- **`HudModel`**：
  - Boss Bar：`ClientboundBossEventPacket` 按 `BossBarAction` 处理，每种动作只读取它带有的字段。状态为 `BossBarSnapshot(Component title, float health, BossBarColor color, BossBarDivision division, boolean darkenSky, boolean playEndMusic, boolean showFog)`，后三项为标记 0x1、0x2、0x4。更新没有添加过的 Boss Bar 时 bot 因异常结束（原版客户端读取不存在的条目时出错并断开连接）；移除没有添加过的 Boss Bar 什么都不做（原版 `BossHealthOverlay` 的 remove 只是从表中删除）
  - 标题、副标题、标题时间、清除标题
  - 动作栏（Set Action Bar Text 与带 overlay 标记的 System Chat Message）
  - 玩家列表与页眉页脚：条目的游戏模式为 MCProtocolLib 的 `GameMode`，新条目的名称取自 ADD_PLAYER 带有的档案。Player Info Update 中不带 ADD_PLAYER、玩家列表中没有的玩家的条目被忽略；ADD_PLAYER 对已在列表中的玩家保留原来的条目（名称等不变），只应用同一数据包中的其它更新（原版客户端行为，`handlePlayerInfoUpdate` 用 `putIfAbsent` 加入）；新条目的初始值与原版 `PlayerInfo` 相同：生存模式，延迟 0，不显示在列表中，没有显示名称，排序 0
  - 计分板（Update Objectives、Display Objective、Update Score、Reset Score、Update Teams）。Update Objectives 按 `ObjectiveAction`（`ADD`、`REMOVE`、`UPDATE`）处理，目标的显示类型为 `ScoreType`；显示位置以 `ScoreboardPosition` 为键；Update Teams 按 `TeamAction` 处理。Update Objectives、Display Objective 的位置、Update Teams 的动作出现不认识的编号时，MCProtocolLib 解码失败，bot 结束。Update Objectives 与 Display Objective 按原版 `handleAddObjective`、`handleSetDisplayObjective` 与 `Scoreboard` 处理：创建已经存在的目标时 bot 因异常结束（原版 `addObjective` 抛出异常，客户端断开连接）；删除与更新不存在的目标时什么都不做；删除目标时清空指向它的显示位置（`removeObjective`）；Display Objective 的名称为空或者没有这个目标时清空这个显示位置。Update Teams 按原版 `Scoreboard` 处理：创建已经存在的队伍时沿用它与它的成员，只更新显示设置（`addPlayerTeam`）；一个名称只属于一个队伍，加入队伍（创建时列出的成员与 Add Entities）时先离开原来的队伍（`addPlayerToTeam`）；Remove Entities 移除不在这个队伍中的成员时 bot 因异常结束（`removePlayerFromTeam` 抛出异常，客户端断开连接）；创建以外的操作指向不存在的队伍时什么都不做
  - Server Data

#### 7.8 时间、tick 状态、世界边框、天气

- **推进的时机**（原版 `Minecraft.tick`）：世界边框、World Age 与时钟在实体 tick（`level.tickEntities`，包括本地玩家的移动）之后由 `ClientLevel.tick` 推进，只在这个 tick 正常运行时推进（见 `TickState`），依次为：世界边框推进一步，World Age 加一，时钟按新的 World Age 推进（`tickTime`）。所以一个 tick 中的挖掘、移动与碰撞使用的是上一个 tick 结束时的世界边框，骆驼的骑乘位置（9.6）使用的是上一个 tick 结束时的 World Age。
- **World Age**：属于世界（原版 `ClientLevelData.gameTime`），新的世界从 0 开始，Update Time 把它设为数据包的值。
- **`WorldClocks`**：Update Time 给出的各时钟（原版 `ClientClockManager`），每个时钟有总 tick 数（long）、小数部分（float）与速率（wiki Update Time 的 Rate）。数据包中的时钟按世界时钟注册表的编号给出，本库按 `SyncedRegistries.requireEntry(WORLD_CLOCK, 编号)` 换成名称，以名称（`Key`）保存。时钟属于连接，不属于世界：原版的 `ClientClockManager` 由 `ClientPacketListener` 持有，每次 Finish Configuration 进入游戏阶段时新建，所以本库在每次 Finish Configuration 时重新创建它，Login (play) 与 Respawn（包括维度改变）时保留。时钟按 World Age 的差值推进（`ClientClockManager.tick`）：记下上次推进时的 World Age，每个时钟前进「差值 × 速率」，整数部分计入总 tick 数，小数部分以 float 保存（整数部分按原版 `Mth.floor` 取 int）；正常运行的 tick 中差值通常为 1。Update Time（`handleSetTime`）先把世界的 World Age 设为数据包的值，再按这个值推进全部时钟，然后用数据包中的值替换其中列出的时钟，没有列出的时钟因此前进了「两次 Update Time 之间 World Age 的差值 × 速率」。换维度之后新的世界 World Age 从 0 开始，下一次推进时差值为负数，时钟随之后退，直到服务端发来新的 Update Time，与原版相同。
- **`TickState`**：Set Ticking State 的速率与冻结标记、Step Tick 的步数；速率用于 3.2 的 tick 周期。每个 tick 开始时按原版 `TickRateManager.tick` 决定这个 tick 是否正常运行：没有冻结，或者还有要步进的 tick；还有要步进的 tick 时步数减一，所以 Step Tick 给出几步就正常运行几个 tick。不正常运行的 tick 中，其它实体不执行实体步骤（7.4），世界边框与时间不推进。
- **`WorldBorderState`**（原版 `WorldBorder`）：Initialize World Border 的插值时间大于 0 时从旧的大小插值到新的大小，否则直接设为新的大小；Set Border Lerp Size 的两个大小相同时直接设为这个大小，否则开始插值；Set Border Size 直接设为这个大小（原版 `handleInitializeBorder`、`handleSetBorderLerpSize`、`handleSetBorderSize`）。
  - 插值时间以 tick 计，按原版：wiki 修订版本的 Initialize World Border 与 Set Border Lerp Size 把 Speed 写成真实时间的毫秒数，原版客户端（`MovingBorderExtent` 每次推进把剩余时间减一）与 Minestom（`WorldBorder.createLerpSizePacket` 的 `transitionTicks`）都以 tick 计。
  - 每次推进（原版 `MovingBorderExtent.update`）：剩余时间减一，记下推进之前的大小，新的大小为 `起始大小 + (插值时间 − 剩余时间) / 插值时间 × (目标大小 − 起始大小)`；剩余时间不大于 0 时静止在目标大小。插值时间小于等于 0 的 Set Border Lerp Size 同样开始插值：插值时间为 0 时此刻就是目标大小，小于 0 时此刻为起始大小，第一次推进之后都静止在目标大小。
  - 范围判断（挖掘的世界边框检查）与碰撞（转换为 Minestom 的 `WorldBorder` 交给 `PhysicsUtils`）使用的大小，插值期间为推进之前的大小，即上一 tick 的大小（原版 `MovingBorderExtent` 的 `getMinX(0)` 等取 `previousSize`，碰撞形状 `getCollisionShape` 同样由它们得出），静止时为当前大小。
- **`WorldBorderState`** 的数据取自 MCProtocolLib 的各个 Border 数据包；Initialize World Border 中的 Portal Teleport Boundary 字段名为 `getNewAbsoluteMaxSize()`。
- **`Weather`**：使用 Game Event 7、8；事件 1、2 的效果与名称相反，原版服务端紧接着会发送事件 7，所以忽略（wiki Game Event）。MCProtocolLib 把 Game Event 7、8 的数值解码为 `RainStrengthValue`、`ThunderStrengthValue`，限制到 [0, 1]；原版 `setRainLevel`、`setThunderLevel` 同样限制。

### 8. 动作请求

#### 8.1 Action 句柄

每个动作方法立即返回 `Action`。`Action` 只用于等待和取消这个请求，不返回值，也不表示动作在游戏中是否达到了目的；动作的效果由 observer 观察（见 11）。

```java
public interface Action {
    /// 等到动作结束：执行完毕返回 true，被取消返回 false。
    /// 连接结束时抛出 BotDisconnectedException，bot 因异常结束时抛出 BotFailureException，超时抛出 BotTimeoutException
    boolean await(Duration timeout);

    /// 执行完毕时正常完成；被取消时以 ActionCancelledException 完成，异常信息为取消原因；
    /// 连接结束或 bot 因异常结束时，以 await 抛出的同一个异常完成
    CompletableFuture<Void> future();

    /// 取消动作；对已结束的动作不做任何事
    void cancel();
}

public final class ActionCancelledException extends RuntimeException {
    /// 下表中各取消原因的常量：BY_USER、NO_PATH、BLOCKED、NO_ENTITY、CHUNK_NOT_LOADED、OUT_OF_RANGE、WORLD_REBUILT、PLAYER_DIED
    public ActionCancelledException(String reason) { super(reason); }
}
```

动作的结束情况：

| 情况 | `future()` | `await` |
|---|---|---|
| 执行完毕：bot 把这个动作的执行过程走完了，每个动作的执行过程写在 8.4–8.6 中 | 正常完成 | 返回 true |
| 用户调用 `cancel()`（`BY_USER`） | 以 `ActionCancelledException` 完成 | 返回 false |
| 寻路找不到到达目标的路径（8.4，`NO_PATH`） | 以 `ActionCancelledException` 完成 | 返回 false |
| 移动连续被阻挡，重新寻路次数用完（8.4，`BLOCKED`） | 以 `ActionCancelledException` 完成 | 返回 false |
| `lookAt(int entityId)`、`attack`、`interact` 的目标实体不在模型中（8.4、8.5，`NO_ENTITY`） | 以 `ActionCancelledException` 完成 | 返回 false |
| 移动范围涉及未加载的区块（9.1）；`moveTo` 找不到路径（或者只能走到最近点）并且寻路时目标所在区块未加载（8.4）；或者手部交互动作读取的方块所在区块未加载（8.5）（`CHUNK_NOT_LOADED`） | 以 `ActionCancelledException` 完成 | 返回 false |
| `attack` 的目标不在原版客户端的攻击距离内（8.5，`OUT_OF_RANGE`） | 以 `ActionCancelledException` 完成 | 返回 false |
| 世界重建或玩家死亡，队列被清空（8.2，`WORLD_REBUILT`、`PLAYER_DIED`） | 以 `ActionCancelledException` 完成 | 返回 false |
| 连接结束（服务端断开、`Bot.disconnect()`、`BotRuntime.close()`）；连接结束时 observer 的 `onClose` 抛出异常的属于下面的「bot 因异常结束」（12） | 以 `BotDisconnectedException` 完成，异常信息为断开原因 | 抛出该异常 |
| 写出即执行完毕的动作（一次性请求、`attack`、`interact`、`useItem` 的结束），数据包交给连接之后、写出之前，Transfer 关闭的旧连接断开 | 以 `BotDisconnectedException` 完成，异常信息说明连接在写出之前关闭；bot 继续在新连接上登录 | 抛出该异常 |
| bot 因异常结束（12），表示程序错误 | 以 `BotFailureException` 完成 | 抛出该异常 |

- **`cancel()`**：
  - 排队中的动作直接取消；执行中的动作由状态机完成清理（例如挖掘中发送「Cancelled digging」，保持使用物品中发送「Shoot arrow / finish eating」）后取消。
  - 一次性请求已经执行、数据包已经交给连接之前被取消时，`future()` 以取消结束，已经发出的数据包不收回。
  - 在其它线程调用时，取消请求在下一轮调度中处理；在调度线程上（例如 observer 的回调中）调用时立即处理。两种情况下，被取消的动作都不会再被下一个 tick 推进。
- **`future()`**：
  - 返回内部 future 的副本（`CompletableFuture.copy()`），对它调用 `complete`、`cancel` 等方法不影响动作；取消动作使用 `Action.cancel()`。
  - 内部 future 在调度线程上完成，不带 async 的后续回调在调度线程上执行，3.3 中关于回调阻塞的约定同样适用。
- `await` 在等待期间执行 3.4 的推进函数。
- 动作结束时产生 `ActionCompleted` 观察记录（见 11.2）。
- 典型用法：先注册 observer，再提交动作，`await` 动作结束后，检查 observer 的状态决定后续代码（见 11.10 的示例）。只观察一个动作执行期间的变化时使用 `Bot.observeDuring`（11.8）。需要传入动作的 observer（11.7 的 `distanceWithIn`、`distanceOutOf`）在提交动作之后注册，它们在注册时先检查一次当前状态（不在任何世界中时没有玩家位置，不做这次检查）。

#### 8.2 三类队列

| 队列 | 动作 | 执行方式 |
|---|---|---|
| 移动 | `moveTo`、`moveForward`、`jumpTo`、`rotate`、`lookAt` | 先进先出，一次运行一个，按 tick 推进；movement suspension 期间 `moveTo`、`moveForward`、`jumpTo` 不推进，轮到的也不开始，排在它们后面的动作随之等待（9.4） |
| 手部交互 | `attemptBreakBlock`、`placeBlock`、`useItemOn`、`useItem`、`attack`、`interact` | 先进先出，一次运行一个，按 tick 推进 |
| 一次性请求 | `chat`、`command`、`selectHotbar`、`swapHands`、`drop`、`respawn`、物品栏操作、`send` | 按提交顺序在下一轮调度中执行，产生的数据包本轮发出 |

- 移动与手部交互两条队列互相独立，可以同时运行。一个动作结束之后，同一条队列中的下一个动作在下一个 tick 开始。
- bot 发送 Player Loaded（见 10）之前，三类队列中的请求都不开始执行。不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前，见 5.1）提交的请求同样留在队列中，进入世界并加载之后执行。
- 参数在提交时检查，不合法时在调用线程上抛出 `IllegalArgumentException`：俯仰角不在 [-90, 90] 之内（包括 NaN）、聊天超过 256 个字符、命令超过 32767 个字符、快捷栏槽位超出 [0, 8]、`useItem` 的保持时间不为正数、`moveForward` 指定的方向没有水平分量。
- **队列清空**：世界重建只取消在上一个世界中提交的动作。以下情况下，移动与手部两条队列中执行中和排队中的动作以 `WORLD_REBUILT` 或 `PLAYER_DIED` 取消，一次性请求不受影响：
  - 离开世界：Start Configuration、Transfer（不发送清理数据包，5.1）；
  - 在世界中收到 Login (play)（不发送清理数据包，5.1）或 Respawn（执行中的动作照常清理，例如挖掘中发送 Cancelled digging）；
  - 玩家死亡。

  不在任何世界中时收到的 Login (play)（第一个世界，以及重新配置、Transfer 之后的世界）不取消队列中的动作：它们都是在没有世界时提交的。
- **连接结束**：全部未结束的动作的 `future()` 以 `BotDisconnectedException` 完成；bot 因异常结束时以 `BotFailureException` 完成。

#### 8.3 朝向的归属

`RotationArbiter` 每个 tick 按以下优先级决定朝向：

1. 正在运行、需要对准目标的手部动作（挖掘、放置、对方块使用物品、攻击、交互）：看向目标。
2. 正在运行的 `moveTo`、`moveForward`、`jumpTo`：看向移动方向。
3. 最近一次 `rotate` 或 `lookAt` 设置的朝向，持续到下一个 `moveTo`、`moveForward`、`jumpTo` 开始，或者服务端设置玩家的朝向（不骑乘时的 Synchronize Player Position、Player Rotation、应用到本地玩家的 Synchronize Vehicle Position：编号是被移除的载具的，或者是本地玩家自己的，见 7.4；后者插值时朝向在之后的移动计算中逐步改变）。原版 `handleMovePlayer`、`handleRotatePlayer` 设置的朝向一直保持，所以服务端设置朝向之后，之前 `rotate`、`lookAt` 的朝向不再生效。
4. 保持上一 tick 的朝向（包括服务端设置的朝向）。

移动方向与朝向互相独立：移动由期望方向向量直接计算（见 9.1），不需要先转身。

#### 8.4 移动动作

**`moveTo(Point target, MoveOptions)`**：

1. **寻路**：开始时在寻路线程上调用 `PathGenerator.generate(snapshot, start, target, closeDistance, maxDistance, pathVariance, 玩家包围盒, onGround, new PlayerNodeGenerator(服务端的标签), null)`：
   - `snapshot` 为 7.3 的世界快照，半径取 `maxDistance`；同时记下目标所在区块此刻是否已加载；
   - `PlayerNodeGenerator` 内部有可变状态，每次寻路新建一个；
   - 默认值取 Minestom `Navigator.setPathTo` 的默认参数：`maxDistance` 50，`pathVariance` 20。`closeDistance` 默认 0.5，可以在 `MoveOptions` 中修改。
   - 寻路的结果由移动动作在之后的 tick 中取回；寻路期间期望移动方向为零。寻路线程上的异常按 12 结束 bot。
   - **节点生成器**：`PlayerNodeGenerator` 继承 Minestom 的 `GroundNodeGenerator`，节点高度、起跳与下落的规则不变。Minestom 的 `NodeGenerator.canMoveTowards` 把节点脚部任何不是 `Block.AIR` 的方块当作障碍（草、花、单层雪、`cave_air` 都走不过去），`PlayerNodeGenerator` 改为：
     - 碰撞：与 Minestom 的默认实现相同，`CollisionUtils.handlePhysics` 让包围盒从起点直线移动到终点，三个方向都没有碰撞；
     - 避开本库不模拟其效果的方块（9.5）：包围盒放在起点与终点的范围合起来，各边向内收缩 1.0E-5（原版 `checkInsideBlocks`）之后新接触的方块中有流体（`Block.fluid()`），或者有原版 26.2 中覆盖 `entityInside`、碰撞箱为空、会减速、造成伤害或者传送的方块（蜘蛛网、甜浆果丛、细雪、火、灵魂火、凋灵玫瑰、下界传送门、末地传送门、末地折跃门）；终点所在的列上，脚部在起点与终点高度之间经过的方块属于服务端标签 `minecraft:climbable`，或者是下面有朝向相同梯子的打开的活板门（原版 `LivingEntity.onClimbable`）。起点已经接触的方块不算，否则站在其中时走不出来；
     - `canMoveTowards` 与 `getWalkable` 都做第二项判断，`JUMP` 节点与 `FALL` 节点的落点由后者排除。
2. **路径判断**：`PPath.getState()` 只在包内可见，外部无法调用，路径按节点列表判断：
   - **REPATH 节点**：已搜索的节点数达到 `maxDistance × 10` 而开放列表不为空时，`PathGenerator.computePath` 在最近节点之后接一个坐标为 (0, 0, 0)、类型为 `REPATH` 的节点，并且只在它位于第一个时把路径判为无效（`PPath.getCurrentType` 只看下标 0）。所以先去掉第一个 `REPATH` 节点及其之后的节点（目标靠近原点时，Minestom 还会在它之后接上目标节点），剩下的路径一定是不完整的；
   - 列表为空：动作被取消；
   - 去掉过 `REPATH` 节点，或者最后一个节点没有到达目标（第 5 步的到达条件，高度取这个节点的站立高度，见第 3 步）：只走到最近点（与 Minestom 的 BEST_EFFORT 相同），走完后动作被取消；
   - 这两种取消的原因：寻路时目标所在区块未加载为区块未加载（`CHUNK_NOT_LOADED`），否则为找不到路径（`NO_PATH`）。快照对未加载的区块返回屏障，目标的区块未加载时路径只能停在已加载区域的边缘。按寻路时的状态判断：目标在 `maxDistance` 之外时快照不包含它的区块，只能查 `ClientWorld`；
   - 其余情况为完整路径（最后一个节点为目标本身）。
3. **跟随**（`PathFollower`，每 tick）：
   - **节点的高度**：`GroundNodeGenerator` 的节点高度是整数。它从上往下找到包围盒下方第一层有 `solid()` 方块的格子，节点高度为这一层加一（`gravitySnap`），与方块碰撞箱的实际高度无关：半砖上的节点比半砖顶面高 0.5，楼梯上的节点高度为楼梯所在层加一（下层台阶比它低 0.5），栅栏上的节点比栅栏顶面低 0.5。路径的终点（目标）同样经过 `gravitySnap`。所以跟随时为每个节点计算**站立高度**：在节点的水平位置上，让玩家的包围盒从节点高度加一格处向下移动两格（Minestom 的 `CollisionUtils.handlePhysics`，使用这次寻路的世界快照与玩家包围盒），停下的高度；节点所在层的地毯、雪层等不算 `solid()` 但有碰撞箱的方块也计算在内。到达与起跳都按站立高度判断。
   - 与节点中心的水平距离小于 0.3 时视为到达这个节点（`JUMP` 还要求脚部不低于站立高度 0.1 以上，`FALL` 还要求已着地并且不高于站立高度 0.5 以上，其余要求与站立高度的差小于 1）；
   - 朝下一个节点的中心移动；
   - **起跳**（9.1 的跳跃）：`PhysicsUtils` 没有原版的台阶抬升（9.5），所以下一个节点的站立高度比脚部高出 0.001 以上时都要起跳，与节点类型无关（例如从半砖走上同一层的完整方块，节点类型是 `WALK`）：着地，并且与节点中心的水平距离不大于 0.9（玩家半宽 0.3 加上节点中心到格子边缘的 0.5，再留 0.1 的余量）或者已经顶住墙壁时起跳。0.001 的余量留给碰撞计算的误差；
   - `FALL`：向节点方向走出边缘；
   - 其它节点类型（`CLIMB`、`SWIM`、`FLY`）不会由 `PlayerNodeGenerator`（`GroundNodeGenerator`）产生；
   - 完整路径的节点全部到达之后，直接朝目标移动。
4. **重新寻路**：以下情况下重新寻路，最多 3 次，超过后动作被取消，取消原因为移动被阻挡：
   - 连续 20 tick 水平位移小于 0.05；
   - 路径上的方块被 Block Update 改变；
   - 服务端传送了玩家。

   寻路期间（从开始寻路到取回结果的那个 tick）发生的情况同样处理：寻路使用开始时的世界快照与玩家位置，所以这期间的传送让这次的结果作废，取回之后不使用它，直接重新寻路；这期间被改变的方块先记下来，取回结果之后其中有方块在路径上时重新寻路。
5. **执行完毕**：玩家着地，与目标的水平距离进入 `closeDistance`，并且脚部与目标的站立高度相差小于 1（与到达 `WALK` 节点的高度条件相同）。目标的站立高度与路径终点的计算相同：按 Minestom `PathGenerator.generate` 的方式，用 `GroundNodeGenerator.gravitySnap`（最多下落 100 格）把目标的高度换成整数的节点高度，找不到时保持原来的高度，再按第 3 步计算站立高度；每次寻路时用这次的世界快照重新计算。所以站在半砖上时脚部高度 0.5 与目标的站立高度相同，目标在正上方或者正下方的另一层时不算到达。

**`moveForward(double blocks, MoveOptions)`**：

- 方向取开始时的水平朝向，或 `MoveOptions.direction` 指定的方向。
- 沿直线移动，不寻路、不跳跃。
- 累计水平位移达到 `blocks` 时执行完毕；连续 20 tick 被阻挡（水平位移小于 0.05）时动作被取消，取消原因为移动被阻挡。

**`jumpTo(Point target, MoveOptions)`**：

- 目标为一个方块位置，执行过程以「落在它的顶面」为目标计算起跳。
- 着地时起跳；起跳前按目标的水平距离选择行走或疾跑（`MovementMode` 未指定时，水平距离超过 2 格选择疾跑）；空中保持朝目标顶面中心加速，水平距离小于 0.1 时不再加速。
- 起跳后再次着地即执行完毕。起跳的 tick 移动之后仍然着地时（起跳没有离开地面，例如跳跃力为 0，或者头顶有方块）同样执行完毕。是否落在目标方块上由用户通过 observer 或查询判断。

**`rotate(float yaw, float pitch)`**、**`lookAt(Point target)`**、**`lookAt(int entityId)`**：

- 在移动队列中轮到它的那个 tick 设置朝向：
  - `rotate` 直接使用给出的偏航角与俯仰角，单位为度，取值含义见 wiki Set Player Rotation。俯仰角不在 [-90, 90] 之内（包括 NaN）时在提交请求时抛出 `IllegalArgumentException`。
  - `lookAt` 由玩家眼睛位置指向目标计算朝向，公式见 wiki Set Player Rotation。`lookAt(int entityId)` 的目标取该实体包围盒（7.4）的中心；实体不在模型中时动作被取消，取消原因为实体不存在。
  - 朝向只在这个 tick 计算一次，之后玩家或目标实体移动时不再更新。
- 设置朝向的 tick 结束时执行完毕。没有手部动作占用朝向时（8.3），新朝向由这个 tick 的移动包发出（9.3）。
- 设置的朝向保持到下一个 `moveTo`、`moveForward`、`jumpTo` 开始，或者服务端设置玩家的朝向（8.3）。
- movement suspension 期间照常执行（9.4）。

**`MoveOptions`**：`mode`（`WALK`、`SPRINT`、`SNEAK`；未指定时 `moveTo`、`moveForward` 为 `WALK`，`jumpTo` 按距离选择）、`closeDistance`、`maxDistance`、`direction`。

#### 8.5 手部交互动作

**方块交互的序号**：`BlockSequence` 维护 wiki 定义的 Sequence 计数（用手左键方块、右键使用物品、开始或结束挖掘时加一），收到 `ClientboundBlockChangedAckPacket` 时完成序号小于等于确认值的全部步骤。带序号的动作在收到确认时执行完毕：确认表示服务端已经处理了这个请求，并且服务端在确认之前发出的方块更新都已经应用到模型中，用户此时检查 observer 或查询得到的就是服务端处理之后的结果。

**`attemptBreakBlock(Point pos[, Direction face])`**：一次点击，按原版 `Minecraft.startAttack`、`continueAttack` 与 `MultiPlayerGameMode` 的挖掘逻辑执行（原版客户端行为）。挖掘的 Player Action 为 `ServerboundPlayerActionPacket(PlayerAction, 方块位置, 面, 序号)`，Swing Arm 为 `ServerboundSwingPacket(MAIN_HAND)`。动作开始的 tick 为第 1 tick：

- **面**（MCProtocolLib 的 `Direction`）：未指定时取朝向玩家眼睛的那一面（方块中心指向眼睛的向量中绝对值最大的分量）。挖掘期间看向方块中心（8.3）。
- **挖掘延迟**：原版 `MultiPlayerGameMode` 的 `destroyDelay`，跨动作保存。挖掘完成或者瞬间破坏时设为 5，只在「继续」步骤中减少；Login (play) 时归零（新建游戏模式控制器），Respawn 时不变。开始新的挖掘不需要等待，延迟只推迟进度的累加。
- **开始（第 1 tick）**：
  - 目标方块已经是空气时不发送数据包，直接执行完毕；
  - 旁观模式不挖掘、不挥手，只发送不带实体编号的 Spectator Action（`ServerboundSpectatorActionPacket(OptionalInt.empty())`），数据包写出即执行完毕（原版 `startAttack` 在旁观模式下点击的不是实体时调用 `MultiPlayerGameMode.spectatorNoAction`）；
  - 主手物品带 `piercing_weapon` 组件（穿刺武器，`Items.has(主手物品, PIERCING_WEAPON)`，6.1）时不挖掘：发送 Player Action「Stab」（`PlayerAction.STAB`，位置 0, 0, 0，面 `DOWN`，序号 0）与 Swing Arm，数据包写出即执行完毕（原版 `startAttack` 在判断目标之前调用 `MultiPlayerGameMode.piercingAttack`）；
  - 冒险模式不能破坏目标（原版 `Player.blockActionRestricted`：不能建造，并且主手为空、主手物品没有 `can_break`，或者 `can_break` 不匹配目标方块，见下文的 `BlockPredicates`）、目标在世界边框之外时，不发送「Started digging」，只发送 Swing Arm，再执行一次「继续」中的挖掘延迟步骤（延迟大于 0 时减一并发送 Swing Arm），然后执行完毕；
  - 能力中有瞬间建造时（原版判断的是能力，与游戏模式无关）发送「Started digging」（带序号）与 Swing Arm，挖掘延迟设为 5，收到确认即执行完毕，不发送「Finished digging」（wiki Player Action）；
  - 本 tick 的进度已经达到 1 时发送「Started digging」与 Swing Arm，挖掘延迟不变，收到确认即执行完毕；
  - 其余情况发送「Started digging」与 Swing Arm，然后在同一 tick 执行一次「继续」。
  - 本 tick 的进度在发送「Started digging」之前计算（原版 `startDestroyBlock` 在 `startPrediction` 中先判断进度、再发出数据包）。
- **继续**（开始的 tick 与之后的每个 tick）：
  - 主手拿着穿刺武器时什么都不做，不累加、不挥手（原版 `continueAttack` 不调用 `continueDestroyBlock`）；
  - 目标方块变成空气时不再发送数据包，直接执行完毕；
  - 挖掘延迟大于 0 时减一并发送 Swing Arm，不累加进度；
  - 能力中有瞬间建造并且目标在世界边框之内时（例如挖掘中收到 Player Abilities 或者游戏模式改成创造模式），走原版 `continueDestroyBlock` 中创造模式的分支：不累加进度，也不看主手物品是否改变，挖掘延迟设为 5，发送带新序号的「Started digging」与 Swing Arm，收到确认即执行完毕；
  - 主手物品与开始挖掘时的物品不同（物品种类或组件不同，原版 `sameDestroyTarget` 用 `ItemStack.isSameItemSameComponents` 比较，本库用 `Items.sameItemSameComponents`，按原始补丁比较，待办 T3；例如中途 `selectHotbar`）时，这个 tick 不累加进度，重新开始挖掘（原版 `continueDestroyBlock` 改为调用 `startDestroyBlock`）：
    - 冒险模式不能破坏目标、目标在世界边框之外时什么都不发送，不挥手，下一 tick 再次尝试；
    - 否则先发送「Cancelled digging」（该位置，当前的面，序号 0），再发送带新序号的「Started digging」；本 tick 的进度已经达到 1 时发送 Swing Arm，收到这个序号的确认即执行完毕；否则进度归零，记下现在的主手物品，发送 Swing Arm；
  - 否则按 `DigSpeed` 累加进度（与原版客户端相同，以 float 累加），达到 1 时先发送「Finished digging」（带序号），挖掘延迟设为 5，再发送 Swing Arm，收到确认即执行完毕；未达到时只发送 Swing Arm。
- **示例**：空手挖泥土、延迟为 0 时每 tick 进度为 1/15（float 0.06666667，累加第 15 次正好等于 1.0）。第 1 tick 发送「Started digging」、Swing Arm，累加第 1 次，再发送 Swing Arm；第 2 至 14 tick 每 tick 累加并发送 Swing Arm；第 15 tick 第 15 次累加达到 1，先发送「Finished digging」，再发送 Swing Arm。紧接着的下一次挖掘在开始的 tick 发送「Started digging」，之后 5 次「继续」只减少延迟，第 6 次起才累加进度。
- **取消**：发送「Cancelled digging」，面为 `DOWN`（-Y，wiki Player Action），然后以 `ActionCancelledException` 结束。
- **区块未加载**：目标所在区块，或者计算进度时读取的眼睛所在区块未加载时，以 `CHUNK_NOT_LOADED` 取消（见下文「其它手部动作」中的区块未加载）。开始的 tick 中这些读取都在发送数据包之前，什么都不发送；挖掘中按「取消」发送「Cancelled digging」，与原版准星离开方块时 `stopDestroyBlock` 发送的中止相同。
- **执行完毕的含义**：执行完毕只表示服务端处理了挖掘请求。服务端拒绝破坏时同样发送确认：Minestom 的 `PlayerActionListener` 在旁观模式、冒险模式下手持物品不能破坏该方块、方块不可破坏、`PlayerStartDiggingEvent` 或 `PlayerBlockBreakEvent` 被取消时都只发送确认。所以 `await` 返回 true 不能说明方块被破坏；执行完毕之后查询到的也只是最终状态，被破坏后又被服务端恢复的方块无法通过查询发现。
- **方块更新先于确认到达**：Minestom 的 `InstanceContainer.breakBlock` 设置方块时立即向区块的观察者发送 Block Update，`PlayerActionListener` 在这之后才发送确认；原版服务端在世界 tick 中发送方块变化，在之后的连接 tick 中发送确认（原版服务端行为）。所以这次破坏引起的 `BlockChanged` 都在动作结束之前投递。
- 判断方块是否被破坏使用 `Bot.breakBlock`（11.8）；掉落了什么由用户通过 observer 判断。
- **`DigSpeed`**：按 wiki Breaking 页的公式计算，输入为：
  - 手持物品的 `tool` 组件（`Items.component(手持物品, TOOL)`，MCProtocolLib 的 `ToolData`）：规则中的方块集合为标签时在 `TagTable` 中判断，为编号列表时按方块编号判断；
  - `minecraft:mining_efficiency`、`minecraft:block_break_speed`、`minecraft:submerged_mining_speed` 属性（`AttributeType.Builtin`，以 `ToDoubleFunction<AttributeType>` 传入）；
  - 急迫、潮涌能量、挖掘疲劳效果（MCProtocolLib 的 `Effect`，以 `ToIntFunction<Effect>` 传入等级；挖掘疲劳使用 wiki 给出的固定系数 0.3、0.09、0.0027、0.00081）；
  - 是否着地、眼睛是否在水中；
  - 方块硬度、`requiresTool`。
- **`can_break` 的匹配**（`BlockPredicates.test(谓词, 方块, 方块实体的 NBT, 位置, TagTable)`，原版 `AdventureModePredicate` 与 `BlockPredicate`）：任意一个谓词匹配即为匹配，没有谓词时不匹配。一个谓词的各项都满足时匹配：
  - 方块集合：为标签时按服务端下发的 `TagTable` 判断，为编号列表时按方块编号判断；
  - 属性（原版 `StatePropertiesPredicate`）：方块没有这个属性、给出的值不是它的取值时不匹配；精确值按字符串比较；范围按取值的顺序比较：布尔值 false 小于 true，其余按原版取值的顺序（整数从小到大，枚举按声明顺序），取值的顺序由 `Block.possibleStates()` 中第一次出现的顺序得出并按方块缓存；
  - NBT：用 `NbtCompare` 与方块实体的 NBT 做原版 `NbtUtils.compareNbt` 的部分匹配（复合标签按键逐个递归比较；非空列表要求每个元素都有匹配的元素；空列表只匹配空列表；其余值要求类型与值都相等）。原版与 `BlockEntity.saveWithFullMetadata` 的结果比较，所以比较之前在方块实体的 NBT 中加入 `id`（方块实体类型的名称）、`x`、`y`、`z`。该位置没有方块实体时不匹配；
  - 组件条件（exact components 与 component predicates，待办 T4）：本库不支持。有方块实体时遇到不为空的组件条件抛出 `UnsupportedOperationException`，bot 结束（代码标注 `TODO(T4)`）；没有方块实体时与原版相同，直接不匹配。

**其它手部动作**：

- **先看向目标**：`useItemOn`、`placeBlock`、`attack`、`interact` 的数据包不带朝向，服务端按它最后收到的玩家朝向处理（放置有方向的方块、攻击的击退方向）。手部步骤在移动步骤之前（10，原版 `handleKeybinds` 在 `tickEntities` 之前），同一 tick 中手部数据包先于移动包发出。所以这四个动作开始的 tick 只确定要看向的朝向（开始时计算一次），由这个 tick 的移动步骤设置并发出；之后每个 tick 的手部步骤检查本地玩家的朝向，等于这个朝向时（此时它已经由之前的移动包发出）才发送数据包，通常是开始之后的下一个 tick。本地玩家的位置为 Minestom 的 `Pos`，它把偏航角规范到 (-180, 180]、俯仰角限制到 [-90, 90]，所以比较之前对要看向的朝向做同样的规范（`HandController.aimed`）：目标正好在正北时 `Rotation.lookAt` 的偏航角为 -180，位置中为 180。挖掘不等待：服务端处理 Player Action 时不使用玩家朝向，挖掘在开始的 tick 就发送「Started digging」（见上面的 `attemptBreakBlock`）。
- **区块未加载**：手部动作读取的方块所在区块未加载时，读取抛出 `ChunkNotLoadedException`。`HandController` 在调用当前动作的开始与每 tick 步骤时捕获这个异常，以 `CHUNK_NOT_LOADED` 取消这个动作，取消时照常执行它的清理（与移动步骤处理 `Kinematics` 抛出的同一个异常的方式相同，9.1）。读取方块的是 `attemptBreakBlock`（目标与眼睛所在的方块）与 `useItemOn`、`placeBlock`（计算 Inside block 时读取目标方块）。
- **`placeBlock(Point against, Direction face, Hand hand)`**：等同于 `useItemOn`，光标取该面的中心（面的法向量取自 `Directions.normal`，原版 `Direction.getUnitVec3`）。
- **`useItemOn(Point pos, Direction face, Hand hand, Point cursor)`**：
  - 先看向光标位置（见上）；
  - 目标方块在世界边框之外时不发送任何数据包（也不挥手），直接执行完毕（原版 `MultiPlayerGameMode.useItemOn` 返回 FAIL）；
  - 目标所在区块未加载时不发送任何数据包，以 `CHUNK_NOT_LOADED` 取消（见上面的区块未加载）；
  - 发送 Use Item On：`ServerboundUseItemOnPacket(方块位置, 面, 手, 光标 x, 光标 y, 光标 z, Inside block, World Border Hit, 序号)`，手是第三个参数，World Border Hit 为 false（wiki Use Item On）。Inside block 按原版准星选取方块时的 `VoxelShape.clip` 计算：射线从眼睛出发，沿当前朝向的视线（原版 `Entity.calculateViewVector`），长度为 `block_interaction_range` 与 `entity_interaction_range` 属性中较大的一个（原版 `LocalPlayer.pick`），射线上前进全长千分之一的点在目标方块的轮廓形状之内时为 true。wiki 写的是眼睛在方块的碰撞箱内，原版选取方块使用轮廓形状（`ClipContext.Block.OUTLINE`），本库按原版；
  - 然后发送 Swing Arm（`ServerboundSwingPacket(手)`；本库不预测交互结果，所以总是发送）；
  - 收到确认即执行完毕。
- **`useItem(Hand hand[, int holdTicks])`**：
  - 发送 Use Item（`ServerboundUseItemPacket(手, 序号, 偏航角, 俯仰角)`，当前朝向）；
  - 未指定 `holdTicks` 时，收到确认即执行完毕；
  - 指定 `holdTicks` 时，收到确认后再保持使用状态 `holdTicks` 个 tick，然后发送 Player Action「Shoot arrow / finish eating」（`PlayerAction.RELEASE_USE_ITEM`，位置 0, 0, 0，面 `DOWN`，序号 0），数据包写出即执行完毕。吃完、拉满弓这类结果由用户通过 observer 判断。
  - **正在使用物品**（`HandAction.usingItem()`）：指定了 `holdTicks` 时，从发送 Use Item 到发送「Shoot arrow / finish eating」（包括被取消时的清理）之间，本地玩家正在使用发送 Use Item 时这只手上的物品：移动按 9.1 减速，不开始疾跑（8.7）。`HandController.itemInUse()` 给出这个物品的 `use_effects` 组件，没有这个组件时为原版的默认值 `HandController.DEFAULT_USE_EFFECTS`（`(false, true, 0.2F)`）；空手使用时同样如此。原版只对能够使用的物品（食物、弓等）进入使用状态，本库不预测物品能否使用。
- **`attack(int entityId)`**：先看向目标实体包围盒中心（见上），发送 Attack（`ServerboundAttackPacket`）与 Swing Arm，数据包写出即执行完毕。发送时为旁观模式的，不攻击、不挥手，只发送带这个实体编号的 Spectator Action（`OptionalInt.of(实体编号)`；原版 `startAttack` 在旁观模式下点击实体时调用 `MultiPlayerGameMode.spectate`）。
  - **攻击距离**（`AttackReach`）：发送之前按原版 26.2 客户端检查。原版只攻击准星选中的实体（`LocalPlayer.raycastHitResult`），`startAttack` 再按主手物品的 `attack_range` 组件检查选中位置的距离。本库只对目标实体计算，视线为发送时的朝向：
    1. 当前物品有 `attack_range` 组件时先沿它的距离选取（`AttackRange.getClosesetHit`、`ProjectileUtil.getHitEntitiesAlong`）：线段从 `眼睛 + 视线 × 最小距离` 到 `眼睛 + 视线 × (最大距离 + max(0, 速度 · 视线))`（创造模式取 creative 的距离），起点在包围盒内时选中起点，否则取与包围盒的交点，都没有时线段与按 `hitbox_margin` 扩大的包围盒相交，就从这个交点向包围盒中心取与包围盒的交点。手部队列一次运行一个动作，攻击时不在使用物品，当前物品就是主手物品（`getActiveItem`；旁观模式为空）；
    2. 没有这个组件或者没有选中时（`LocalPlayer.pick`、`ProjectileUtil.getEntityHitResult`）：线段从眼睛沿视线到方块与实体交互距离中较大的一个，目标包围盒按选取半径（`getPickRadius`：可选取的 `Projectile` 为 1，其余为 0）扩大；眼睛在框内时选中线段与框的交点（没有时为眼睛），身体槽位有物品的硫磺立方不能从内部选取；否则取交点，目标与玩家骑乘同一个最下层载具时不选中；选中位置到眼睛的距离平方小于实体交互距离的平方才算选中（`filterHitResult`）；
    3. 非旁观模式下主手物品有 `attack_range` 组件时，选中位置到眼睛的距离还要在 `[最小距离 − hitbox_margin, 最大距离 + hitbox_margin]` 之内（`AttackRange.isInRange`）。

    `Projectile.isPickable` 为实体类型属于服务端标签 `minecraft:redirectable_projectile`，潜影贝子弹总是可选取，箭、光灵箭、三叉戟还要求没有插在方块上（元数据 `IN_GROUND`）。线段与包围盒的交点移植原版 `AABB.clip`（Minestom 的 `RayUtils` 包内可见，并且把结果乘以 0.99999）。不判断射线上的方块与其它实体，也不判断目标能否被选取（`isPickable`）。
  - 不在攻击距离内时按原版 `startAttack` 没有选中实体的处理：只发送 Swing Arm（旁观模式下发送不带实体编号的 Spectator Action，原版 `spectatorNoAction`），动作被取消，取消原因为超出攻击距离（`OUT_OF_RANGE`）。
- **`interact(int entityId, Hand hand)`**：
  - 先看向目标（见上）；
  - 发送 Interact（`ServerboundInteractPacket(实体编号, 手, 目标偏移, Sneak Key Pressed)`）：目标偏移取包围盒中心（相对实体位置为 `(0, 高度 / 2, 0)`）；Sneak Key Pressed 取当前移动动作是否为 `SNEAK`；
  - 数据包写出即执行完毕。
- `attack` 与 `interact` 开始时，或者到了发送数据包的 tick，目标实体不在模型中，动作被取消，取消原因为实体不存在。

wiki 记录了服务端对交互（4 格）与挖掘（6 格）的距离检查。本库不在客户端预先检查这两种距离，结果由服务端决定。

#### 8.6 一次性请求

| 方法 | 发送 |
|---|---|
| `chat(String)` / `command(String)` | 见 5.3 |
| `selectHotbar(int)` | Set Held Item（`ServerboundSetCarriedItemPacket`）；同时更新本地的快捷栏槽位与已经发送的槽位，产生 `HeldSlotChanged`（原版客户端行为） |
| `swapHands()` | Player Action「Swap item in hand」（`PlayerAction.SWAP_HANDS`，位置 0, 0, 0，面 `DOWN`，序号 0） |
| `drop(boolean wholeStack)` | Player Action「Drop item stack」或「Drop item」（`DROP_ITEM_STACK`、`DROP_ITEM`，同上） |
| `respawn()` | Client Status「Perform respawn」（`ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN)`）；是否重生由 observer 观察 `WorldEntered`（`cause` 为 `RESPAWN`） |
| `inventory().click(slot, ContainerActionType, ContainerAction)`，以及 `leftClick`、`rightClick`、`shiftClick`、`swapWithHotbar`、`dropSlot` | 见 7.6 |
| `inventory().clickButton(int)` / `inventory().close()` | Click Container Button / Close Container（关闭玩家物品栏时窗口编号为 0）；`close` 同时在本地关闭打开的窗口，产生 `WindowClosed`（原版客户端行为） |
| `send(MinecraftPacket)` | 任意发往服务端的数据包，用于本库没有封装的操作；在调用线程上用 `PacketDirection.requireServerbound` 检查，不是发往服务端的数据包时抛出 `IllegalArgumentException` |

一次性请求在数据包交给连接（TCP 连接在写进套接字之后）时执行完毕。

**快捷栏同步**：手部控制器记录已经发送的快捷栏槽位（原版 `MultiPlayerGameMode.carriedIndex`），每个 tick 开始时与本地的快捷栏槽位比较，不同时发送 Set Held Item（原版 `ensureHasSentCarriedItem`，在 `MultiPlayerGameMode.tick` 中、Player Loaded 之前）。所以服务端用 Set Held Item 改变快捷栏槽位之后，bot 在下一 tick 发回同一个槽位；Respawn 之后快捷栏槽位为 0 时发送 Set Held Item(0)（7.5）。已经发送的槽位在 Login (play) 时归零，Respawn 时不变。

#### 8.7 由状态派生的协议数据包

公开接口不提供按键输入，以下数据包由移动状态派生，每个 tick 在有变化时发送：

- **Player Input**：
  - 前、后、左、右按「期望移动方向相对当前朝向的分量符号」确定；
  - 跳跃为本 tick 是否起跳；
  - 潜行为 `MovementMode.SNEAK`；
  - 疾跑为疾跑键：`MovementMode.SPRINT` 并且期望移动方向不为零（原版 `keyPresses.sprint` 是按键的状态，与是否真的在疾跑无关）。

  `ServerboundPlayerInputPacket` 没有单独的标记字段，所以上一次发送的 Player Input 保存为数据包本身，初始为全部按键都为 false 的常量（原版新玩家的 `lastSentInput`），用 `equals` 比较是否有变化。wiki Player Input：按下或松开按键时发送；服务端依据它判断潜行。按键在本 tick 的移动模拟之前确定，数据包在移动模拟之后、移动包之前发送（原版 `LocalPlayer.tick` 在 `super.tick()` 之后发送）；骑乘时在 Set Player Rotation 之前发送。
- **本地的疾跑状态**（原版 `LocalPlayer.aiStep`，在移动模拟之前，先判断开始、再判断停止，所以顶住墙壁时开始之后在同一个 tick 里立即停止）：
  - 开始（原版 `canStartSprinting`）：按住疾跑键，有向前的输入（期望移动方向在朝向前方的分量大于 0.001），可以疾跑，并且没有在使用不允许疾跑的物品（物品 `use_effects` 组件的 `can_sprint`，没有这个组件时取原版默认值，不允许；正在使用物品的定义见 8.5 的 `useItem`）；
  - 停止（原版 `shouldStopRunSprinting`）：不能疾跑，或者没有向前的输入，或者上一 tick 顶住墙壁并且不是擦过墙壁（9.1 第 8 步）；
  - 可以疾跑（原版 `isSprintingPossible`）：没有失明效果，并且骑乘时载具可以疾跑并且由本地玩家控制（9.6），不骑乘时饥饿值大于 6 或者允许飞行；
  - 本库没有单独的疾跑键，移动方式不是 `SPRINT`（包括没有移动动作）时也停止疾跑：原版松开疾跑键不停止疾跑，本库的 `MovementMode` 表示这个动作要求的移动方式。潜行与疾跑是不同的移动方式，所以原版「蹲下时不开始疾跑」的条件自然成立。
- **Player Command**「Start sprinting」/「Stop sprinting」（`ServerboundPlayerCommandPacket(实体编号, PlayerState.START_SPRINTING / STOP_SPRINTING)`）：本地的疾跑状态与上次告知服务端的不同时发送（wiki：疾跑仍由 Player Command 告知；原版 `sendIsSprintingIfNeeded`）。骑乘时不控制载具则不发送，下马之后的第一次移动包之前再发送（9.6）。

### 9. 移动模拟与暂停

#### 9.1 每 tick 的移动计算

`Kinematics` 按原版 `LocalPlayer.aiStep`、`LivingEntity.aiStep`、`Player.travel` 与 `LivingEntity.travelInAir` 的顺序计算一个 tick（原版客户端行为），其中的移动与碰撞交给 Minestom 公开的 `PhysicsUtils.simulateMovement(position, velocity, boundingBox, worldBorder, world, aerodynamics, noGravity, hasPhysics, onGround, flying, previousResult)`：

1. **飞行时的竖直输入**：正在飞行时，跳跃键（本 tick 的期望起跳）使竖直速度加 `3 × 飞行速度`，潜行键（`MovementMode.SNEAK`）使它减同样的值。
2. **插值与速度归零**：有正在进行的插值时（`Interpolation`，由指向本地玩家自己的 Synchronize Vehicle Position 开始，7.4），先插值一步（原版 `LivingEntity.aiStep` 中的 `InterpolationHandler.interpolate`）：从开始插值或者上一步插值之后本地的位移加到目标位置上，加上之后玩家的包围盒不与方块的碰撞箱相交（`ClientWorld.collidesWithBlocks`，与 7.5 中 `resetPos` 的检查相同，未加载区块中没有方块）、也不与世界边框碰撞（`WorldBorderState.collides`，原版 `noBorderCollision`）时才加（原版 `level.noCollision`；与实体的碰撞不覆盖，9.5），同一期间本地的转向加到目标朝向上；然后位置与俯仰角向目标前进剩余步数分之一，偏航角沿较近的方向前进（原版 `Mth.rotLerp`），朝向按原版 `Entity.setRot` 对 360 取余，俯仰角限制到 [-90, 90]。一次插值共 3 步（原版 `DEFAULT_INTERPOLATION_STEPS`），之后的计算从插值得到的位置与朝向开始。然后水平速度的平方小于 9.0E-6 时水平速度归零；竖直速度的绝对值小于 0.003 时归零。
3. **输入**：期望方向向量乘以 0.98；正在使用物品时再乘以物品 `use_effects` 组件的速度系数（没有这个组件时取原版默认值 0.2，原版 `modifyInput`；正在使用物品的定义见 8.5 的 `useItem`）；蹲下（潜行并且不在飞行，原版 `crouching`）时再乘以 `minecraft:sneaking_speed` 属性。
4. **起跳**（原版 `jumpFromGround`）：期望起跳、着地、不在飞行，并且起跳延迟为 0 时起跳，之后 10 tick 内持续期望起跳也不再起跳（原版 `noJumpDelay`，不期望起跳的 tick 归零）。起跳力为 `(float) jump_strength 属性 × 起跳系数 + 0.1 × 跳跃提升等级`，以 float 计算；起跳力不大于 1.0E-5 时不起跳，否则竖直速度取起跳力与当前竖直速度中较大的一个；疾跑时再沿朝向增加 0.2 的水平速度（正弦与余弦取自 `SineTable`）。起跳系数为脚部所在方块的 `jumpFactor()`，它为 1 时取脚下方块的。
5. **水平加速**：着地时先读出脚下方块的摩擦 `f = clamp(1 − (1 − friction()) × friction_modifier 属性, 0, 1)`（移动之前读取），不着地时 `f = 1`。加速度为输入乘以：着地时 `movement_speed 属性`（`f` 大于 0.6 时再乘以 `0.21600002 / f³`）；空中飞行时为能力中的飞行速度，疾跑时乘以 2；空中不飞行时为 0.02，疾跑时为 0.026（原版 `getFrictionInfluencedSpeed`、`Player.getFlyingSpeed`）。
6. **移动与碰撞**：调用 `PhysicsUtils.simulateMovement`，`aerodynamics` 为 `(0, f × 空气阻力, 1)`，`onGround`、`flying`、`noGravity` 都传 false。这样它只按速度移动、处理与方块碰撞箱及世界边框的碰撞（旁观模式 `hasPhysics` 为 false，不碰撞），水平速度乘以原版的阻力 `f × 空气阻力`，返回的竖直速度为碰撞之后的竖直速度。`onGround` 传 false 的原因：`PhysicsUtils` 按上一次的着地标记与移动之后的位置读取方块摩擦，原版用移动之前读出的摩擦。空气阻力为 `clamp(1 − (1 − 0.91) × air_drag_modifier 属性, 0, 1)`。
7. **竖直速度**（原版 `travelInAir`）：有飘浮效果时为 `v + (0.05 × 等级 − v) × 0.2`，否则为 `v − 有效重力`，`v` 为碰撞之后的竖直速度；再乘以 `clamp(1 − (1 − 0.98) × air_drag_modifier 属性, 0, 1)`。有效重力：元数据 `HAS_NO_GRAVITY` 为真时为 0，否则为 gravity 属性；`v` 不大于 0 并且有缓降效果时不超过 0.01。正在飞行时竖直速度改为这个 tick 移动之前的竖直速度乘以 0.6（原版 `Player.travel`）。
8. **着地与支撑方块**：着地标记与顶住墙壁标记取碰撞结果；顶住墙壁时，期望的水平移动与实际移动的夹角小于 8 度（0.13962634 弧度）为擦过墙壁（原版 `minorHorizontalCollision`，疾跑判断使用，8.7）。着地时按原版 `checkSupportingBlock` 查找支撑方块：包围盒底面以下碰撞箱与它相交的方块中，中心离玩家位置最近的一个（距离相同时取 y、z、x 依次比较较大的一个）；没有找到、并且上一次不是「着地但没有支撑方块」时，按这次的水平移动退回去再找一次。原版的检查深度为 1.0E-6，Minestom 的碰撞让着地的实体停在方块顶面之上最多约 1.0E-5 处，`Shape.intersectBox` 又要求重叠超过 1.0E-6，所以本库的检查深度为 1.0E-4。
9. **方块速度系数**（原版 `Entity.move` 的最后，按移动之后的位置）：脚部所在方块的 `speedFactor()` 不为 1、或者它是水或气泡柱时取它，否则取脚下方块的；再按 `minecraft:movement_efficiency` 属性向 1 插值，水平速度乘以这个系数。

「脚下方块」（原版 `getBlockPosBelowThatAffectsMyMovement`）：有支撑方块时取它所在的列，脚部向下 0.500001 格的高度；支撑方块为墙或栅栏门（按 `TagTable` 中的 `minecraft:walls`、`minecraft:fence_gates` 判断）时取支撑方块本身；没有支撑方块时取脚部所在的列。玩家死亡期间不执行模拟。

疾跑状态由移动控制器在模拟之前按 8.7 的条件决定；疾跑状态改变时在本地属性上加入或移除 `minecraft:sprinting` 修饰符（`new AttributeModifier(SPRINTING_ID, 0.3, ModifierOperation.ADD_MULTIPLIED_TOTAL)`），之后服务端的 Update Attributes 按 7.5 替换全部修饰符。

**停止飞行**：模拟之后，旁观模式以外着地并且正在飞行时停止飞行，发送 Player Abilities（`ServerboundPlayerAbilitiesPacket(false)`，它只有「正在飞行」一项；原版 `LocalPlayer.aiStep` 调用 `onUpdateAbilities`）。

`PhysicsUtils` 计算出的结果在两处与原版有很小的差别：它把绝对值小于 1.0E-6 的速度分量归零；位置完全没有变化时它把水平速度归零。前者小于原版下一 tick 的归零阈值，后者只在各方向都被挡住或者速度为零时发生，这时原版的水平速度同样为零。

这个顺序可以复现 wiki 记载的数值：

| 场景 | 数值 |
|---|---|
| 平地行走 | 4.317 m/s |
| 平地疾跑 | 5.612 m/s |
| 平地潜行 | 1.3 m/s |
| 跳跃高度 | 1.2522 格 |
| 跳跃提升 I | 1.8361 格 |
| 跳跃提升 II | 2.5168 格 |
| 飞行 | 10.89 m/s |
| 疾跑飞行 | 21.78 m/s |

本库按期望方向向量直接施加加速度，任何方向的速度都等于向前行走的速度，wiki 中的斜向行走数值（4.405 m/s）不适用。

**区块未加载**：本 tick 的移动范围涉及未加载区块时不执行模拟（玩家的任何状态都不改变，包括起跳延迟与插值的进度：它们在确认区块已加载之后才写回），执行中的移动动作被取消，取消原因为区块未加载。移动范围为玩家包围盒（第 2 步插值之后的位置）的水平范围按第 6 步交给 `PhysicsUtils` 的速度（加速与起跳之后）扩展：`PhysicsUtils` 只读取这个范围中的方块，移动之前读取的脚下方块（玩家所在的列或者支撑方块所在的列）与移动之后读取的脚下方块、方块速度系数也都在其中。第 8 步查找支撑方块时按原版 `BlockCollisions` 向包围盒四周多检查一格（碰撞形状可以超出所在的格子，例如伸出的活塞头），其中未加载区块的格子按原版没有方块，不读取。

#### 9.2 服务端施加的位置与速度

- **Synchronize Player Position**：按 5.2 处理。传送的两个应答由协议状态机直接发出，不计入 9.3 的上一次发送记录（原版 `handleMovePlayer` 直接发送的 PosRot 不更新 `xLast`、`yRotLast`、`lastOnGround`、`positionReminder` 等字段），着地与顶住墙壁标记也不变；下一次移动包照常与上一次发送记录比较。
- **应用到本地玩家的 Synchronize Vehicle Position**（本地玩家乘坐过的实体被移除之后，7.4）：与 Synchronize Player Position 相同地计算与处理，只发送 Set Player Position and Rotation。
- **指向本地玩家自己的 Synchronize Vehicle Position**（7.4）：速度立即设置；位置与朝向在之后的移动计算中插值（9.1 第 2 步），不插值的情况下立即设置；然后设置着地标记。不发送任何数据包，9.3 的上一次发送记录不变，新的位置与朝向由之后的移动包按 9.3 照常发出。
- **自己实体编号的 Set Entity Velocity**：替换当前速度。
- **Player Info Update 把自己改成旁观模式**：玩家列表中自己原来的游戏模式与新的不同、并且新的是旁观模式时，竖直速度清零（原版 `applyPlayerInfoUpdate` 调用 `LocalPlayer.onGameModeChanged`）。原来的游戏模式取更新之前的条目；同一数据包中刚刚由 ADD_PLAYER 加入的条目按原版 `PlayerInfo` 的初始值生存模式计算。Login (play)、Respawn、Game Event 3 改变游戏模式时不清零（原版只在这里调用 `onGameModeChanged`）。
- **Explosion 中的玩家击退**：加到当前速度上。
- 服务端施加位置后，下一 tick 的 `previousResult` 清空。

#### 9.3 移动包

`MovementSender` 在每次模拟之后按 wiki 发送：

- 位置变化发送位置（`ServerboundMovePlayerPosPacket`），朝向变化发送朝向（`ServerboundMovePlayerRotPacket`），两者都变化时发送 Set Player Position and Rotation（`ServerboundMovePlayerPosRotPacket`）；
- 只有着地或顶住墙壁标记变化时发送 Set Player Movement Flags（`ServerboundMovePlayerStatusOnlyPacket`）；
- 四种数据包的构造方法中，着地与顶住墙壁两个标记排在最前；
- 静止时每 20 tick 至少发送一次位置（wiki Set Player Movement Flags）。
- 判断位置变化的阈值为位移平方大于 `(2.0E-4)²`（原版客户端行为）。
- 顶住墙壁标记取 `PhysicsResult` 的 `collisionX` 或 `collisionZ`。

#### 9.4 movement suspension

**接口**：

- `Bot.suspendMovement()` / `Bot.resumeMovement()` 控制单个 bot；
- `BotRuntime.suspendMovement()` / `BotRuntime.resumeMovement()` 控制全部 bot；
- 任意一级处于暂停状态，该 bot 的移动就暂停。
- 暂停与恢复是请求，在下一轮调度中生效；某个 bot 的实际暂停状态改变时产生 `MovementSuspensionChanged` 观察记录。

**暂停期间停止**（只涉及速度与位置的计算）：

- 9.1 的整个计算都不执行（包括第 2 步的插值，指向本地玩家自己的 Synchronize Vehicle Position 开始的插值等到恢复之后才进行），重力也不生效，位置保持不变；
- 移动队列中的 `moveTo`、`moveForward`、`jumpTo` 不推进，「连续若干 tick 没有进展」的计数也暂停。它们的期望移动方向与 `MovementMode` 保持暂停开始时的值，排在它们后面的动作随之等待。
- 暂停期间在移动队列中轮到的 `moveTo`、`moveForward`、`jumpTo` 不开始：不寻路，不接管朝向，也不清除 `rotate`、`lookAt` 设置的朝向（8.3）；它留在队首，恢复之后才开始，排在它后面的动作（包括 `rotate`、`lookAt`）随之等待。

**暂停期间照常进行**：

- 数据包处理与 5.2 的全部自动应答。服务端传送照常生效：Confirm Teleportation 与紧随的 Set Player Position and Rotation 属于协议要求的应答，照常发送。
- 朝向：`RotationArbiter` 照常按 8.3 决定朝向；移动队列中轮到的 `rotate`、`lookAt` 照常执行。
- `MovementSender` 照常按 9.3 发送：位置不变，所以没有位置变化；朝向变化时发送 Set Player Rotation；每 20 tick 一次的位置照常发送。
- 由移动状态派生的 Player Input 与疾跑 Player Command 照常按 8.7 在变化时发送。暂停期间期望移动方向不变，朝向改变时，前、后、左、右按新朝向重新计算。
- 手部交互队列与一次性请求。
- Player Loaded 与 Client Tick End。

**速度**：暂停期间收到的速度变化（9.2）累积在当前速度上，恢复后参与模拟。

**原版服务端**：玩家暂停在空中时，原版服务端的悬空检查会在 80 tick 后以 `multiplayer.disconnect.flying` 踢出玩家（wiki Set Player Position），允许飞行的情况除外。Minestom 没有这项检查。

#### 9.5 不覆盖的移动

`PhysicsUtils` 是为服务端实体编写的，以下原版玩家移动行为不覆盖：

- 台阶抬升（原版玩家走上 0.6 格以内的台阶不需要跳跃）：半砖、楼梯、地毯等任何高于脚部的表面都要跳上去。`moveTo` 按节点的站立高度起跳（8.4）；`moveForward` 不跳跃，遇到台阶被阻挡
- 潜行时在方块边缘停住
- 头顶空间不足时被迫蹲下或者爬行（原版的 `crouching` 与爬行姿态只按潜行键与飞行状态决定）
- 水与熔岩中的阻力、浮力与游泳，以及浅水中不能开始疾跑的限制
- 区块未加载时原版改为以 0.1 的速度下落（本库按 9.1 不执行模拟并取消移动动作）
- 落到地面与撞墙时的反弹（`minecraft:bounciness` 属性、粘液块与床）
- 连续两次按下跳跃键切换飞行状态（原版 `jumpTriggerTime`）
- 攀爬梯子和藤蔓
- 蜘蛛网与细雪的减速
- 鞘翅滑翔
- 与实体的碰撞
- 控制载具时载具自身的移动模拟（9.6 中 Move Vehicle 发送的是模型中服务端给出的载具位置）

连接原版服务端时，这些差异可能触发它的「移动错误」检查。按已确定的决策，这项检查的优先级低。

#### 9.6 骑乘

本地玩家乘坐模型中的一个实体时（Set Passengers 中包含自己，7.4），移动步骤按原版 `LocalPlayer.tick` 中乘客的分支执行。原版的乘客只在载具 `tick()` 之后由 `tickPassenger` 调用 `rideTick()`，乘坐关系从最下层的载具开始逐层执行（`tickNonPassenger`），这要求最下层的载具所在区块已加载，中间的载具（玩家除外）所在区块也已加载：任何一个未加载时，本地玩家整个 tick 不执行，移动步骤什么都不做，除 Client Tick End 以外不发送数据包（手部交互与快捷栏同步照常）。

**每 tick**：

1. 移动队列照常推进，`RotationArbiter` 照常决定朝向（8.3）。
2. **疾跑**：只有载具可以疾跑（`canSprint()`，载具中只有骆驼）并且由本地玩家控制时才保持或开始疾跑，否则本地停止疾跑（移除疾跑修饰符）。
3. Player Input 照常在变化时发送（8.7）。
4. 9.1 的计算只执行其中的插值：原版 `rideTick` 在每次乘客 tick 开始时把速度清零，乘客的 tick 中 `LivingEntity.aiStep` 照常插值一步（9.1 第 2 步），位置随后被 `positionRider` 覆盖。本库把速度设为零，着地与顶住墙壁设为 false（没有移动输入时原版的两个标记也都为 false；有移动输入时本库同样取 false），有正在进行的插值时插值一步（位置随后被座位覆盖，插值得到的朝向保留），位置设为座位位置（见下文）。movement suspension 期间位置与速度不变，也不插值。位置变化时产生 `PlayerMoved`，移动动作照常按位移判断是否被阻挡。
5. 发送 Set Player Rotation（`ServerboundMovePlayerRotPacket`：着地、顶住墙壁，偏航角、俯仰角，插值时为插值之后的朝向）；不发送移动包，9.3 的上一次发送记录与 `positionReminder` 不变。
6. 本地玩家控制最下层的载具时（原版 `getRootVehicle().isLocalInstanceAuthoritative()`），再发送 Move Vehicle（`ServerboundMoveVehiclePacket`：载具在模型中的位置、偏航角、俯仰角、着地），然后在疾跑状态改变时发送 Player Command。不控制时不发送疾跑的 Player Command，下马之后的第一次移动包之前再发送（8.7）。

**控制载具**（原版各实体的 `getControllingPassenger`）：前提都是本地玩家为载具的第一个乘客。

| 载具 | 条件 |
|---|---|
| 船与竹筏（MCProtocolLib `EntityType` 的名称以 `_boat` 或 `_raft` 结尾） | 无 |
| 马、驴、骡、骷髅马、僵尸马、骆驼、骆驼尸壳、羊驼、行商羊驼、鹦鹉螺、僵尸鹦鹉螺 | 装有鞍 |
| 猪、炽足兽 | 装有鞍，并且主手或副手拿着胡萝卜钓竿、诡异菌钓竿（按 `ItemData.id` 得到的物品编号比较） |
| 快乐恶魂 | 佩戴挽具（身体槽位），并且没有保持静止（元数据 `HAPPY_GHAST_STAYS_STILL`） |
| 其余实体（包括矿车） | 不能控制 |

「装有鞍」「佩戴挽具」按原版 `Mob.hasValidEquippableItemForSlot`：该槽位有物品，物品的 `equippable` 组件（`Items.component(物品, EQUIPPABLE)`，6.1）对应这个槽位并且允许这个实体类型装备（`allowedEntities()` 为标签时按 `TagTable` 判断，为编号列表时按 `EntityType` 的序号判断，没有时允许全部），实体可以使用这个槽位（存活：元数据 `LIVING_HEALTH` 大于 0；不是幼年：`AGEABLE_IS_BABY`；马类还要已被驯服：`HORSE_IS_TAME`，鹦鹉螺为 `TAMEABLE_IS_TAMED`；羊驼没有限制）。

**座位位置**（`RidingSeat`，原版 `Entity.positionRider`）：载具位置 + 载具的乘客挂点 − 玩家的载具挂点。

- 默认的乘客挂点：按本地玩家在载具乘客列表中的序号取（超出时取最后一个，原版 `EntityAttachments.getClamped`），随载具偏航角旋转（绕 y 轴旋转 −偏航角，使用 `SineTable`）。挂点取 `EntityTypeData.passengerAttachments`（6.1），没有时为实体高度处。活体载具的尺寸与挂点乘以 `minecraft:scale` 属性；幼年时使用原版各实体类的幼年尺寸（马的挂点为高度 − 0.125 再乘 0.7；骷髅马与僵尸马为高度 − 0.25 再乘 0.7；驴与骡为 (0, 高度 + 0.03125, −0.3125) 乘 0.5；羊驼为 (0, 羊驼高度 − 0.25, −0.3) 乘 0.5；猪为 0.5；炽足兽为 0.65625；鹦鹉螺为 0.5；鸡为 0.375；其余幼年实体为成年挂点乘年龄缩放 0.5，快乐恶魂为 0.2375）。本库识别幼年状态的实体类型为上面这些载具，以及骆驼、骆驼尸壳、史莱姆、岩浆怪；其余实体按成年处理。
- 船与竹筏（原版 `AbstractBoat`）：高度为船与运输船高度的三分之一、竹筏高度的 0.8888889；只有一个乘客时向前偏移 0（带箱子的为 0.15），多个乘客时第一个为 0.2、其余为 −0.6；随船的偏航角旋转。
- 史莱姆与岩浆怪（原版 `AbstractCubeMob`）：尺寸按大小（元数据 `CUBE_SIZE`）与 scale 属性缩放，挂点在顶面向下 `0.015625 × 大小 × scale` 处，不旋转。
- 骆驼与骆驼尸壳（原版 `Camel`）：高度随坐下与站起的过渡动画变化（`getBodyAnchorAnimationYOffset`：由元数据 `CAMEL_LAST_POSE_CHANGE_TICK` 与 World Age 得到姿态时间，坐下姿态使用坐下的尺寸），第一个乘客在前（0.5），多个乘客时其余在后（−0.7），乘以 scale 与年龄缩放（幼年 0.6），随偏航角旋转。
- 马、驴、骡、骷髅马、僵尸马（原版 `AbstractHorse`）：在默认挂点上加上人立动画的偏移 `(0, 0.15 × standAnimO × scale, −0.7 × standAnimO × scale)`，随偏航角旋转。`standAnimO` 为载具上一 tick 的人立动画进度，由每 tick 的实体步骤更新（7.4），与本地玩家是否乘坐它无关；载具在乘客之前执行，所以本 tick 的座位使用载具本 tick 开始时的进度。
- 羊驼、矿车、鸡使用默认挂点（原版的覆盖只改变乘客的身体朝向或者只对村民生效）。
- 玩家的载具挂点为 (0, 0.6, 0) 乘以玩家的 scale 属性。
- 载具位置取模型中服务端最后给出的值，不插值（7.4）。

**Minestom 的限制（M12）**：Minestom 的实体类型数据（`net.minestom:data` 的 `entity_type.json`）只有成年实体默认姿态的尺寸与挂点，没有幼年与坐下姿态的尺寸和挂点。本库内置原版 26.2 各实体类的取值（这里的座位位置，以及 7.4 的实体包围盒），代码标注 `FIXME(Minestom M12)`。

**不覆盖**：炽足兽行走时乘客的上下起伏。原版按炽足兽每 tick 的位移计算行走动画（`walkAnimation`），这个位移来自客户端对服务端位置的插值，由本地玩家控制时来自客户端对载具的移动模拟；本库不插值实体位置（7.4），也不模拟载具的移动（9.5），没有这个位移。

登上船或竹筏时偏航角设为船的偏航角（7.4）。`lookAt` 与挖掘使用的眼睛位置随座位位置变化。

### 10. tick 内的顺序

`BotTicker.tick(bot)`：

1. 推进计数：物品冷却、药水效果剩余时间；`TickState` 决定这个 tick 是否正常运行（7.8）。然后同步快捷栏（8.6：本地的快捷栏槽位与已经发送的不同时发送 Set Held Item，原版在 `MultiPlayerGameMode.tick` 中、Player Loaded 之前执行，与加载状态无关）。
2. **`PlayerLoadTracker`**（原版客户端行为，`LevelLoadTracker` 与 `ClientPacketListener.notifyPlayerLoaded`）：
   - Login (play) 与 Respawn 时开始等待，记下此刻的墙钟时间；离开世界（5.1）时停止等待。
   - 收到 Game Event 13（`GameEvent.LEVEL_CHUNKS_LOAD_START`）之后，每个 tick 检查加载条件（取自 wiki FAQ），满足以下任意一条即可：
     - 玩家所在区块已加载；
     - 玩家在维度高度范围之外；
     - 玩家为旁观模式；
     - 玩家已死亡。
   - 条件满足，或者自开始等待起已经超过 30 秒（原版 `CLIENT_WAIT_TIMEOUT_MS`，按墙钟时间计算，与 tick 速率无关）时，这个 tick 发送 Player Loaded，`Bot.loaded()` 完成，动作队列开始执行，产生 `PlayerLoaded` 观察记录。超时之后同样发送 Player Loaded。
   - 超时只在收到 Game Event 13 之后检查：没有收到 Game Event 13 时一直等待，不发送 Player Loaded，也不开始模拟玩家。开始等待之前与加载之后收到的 Game Event 13 被忽略。
   - wiki Player Loaded 记载的「60 tick 后开始模拟玩家」在原版 26.2 客户端中不存在，本库按原版客户端。
   - 加载之前（原版 `LocalPlayer.tick` 在 `hasClientLoaded` 为 false 时不执行，加载画面也让 `handleKeybinds` 不执行）第 3、5 步不执行。
3. 手部交互队列推进一步（原版 `handleKeybinds`，在本地玩家的 tick 之前）。这一步发出的数据包排在第 5 步的移动包之前，服务端处理它们时使用的是之前的 tick 发出的朝向，所以需要对准目标的 `useItemOn`、`placeBlock`、`attack`、`interact` 在开始的 tick 只确定朝向，由第 5 步发出，之后的 tick 才在这一步发送数据包（8.5）。动作结束时产生 `ActionCompleted`。
4. 其它实体执行每 tick 的实体步骤（7.4，原版 `ClientLevel.tickEntities`），与加载状态无关。原版的载具在它的乘客之前执行，所以本地玩家乘坐的载具在第 5 步之前更新。
5. 移动队列推进一步（暂停时 `moveTo`、`moveForward`、`jumpTo` 不推进，见 9.4）。不骑乘时依次：`RotationArbiter` 确定朝向；决定本地的疾跑状态（8.7）；确定 Player Input 的按键；`Kinematics` 模拟（暂停时跳过）并在着地时停止飞行（9.1，发送 Player Abilities）；发送 Player Input；疾跑状态改变时发送 Player Command；`MovementSender` 发送移动包。骑乘时按 9.6：最下层的载具或者中间的载具所在区块未加载时这一步什么都不做，否则确定朝向、派生 Player Input、有正在进行的插值时插值一步、设置座位位置、发送 Set Player Rotation，控制载具时再发送 Move Vehicle 与疾跑的 Player Command。位置变化时产生 `PlayerMoved`，动作结束时产生 `ActionCompleted`。
6. 这个 tick 正常运行时依次推进 `WorldBorderState`、World Age 加一、`WorldClocks` 按新的 World Age 推进（7.8；原版 `Minecraft.tick` 在 `level.tickEntities` 之后调用 `ClientLevel.tick`）。
7. 发送 Client Tick End（每个 tick 一次）。
8. 产生 `BotTicked`。

每一步产生的观察记录在该步完成后投递，observer 回调中读到的模型已经包含这一步的结果。

### 11. 查询与观察

动作是 bot 的输出；服务端发来的数据包和由此引起的模型变化是 bot 的输入。查询读取某一时刻的模型；观察接收一段时间内发生的变化。接收变化只有 `observe` 一种方式。

#### 11.1 查询

- `Bot.view()` 返回 `BotView`，方法返回不可变快照：
  - `PlayerSnapshot`（位置、朝向、速度、生命、饥饿、游戏模式为 MCProtocolLib 的 `GameMode`、属性为 `Map<AttributeType, Double>`、效果为 `Map<Effect, ActiveEffect>`、物品冷却为 `Map<Key, Integer>`、元数据为 `Map<Integer, EntityMetadata<?, ?>>`，命名的元数据条目用 `meta(EntityMeta<T>)` 读取）
  - `WorldStateSnapshot`（World Age、难度为 MCProtocolLib 的 `Difficulty`、默认出生点、服务端视距与模拟距离、tick 状态、世界边框、雨量与雷暴强度）；不在任何世界中时 `worldState()` 抛出 `IllegalStateException`
  - 生物群系：`biome(x, y, z)` 或 `biome(Point)` 返回 `worldgen/biome` 注册表中的名称（`Key`），受待办 T1 影响
  - 连接的信息：`serverBrand()`（没有收到时为 null）、`serverLinks()`（MCProtocolLib 的 `ServerLink`）、`featureFlags()`（`List<Key>`），以及 `clocks()`（时钟名称到含小数部分的 tick 数）
  - `Block`（Minestom 的方块对象本身不可变，不带 NBT）；方块实体的 NBT 由 `blockEntity(x, y, z)` 或 `blockEntity(Point)` 读取（`NbtMap`，没有时为 null，7.1）
  - `EntitySnapshot`（MCProtocolLib 的 `EntityType`、`ObjectData`、元数据 `Map<Integer, EntityMetadata<?, ?>>`、装备 `Map<EquipmentSlot, ItemStack>`（没有空装备）、属性 `Map<AttributeType, Double>`、效果 `Map<Effect, ActiveEffect>`；命名的元数据条目用 `meta(EntityMeta<T>)` 读取，7.4）
  - `WindowSnapshot`（窗口类型为 `@Nullable ContainerType`，窗口 0 与马、鹦鹉螺的物品栏为 null；槽位为 MCProtocolLib 的 `ItemStack`，空槽位为 null；`trades` 为 `@Nullable ClientboundMerchantOffersPacket`）
  - `@Nullable ItemStack`：光标物品，以及窗口 0 中没有槽位的身体护甲与鞍（`equipment(EquipmentSlot)`，7.6）；空时为 null
  - `ChatLine`
  - `HudSnapshot`（显示位置为 `Map<ScoreboardPosition, String>`，目标的显示类型为 `ScoreType`，玩家列表的游戏模式为 MCProtocolLib 的 `GameMode`，Boss Bar 为 `BossBarSnapshot`，7.7）
- `Bot.inspect(Function<StateReader, T>)` 在调度线程上执行函数，多项读取在同一时刻完成。`StateReader` 是只读接口，函数返回值不应包含它给出的可变对象。

#### 11.2 观察记录

`Observation` 是 sealed 接口，全部子类型为不可变 record，声明为 `Observation` 的嵌套类型。

| 类别 | 观察记录 | 产生时机 |
|---|---|---|
| 数据包 | `PacketReceived(MinecraftPacket packet)` | 数据包应用到模型之后；只有服务端发往客户端的数据包 |
| | `PacketSent(MinecraftPacket packet)` | 数据包交给连接之后；只有客户端发往服务端的数据包 |
| 世界 | `WorldEntered(WorldInfo world)` | Login (play) 或 Respawn 应用之后 |
| | `ChunkLoaded(int x, int z)`、`ChunkUnloaded(int x, int z)` | |
| | `BlockChanged(Point pos, Block old, Block now)` | Block Update、Update Section Blocks；区块加载与卸载不产生 |
| | `BlockEntityChanged(Point pos, NbtMap nbt)` | 被应用的 Block Entity Data（7.1） |
| 实体 | `EntitySpawned(EntitySnapshot entity)`、`EntityRemoved(EntitySnapshot last)` | Spawn Entity 的编号已有实体时，先产生旧实体的 `EntityRemoved`（7.4） |
| | `EntityMoved(int entityId, Point from, Point to, boolean onGround)` | 每个改变实体位置的数据包；坐标为模型算出的绝对位置 |
| | `EntityMetadataChanged(int entityId, Map<Integer, EntityMetadata<?, ?>> changed)` | Set Entity Metadata，按数据包中的顺序 |
| | `EntityEquipmentChanged(int entityId, EquipmentSlot slot, @Nullable ItemStack old, @Nullable ItemStack now)` | Set Equipment；Set Player Inventory Slot 改变本地玩家的身体护甲与鞍时（7.6），实体编号为本地玩家的。空物品为 null |
| | `EntityDamaged(int entityId, Key type, @Nullable Integer sourceId, @Nullable Integer directSourceId, @Nullable Point sourcePos)` | Damage Event。`type` 为伤害类型在 `minecraft:damage_type` 注册表中的名称；MCProtocolLib 以 -1 表示没有来源实体，这时为 null。伤害类型的编号不在注册表中时 bot 结束，与是否有 observer 订阅无关（原版解码时出错） |
| | `EntityStatusReceived(int entityId, EntityEvent event)` | Entity Event（7.4） |
| | `ItemPickedUp(int collectedId, int collectorId, int count)` | Pickup Item |
| 本地玩家 | `PlayerLoaded` | 见 10 |
| | `PlayerMoved(Pos from, Pos to, boolean onGround)` | 自身模拟或者骑乘的座位（9.6）使位置变化的 tick |
| | `PlayerTeleported(Pos from, Pos to)` | Synchronize Player Position；应用到本地玩家的 Synchronize Vehicle Position（7.4：编号是被移除的载具的；或者是本地玩家自己的并且立即设置位置，插值时不产生） |
| | `HealthChanged(float oldHealth, float health, int food, float saturation)` | Set Health |
| | `PlayerDied(Component message)` | Combat Death |
| | `GameModeChanged(GameMode old, GameMode now)` | |
| | `EffectChanged(Effect effect, @Nullable ActiveEffect now, long tick)` | Entity Effect、Remove Entity Effect（目标为自己）；移除时 `now` 为 null；`tick` 为收到时 bot 已经执行的 tick 数 |
| | `ExperienceChanged(float progress, int level, int total)` | Set Experience |
| 物品栏 | `SlotChanged(int windowId, int slot, @Nullable ItemStack old, @Nullable ItemStack now)` | 包括 7.6 中同步到窗口 0 的修改；空槽位为 null |
| | `CursorChanged(@Nullable ItemStack old, @Nullable ItemStack now)` | 当前窗口的光标改变时（7.6），包括打开与关闭窗口使当前窗口改变；这时它在 `WindowOpened`、`WindowClosed` 之前 |
| | `WindowOpened(int windowId, @Nullable ContainerType type, Component title)`、`WindowClosed(int windowId)` | 马与鹦鹉螺的物品栏没有对应的 `ContainerType`，`type` 为 null；`WindowClosed` 也在 `inventory().close()` 本地关闭窗口时产生 |
| | `HeldSlotChanged(int old, int now)` | Set Held Item（两个方向） |
| 聊天与界面 | `ChatReceived(ChatLine line)` | Player Chat Message、Disguised Chat Message、不带 overlay 的 System Chat Message；完全过滤的 Player Chat Message 不产生（7.7） |
| | `ActionBarShown(Component text)` | Set Action Bar Text、带 overlay 的 System Chat Message |
| | `TitleShown(@Nullable Component title, @Nullable Component subtitle)` | |
| | `BossBarChanged(UUID id, @Nullable BossBarSnapshot now)` | 移除时 `now` 为 null；移除没有添加过的 Boss Bar 不产生 |
| | `ScoreChanged(String objective, String holder, @Nullable Integer score)` | 移除时 `score` 为 null |
| bot 自身 | `ActionCompleted(Action action, @Nullable Throwable error)` | 动作结束时（见 8.1）；执行完毕时 `error` 为 null，其余情况为 `future()` 完成时的异常 |
| | `MovementSuspensionChanged(boolean suspended)` | 见 9.4 |
| | `BotTicked(long tick)` | 每次 tick 结束 |
| | `StateUpdated` | 一轮调度中该 bot 的状态有变化时一条（见 3.2）：应用了入站数据包、执行了一次性请求、执行了 tick，或者实际的暂停状态改变（9.4，`StateReader.movementSuspended()`，包括登录与配置等不 tick 的阶段）；注册 observer 与查询不算变化 |

粒子、音效、世界事件、爆炸没有对应的模型状态，通过 `PacketReceived` 观察 `ClientboundLevelParticlesPacket`、`ClientboundSoundPacket`、`ClientboundSoundEntityPacket`、`ClientboundLevelEventPacket`、`ClientboundExplodePacket`；内置 observer 把它们整理成记录（见 11.7）。

**投递顺序**：

- 一个数据包产生的模型层观察记录按变化发生的顺序投递，最后投递它的 `PacketReceived`。
- Bundle 中的数据包全部应用之后才开始投递，仍按「数据包 1 的模型记录、数据包 1 的 `PacketReceived`、数据包 2 的模型记录……」的顺序。回调中读到的模型包含整个 bundle 的结果。
- 同一个 observer 收到的观察记录顺序与产生顺序一致。

#### 11.3 Observer 与订阅

observer 由用户构造，交给 bot 之后，`observe` 做的事情只有一件：把订阅的观察记录交给 observer，由 observer 更新自己的状态。observer 的状态怎样算「成功」由用户决定（见 11.6）。

```java
public interface Observer {
    /// 订阅的观察记录类型；注册时读取一次，之后不再变化
    Subscription subscription();

    /// 注册生效时调用一次，可以通过 context.state() 读取此刻的模型
    default void onStart(ObserverContext context) {}

    /// 收到一条订阅的观察记录，据此更新自己的状态
    void onObservation(Observation observation, ObserverContext context);

    /// 关闭时调用一次；因回调抛出异常而关闭时不调用
    default void onClose(CloseReason reason, ObserverContext context) {}
}

public interface ObserverContext {
    Bot bot();
    StateReader state();            // 回调时刻的模型，只读，只能在回调中使用
    @Nullable WorldInfo world();    // 当前世界；不在任何世界中时（5.1）为 null
    void close();                   // observer 关闭自己，当前回调返回后生效
}
```

`ObserverContext.close()` 在 observer 自己的回调中调用时，回调返回后关闭；在调度线程上的其它时刻调用时立即关闭；在其它线程调用时抛出 `IllegalStateException`。

`Subscription` 是 record（模型层与 bot 自身的观察记录类型、入站数据包类、出站数据包类三个集合，数据包类为 `Class<? extends MinecraftPacket>`），`Subscription.NONE` 不订阅任何类型。

`Subscription` 描述要接收的类型：

```java
Subscription.of(EntityMoved.class, EntitySpawned.class)    // 模型层与 bot 自身的观察记录
        .received(ClientboundLevelParticlesPacket.class)    // PacketReceived：只投递这些数据包类
        .sent(ServerboundChatPacket.class)                  // PacketSent：只投递这些数据包类
        .and(other);
```

- `received` 与 `sent` 按 `Class.isInstance` 匹配。传入 `MinecraftPacket.class` 表示接收这一方向的全部数据包。
- MCProtocolLib 的数据包类没有区分方向的类型，所以构造时用 `PacketDirection` 检查：`received` 中的类必须是服务端发往客户端的数据包，`sent` 中的类必须是客户端发往服务端的数据包，否则抛出 `IllegalArgumentException`。`PacketDirection` 遍历全部协议阶段，按 `MinecraftCodec.CODEC.getCodec(阶段)` 中这个类的注册判断方向（没有在任何阶段注册的类不属于任何方向），结果按类缓存；`MinecraftPacket.class` 本身两个方向都接受。
- `Subscription.of(...)` 中不能直接写 `PacketReceived.class` 或 `PacketSent.class`，必须通过 `received`、`sent` 指定数据包类，否则构造时抛出异常。
- 11.7 中按传入的类型订阅的 `first`、`observations`、`listener` 在调用工厂方法时就构造订阅，类型为 `PacketReceived` 或 `PacketSent` 时在调用线程上抛出 `IllegalArgumentException`，bot 不受影响（订阅在注册时读取，注册时 `subscription()` 抛出异常会让这次注册失败，见 11.4）。数据包使用 `firstPacket`、`packets`、`sentPackets`、`packetListener`，它们同样在调用工厂方法时检查数据包类的方向，方向不对时在调用线程上抛出 `IllegalArgumentException`。

#### 11.4 生命周期

```java
public interface ObserverHandle<O extends Observer> extends AutoCloseable {
    O observer();
    boolean isOpen();
    @Nullable CloseReason closeReason();
    @Nullable Throwable failure();                // 以 FAILED 关闭时回调抛出的异常，其余为 null
    <R> R read(Function<? super O, R> reader);   // 观察期间读取 observer 的状态：reader 在调度线程上执行
    CloseReason awaitClose(Duration timeout);     // 等到关闭；以 FAILED 关闭时抛出 ObserverFailureException，超时抛出 BotTimeoutException
    void close();                                 // 关闭观察；对已关闭的 observer 不做任何事
}

public enum CloseReason { BY_HANDLE, BY_OBSERVER, ACTION_COMPLETED, DISCONNECTED, FAILED }
```

`Bot.observe(O observer)` 返回 `ObserverHandle<O>`。一个 observer 依次经历「注册」「观察中」「已关闭」三个状态。

**注册**

- 每个 observer 实例只能注册一次，并且只属于一个 bot。重复注册（包括关闭之后再次注册）抛出 `IllegalStateException`。
- 在其它线程调用 `observe` 时：
  - 注册请求交给调度线程，在下一轮处理该 bot 的第一步完成（见 3.2）；
  - `observe` 等到注册完成才返回。这一步不依赖服务端，所以不执行推进函数；
  - `observe` 返回之后应用的数据包和发生的变化都会被观察到，之前发生的不补发。

  所以「先注册 observer，再提交动作」可以保证动作引起的全部变化都被观察到。
- 在调度线程上（例如另一个 observer 的回调中）调用时立即注册。在 observer 的回调（`onStart`、`onObservation`、`onClose`）中调用时，从这个回调返回之后产生的观察记录开始投递，同一个回调中注册之后产生的记录（例如在回调中取消动作产生的 `ActionCompleted`）不投递给它；在调度线程上的其它代码中（例如 `Action.future()` 不带 async 的后续回调在 observer 的回调以外执行时）调用时，从注册时开始投递。
- 注册完成时调用 `onStart`。注册时调用的 `subscription()`（`WorldBoundObserver` 中为 `worldSubscription()`）或 `onStart` 抛出异常时，这个 observer 以 `FAILED` 关闭（下文的关闭表），`observe` 抛出 `ObserverFailureException`，原因为回调抛出的异常，bot 不受影响；在其它线程调用时同样如此，不会一直等待。这时 observer 算作注册过。
- bot 已经断开时，`observe` 抛出 `BotDisconnectedException`；bot 因异常结束时抛出 `BotFailureException`（见 12）。这两种情况下 observer 不算注册过（包括在其它线程调用、注册请求交给调度线程之后 bot 才结束的情况），可以再交给其它 bot；`observeDuring` 相同。
- 实现了内部接口 `RegistrationCheck` 的内置 observer（11.7 的 `distanceWithIn`、`distanceOutOf`）在调用 `observe` 的线程上先做检查；检查失败时 `observe` 抛出异常，bot 不受影响，observer 也不算注册过。
- 「只能注册一次」按对象身份判断（以弱引用记录已经注册过的 observer）。

**观察中**

只投递订阅的类型。bot 进入新世界时 observer 继续观察，订阅了 `WorldEntered` 的会收到这条记录。需要在换世界时切换状态的 observer 继承 `WorldBoundObserver`（11.5）。

**关闭**

以下任一情况都会关闭 observer，关闭之后不再有任何回调：

| 原因 | 触发 | 时机 |
|---|---|---|
| `BY_HANDLE` | 用户调用 `handle.close()` | 在其它线程调用时，等到调度线程处理完才返回，返回时 `onClose` 已经执行；在调度线程上调用时立即关闭。这次关闭中 `onClose` 抛出异常时，observer 以 `FAILED` 关闭，`close()` 抛出 `ObserverFailureException`，在其它线程调用时同样如此 |
| `BY_OBSERVER` | observer 在回调中调用 `context.close()` | 当前回调返回之后 |
| `ACTION_COMPLETED` | 通过 `Bot.observeDuring` 注册的 observer，它对应的动作结束 | 这个动作的 `ActionCompleted` 投递之后（11.8） |
| `DISCONNECTED` | bot 的连接结束：服务端断开、`Bot.disconnect()`、`BotRuntime.close()` | 连接结束时，先丢弃待投递的观察记录，再关闭该 bot 的全部 observer，然后结束未结束的动作 |
| `FAILED` | observer 的回调（`subscription()`、`onStart`、`onObservation`、`onClose`）抛出异常 | 立即关闭，不调用它的 `onClose`（`onClose` 本身抛出异常的除外）。只有这个 observer 关闭，bot 与其余 observer 不受影响，同一条观察记录照常交给其余 observer。异常记在 `handle.failure()` 中，交给持有这个 observer 的一方：`observe`、`observeDuring`（注册时）、`awaitClose`、`close()`（这次关闭中 `onClose` 抛出异常时）、`ObservedAction.await`、`Bot.await` 抛出 `ObserverFailureException`，原因为回调抛出的异常。回调期间这个 observer 已经被关闭（在自己的回调中调用 `handle.close()`，或者连接在回调中结束）时，异常无法再随关闭交给持有者，按 12 结束 bot |

**读取状态**

- 观察中：observer 的字段只由调度线程修改，其它线程只能通过 `handle.read(fn)` 读取。`fn` 在调度线程上执行，返回值不应包含 observer 内部的可变对象。
- 已关闭：`close()` 或 `awaitClose()` 返回之后，任何线程都可以直接读取 observer，因为关闭发生在这两个方法返回之前。
- 内置 observer 的状态读取方法在观察中被调度线程以外的线程调用时抛出 `IllegalStateException`。
- 关闭的 observer 立即从订阅索引中移除；状态保存在 observer 对象中，由调用方持有，关闭后一直可读。

#### 11.5 WorldBoundObserver

wiki 对 EID 的定义写明：实体每次出现在一个世界中都会重新分配 EID，换维度、卸载后重新加载都会改变。方块坐标同样只在一个维度内有意义。所以与实体编号或坐标有关的 observer，在 bot 进入新世界时需要丢弃旧世界的跟踪状态、重新开始。`WorldBoundObserver` 自动完成这件事：

```java
public abstract class WorldBoundObserver<W> implements Observer {
    /// 子类订阅的类型；基类在此基础上自动加入 WorldEntered
    protected abstract Subscription worldSubscription();

    /// bot 进入一个世界时创建该世界的状态
    protected abstract W openWorld(WorldInfo world, ObserverContext context);

    /// 当前世界中的观察记录
    protected abstract void observe(W world, Observation observation, ObserverContext context);

    /// 一个世界的观察结束：bot 进入了下一个世界，或者 observer 关闭
    protected void closeWorld(W world, WorldClosed closed, ObserverContext context) {}

    /// 按进入顺序排列的全部世界及其状态
    public final List<WorldRecord<W>> worlds();

    /// 最后一个世界的状态；还没有进入任何世界时为 null
    public final @Nullable W currentWorld();

    /// 观察中被调度线程以外的线程调用时抛出 IllegalStateException；worlds() 与 currentWorld() 都先做这个检查
    protected final void checkReadable();
}

public record WorldInfo(long epoch, Key dimension, int dimensionTypeId, WorldEntryCause cause) {}
public enum WorldEntryCause { JOIN, RECONFIGURATION, RESPAWN, TRANSFER }
public record WorldRecord<W>(WorldInfo world, W state, @Nullable WorldClosed closed) {}
public sealed interface WorldClosed {
    record Left(WorldInfo next) implements WorldClosed {}
    record ObserverClosed(CloseReason reason) implements WorldClosed {}
}
```

**基类的行为**：

| 时机 | 行为 |
|---|---|
| `subscription()`（final） | 返回 `worldSubscription()` 加上 `WorldEntered` |
| `onStart` | bot 已在某个世界中时，调用 `openWorld(当前世界)` |
| 收到 `WorldEntered` | 对当前世界调用 `closeWorld(..., Left(新世界))`，再调用 `openWorld(新世界)` |
| 收到其它观察记录 | 交给 `observe(当前世界状态, ...)`；observer 还没有打开任何世界时丢弃。bot 离开世界（5.1）之后、进入下一个世界之前的记录交给最后打开的世界，它在收到下一条 `WorldEntered` 时关闭 |
| `onClose` | 对当前世界调用 `closeWorld(..., ObserverClosed(原因))` |

`onStart`、`onObservation`、`onClose` 由基类实现并且是 final。

**`WorldInfo` 的字段**：

- `epoch`：每个 bot 从 1 开始，每进入一个世界加一。
- `cause`：
  - 一个连接上的第一个 Login (play) 为 `JOIN`；
  - 重新配置之后的 Login (play) 为 `RECONFIGURATION`；
  - Respawn 为 `RESPAWN`（死亡后重生与换维度都是这个值）；
  - Transfer 之后新连接上的 Login (play) 为 `TRANSFER`。

#### 11.6 判断结果

库中没有通用的「成功」概念，也不提供断言。动作结束（8.1）之后，测试代码按以下两种方式之一决定后续动作：

1. **检查 observer 的状态**：observer 只记录发生了什么（例如收集到的粒子、各实体的位移），测试代码读取后自行判断。
2. **observer 自己得出结论**：observer 在收到足够的观察记录、能够得出结论时调用 `context.close()` 关闭自己，并通过它自己定义的方法给出结论（例如 `met()`、`matched()`）。测试代码用 `handle.awaitClose(timeout)` 等它关闭，再读取结论。

- 两种方式都只读取 observer 的状态。observer 只负责更新状态，以及在需要时关闭自己（11.7 的 `distanceWithIn`、`distanceOutOf` 在关闭自己之前还会取消交给它的动作）；结论的形式由每个 observer 自己定义，库中没有统一的结论接口。
- observer 没有自己的时限：等多久由测试代码在 `awaitClose` 中决定；「某件事没有发生」由测试代码在关闭 observer 之后检查记录确定。
- 自定义 observer 直接实现 `Observer`；与实体编号或坐标有关时继承 `WorldBoundObserver`。

#### 11.7 内置 observer

`Observers` 提供工厂方法，每次调用返回一个新的 observer 实例。「按世界」一列为「是」的继承 `WorldBoundObserver`：状态读取方法返回当前世界的状态，各世界的完整状态见 `worlds()`。

**只记录的 observer**（不会自行关闭）

| 工厂方法 | 按世界 | 订阅 | 状态读取方法 |
|---|---|---|---|
| `particlesWithin(Region region[, Predicate<Particle> filter])` | 是 | `received(ClientboundLevelParticlesPacket.class)` | `particles()`：`List<ParticleRecord>`，每项含粒子（MCProtocolLib 的 `Particle`）、中心坐标、偏移、速度、数量、是否远距离。范围判断使用数据包中的中心坐标；实际的粒子由客户端在中心附近按偏移随机生成（原版客户端行为） |
| `sounds(Region region[, Predicate<Sound> filter])` | 是 | `received(ClientboundSoundPacket.class, ClientboundSoundEntityPacket.class)` | `sounds()`：`List<SoundRecord>`，每项为 `SoundRecord(Sound sound, SoundCategory category, Point position, float volume, float pitch, @Nullable Integer entityId)`（MCProtocolLib 的 `Sound` 与 `SoundCategory`）；位置音效的坐标由 MCProtocolLib 除以 8；实体音效按收到时该实体在模型中的位置判断范围 |
| `blockChanges(Region region)` | 是 | `BlockChanged` | `changes()`：`List<BlockChanged>` |
| `blockAt(Point pos)` | 是 | `BlockChanged` | `current()`：当前方块（进入世界时所在区块未加载、之后也没有变化时为 null）；`history()`：该位置的每次变化 |
| `movementOf(Predicate<EntitySnapshot> who)` | 是 | `EntitySpawned`、`EntityMoved`、`EntityRemoved` | `tracks()`：每个被跟踪实体一条 `MovementTrack(int entityId, EntityType type, Point start, Point current, boolean removed)`（MCProtocolLib 的 `EntityType`），`displacement()` 返回位移向量 |
| `chatMessages([Predicate<ChatLine> filter])` | 否 | `ChatReceived` | `lines()`：`List<ChatLine>` |
| `actionBars()` | 否 | `ActionBarShown` | `texts()`：`List<Component>` |
| `damageTaken()` | 否 | `EntityDamaged`（目标为自己）、`HealthChanged` | `damages()`：`List<DamageRecord>`，`DamageRecord.Event`（伤害事件）或 `DamageRecord.HealthDrop`（生命值下降） |
| `packets(Class<P> type[, Predicate<P> filter])` | 否 | `received(type)` | `packets()`：`List<P>` |
| `sentPackets(Class<P> type[, Predicate<P> filter])` | 否 | `sent(type)` | `packets()`：`List<P>` |
| `observations(Class<O> type[, Predicate<O> filter])` | 否 | `of(type)` | `observations()`：`List<O>` |

- 记录默认不限制数量。`limit(int)` 在注册之前设置上限，超出的记录只计数不保存，`truncated()` 返回 true。按世界的 observer 对每个世界分别计数，各世界的记录为 `Records`（`items()`、`truncated()`、`count()`）。observer 交给 `observe` 或 `observeDuring` 之后再调用 `limit` 抛出 `IllegalStateException`：注册之后 observer 的字段由调度线程读写，在其它线程修改会与它形成数据竞争。
- `sounds` 的实体音效在实体不在模型中时不记录。
- `Region` 是 record `Region(Point min, Point max)`，提供 `Region.around(Point center, double radius)` 与 `contains(Point)`。
- `movementOf` 跟踪的实体：注册时（以及每次进入新世界时）模型中已有的、满足 `who` 的实体，以及之后 `EntitySpawned` 中满足 `who` 的实体。`who` 只在开始跟踪时判断一次。`start` 为开始跟踪时的位置；实体被移除时 `removed` 变为 true，记录保留。进入新世界时按 11.5 重新开始跟踪。

**得出结论时关闭自己的 observer**

| 工厂方法 | 按世界 | 关闭条件 | 结论 |
|---|---|---|---|
| `movementOf(Predicate<EntitySnapshot> who, MovementCriteria closeWhen)` | 是 | 当前世界中满足条件的实体数达到 `closeWhen` 要求的数量 | `met()`：是否因满足条件而关闭；`metBy()`：当前世界中满足条件的实体的 `MovementTrack`；`tracks()` 同上 |
| `blockAt(Point pos, Predicate<Block> closeWhen)` | 是 | 该位置的方块满足条件；进入世界时先检查一次当前方块 | `met()`；`current()`、`history()` 同上 |
| `blockBroken(Point pos)` | 否 | 该位置的方块变成方块编号（`Block.id()`）与 `original()` 不同的方块；进入新世界时也关闭 | `broken()`：是否因方块变化而关闭；`original()`：注册时该位置的方块（所在区块未加载或者不在任何世界中注册时见下文）；`changedTo()`：第一次变成的不同方块，没有时为 null |
| `first(Class<O> type, Predicate<O> filter)` | 否 | 出现匹配的观察记录 | `matched()`：`Optional<O>` |
| `firstPacket(Class<P> type, Predicate<P> filter)` | 否 | 收到匹配的数据包 | `matched()`：`Optional<P>` |
| `firstChat(Predicate<ChatLine> filter)` | 否 | 收到匹配的聊天消息 | `matched()`：`Optional<ChatLine>` |
| `firstTranslatedChat(String translationKey)` | 否 | 收到的聊天消息的组件中（包括子组件与翻译参数）有翻译键为 `translationKey` 的翻译组件，不考虑消息的其它内容 | `matched()`：`Optional<ChatLine>` |
| `condition(Predicate<StateReader> closeWhen)` | 否 | 条件成立；注册时先检查一次，之后在每条 `StateUpdated` 时检查 | `met()` |
| `distanceWithIn(double range, Point target, Action action)` | 否 | 玩家与 `target` 的距离不大于 `range` 时，取消 `action` 并关闭；`action` 先结束时关闭 | `met()`：是否因满足距离条件而关闭 |
| `distanceOutOf(double range, Point target, Action action)` | 否 | 玩家与 `target` 的距离大于 `range` 时，取消 `action` 并关闭；`action` 先结束时关闭 | `met()`：是否因满足距离条件而关闭 |

- 这些 observer 在得出结论之前被句柄关闭、因连接断开而关闭或以 `ACTION_COMPLETED` 关闭（11.8）时，`met()`、`broken()` 为 false，`matched()` 为空，关闭原因见 `handle.closeReason()`。
- `blockBroken`：
  - 订阅 `BlockChanged`、`ChunkLoaded` 与 `WorldEntered`。
  - 在世界中注册时，`original()` 在 `onStart` 中读取；该位置所在区块未加载时，`original()` 为之后该区块第一次加载（`ChunkLoaded`）时的方块，在这之前为 null，这期间该位置也不会产生 `BlockChanged`。所以在未加载区块上调用 `Bot.breakBlock` 时，动作以 `CHUNK_NOT_LOADED` 取消（8.5），observer 随之以 `ACTION_COMPLETED` 关闭，`broken()` 为 false，`original()` 为 null。
  - 只改变方块状态（例如朝向、含水）不算变成不同的方块。
  - 坐标只在一个世界中有意义，所以进入新世界时关闭自己，`broken()` 为 false。
  - 不在任何世界中时注册（5.1：进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）：坐标属于下一个世界，与这期间提交的动作一样等待进入世界（8.2）。收到的第一条 `WorldEntered` 不关闭；`original()` 为进入之后该位置所在区块第一次加载（`ChunkLoaded`）时的方块，在这之前为 null，这期间该位置也不会产生 `BlockChanged`（区块加载之前没有 Block Update 可以应用）；之后的 `WorldEntered` 照常关闭。
  - 通常通过 `Bot.breakBlock`（11.8）使用。
- `distanceWithIn` 与 `distanceOutOf`：
  - 订阅 `PlayerMoved`、`PlayerTeleported`、`ActionCompleted`。
  - 距离为玩家位置（脚部）与 `target` 的直线距离。
  - 注册时先检查一次距离条件，之后在每条 `PlayerMoved`、`PlayerTeleported` 之后检查。条件满足时在回调中调用 `action.cancel()`（在调度线程上立即处理，见 8.1），然后关闭自己，`met()` 为 true。
  - 不在任何世界中时注册（5.1）没有玩家位置，不做注册时的检查；这期间提交的 `action` 等待进入世界（8.2），进入世界之后照常在每条 `PlayerMoved`、`PlayerTeleported`（包括进入世界之后服务端传送玩家到出生位置）之后检查。
  - 注册时距离条件不满足（或者不在任何世界中）并且 `action` 已经结束，或者之后收到 `action` 的 `ActionCompleted` 时，关闭自己，`met()` 为 false。
  - `action` 必须由同一个 bot 提交，否则注册时（在调用 `observe` 的线程上，11.4）抛出 `IllegalArgumentException`。
  - 这两个 observer 只能在提交动作之后构造，注册时的检查保证注册之前已经发生的移动不会被漏掉。世界重建时移动与手部队列中的动作被取消（8.2），observer 随之关闭，所以它们不需要按世界切换状态。
- 只记录的 `movementOf`、`blockAt` 没有结论，调用 `met()` 抛出 `IllegalStateException`。

`MovementCriteria` 是不可变对象，由 `MovementCriteria.create()` 开始，用以下方法组合（每个方法返回新的对象），至少设置前三项中的一项，否则构造 observer 时抛出 `IllegalArgumentException`。设置的各项同时满足时，这个实体满足条件：

| 方法 | 条件 |
|---|---|
| `atLeast(double blocks)` | 位移长度不小于 `blocks` |
| `towards(Point target, double maxAngleDegrees)` | 位移方向与「起点指向 target」的方向夹角不超过 `maxAngleDegrees`；位移为零时不满足 |
| `into(Region area)` | 实体当前位置在 `area` 内 |
| `entities(int count)` | 满足条件的实体数达到 `count`，默认 1 |

每条 `EntityMoved` 之后对相应实体重新判断；实体被移除时，已经满足条件的计数保留。

**监听**

`listener(Class<O> type, Consumer<O> consumer)` 与 `packetListener(Class<P> type, Consumer<P> consumer)`：回调在调度线程上执行，不保存状态。

#### 11.8 动作期间的观察

11.4 中「先注册 observer，再提交动作」保证动作引起的变化都被观察到，停止观察的时间由测试代码决定。需要观察「一个动作执行期间」发生的变化时，使用 `Bot.observeDuring`：观察在提交动作之前开始，在动作结束时停止。

```java
/// 在调度线程上的同一个任务中依次完成：注册 observer（调用 onStart）、调用 submit 提交一个动作。
/// observer 最晚在这个动作的 ActionCompleted 投递之后关闭，关闭原因为 ACTION_COMPLETED
<O extends Observer> ObservedAction<O> observeDuring(O observer, Function<Bot, Action> submit);

public interface ObservedAction<O extends Observer> {
    Action action();
    ObserverHandle<O> handle();

    /// 等到动作结束并且 observer 关闭，返回 observer，之后任何线程都可以直接读取它的状态。
    /// 动作被取消时照常返回；observer 以 FAILED 关闭时立即抛出 ObserverFailureException，不等动作结束；
    /// 连接结束或 bot 因异常结束时抛出与 Action.await 相同的异常；超时抛出 BotTimeoutException
    O await(Duration timeout);
}
```

- **执行方式**：
  - 在其它线程调用时，注册与提交作为一个任务交给调度线程，在下一轮处理该 bot 的第一步完成（与 11.4 的注册相同）。`observeDuring` 等到任务完成才返回，这一步不执行推进函数。在调度线程上调用时立即执行。
  - 注册与提交在同一个任务中完成，动作开始执行之前 observer 已经在观察，中间没有可以被其它线程插入的间隔。
  - `submit` 在调度线程上执行，必须返回它执行期间由这个 bot 提交的一个动作（按动作的提交编号判断），否则以 `BY_HANDLE` 关闭 observer 并抛出 `IllegalArgumentException`，bot 不受影响。`subscription()` 或 `onStart` 抛出异常时按 11.4 只有这个 observer 以 `FAILED` 关闭，`observeDuring` 抛出 `ObserverFailureException`，不调用 `submit`；`submit` 抛出的异常按 12 结束 bot，`observeDuring` 抛出 `BotFailureException`。
- **关闭时机**：动作结束时产生的 `ActionCompleted` 按 11.2 的顺序投递，投递完成后 observer 以 `ACTION_COMPLETED` 关闭。在此之前到达的数据包产生的观察记录都已经投递给它，包括同一个 bundle 中排在确认之前的数据包。observer 在这之前自己关闭、被句柄关闭或因连接断开而关闭时，按 11.4 的原因关闭。
- 动作在 `submit` 执行期间就已经结束时（例如在 `submit` 中对它调用 `cancel()`，在调度线程上立即处理）同样如此：observer 在 `submit` 返回之后才绑定到动作上，所以 `submit` 执行期间提交的动作结束时总是产生 `ActionCompleted`（即使没有 observer 订阅它），它在绑定之后投递，observer 随之以 `ACTION_COMPLETED` 关闭。
- `ObservedAction.await` 在等待期间执行 3.4 的推进函数；超时时不关闭 observer，也不取消动作。observer 以 `FAILED` 关闭时立即抛出 `ObserverFailureException`，同样不取消动作，由调用方决定是否取消。

**破坏方块**：

```java
/// 等同于 observeDuring(Observers.blockBroken(pos), bot -> bot.attemptBreakBlock(pos[, face]))
ObservedAction<BlockBrokenObserver> breakBlock(Point pos[, Direction face]);
```

`breakBlock(pos).await(timeout).broken()` 为 true 表示：从提交 `attemptBreakBlock` 到它结束（执行完毕或被取消）这段时间内，`pos` 处的方块在 bot 的世界模型中至少有一次变成了方块编号不同的方块。

不在任何世界中时调用 `breakBlock`：`attemptBreakBlock` 等待进入下一个世界并加载之后执行（8.2），`blockBroken` 按 11.7 观察下一个世界中的这个位置，`original()` 为该位置所在区块加载时的方块。

- 挖掘完成时，这次破坏引起的 Block Update 都在确认之前到达（8.5），所以都在观察范围内。
- 服务端拒绝破坏时只发送确认，没有 `BlockChanged`，结果为 false。
- 原版服务端在一个 tick 中对每个位置只发送最终状态：同一 tick 内被破坏又被恢复的方块，客户端看不到变化，结果为 false（原版服务端行为）。Minestom 每次设置方块都发送 Block Update，这种情况结果为 true。

#### 11.9 等待

| 方法 | 行为 |
|---|---|
| `Bot.await(Predicate<StateReader>, Duration)` | 注册一个 `condition(predicate)` 并等它关闭；超时时关闭它并抛出 `BotTimeoutException`；predicate 抛出异常时（注册时的检查或之后的检查）这个 observer 以 `FAILED` 关闭，抛出 `ObserverFailureException`，bot 不受影响；它因连接结束或 bot 因异常结束而关闭时抛出与 `Action.await` 相同的异常 |
| `Bot.joined()` / `Bot.loaded()` | 返回 `Action`，分别在 Login (play) 处理完成与 Player Loaded 发送（见 10）时执行完毕。「已经发生过」按当前状态判断：`joined()` 为 bot 在世界中（Login (play) 处理完成之后、离开世界之前）；`loaded()` 为当前世界已经加载（自上次 Login (play) 或 Respawn 以来发送过 Player Loaded，与 8.2 中请求开始执行的条件相同）。不在任何世界中时（5.1）调用的两者等待下一个世界的 Login (play) 与 Player Loaded，Respawn 之后、加载之前调用的 `loaded()` 等待这次重生之后的加载。已经发生过时在下一轮调度中执行完毕：在其它线程调用时请求本来就在下一轮处理，在调度线程上调用时推迟到下一轮，所以 `observeDuring(o, Bot::loaded)` 在动作结束之前已经把 observer 绑定到动作上，observer 以 `ACTION_COMPLETED` 关闭 |

`Bot.await`、`ObserverHandle.awaitClose`、`Action.await`、`ObservedAction.await` 都执行 3.4 的推进函数。

#### 11.10 示例

```java
Point trap = new Vec(10, 64, 10);
var particles = bot.observe(Observers.particlesWithin(Region.around(trap, 3)));
var damage = bot.observe(Observers.damageTaken());
var zombie = bot.observe(Observers.movementOf(
        e -> e.type() == EntityType.ZOMBIE,
        MovementCriteria.create().atLeast(2.0).towards(trap, 30)));

Action walk = bot.moveTo(trap);
if (!walk.await(Duration.ofSeconds(10))) {
    fail("moveTo 被取消", walk.future().exceptionNow());
}

// observer 自己得出结论：满足条件时关闭自己
zombie.awaitClose(Duration.ofSeconds(3));
assertTrue(zombie.observer().met());

// 测试代码检查 observer 的状态
particles.close();
assertFalse(particles.observer().particles().isEmpty());

damage.close();
assertTrue(damage.observer().damages().isEmpty());
```

#### 11.11 开销

- 每个 bot 有一个 `SubscriptionIndex`，按观察记录类型与数据包类索引已注册的 observer。注册与关闭时更新索引。
- 模型在产生观察记录之前先查询索引，没有订阅者的类型不创建对象（`observeDuring` 调用 `submit` 期间提交的动作结束时的 `ActionCompleted` 除外，见 11.8）。`EntityMoved` 等高频记录只有在被订阅时才有开销。
- 观察记录在同一轮内直接投递，不排队，不跨线程。

### 12. 错误处理

就地失败：出错的位置抛出异常，所属的 bot 结束，异常传到测试代码；同一 `BotRuntime` 中的其它 bot 不受影响。

- **调度线程上的异常**：数据包处理、请求执行、tick 中抛出的异常，在该 bot 的处理入口处被捕获，并用来结束这个 bot：
  - 关闭连接；
  - 状态进入 `DISCONNECTED`，记录异常（`Bot.failure()`；`BotStatus` 的转移见 5.1 的连接状态机）；
  - 全部未结束的动作的 `future()` 以 `BotFailureException(cause)` 完成，`Action.await` 抛出这个异常（见 8.1）；
  - 仍在观察的 observer 以 `DISCONNECTED` 关闭（见 11.4）；
  - 之后对该 bot 的调用（提交动作、注册 observer、查询、等待）立即抛出 `BotFailureException(cause)`。

  调度线程是所有 bot 共用的，异常不能让它退出。
- **observer 回调中的异常**只关闭这个 observer（`FAILED`），交给持有它的一方（`ObserverFailureException`，11.4），bot 不受影响。例外是回调期间这个 observer 已经被关闭（在自己的回调中调用 `handle.close()`，或者连接在回调中结束）的情况：异常无法再随关闭交给持有者，按上一条结束 bot。
- **连接上的异常**（解码失败、`InboundSink.accept` 中解码上下文的异常，例如注册表构建失败与 Encryption Request、写出失败、套接字错误）由连接以这个异常关闭，`InboundSink.closed(error)` 以同样的方式结束 bot；对端关闭属于正常关闭，按下文的服务端断开处理。MCProtocolLib 解码时遇到不认识的枚举编号（Entity Event、Display Objective 的位置、Update Objectives 与 Update Teams 的动作等）同样属于解码失败。寻路线程上的异常在移动动作取回结果时抛出，Transfer 打开新连接失败时在下一轮抛出，同样结束 bot。
- observer 的 `onClose` 在连接结束时抛出异常：这个 observer 以 `FAILED` 关闭，异常记在它的句柄上（`handle.failure()`，`awaitClose` 抛出 `ObserverFailureException`），其余 observer 照常关闭；bot 的结束方式不变（`Bot.failure()` 与动作得到的异常不受影响）。
- **服务端断开**：这属于游戏中可能发生的结果，不算程序错误。
  - Disconnect 的原因记录在 `Bot.disconnectReason()` 与各动作 `future()` 的 `BotDisconnectedException` 中；
  - 未结束的动作的 `future()` 以 `BotDisconnectedException` 完成，`Action.await` 抛出这个异常；observer 以 `DISCONNECTED` 关闭；
  - 之后提交动作或注册 observer 抛出 `BotDisconnectedException`，查询仍可读取断开时的模型。
- **不提供替代值**：查询未加载区块的方块直接抛出 `ChunkNotLoadedException`；注册表条目取不到值时直接失败。
- **不支持的输入**：部分过滤的 Player Chat Message（待办 T2，7.7）与带组件条件的 `can_break`（待办 T4，8.5）抛出 `UnsupportedOperationException`，bot 结束。
- **不符合协议顺序的输入**（5.1 的连接状态机）：握手发出之前收到数据包、收到不属于当前协议阶段的数据包、不允许的状态转移，抛出 `IllegalStateException`，bot 结束。

### 13. 资源占用

**线程**

- 每个 `BotRuntime` 一个平台线程。
- 全部 TCP 连接共用一组 Netty 事件循环（`NettyGroup`，守护线程，名称以 `yuanban-bot-netty` 开头，第一次使用时创建，不关闭），连接没有自己的线程（3.1）。
- 外部模块实现的连接使用的线程由它自己决定（4.3）。

**内存**

- 区段在同一 JVM 内按内容共享，写入时复制（7.2）。
- 光照与高度图在解码上下文中丢弃。
- 加载区域外的区块立即卸载。
- 默认视距为 2，服务端按客户端视距与服务端视距中较小的值发送区块（原版服务端行为）。
- 聊天日志有长度上限。
- 静态数据：Minestom 的全局静态方块数据，以及 `data` 包从 `net.minestom:data` 读取的 JSON（6.1），都在类初始化时加载一次；原版的同步注册表数据（`VanillaNetworkCodec`）第一次使用时加载一次。

**CPU**

- 帧解码与区段解码在调度线程以外完成：TCP 连接在 Netty 事件循环上，其它连接在调用 `InboundSink.accept` 的线程上。调度线程只应用结果。
- 寻路在虚拟线程上计算，不占用调度线程。
- 没有 observer 订阅的观察记录不创建对象（11.11）。

### 14. 公开接口示例

```java
try (BotRuntime runtime = BotRuntime.create(RuntimeOptions.builder()
        .waitPump(env::tick)                       // 手动推进的 Minestom 服务端
        .build())) {
    Bot alice = runtime.connect(BotOptions.builder()
            .username("alice")
            .transport(embedded)                   // 外部模块提供的进程内连接（4.3）
            .build());
    alice.loaded().await(Duration.ofSeconds(10));

    alice.moveTo(new Vec(10, 64, 10)).await(Duration.ofSeconds(10));

    Point target = new Vec(10, 64, 11);
    assertTrue(alice.breakBlock(target, Direction.UP).await(Duration.ofSeconds(5)).broken());
    alice.placeBlock(new Vec(10, 63, 11), Direction.UP, Hand.MAIN_HAND).await(Duration.ofSeconds(5));

    Point flag = new Vec(10, 64, 20);
    alice.lookAt(flag).await(Duration.ofSeconds(1));
    Action forward = alice.moveForward(20);              // 沿 lookAt 设置的朝向直线移动
    var near = alice.observe(Observers.distanceWithIn(2, flag, forward));
    near.awaitClose(Duration.ofSeconds(10));
    assertTrue(near.observer().met());                   // 进入 flag 的 2 格以内时取消了 forward
    assertFalse(forward.await(Duration.ofSeconds(1)));

    alice.suspendMovement();
    var hello = alice.observe(Observers.firstChat(line -> line.plainText().contains("hello")));
    alice.chat("hello");
    hello.awaitClose(Duration.ofSeconds(2));
    assertTrue(hello.observer().matched().isPresent());
    alice.resumeMovement();

    Bot bob = runtime.connect(BotOptions.builder()
            .username("bob")
            .transport(new TcpTransport("127.0.0.1", 25565))
            .build());
    bob.loaded().await(Duration.ofSeconds(10));
    bob.jumpTo(new Vec(3, 65, 0)).await(Duration.ofSeconds(5));
}
```

### 15. 测试

README 规定本库只编写单元测试，不编写端到端测试。进程内连接由外部模块提供（4.3），TCP 连接接入真实服务端的验证同样不在本库测试中。测试使用真实输入：

- 数据包为 MCProtocolLib 的数据包对象；需要字节时用 MCProtocolLib 的 `serialize`、`MinecraftTypes` 与 `MinecraftCodec` 生成，区块数据用 MCProtocolLib 的 `DataPalette` 与 `MinecraftTypes.writeChunkSection` 生成（`ChunkBuilder`，方块数按非空气状态计数，方块实体为 `BlockEntityInfo`）；
- 同步注册表与标签取自 MCProtocolLib 自带的原版数据（`MinecraftProtocol.loadNetworkCodec()`、`loadNetworkTags()`），`TestServer` 按 MCProtocolLib `ServerListener` 的方法把它们转换为 Registry Data 与 Update Tags；省略数据时条目的数据为 null；
- 静态数据取自 Minestom 的方块数据与 `net.minestom:data`，与运行时相同；
- 档案为 MCProtocolLib 的 `GameProfile`。

组件（解码、注册表、世界模型、移动计算、挖掘速度、已读跟踪、窗口布局、bundle 与批次、物品数据、属性、方块谓词、数据包方向、元数据下标）直接构造并检查。需要运行 bot 的测试由测试代码扮演服务端一侧：`TestPeer` 实现 `Transport`，测试线程串行地把数据包对象交给 bot 的 `InboundSink`，并按顺序记录 bot 交给连接的数据包，测试检查这些数据包；`TestPeer.holdWrites()` 让之后交出的数据包推迟写出，用来检查写出之前连接断开的情况。Keep Alive 作为屏障：收到它的应答时，之前交给 bot 的数据包都已经应用并投递了观察记录。`TcpConnection` 的测试在回环地址上打开 `ServerSocketChannel`，由测试代码扮演服务端，用 MCProtocolLib 的 `MinecraftCodec` 与 `MinecraftTypes` 按阶段与压缩阈值读写帧。

| 测试对象 | 内容 |
|---|---|
| `decode` | 经过 MCProtocolLib 编码与解码的区块数据中，方块实体取区段中的方块：告示牌等类型名称与方块名称不同的方块实体照常保留，没有 NBT 时为空的 `NbtMap`，区段中的方块不同、方块实体类型不同、超出维度高度时忽略；区段解码与 `SectionPool` 共享；把解码得到的区段重新写出与数据包中的字节相同；区段之后有剩余字节时出错，数据比维度高度要求的少时出错；Encryption Request 抛出 `UnsupportedOperationException`；区块按最近一次 Login (play) 或 Respawn 的维度确定区段数；重新配置的第一个 Registry Data 删除之前同步注册表的标签，方块标签保留 |
| `transport` | TCP 连接的阶段与压缩：Set Compression、紧跟的压缩数据包与 Login Finished 之后的配置阶段数据包在同一次写入中到达时照常解码，Login Acknowledged 与 Finish Configuration 改变出站阶段，超过阈值的数据包压缩写出；`close()` 之前交出的批次照常写出；对端不读取、发送缓冲区已满时 `close()` 丢弃没有交给套接字的数据，连接随即关闭；连接重置时正在写出的批次以异常结束；编码失败时这一批以 `EncoderException` 结束、连接以这个异常关闭，之后交出的批次以 `ClosedChannelException` 结束；连接失败时 `open` 抛出 `UncheckedIOException`；登录阶段的读取超时 |
| `protocol` | 登录与配置的发送顺序与内容（握手、Login Start、brand、Client Information 与它的语言 `en_us`、Known Packs 子集）；握手发出之前收到数据包时 bot 结束，什么都不发送，连接被关闭；配置阶段收到游戏阶段的数据包、游戏阶段收到登录阶段的数据包时 bot 结束；数据包方向的判断（只在一个阶段注册的数据包、配置与游戏阶段都有的数据包、方向不对与没有注册的类被拒绝、`MinecraftPacket.class` 两个方向都接受）；Set Passengers 拒绝形成循环的乘坐、实体乘坐自己时 bot 结束、跳过未知的乘客、乘客离开原来的载具；5.2 的应答（Ping、Cookie、资源包两种方式、区块批次确认、传送的两个应答）；Bundle 的整批交出与 4096 上限；区块批次确认的数值；cookie 的 5 KiB 上限；Transfer 重新登录并保留 cookie、取消移动队列；Transfer 时 Outbox 中的数据包交给旧连接、不发送清理数据包、新连接上的第一个数据包是 Handshake、同一个 bundle 中之后的数据包被忽略、旧连接写出失败的动作以 `BotDisconnectedException` 结束；重新配置的完整流程（离开世界、取消挖掘不发送清理数据包、配置阶段与 Login (play) 之前不 tick、期间提交的请求在下一个世界执行、`RECONFIGURATION`、新的本地玩家与方块交互序号）；Respawn（同一维度保留世界与方块交互序号、换维度重新创建并且序号从 1 开始、Close Container、Data kept 两个掩码、没有 0x02 时上次发送的 Player Input 与疾跑状态重新开始、Set Held Item(0)）；重复的 Login (play)；传送速度的 ROTATE_DELTA 旋转与 DELTA 标志、应答不计入上一次发送记录；Synchronize Vehicle Position 对实体的速度同样按 ROTATE_DELTA 旋转，俯仰角限制到 [-90, 90]；Teleport Entity 不改变实体的速度，本地玩家控制的船不被它移动，下船之后照常应用；实体包围盒的幼年尺寸（僵尸、乘以 scale 的牛、坐下的骆驼，史莱姆的幼年标记被忽略）与玩家、村民的姿势尺寸（潜行乘以 scale，死亡与睡觉为固定尺寸），都按 float 计算；载具被移除与 Login (play) 时清除乘坐状态；Update Section Blocks 的负数区段坐标（从协议字节经过 MCProtocolLib 解码）；Chunk Biomes；区块方块实体的 NBT 经 `blockEntity()` 读取，`block()` 不带 NBT；Block Entity Data 只应用到同一类型的方块实体（类型不同、方块没有方块实体、区块没有加载、超出维度高度时忽略）；Entity Event 产生 `EntityStatusReceived`；Damage Event 的伤害类型换成名称、-1 的来源编号换成 null、编号不在注册表中时 bot 结束；Game Event 7、8 的数值限制到 [0, 1]；更新不存在的 Boss Bar 与引用不存在的签名编号时 bot 结束，Login (play) 之后签名缓存重新开始；新玩家的属性基础值；Game Event 3 按游戏模式设置能力（包括可以建造）；未知玩家的 Player Info Update 被忽略；断开之后的动作与查询；Respawn（包括同一维度）之后的第一次 tick 在一个间隔之后；Start Configuration 先发送 Acknowledge Message；Player Chat Message 的全局编号不连续时 bot 结束；Delete Message 去掉还没有告知服务端的已读条目；部分过滤的玩家聊天让 bot 以 `UnsupportedOperationException` 结束（待办 T2），完全过滤的不记录、只计入未确认的消息数；发送者不在玩家列表中的玩家聊天记录 `chat.validation_error`、只计入未确认的消息数；`ChatLine` 的聊天类型、发送者名称与目标名称，聊天类型的编号不在注册表中时 bot 结束；随数据包给出定义的聊天类型记录在 `inlineChatType` 中、`chatType` 为 null；移除实体时乘客停止乘坐、离开自己的载具，之后对被移除的载具的 Synchronize Vehicle Position 应用到本地玩家，同一编号的实体重新生成之后不再应用；Spawn Entity 的编号已有实体时先移除旧实体（乘坐关系解除，先产生旧实体的 `EntityRemoved`，不记下本地玩家乘坐过的载具）；ADD_PLAYER 保留已有条目；移除不存在的 Boss Bar 不产生观察记录；世界冻结时时间与世界边框只在 Step Tick 给出的 tick 推进；世界边框、World Age 与时钟在移动步骤之后推进；时钟属于连接（换维度的 Respawn 之后保留，重新配置之后重新开始），Update Time 按 World Age 的差值与速率推进没有列出的时钟；Player Info Update 改成旁观模式时竖直速度清零；队伍成员互斥与重复创建队伍，移除不在队伍中的成员时 bot 结束；玩家列表新条目的游戏模式为生存模式；重复创建目标时 bot 结束，删除目标时清空指向它的显示位置，Display Objective 指向不存在的目标时清空这个位置；离开世界之后 `joined()`、`loaded()` 等待下一个世界，Respawn 之后 `loaded()` 等待重新加载 |
| `registry` | 用原版的全部 Registry Data 构建 `SyncedRegistries`，条目名称的顺序与编号与服务端一致，维度类型的最低 y 与高度一致；条目省略数据时取自 `VanillaNetworkCodec`，数据包中带有的维度类型数据优先，原版中没有的条目省略数据时构建失败；重新配置的第一个 Registry Data 清空之前的同步注册表，之后的不再清空；聊天类型、伤害类型、世界时钟的编号换成名称，编号超出范围时为 null 或抛出异常；生物群系数量；`TagTable` 按标签逐个替换、删除给出的注册表的全部标签 |
| `data` | `ItemData`：物品编号；`tool` 的规则保留标签与方块编号列表，省略的字段按原版编解码器的默认值补上；`equippable`、`use_effects`、`piercing_weapon`、`attack_range` 的默认值；其它组件类型被拒绝。`Items`：补丁覆盖与移除默认组件、空物品没有组件、原版物品没有默认的 `can_break`；按原始补丁比较物品，没有补丁与空补丁相同，数量只在 `sameStack` 中比较 |
| `player` 中的 `AttributeMap` | `attribute.json` 中的默认值与取值范围（`jump_strength` 为 `(double) 0.42F`）、玩家的基础值；三种修饰符按原版的顺序计算，同名修饰符替换之前的；取值限制到范围之内；Respawn 时复制基础值与修饰符或者只复制基础值 |
| `view` 中的 `EntityMetaIndex` | 各常量的下标、掩码与默认值与 Minestom 的 `MetadataDef` 一致；没有收到的条目读出默认值；按下标与按掩码读取 |
| `world` | 写入时复制；快照不受之后写入的影响；加载区域移动后的卸载，加载区域的半径（视距小于 2 时按 2 计算）；高度范围之外与未加载区块；方块实体数据单独读取，`getBlock` 不带 NBT，方块实体类型改变时删除数据；Chunk Biomes 替换每个区段的生物群系（整个区段同一种生物群系，待办 T1）；世界边框插值期间范围与碰撞使用上一 tick 的大小，插值结束时静止在目标大小，插值时间小于 0 时第一次推进之后为目标大小 |
| `movement` | 9.1 的 wiki 数值（包括飞行与疾跑飞行）；使用物品时的减速；缓降、飘浮与 NoGravity；`air_drag_modifier`、`movement_efficiency` 属性；起跳时竖直速度取较大的一个；开始时的速度归零；站在方块边缘时按支撑方块确定脚下方块；着地时停止飞行并发送 Player Abilities；疾跑的开始与停止条件（饥饿值、失明、撞墙）与 Player Input 的疾跑键；`jumpTo` 起跳没有离开地面时执行完毕；寻路跟随（在构造的世界上走完 `WALK`、`JUMP`、`FALL` 节点；按站立高度走上半砖与楼梯，在半砖上行走不起跳，再走下半砖）；穿过草、花、单层雪与 `cave_air`；避开流体、蜘蛛网、甜浆果丛（包围盒不接触）与藤蔓（脚部不进入），从一排这样的方块的缺口通过；找不到路径时停在最近点并以 `NO_PATH` 取消（远离原点、寻路结果末尾带 `REPATH` 节点）；目标所在区块未加载时走到最近点之后以 `CHUNK_NOT_LOADED` 取消；目标在正上方或者正下方另一层、没有路径时不算到达，以 `NO_PATH` 取消；走进未加载区块时以 `CHUNK_NOT_LOADED` 取消，包围盒离未加载区块不到一格时背离它的移动照常执行；重新寻路三次之后以 `BLOCKED` 取消；寻路期间的传送让结果作废，寻路期间被改变的路径方块在取回结果之后触发重新寻路；`moveForward` 连续被阻挡时取消；斜着顶住墙滑动时按原版半宽 `(double) 0.3F` 算出的包围盒不伸进墙内；`rotate`、`lookAt` 的朝向计算、发送，俯仰角超出 [-90, 90] 或为 NaN 时提交时抛出异常，以及与手部动作朝向的优先级（8.3），服务端用传送或 Player Rotation 设置的朝向不被 `rotate` 留下的朝向改回；指向本地玩家自己的 Synchronize Vehicle Position 在距离不超过 64 时由之后 3 次移动计算插值到目标的位置与朝向（每一步前进剩余的三分之一、二分之一、全部），不发送 Confirm Teleportation，距离超过 64 时立即设置并产生 `PlayerTeleported`；movement suspension 的停止与照常项（暂停期间 `rotate` 照常执行，每 20 tick 的位置照常发送；暂停期间轮到的 `moveTo` 不开始，不清除 `rotate` 设置的朝向，排在后面的动作随之等待，恢复之后照常执行）；`jumpTo`（按水平距离选择行走或疾跑，指定移动方式时不按距离选择，起跳后再次着地即执行完毕，与目标顶面中心的水平距离小于 0.1 时原地起跳）；骑乘（座位位置、新登上船时的偏航角、每 tick 的 Set Player Rotation 与 Move Vehicle、不控制矿车、载具或者它下面的载具所在区块未加载时只发送 Client Tick End、下马之后恢复移动包、载具未知的 Set Passengers 被忽略、马在被乘坐之前的人立动画、世界冻结时没有玩家乘客的马不更新人立动画） |
| `interaction` | `DigSpeed` 与 wiki Breaking 页的公式与示例一致（急迫 II、效率 V 钻石镐瞬间破坏石头）；序号确认；挖掘每个 tick 的 Swing Arm 次数与先后（第 1 tick 两次，Finished digging 与它所在 tick 的 Swing Arm 的先后）；挖掘延迟 5 推迟下一次挖掘的进度；`breakBlock`：确认之前收到 Block Update 时 `broken()` 为 true，只收到确认时为 false，Block Update 只改变方块状态时为 false；`placeBlock` 的字段；`placeBlock` 与 `attack` 的数据包在之前的 tick 已经发出目标朝向之后才发送，目标正好在正北（偏航角 -180 规范为 180）时同样如此；目标在未加载区块上的 `attemptBreakBlock`、`placeBlock`、`breakBlock` 以 `CHUNK_NOT_LOADED` 取消，什么都不发送，`breakBlock` 的 `original()` 为 null；挖掘中目标所在区块被卸载时发送 Cancelled digging 并取消；眼睛所在区块未加载时挖掘在开始的 tick 取消，什么都不发送；Inside block 按方块的轮廓形状计算（眼睛在蜘蛛网中时为 true），目标在世界边框之外的 `placeBlock` 什么都不发送；`useItem` 的保持与结束；`attack` 的目标不存在时取消，攻击幼年僵尸时看向它的幼年包围盒的中心；`attack` 的攻击距离按视线进入包围盒的位置计算，超出时只挥手并以 `OUT_OF_RANGE` 取消（旁观模式下发送不带实体编号的 Spectator Action），实体交互距离属性改为 6 之后照常攻击，主手物品的 `attack_range` 组件限制最小距离，可选取的投射物（潜影贝子弹、属于 `redirectable_projectile` 的火球）选取半径为 1；挖掘中更换手持物品时中止并以新的序号重新开始，重新开始的 tick 不累加；主手拿着穿刺武器时发送 Stab，挖掘中换成穿刺武器时不累加、不挥手；旁观模式的挖掘与攻击只发送 Spectator Action（挖掘不带实体编号，攻击带目标实体编号）；挖掘中能力变成瞬间建造时以新的序号发送 Started digging，不再累加；`can_break` 的匹配（服务端标签与编号列表、任意一个谓词匹配、精确属性值、整数与枚举与布尔值的范围、部分匹配的 NBT 与方块实体的类型和坐标、没有方块实体时不匹配、组件条件抛出 `UnsupportedOperationException`） |
| `inventory` | `ContainerSlots.unique` 与 wiki Inventory 页的第一个主物品栏槽位逐项一致，合成器与讲台的布局；窗口槽位与窗口 0 的同步，打开窗口时玩家物品栏部分取窗口 0 的物品、不产生 `SlotChanged`，只带容器槽位的 Set Container Content 不改变这一部分；点击包字段，按键与模式不对应时在调用线程上抛出异常；每个窗口各自的状态编号与光标；关闭窗口，Close Container 不看窗口编号；Open Horse Screen 只为模型中的马类与鹦鹉螺打开窗口，鹦鹉螺的窗口不看列数；Set Container Slot 与 Set Container Content 的槽位越界时 bot 结束，窗口编号不符时忽略；Set Player Inventory Slot 的下标 41、42 为身体护甲与鞍（`equipment` 与 `EntityEquipmentChanged`），大于 42 的下标被忽略，负数时 bot 结束；选择快捷栏 |
| `chat` | Checksum 与 wiki 描述的计算方法一致；结果为 0 时改为 1；已读窗口与新增消息数；`ignorePending` 只去掉还没有告知服务端的条目；没有显示的消息只计入新增消息数，不计入已读 |
| `player` | `PlayerLoadTracker`：收到 Game Event 13 之前不发送 Player Loaded，也不超时；之后条件满足，或者自开始等待起超过 30 秒时发送；Respawn 重新开始计时；开始等待之前的 Game Event 13 被忽略 |
| `scheduler` | 连接状态只允许 5.1 表中的转移；两个线程同时等待时推进函数不并发执行，开始时间至少相隔 50 毫秒；不 tick 的阶段中暂停状态改变时产生 `StateUpdated`，`condition` 随之检查；三类队列的执行规则（一次性请求等到 Player Loaded 之后执行；没有收到 Game Event 13 时一直不发送 Player Loaded，请求不执行；进入第一个世界之前提交的移动与手部动作不被第一次 Login (play) 取消；移动队列先进先出；两条队列互相独立）；8.1 每种结束情况下 `await` 的返回值或异常与 `future()` 的结果；取消（排队中、执行中、在调度线程上调用）；世界重建与玩家死亡时取消移动与手部队列中的动作；进入世界之前查询本地玩家与方块抛出 `IllegalStateException`；在调度线程上调用阻塞方法时抛出异常；模型入口在其它线程调用时抛出异常；某个 bot 抛出异常时其它 bot 继续运行；关闭之后不能再连接；runtime 关闭之后 Transfer 线程才打开的连接被关闭，关闭期间连接的 bot 被断开，最后一轮之后才入队的查询得到结果 |
| `observe` | 注册后应用的数据包全部被观察到、注册前的不补发；投递顺序（含 bundle）；只投递订阅的类型；`Subscription` 拒绝方向不对的数据包类；五种关闭原因各自的时机与 `onClose` 调用；回调抛出异常时只有这个 observer 以 `FAILED` 关闭，不调用它的 `onClose`，同一条记录照常交给之后的 observer，bot 继续运行；`subscription()` 或 `onStart` 抛出异常时，在其它线程与调度线程上调用的 `observe` 以及 `observeDuring` 都抛出 `ObserverFailureException`，`observeDuring` 不调用 `submit`，这个 observer 算作注册过；`Bot.await` 的条件抛出异常时抛出 `ObserverFailureException`；`ObservedAction.await` 在 observer 失败时立即抛出，动作仍在执行；回调期间被自己的句柄关闭之后又抛出异常时 bot 结束；重复注册抛出异常；bot 已经结束时 `observe` 失败的 observer 不算注册过（包括注册请求交给调度线程之后 bot 才结束的情况），可以交给其它 bot；在回调中注册的 observer 收不到同一个回调中注册之后产生的记录，回调返回之后的照常收到；在其它线程调用 `handle.close()` 时 `onClose` 抛出异常，observer 以 `FAILED` 关闭，`close()` 抛出 `ObserverFailureException`；连接结束时 `onClose` 抛出异常，只有这个 observer 以 `FAILED` 关闭，bot 按断开结束，断开原因保留；观察中跨线程读取状态抛出异常；`observeDuring` 的注册先于动作提交，`ACTION_COMPLETED` 在该动作的 `ActionCompleted` 投递之后关闭（含确认与 Block Update 在同一个 bundle 中的情况，已经发生过的 `loaded()`，以及在 `submit` 中就已经取消、没有 observer 订阅 `ActionCompleted` 的动作），`submit` 返回其它动作时抛出异常 |
| `observe` 中的 `WorldBoundObserver` | 进入新世界时的 `closeWorld` / `openWorld` 顺序；进入第一个世界之前的记录被丢弃；关闭时关闭当前世界 |
| `observe` 中的内置 observer | 范围判断（粒子的中心坐标、实体音效的实体位置）；`limit` 与 `truncated()`，注册之后调用 `limit` 抛出异常；`first`、`observations`、`listener` 的类型为 `PacketReceived` 或 `PacketSent` 时在调用工厂方法时抛出异常，`packets`、`firstPacket`、`packetListener`、`sentPackets` 的数据包类方向不对时同样如此；`movementOf` 的跟踪、关闭条件、实体移除与换世界后的重新跟踪；`blockAt` 注册时先检查当前方块；`distanceWithIn` 在移动后满足距离条件时取消动作，`distanceOutOf` 在动作先结束时关闭且 `met()` 为 false，动作属于其它 bot 时注册失败；`condition`、`firstChat`、`damageTaken`；`firstTranslatedChat` 按子组件与翻译参数中的翻译键匹配；`blockBroken` 进入新世界时关闭；进入第一个世界之前注册的 `breakBlock` 与 `distanceWithIn` 观察下一个世界 |
