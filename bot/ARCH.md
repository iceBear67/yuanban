# bot 架构设计

## 依据

协议行为以 minecraft.wiki 的 *Java Edition protocol* 系列页面为准。wiki 当前页面已经更新为 26.3（协议号 777），本库使用的 MCProtocolLib `26.2-SNAPSHOT` 对应 26.2（协议号 776），因此引用下列修订版本：

| 页面 | 修订号 | 时间 |
|---|---|---|
| Java Edition protocol/Packets | 3786274 | 2026-09-20 |
| Java Edition protocol/FAQ | 3769958 | 2026-09-11 |
| Java Edition protocol/Registries | 3749652 | 2026-08-29 |
| Java Edition protocol/Chunk format | 3661930 | 2026-07-07 |
| Java Edition protocol/Inventory | 3201379 | 2025-10-14 |
| Java Edition protocol/Chat | 3624820 | 2026-06-13 |

- 游戏机制的数值（移动速度、跳跃高度、挖掘时间公式、`/tick` 对玩家的影响）取自 wiki 的 Transportation、Jumping、Breaking、Commands/tick 页面。
- wiki 没有写明的客户端行为按原版 26.2 客户端实现，文中逐条标注「原版客户端行为」。
- 数据包的编码与解码由 MCProtocolLib 完成，其 `MinecraftCodec.CODEC` 的协议号为 776。

---

## 第一部分：需要修改的已有内容

只修改 `bot/build.gradle`：

```groovy
dependencies {
    api("org.geysermc.mcprotocollib:protocol:26.2-SNAPSHOT")
    implementation("net.minestom:data:26.2-rv3")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("it.unimi.dsi:fastutil:8.5.19")
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.0")
}
```

每一项的必要性：

1. **MCProtocolLib 从 `implementation` 改为 `api`**：公开接口直接使用 MCProtocolLib 的类型（`Packet`、`ItemStack`、`EntityType`、`Direction`、`Hand`、`GameMode`、cloudburst math 的 `Vector3d`/`Vector3i`、adventure 的 `Component`）。保持 `implementation` 时，使用本库的测试代码无法编译这些方法签名。另外定义一套等价类型会产生两套平行的数据结构，所以直接沿用。
2. **`net.minestom:data:26.2-rv3`**：物理模拟与挖掘计时需要每个方块状态的碰撞箱、摩擦系数、速度系数、跳跃系数、硬度、是否需要正确工具，以及物品默认组件和实体尺寸。wiki 推荐的原版 Data Generators（`--reports`）只输出方块状态编号与属性，没有碰撞箱和这些物理常量。这个构件是从原版 26.2 服务端导出的 JSON 数据，内容只有数据文件和一个读取资源的类 `net.minestom.data.MinestomData`。本库只把它当作数据文件读取，不使用 Minestom 服务端的任何代码。加载时比对其 `constants.json` 的 `protocol` 字段与 `MinecraftCodec.CODEC.getProtocolVersion()`，不一致立即抛出异常。
3. **`gson`**：流式解析上述 JSON（`block.json` 约 15 MB）。MCProtocolLib 已经间接引入 2.13.2，本库代码直接使用它，所以显式声明同一版本。
4. **`fastutil`**：区块列以 `long` 为键、实体以 `int` 为键的原始类型映射。MCProtocolLib 只引入了 fastutil 的几个 int 子集，缺少 `Long2ObjectMap`。
5. **`caffeine`**：区段共享使用的弱引用驻留器 `Interner.newWeakInterner()`。

根目录 `build.gradle.kts` 与 `settings.gradle.kts` 不需要修改。

---

## 第二部分：新增内容

### 1. 分层结构

```
测试代码
  │  同步查询 / 返回 CompletableFuture 的动作 / 条件等待 / 时钟控制
  ▼
Bot ──────────── BotClock ──── ClientTicker（玩家物理、移动包、挖掘与使用物品等按 tick 推进的逻辑）
  │                                  │
  │   全部可变状态只在该 Bot 绑定的 EventLoop 线程上读写
  ▼                                  ▼
PacketRouter ──► 各模块处理器 ──► ClientState（注册表、区块、实体、本地玩家、物品栏、聊天、界面状态）
  ▲                                  │
  │                               Outbox（本次处理产生的待发送数据包）
BotSession（继承 MCProtocolLib 的 ClientNetworkSession：编解码、压缩、加密、协议状态切换）
  │
Transport（TCP 实现；进程内传输只提供接口）
```

### 2. 包结构

根包 `io.ib67.yuanban.bot`。公开类型使用 JetBrains annotations 标注可空性，内部包整体标注 `@ApiStatus.Internal`。值类型使用 Java record，`BotOptions` 使用 Lombok `@Builder`。

```
io.ib67.yuanban.bot
├── BotRuntime            持有事件循环组与静态数据，创建 Bot，关闭时断开全部 Bot
├── Bot                   单个虚拟玩家的公开入口
├── BotOptions            创建参数（不可变）
├── BotState              CREATED / CONNECTING / LOGIN / CONFIGURATION / PLAY / DISCONNECTED
├── BotFailureException   连接异常结束后，所有等待与后续调用抛出的异常
├── view/                 BotView、StateReader 与各类快照 record
├── transport/            Transport、TcpTransport、TransportResolver
├── network/              BotSession、SessionBridge、PacketRouter、BundleBuffer、Outbox、
│                         ProtocolResponder、ChunkBatchTracker、CookieStore
├── loop/                 BotLoop、BotClock、ClockGroup、ClientTicker
├── data/                 GameData、GameDataSource、MinestomDataSource、
│                         BlockStateTable、BoxTable、ItemTable、EntityTypeTable
├── data/server/          ServerRegistries、DimensionTypes、TagTable
├── world/                Level、ChunkStore、ChunkColumn、SectionRef、SectionPool、
│                         BlockEntityStore、WorldClocks、TickState、WorldBorderState、Weather
├── entity/               EntityStore、ClientEntity
├── player/               LocalPlayer、Abilities、AttributeMap、EffectMap、MovementInput、PlayerLoadTracker
├── physics/              Aabb、CollisionWorld、Collisions、PlayerPhysics、FluidSampler、
│                         BlockBehaviors、MovementSender
├── inventory/            InventoryModel、Window、WindowLayouts、ClickRequest
├── interaction/          Actions、BlockSequence、DigTask、DigSpeed、UseItemTask、MoveToTask、TaskQueue
├── chat/                 ChatModel、LastSeenTracker
├── hud/                  HudModel（标题、动作栏、Boss 栏、玩家列表、计分板）
└── event/                BotEvent 及其子类型、EventBus、ConditionWaiter
```

### 3. 线程模型

**事件循环的归属**

- `BotRuntime` 为每一类 Transport 持有一个 Netty `EventLoopGroup`，按 `Transport.groupKey()` 缓存，由 `Transport.newEventLoopGroup` 创建。线程数默认等于处理器数量，线程为守护线程，命名为 `yuanban-bot-<类型>-<序号>`。
- 每个 Bot 创建时从对应的组中取一个 `EventLoop`（`group.next()`），之后的连接注册、数据包处理、tick、动作全部在这个线程上执行。`EventLoop` 本身就是只含一个循环的 `EventLoopGroup`，所以直接传给 `Bootstrap.group(...)`。
- Transfer 或重连时，如果新 Transport 属于另一类事件循环，Bot 在旧连接关闭后换到新组中的一个 `EventLoop`。随连接重建的世界状态直接丢弃；跨连接保留的部分（选项、`CookieStore`、事件订阅）是不可变或线程安全的对象，切换线程不需要额外同步。

**数据包按到达顺序处理**

MCProtocolLib 的 `NetworkSession.channelRead0` 把 `shouldRunOnGameThread()` 为真的数据包交给 `packetHandlerExecutor`，其余数据包直接在网络线程处理。两组数据包之间的先后顺序没有保证，例如 Block Update 可能先于它所属区块的 Chunk Data and Update Light 被处理。`BotSession` 把 `packetHandlerExecutor` 设为 `Runnable::run`，全部数据包都在连接所在的 `EventLoop` 上按到达顺序处理。这样也不会创建 MCProtocolLib 默认的数据包线程池 `DefaultPacketHandlerExecutor`。

**线程封闭与跨线程调用**

- `ClientState` 只允许在该 Bot 的 `EventLoop` 上访问，内部修改方法调用 `BotLoop.assertInLoop()`，在其它线程调用时立即抛出异常。
- 查询方法在其它线程调用时，把读取操作提交到 `EventLoop` 并等待结果，返回不可变快照（record）；在 `EventLoop` 上调用时直接执行。
- 动作方法返回 `CompletableFuture`。
- 会阻塞的方法（`BotClock.step`、`Bot.await*`、查询方法的跨线程等待）在 `EventLoop` 线程上调用时抛出 `IllegalStateException`，因为等待的任务排在当前任务之后，等待永远不会结束。
- 事件监听器在 `EventLoop` 上执行，不允许阻塞。
- 每个 Bot 不创建任何自己的线程。

### 4. 连接、协议阶段与自动应答

#### 4.1 BotSession

`BotSession` 继承 `ClientNetworkSession`，构造参数：远端地址取自 Transport；协议对象为 `new MinecraftProtocol(MinecraftCodec.CODEC, profile, accessToken)`；`packetHandlerExecutor` 为 `Runnable::run`；不设置本地绑定地址与代理。

重写的方法：

| 方法 | 内容 |
|---|---|
| `getEventLoopGroup()` | 返回 Bot 绑定的 `EventLoop` |
| `getChannelFactory()` | 返回 `Transport.channelFactory()` |
| `setOptions(Bootstrap)` | 调用 `Transport.configureBootstrap`，再设置 `ChannelOption.ALLOCATOR` |
| `getChannelHandler()` | 先执行 MCProtocolLib 的 `MinecraftChannelInitializer`（其中会创建 `ClientListener`），然后注册 `SessionBridge`，调用 `Transport.configurePipeline`，最后在管线头部加入 Netty 的 `FlushConsolidationHandler` |

- `SessionBridge` 注册在 `ClientListener` 之后。同一个数据包先由 `ClientListener` 完成协议状态切换，再交给本库处理，本库在新状态下发送的数据包才能用正确的编号表编码。
- `FlushConsolidationHandler` 把同一次事件循环任务中的多次 flush 合并为一次。wiki FAQ 建议把同一 tick 内发送的数据包合并到一个发送缓冲区；一次 tick 是一个事件循环任务，所以 tick 内产生的全部数据包合并为一次写出。
- 处理器与 `ClientTicker` 不持有 `BotSession`，因为 Transfer 与重新连接会替换 `BotSession`。处理器把待发送的数据包写入 `Outbox`，`PacketRouter` 与 `ClientTicker` 在每次处理结束后把 `Outbox` 中的数据包按顺序交给当前的 `BotSession` 发送。

设置的 MCProtocolLib 会话标志：

| 标志 | 值 | 原因 |
|---|---|---|
| `CLIENT_HOST` / `CLIENT_PORT` | `Transport.handshakeHost()` / `handshakePort()` | `ClientListener.connected` 在未设置这两个标志时把远端地址强制转换为 `InetSocketAddress`，非 TCP 的 Transport 会失败 |
| `FOLLOW_TRANSFERS` | `false` | `ClientListener` 自己处理 Transfer 时会新建一个普通的 `ClientNetworkSession`，绕过本库的 Transport 与事件循环。Transfer 由本库处理（见 4.3） |
| `AUTOMATIC_KEEP_ALIVE_MANAGEMENT` | `true` | 收到 Keep Alive 立即回应，与时钟是否冻结无关 |
| `SEND_BLANK_KNOWN_PACKS_RESPONSE` | `true` | 回应空的 Known Packs，见 4.3 |
| `READ_TIMEOUT` | `BotOptions.readTimeout`，默认 20 秒，0 表示不检测 | wiki Keep Alive：服务端 20 秒没有发送 Keep Alive 时客户端以超时断开 |
| `CLIENT_CONNECT_TIMEOUT` | `BotOptions.connectTimeout`，默认 30 秒 | |
| `ATTEMPT_SRV_RESOLVE` | `BotOptions.attemptSrvResolve`，默认 `false` | 集成测试通常直接给出地址，避免 DNS 查询 |

身份：默认离线模式，UUID 按 wiki Login 一节的约定由字符串 `OfflinePlayer:<名称>` 生成 UUID v3。提供 `GameProfile` 与 access token 时使用 MCProtocolLib `ClientListener` 自带的正版认证流程。

#### 4.2 协议阶段

握手、Login Start、加密、压缩、Login Acknowledged、配置阶段的 Acknowledge Finish Configuration、Keep Alive、Disconnect、Start Configuration 后的 Acknowledge Configuration 由 MCProtocolLib 的 `ClientListener` 完成。`BotState` 随之变化：

```
CREATED ─connect()─► CONNECTING ─连接建立─► LOGIN ─Login Success─► CONFIGURATION
CONFIGURATION ─Finish Configuration─► PLAY ─Start Configuration─► CONFIGURATION
任意状态 ─连接关闭─► DISCONNECTED
```

- `Bot.connect()` 返回的 future 在 Login (play) 处理完成时完成。
- `Bot.loaded()` 返回的 future 在发送 Player Loaded 时完成，或者在 60 tick 规则生效时完成（见 7.3）。这一步需要客户端 tick，时钟冻结时由测试推进时钟。
- 收到 Finish Configuration 时校验注册表数据（见 5.2），校验失败时以该异常断开连接。

#### 4.3 自动应答

`ProtocolResponder` 处理 MCProtocolLib 没有处理、而客户端必须回应的数据包。全部应答在收到数据包时立即发送，与时钟是否冻结无关。

| 收到 | 阶段 | 回应 | 依据 |
|---|---|---|---|
| Login Plugin Request | Login | Login Plugin Response，数据缺省（表示未理解）。`BotOptions.loginPluginHandler` 可以按频道给出自定义回应 | wiki：原版客户端总是回应未理解 |
| Cookie Request | Login / Configuration / Play | Cookie Response，携带 `CookieStore` 中对应键的数据，没有则缺省 | wiki Cookie Request |
| Store Cookie | Configuration / Play | 写入 `CookieStore`，超过 5 KiB 时抛出异常 | wiki：原版客户端只接受 5 KiB 以内的 cookie，cookie 在 Transfer 之间保留 |
| Login Success（进入配置阶段） | Configuration | Plugin Message `minecraft:brand`（`BotOptions.brand`），随后 Client Information | wiki FAQ 登录流程第 13、14 步 |
| Known Packs (clientbound) | Configuration | 空列表的 Known Packs (serverbound)，由 MCProtocolLib 发送 | wiki Registries：客户端可以回应空列表，要求服务端发送全部注册表 NBT |
| Code of Conduct | Configuration | Accept Code of Conduct。`BotOptions.acceptCodeOfConduct` 为 `false` 时不回应，服务端停在配置阶段 | wiki：服务端在收到接受之前不继续配置 |
| Add Resource Pack | Configuration / Play | `ResourcePackPolicy.ACCEPT`：依次发送 Accepted(3)、Downloaded(4)、Successfully downloaded(0)；`DECLINE`：发送 Declined(1)。本库不下载资源包 | 结果码取自 wiki Resource Pack Response；发送顺序为原版客户端行为 |
| Ping | Configuration / Play | 相同 ID 的 Pong | wiki Ping |
| Chunk Batch Finished | Play | Chunk Batch Received，见下文 | wiki Chunk Batch Finished |
| Synchronize Player Position | Play | Confirm Teleportation，然后 Set Player Position and Rotation（on ground 与 pushing against wall 均为 false）。骑乘时不改变位置，两个包照常发送 | wiki Synchronize Player Position |
| Transfer | Configuration / Play | 关闭当前连接，用 `TransportResolver` 把目标主机与端口解析为 Transport，以握手 intent 3 重新登录，`CookieStore` 保留 | wiki Transfer |

**Chunk Batch Received 的数值**：`ChunkBatchTracker` 在 Chunk Batch Start 时记录时间，在 Chunk Batch Finished 时用耗时除以批次区块数得到每区块毫秒数，保留最近 15 批的样本，回应 `25 / 每区块毫秒数`。计算方法与样本数取自 wiki Chunk Batch Finished。wiki 指出原版服务端在收到确认前不再发送区块，所以这个回应不能依赖时钟。

**Client Information 的内容**：语言、视距（`BotOptions.viewDistance`，默认 2；wiki 中 Login (play) 与 Set Render Distance 的视距取值范围为 2–32）、聊天模式、聊天颜色、皮肤部件、主手、文本过滤（离线时为 false）、是否出现在服务器列表、粒子等级，全部来自 `BotOptions`。`Bot.updateClientInformation(...)` 可以在游戏阶段重新发送（wiki：设置改变时发送）。

### 5. 静态数据与服务端注册表

#### 5.1 静态数据

`GameDataSource` 是读取静态数据的接口，`MinestomDataSource` 是唯一实现，通过 `MinestomData.resource(path)` 读取 `block.json`、`item.json`、`entity_type.json`、`constants.json`。解析结果为不可变的 `GameData`，每个 JVM 只加载一次，所有 `BotRuntime` 共用。

- **`BlockStateTable`**
  - 方块状态总数。直接调色板的位宽按 wiki Chunk format 为 `ceil(log2(状态总数))`，传给 MCProtocolLib 的 `readChunkSection`。
  - 每个状态：方块编号、属性取值、碰撞箱编号、轮廓箱编号、流体信息、是否为空气、是否可替换。
  - 每个方块：名称、属性名与取值列表、默认状态、硬度、摩擦系数、速度系数、跳跃系数、是否需要正确工具。
  - 流体信息：`minecraft:water` 与 `minecraft:lava` 读取 `level` 属性，带 `waterlogged=true` 属性的状态视为水源。
  - 未知状态编号立即抛出异常。
- **`BoxTable`**：碰撞箱与轮廓箱以「若干个各边与坐标轴平行的长方体」的形式去重存储，状态只保存编号。
- **`ItemTable`**：物品编号、名称，以及本库计算需要的默认组件：`minecraft:tool`（规则列表、`default_mining_speed`）、`minecraft:max_stack_size`、`minecraft:glider`。物品堆叠实际生效的组件为 Slot 数据中的组件修改叠加在默认组件上。
- **`EntityTypeTable`**：实体类型编号、名称、宽度、高度、眼睛高度。

#### 5.2 服务端注册表与标签

- **`ServerRegistries`** 保存 Registry Data 中每个注册表按顺序排列的条目（标识符与 NBT），条目序号即协议中引用的数字编号（wiki Registry Data）。本库读取两个注册表：
  - `minecraft:dimension_type`：`min_y` 与 `height`。区块数据中区段数组没有长度前缀，区段数等于 `height / 16`（wiki Chunk format）。
  - `minecraft:worldgen/biome`：条目数，用于生物群系调色板的位宽。
  - 收到 Finish Configuration 时，如果这两个注册表缺失或条目没有 NBT，以 `IllegalStateException` 断开连接，异常信息写明注册表名称。
- **重新配置**：wiki Update Tags 指出，重新配置期间只要收到任何 Registry Data，之前全部同步注册表及其标签都作废。`ServerRegistries` 在一次配置阶段收到第一个 Registry Data 时清空同步注册表与它们的标签。
- **`TagTable`**：Update Tags 按标签逐个替换（wiki：列表替换该标签之前的编号；游戏阶段只替换包中提到的标签）。物理与挖掘使用服务端下发的方块标签，例如 `minecraft:climbable` 以及 `minecraft:tool` 规则引用的标签。
- Feature Flags 原样保存。

### 6. 世界模型

`ClientState` 是根对象：

```
ClientState
├── ConnectionInfo     协议阶段、服务端品牌、Server Links、Feature Flags、Login (play) 中的 online mode 与 enforces secure chat
├── ServerRegistries / TagTable
├── Level              收到 Login (play) 或 Respawn 时重新创建
│   ├── 维度编号、维度名称、DimensionInfo(minY, height)
│   ├── ChunkStore / BlockEntityStore
│   ├── EntityStore
│   ├── WorldClocks、TickState、WorldBorderState、Weather
│   └── 难度、默认出生点、服务端视距、模拟距离、加载区域中心
├── LocalPlayer
├── InventoryModel
├── ChatModel
├── HudModel
└── CookieStore        跨连接保留
```

#### 6.1 区块

- **解码**：Chunk Data and Update Light 的 `chunkData` 用 `MinecraftTypes.readChunkSection(buf, 方块状态总数, 生物群系条目数)` 按自下而上的顺序读取 `height / 16` 个区段。
- **舍弃的数据**：高度图与光照数据不需要，处理完立即丢弃。
- **方块实体**：以区块内坐标为键保存类型与 NBT，由 Block Entity Data 更新。
- **更新**：Block Update、Update Section Blocks、Chunk Biomes、Unload Chunk。
- **加载区域**：宽度为 `2 × 服务端视距 + 7`（wiki Set Center Chunk）。服务端视距取自 Login (play) 与 Set Render Distance。
  - 加载区域外的区块数据直接忽略，与原版客户端一致。
  - 收到 Set Center Chunk 后立即卸载落到区域外的区块。wiki 写明客户端可以这样做。
- **查询**：
  - 查询未加载区块中的方块时抛出 `ChunkNotLoadedException`。
  - 维度高度范围之外的位置视为空气。

#### 6.2 区段共享

同一个 JVM 中多个 Bot 位于同一区域时，收到的区段内容相同。

- `SectionPool` 用 Caffeine 弱引用驻留器保存区段：解码得到的区段交给 `SectionPool.intern`，返回内容相同的共享实例，临时解码出的对象随即被回收。没有任何 Bot 引用时，共享实例由垃圾回收释放。
- 相等判断依赖 MCProtocolLib 中 `ChunkSection`、`DataPalette`、`BitStorage` 与各个 `Palette` 实现类由 Lombok 生成的 `equals`/`hashCode`。
- `SectionRef` 持有共享实例和一个「共享」标记。第一次写入（Block Update、Update Section Blocks）时用 `new ChunkSection(original)` 复制出私有副本再写入。共享实例永远不会被写入，所以它的哈希值保持不变。

#### 6.3 实体

`ClientEntity` 保存：

- 编号、UUID、`EntityType`、Spawn Entity 中的附加数据
- 位置、朝向（yaw、pitch、头部 yaw）、速度、是否着地
- 元数据（索引到 `EntityMetadata`）、装备
- 属性与药水效果（生物）
- 乘坐关系

各数据包的处理（数据包定义见 wiki）：

- Update Entity Position / and Rotation：位移为 `delta / 4096`。
- Update Entity Rotation、Set Head Rotation。
- Teleport Entity：绝对位置、速度、朝向、是否着地。
- Synchronize Vehicle Position：按 Teleport Flags 处理相对字段。
- Set Entity Velocity、Set Entity Metadata、Set Equipment、Update Attributes、Entity Effect、Remove Entity Effect、Set Passengers、Link Entities、Remove Entities。
- Pickup Item、Entity Event、Damage Event、Hurt Animation 只产生事件。

实体位置保存服务端最后给出的数值，不做插值（原版客户端的插值只用于渲染）。玩家实体通过 UUID 与 Player Info 条目关联。实体包围盒为 `EntityTypeTable` 中的宽高乘以 `minecraft:scale` 属性；玩家在潜行、游泳、滑翔、睡眠姿态下的高度为原版客户端常量（原版客户端行为）。

#### 6.4 本地玩家

`LocalPlayer` 保存：

- **身份**：实体编号（Login (play)）、`GameProfile`。
- **游戏模式**：取自 Login (play)、Respawn、Game Event 3，以及自己 UUID 的 Player Info Update。wiki FAQ 指出旁观模式通过 Player Info Update 告知。
- **能力**：Player Abilities 中的无敌、飞行中、允许飞行、瞬间建造、飞行速度、视野系数。
- **生命与经验**：生命、饥饿、饱和度（Set Health，生命值 ≤ 0 为死亡）、经验（Set Experience）、Combat Death。
- **属性**：自己实体编号的 Update Attributes，取值按 wiki Attribute 页的修饰符运算规则计算。疾跑速度修饰符在客户端按自己的疾跑状态加入（原版客户端行为）。
- **效果与冷却**：药水效果，物品冷却（Set Cooldown，按客户端 tick 递减）。
- **运动状态**：位置、朝向、速度、是否着地、是否顶住墙壁、下落状态、姿态（潜行、疾跑、游泳、滑翔）。
- **其它**：当前快捷栏槽位、按键输入、等待确认的传送编号、`PlayerLoadTracker`。

Respawn 按 wiki 的 Data kept 掩码决定是否保留属性（0x01）与元数据（0x02）。

#### 6.5 物品栏

**窗口与槽位**

- 窗口 0 为玩家物品栏，槽位 0–45 的含义见 wiki Inventory。
- Open Screen 与 Open Horse Screen 打开其它窗口，记录窗口编号、类型、标题。
- 每种窗口类型的「独有槽位数」按 wiki Inventory 页编码为 `WindowLayouts` 常量表。窗口槽位排列为：独有槽位，然后玩家主物品栏 27 格，然后快捷栏 9 格。

**服务端下发的数据**

- Set Container Content：状态编号、全部槽位、光标物品。
- Set Container Slot：状态编号、单个槽位。
- Set Cursor Item。
- Set Player Inventory Slot：参数为玩家物品栏下标，wiki 写明它与窗口槽位的顺序不同，下标到窗口 0 槽位的对应关系按原版客户端行为编码。
- Set Container Property：按属性编号保存整数。
- Set Held Item (clientbound)、Merchant Offers。
- wiki Set Container Slot 规定：打开其它窗口时，服务端对该窗口玩家物品栏部分的修改不会再针对窗口 0 重发，客户端必须自己同步到窗口 0。`InventoryModel` 按 `WindowLayouts` 把这些槽位的修改同时写入窗口 0。

**点击**

- 发送 Click Container：窗口编号、最近一次收到的状态编号、槽位、按键、模式，变化槽位列表为空，光标物品为空。
- wiki Click Container 写明：服务端执行点击后，把客户端给出的变化叠加到点击前的服务端状态上再比较，不一致的槽位用 Set Container Slot 发回正确结果；状态编号不一致时发回完整的 Set Container Content。客户端声明「没有变化」时，点击改变的每个槽位都会由服务端发回。
- 光标物品与客户端声明不一致时，服务端用 Set Cursor Item 发回（原版服务端行为）。本库声明光标为空，所以点击后光标不为空时总会收到 Set Cursor Item。
- 所以本库不在客户端预测点击结果，物品栏内容只由服务端发回的数据更新。MCProtocolLib 没有提供 Hashed Slot 的哈希计算；做预测还需要实现全部点击规则。
- 点击在协议中没有确认包，点击的 future 在数据包写出时完成，测试用条件等待检查物品栏内容。
- 关闭窗口发送 Close Container。关闭玩家物品栏时窗口编号为 0（wiki Close Container）。

#### 6.6 聊天

**接收**

- Player Chat Message、Disguised Chat Message、System Chat Message、Delete Message 写入长度有限的消息日志（`BotOptions.chatLogSize`，默认 256 条）。
- 带 overlay 标记的 System Chat Message 写入 `HudModel` 的动作栏。

**发送**

- Chat Message 不签名（签名字段缺省），不发送 Player Session。
  - 消息超过 256 个字符时在本地抛出 `IllegalArgumentException`（wiki：服务端会踢出客户端）。
  - Login (play) 的 Enforces Secure Chat 为真时，调用立即抛出 `IllegalStateException`。
- Chat Command 不签名，命令长度上限 32767。

**已读消息跟踪**

- `LastSeenTracker` 记录带签名的 Player Chat Message。
- Chat Message 中 Message Count、Acknowledged（20 位）、Checksum 的含义与计算方法取自 wiki Chat Message 一节：校验值对最近消息签名的校验值按 `Arrays.hashCode(byte[])` 的方法计算，转换为 byte，结果为 0 时改为 1。
- 未确认的消息数超过 64 时发送 Acknowledge Message（原版客户端行为）。

#### 6.7 界面状态

`HudModel` 保存：

- Boss Bar
- Set Title Text、Set Subtitle Text、Set Title Animation Times、Clear Titles
- Set Action Bar Text 与带 overlay 标记的 System Chat Message
- Set Tab List Header And Footer
- Player Info Update / Player Info Remove（档案、游戏模式、延迟、是否列出、显示名）
- Update Objectives、Display Objective、Update Score、Reset Score、Update Teams
- Server Data

#### 6.8 时间与 tick 状态

- `WorldClocks`：Update Time 中的 World Age 原样保存。各个时钟的时间与小数部分在每个客户端 tick 增加 Rate（wiki：Rate 为每个客户端 tick 推进的时钟 tick 数）。
- `TickState`：Set Ticking State 的 tick 速率与冻结标记、Step Tick 的步数。
- `WorldBorderState`：Initialize World Border 与各个 Set Border 包。大小插值随客户端 tick 推进。
- `Weather`：使用 Game Event 7、8 的降雨与雷暴强度。wiki 指出事件 1、2 的效果与名称相反，并且原版服务端紧接着会发送事件 7，所以忽略 1、2。

### 7. 时钟与客户端 tick

#### 7.1 BotClock

| 方法 | 行为 |
|---|---|
| `freeze()` | 停止自动 tick。当前正在执行的 tick 完成后生效 |
| `resume()` | 恢复自动 tick |
| `step(int n)` | 只能在冻结状态、其它线程调用；在 `EventLoop` 上连续执行 n 次 tick，全部完成后返回 |
| `isFrozen()` / `ticks()` | 是否冻结 / 已执行的 tick 总数 |

- 初始状态由 `BotOptions.clockMode` 决定（`REALTIME` 或 `FROZEN`）。
- 自动 tick 以固定延迟调度在 Bot 的 `EventLoop` 上，周期为 `1000 / min(tickRate, 20)` 毫秒，`tickRate` 取自 Set Ticking State，默认 20。wiki Commands/tick 写明：高于默认值的 tick 速率不影响玩家；冻结时除玩家及玩家骑乘的实体外都不运行。本库只模拟本地玩家，所以服务端冻结状态只记录在 `TickState` 中，玩家 tick 照常进行。
- `ClockGroup`（`BotRuntime.clockGroup(Bot...)`）对一组 Bot 统一冻结、恢复与步进。步进时每一步向每个 Bot 的 `EventLoop` 提交一次 tick，全部完成后进入下一步。

时钟只控制客户端自己的模拟。数据包随时到达随时处理（Bundle 除外，见 7.2），冻结期间 Keep Alive、Ping、区块批次确认、传送确认等应答照常进行，世界模型照常随服务端数据更新。

#### 7.2 Bundle

wiki Bundle Delimiter 规定：两个分隔符之间的数据包必须在同一 tick 内处理，原版客户端不接受超过 4096 个数据包的 bundle。`BundleBuffer` 在分隔符之间缓存数据包，收到结束分隔符时一次性交给 `PacketRouter`，超过 4096 个时抛出异常断开连接。tick 是单独的事件循环任务，因此一个 bundle 的处理过程中不会插入 tick。

#### 7.3 一次 tick 的步骤

`ClientTicker.tick()`：

1. 执行 `TaskQueue` 中等待下一个 tick 的操作（例如冻结期间修改的按键在下一次 tick 生效）。
2. 推进计数：物品冷却、药水效果剩余时间、`WorldClocks`、`WorldBorderState`、`PlayerLoadTracker` 的 tick 计数。
3. **`PlayerLoadTracker`**：
   - 加载条件取自 wiki FAQ：自上次 Login (play) 或 Respawn 以来收到过 Game Event 13，并且满足以下任意一条：
     - 玩家所在区块已加载；
     - 玩家位于维度高度范围之外；
     - 玩家为旁观模式；
     - 玩家已死亡。
   - 条件是否满足由数据包更新，`StateReader.player().loadConditionsMet()` 可以查询。条件满足后的第一次 tick 发送 Player Loaded，玩家从这次 tick 开始模拟。
   - wiki Player Loaded 记载：原版客户端自 Login (play) 或 Respawn 起经过 60 tick 后开始模拟玩家，并且不再发送 Player Loaded。本库照此处理。
   - 玩家开始模拟时 `loaded()` 完成。
4. **玩家模拟**（玩家已开始模拟时）：
   - 多 tick 任务更新按键与朝向（`DigTask`、`UseItemTask`、`MoveToTask`）。
   - 按键变化时发送 Player Input（wiki：按下或松开时发送；潜行通过 Player Input 告知）。
   - 疾跑状态变化发送 Player Command 1/2，开始滑翔发送 Player Command 6（wiki Player Command）。
   - 未骑乘时执行 `PlayerPhysics`，然后由 `MovementSender` 发送移动包：位置变化发送位置，朝向变化发送朝向，只有标记变化时发送 Set Player Movement Flags，静止时每 20 tick 至少发送一次位置（wiki Set Player Movement Flags）。判断位置变化的阈值为位移平方大于 `(2.0E-4)^2`（原版客户端行为）。
   - 骑乘时发送 Set Player Rotation 与 Player Input，载具位置由服务端给出。
5. `DigTask` 推进挖掘进度，需要时发送 Player Action 2 与 Swing Arm。
6. 发送 Client Tick End（每个 tick 一次）。
7. 检查条件等待。

### 8. 物理

**输入**

- `MovementInput`：前、后、左、右、跳跃、潜行、疾跑，与 wiki Player Input 的按键位一一对应。
- 朝向。
- `LocalPlayer` 中的属性、药水效果、能力、姿态、装备。
- `CollisionWorld`：`ChunkStore` 与 `BlockStateTable` 给出的方块碰撞箱、世界边框、可碰撞实体的包围盒。
- 方块物理常量：摩擦系数、速度系数、跳跃系数。
- 流体状态。
- 服务端下发的方块标签。

**输出**：位置、速度、是否着地、是否顶住墙壁（Set Player Position 的 0x02 标记）、下落状态、姿态。

**服务端给出的速度**：

- Synchronize Player Position 的速度字段，按 Teleport Flags 处理相对值。
- 自己实体编号的 Set Entity Velocity。
- Explosion 中的玩家击退。

**计算过程**：wiki Set Player Position 一节写明，完整描述移动需要整个物理引擎，wiki 没有给出算法。`PlayerPhysics` 的计算过程按原版 26.2 客户端实现（原版客户端行为），分为以下阶段：

1. 按键转换为加速度：潜行乘以 `minecraft:sneaking_speed` 属性，使用物品时减速。
2. 疾跑的开始与结束条件。
3. 跳跃：`minecraft:jump_strength` 属性乘以方块跳跃系数，加上跳跃提升效果。
4. 按所处介质分别计算：
   - 地面与空中，使用脚下方块的摩擦系数；
   - 水中、熔岩中；
   - 可攀爬方块（`minecraft:climbable` 标签）；
   - 创造与旁观模式飞行；
   - 鞘翅滑翔（胸甲槽物品带 `minecraft:glider` 组件）。
5. 重力：`minecraft:gravity` 属性，以及缓降、飘浮效果。
6. 碰撞：按轴分别处理，包括台阶抬升（`minecraft:step_height` 属性）、潜行时在方块边缘停住、方块速度系数、蜘蛛网等方块的减速、世界边框。
7. 包围盒尺寸乘以 `minecraft:scale` 属性。

**不模拟的内容**：载具（船、马等）的客户端模拟。骑乘时只发送朝向与按键，载具位置取自 Move Vehicle (clientbound) 与 Synchronize Vehicle Position。

**必须满足的约束**：取自 wiki Set Player Position 中服务端的检查规则。

- 单个移动包的位移不能超过「移动过快」的上限。
- 服务端从当前位置做一次带碰撞的移动，结果与上报位置的差距不能大于 0.25（y 方向小于 0.5 的差距不计）。
- 上报位置的包围盒不能与方块相交。
- 悬空状态不能连续超过 80 tick。

**验收数值**：取自 wiki Transportation 与 Jumping 页。

| 场景 | 数值 |
|---|---|
| 平地行走 | 4.317 m/s |
| 平地斜向行走 | 4.405 m/s |
| 平地疾跑 | 5.612 m/s |
| 平地潜行 | 1.3 m/s |
| 跳跃高度 | 1.2522 格 |
| 跳跃提升 I 时的跳跃高度 | 1.8361 格 |
| 跳跃提升 II 时的跳跃高度 | 2.5168 格 |
| 从蜂蜜块起跳的高度 | 3/16 格 |

### 9. 动作接口

`Bot.actions()` 返回 `Actions`，`Bot.inventory()` 返回物品栏操作，`Bot.input()` 返回按键状态。动作方法可以在任意线程调用，在 Bot 的 `EventLoop` 上执行，返回 `CompletableFuture`。

**方块交互的序号**：wiki 对 Sequence 的定义为用手左键方块、右键使用物品、开始或结束挖掘时各加一。`BlockSequence` 维护这个计数，收到 Acknowledge Block Change 时完成序号小于等于确认值的全部 future。

| 方法 | 发送的数据包 | future 完成条件 | 依据 |
|---|---|---|---|
| `look(yaw, pitch)` / `lookAt(Vector3d)` | 下一次 tick 的移动包携带朝向 | 该移动包写出 | 朝向计算公式取自 wiki Set Player Rotation |
| `input().press(Key)` / `release(Key)` / `set(Set<Key>)` | 下一次 tick 发送 Player Input | 该包写出 | wiki Player Input |
| `selectHotbar(int)` | Set Held Item (serverbound) | 写出 | |
| `attack(int entityId)` | Attack，然后 Swing Arm(主手) | 写出 | 发送 Attack 取自 wiki；挥手为原版客户端行为 |
| `interact(int entityId, Hand, Vector3d offset)` | Interact，潜行标记取自当前按键 | 写出 | wiki Interact |
| `useItem(Hand)` | Use Item（序号、当前朝向） | 收到该序号的确认 | wiki Use Item |
| `releaseUseItem()` | Player Action 5 | 写出 | wiki Player Action |
| `useItemOn(Vector3i, Direction, Hand, Vector3f cursor)` | Use Item On（序号；Inside block 按眼睛位置是否在方块碰撞箱内计算；World Border Hit 为 false） | 收到该序号的确认 | wiki Use Item On |
| `placeBlock(Vector3i against, Direction face, Hand)` | `useItemOn`，光标取该面的中心 | 同上 | |
| `breakBlock(Vector3i, Direction)` | 见下文 | 收到最后一个序号的确认 | wiki Player Action、Breaking |
| `cancelBreak()` | Player Action 1，面固定为 -Y | 写出 | wiki Player Action |
| `drop(boolean wholeStack)` | Player Action 3 或 4 | 写出 | wiki Player Action |
| `swapHands()` | Player Action 6 | 写出 | wiki Player Action |
| `swing(Hand)` | Swing Arm | 写出 | |
| `chat(String)` / `command(String)` | Chat Message / Chat Command | 写出 | 见 6.6 |
| `respawn()` | Client Status 0 | 收到 Respawn | wiki Client Status |
| `moveTo(double x, double z, double tolerance, int maxTicks)` | 每个 tick 设置朝向与前进键，水平方向被阻挡且前方高度差不超过 1 格时跳跃 | 水平距离进入容差时完成；超过 `maxTicks` 时以异常结束 | 本库提供的辅助行为 |
| `inventory().click(int slot, int button, ClickMode)` 及 `leftClick`、`shiftClick`、`swapWithHotbar`、`dropSlot` 等组合 | Click Container | 写出 | 见 6.5 |
| `inventory().clickButton(int)` | Click Container Button | 写出 | wiki Click Container Button |
| `inventory().close()` | Close Container | 写出 | wiki Close Container |
| `send(Packet)` | 任意数据包 | 写出 | 供测试发送本库没有封装的数据包 |

**挖掘（`DigTask`）**

- 每 tick 的进度按 wiki Breaking 页的公式计算，涉及以下因素：
  - 手持物品生效的 `minecraft:tool` 组件；
  - `minecraft:mining_efficiency`、`minecraft:block_break_speed`、`minecraft:submerged_mining_speed` 属性；
  - 急迫、潮涌能量、挖掘疲劳效果（挖掘疲劳使用 wiki 给出的固定系数 0.3、0.09、0.0027、0.00081）；
  - 是否着地、是否在水中；
  - 方块硬度；
  - 是否可以采集。
- 开始时发送 Player Action 0（带序号）。
- 进度在开始时已达到 1（瞬间破坏），或玩家为创造模式时，不发送 Player Action 2（wiki Player Action）。创造模式手持无法破坏方块的物品时按 wiki Breaking 页的规则处理。
- 其它情况每 tick 累加进度，达到 1 时发送 Player Action 2（带序号）。挖掘期间每 tick 发送 Swing Arm（原版客户端行为）。
- 非瞬间破坏完成后，下一次挖掘开始前等待 6 tick（wiki Breaking）。
- 挖掘进度只在客户端 tick 中推进，时钟冻结时挖掘停住，由测试推进。

**不在客户端检查距离**：wiki 记录了服务端对攻击、交互（4 格）与挖掘（6 格）的距离检查。本库不在客户端预先检查，数据包照常发送，结果由服务端决定，测试可以借此验证服务端的检查。

### 10. 查询、事件与等待

**查询**

- `Bot.view()` 返回 `BotView`，方法返回不可变快照：`PlayerSnapshot`、`BlockState`、`EntitySnapshot`、`WindowSnapshot`、`ChatLine`、`HudSnapshot`，物品堆叠直接使用 MCProtocolLib 的 `ItemStack`。
- `Bot.inspect(Function<StateReader, T>)` 在 `EventLoop` 上执行函数，多个读取在同一时刻完成，结果一致。`StateReader` 是只读接口，函数返回值不应包含 `StateReader` 给出的可变对象。

**事件**

- `BotEvent` 是 sealed 接口，子类型包括：
  - 连接：`Connected`、`ConfigurationStarted`、`Joined`、`Loaded`、`Disconnected(reason, cause)`
  - 世界：`BlockChanged(pos, old, new)`、`ChunkLoaded`、`ChunkUnloaded`、`EntitySpawned`、`EntityRemoved`
  - 玩家：`Teleported`、`HealthChanged`、`Died`、`Respawned`
  - 物品栏：`SlotChanged(window, slot, old, new)`、`WindowOpened`、`WindowClosed`
  - 聊天与界面：`ChatReceived`、`TitleShown`、`BossBarChanged`
  - 原始数据包：`PacketReceived(packet)`
- `Bot.events().on(Class<E>, Consumer<E>)` 返回可关闭的订阅。
- `PacketReceived` 只投递给订阅了对应数据包类的监听器，没有订阅时不产生对象。

**等待**

| 方法 | 行为 |
|---|---|
| `Bot.await(Predicate<StateReader>, Duration)` | 条件在每个数据包（或 bundle）处理后和每次 tick 后检查；超时抛出 `BotTimeoutException` |
| `Bot.awaitEvent(Class<E>, Predicate<E>, Duration)` | 等待满足条件的事件并返回它 |
| `Bot.awaitPacket(Class<P>, Predicate<P>, Duration)` | 等待满足条件的数据包并返回它 |
| `Bot.when(Predicate<StateReader>)` | 不阻塞，返回 `CompletableFuture` |

### 11. 错误处理

本库遵循就地失败：出错的位置抛出异常，Bot 随之结束，异常传到测试代码。

- **数据包处理器中的异常**：由 MCProtocolLib 的 `NetworkSession.callPacketReceived` 捕获并调用 `disconnect(message, cause)`。`DisconnectedEvent` 到达后：
  - Bot 记录原因并进入 `DISCONNECTED`；
  - 全部未完成的 future 与等待以 `BotFailureException(cause)` 结束；
  - 之后对该 Bot 的调用立即抛出同一个异常。
- **tick 中的异常**：Netty 的事件循环只记录日志然后继续运行，异常不会到达测试代码。`ClientTicker` 在 tick 任务的最外层把异常交给 `session.disconnect(reason, cause)`，让 Bot 按上一条的方式结束。
- **解码错误**：MCProtocolLib 的 `PacketErrorEvent` 不做抑制，解码异常导致断开连接。
- **数据错误**：未知方块状态、注册表缺少 NBT、静态数据与协议版本不一致，都在使用处抛出 `IllegalStateException`。
- **服务端断开**：Disconnect 与 Disconnect (login) 的原因写入 `BotFailureException`。
- **不提供替代值**：例如查询未加载区块中的方块直接抛出 `ChunkNotLoadedException`。

### 12. 资源占用

**线程**

- 所有 Bot 共用 `BotRuntime` 的事件循环。
- 数据包在网络线程直接处理。
- 不创建 MCProtocolLib 默认的数据包线程池和静态事件循环组（`getEventLoopGroup` 已重写，`packetHandlerExecutor` 由构造参数给出）。

**内存**

- 区段在同一 JVM 内共享（6.2）。
- 光照与高度图在解码后丢弃。
- 加载区域外的区块立即卸载。
- 默认视距为 2。服务端按客户端视距与服务端视距中较小的值发送区块（原版服务端行为）。
- 聊天日志有长度上限。
- 静态数据每个 JVM 只加载一次，方块状态按编号存放在原始类型数组中，碰撞箱去重存储。

**可关闭的模型部分**：`BotOptions.modelFeatures` 可以关闭 `CHUNKS`、`ENTITIES`、`CHAT_LOG`、`HUD`。

- 关闭 `CHUNKS` 时，区块数据在完成区块批次统计后丢弃，物理不可用，按键与 `moveTo` 调用抛出 `IllegalStateException`。
- 关闭 `CHUNKS` 时，「玩家所在区块已加载」这一条无法满足，Player Loaded 由其它条件或 60 tick 规则触发（wiki Player Loaded）。

**系统调用**：同一 tick 内的数据包合并为一次写出（4.1）。

### 13. Transport

```java
public interface Transport {
    /// 返回相同 key 的 Transport 共用同一个 EventLoopGroup
    Object groupKey();

    EventLoopGroup newEventLoopGroup(int threads, ThreadFactory threadFactory);

    ChannelFactory<? extends Channel> channelFactory();

    SocketAddress remoteAddress();

    /// 写入 Handshake 包的服务器地址与端口
    String handshakeHost();

    int handshakePort();

    void configureBootstrap(Bootstrap bootstrap);

    /// 在 MCProtocolLib 的处理器全部加入管线之后调用
    void configurePipeline(ChannelPipeline pipeline);
}
```

**`TcpTransport(String host, int port)`**

- 通道工厂与事件循环组工厂取自 MCProtocolLib 的 `TransportHelper.TRANSPORT_TYPE`，自动选择 epoll、kqueue、io_uring 或 NIO。
- 设置 `TCP_NODELAY`（wiki FAQ：原版客户端与服务端都关闭 Nagle 算法）。
- 远端地址为 `InetSocketAddress.createUnresolved(host, port)`，由 MCProtocolLib 的 `NettyHelper.resolveAddress` 解析。
- `configurePipeline` 不做任何事。

**`TransportResolver`**

- 接口为 `Transport resolve(String host, int port)`，处理 Transfer 时使用。
- 默认实现返回 `TcpTransport`。
- 测试可以把特定主机与端口映射到自己的 Transport。

**进程内传输**

本库只提供上面的接口，不包含进程内传输的实现。实现者需要满足：

- 通道上的字节在 `configurePipeline` 处理之后符合 wiki Packet format（VarInt 长度前缀，启用压缩后使用压缩格式）。如果对端以「每条 Netty 消息恰好一个数据包、没有长度前缀」的方式收发，实现者在 `configurePipeline` 中替换 MCProtocolLib 的长度处理器（`NetworkConstants.SIZER_NAME`）。
- `groupKey()` 用来区分不兼容的事件循环类型，同一 key 的 `newEventLoopGroup` 创建的事件循环必须能注册 `channelFactory()` 创建的通道。
- `handshakeHost()` 与 `handshakePort()` 给出写入 Handshake 包的值。

### 14. 公开接口示例

```java
try (BotRuntime runtime = BotRuntime.create()) {
    Bot bot = runtime.newBot(BotOptions.builder()
            .username("alice")
            .transport(new TcpTransport("127.0.0.1", 25565))
            .clockMode(ClockMode.FROZEN)
            .build());

    bot.connect().join();
    bot.await(state -> state.player().loadConditionsMet(), Duration.ofSeconds(10));
    bot.clock().step(1);                      // 这次 tick 发送 Player Loaded
    bot.loaded().join();

    bot.input().press(Key.FORWARD);
    bot.clock().step(20);
    PlayerSnapshot player = bot.view().player();

    CompletableFuture<Void> breaking = bot.actions().breakBlock(Vector3i.from(0, 64, 3), Direction.UP);
    while (!breaking.isDone()) {
        bot.clock().step(1);                  // 挖掘进度只随 tick 推进
    }
    breaking.join();

    bot.await(state -> state.world().blockAt(0, 64, 3).isAir(), Duration.ofSeconds(5));
}
```

### 15. 测试

README 规定本库不编写端到端测试，只编写单元测试。测试使用真实的输入：

- 数据包由 MCProtocolLib 的构造方法创建，并经过它的编码与解码。
- 静态数据从 `net.minestom:data` 加载。
- 时钟测试使用真实的 Netty `EventLoop`。

处理器与 `ClientTicker` 只把待发送的数据包写入 `Outbox`（见 4.1），测试直接创建 `Outbox` 并检查其中的数据包，不需要网络连接。

| 测试对象 | 内容 |
|---|---|
| `data` | 方块状态总数与调色板位宽；版本检查；冰与浮冰的摩擦系数 0.98、蓝冰 0.989（wiki Blue Ice 页记载的数值） |
| `world` | 区块解码；区段共享与写入时复制；加载区域移动后的卸载；重新配置时注册表与标签的清空 |
| `network` | Bundle 的原子处理与 4096 上限；各项自动应答的内容；区块批次确认的数值计算；Transfer 时 cookie 的保留 |
| `physics` | 第 8 节的验收数值；每一步之后包围盒与方块不相交 |
| `inventory` | 窗口槽位到窗口 0 的同步；点击包字段 |
| `chat` | Checksum 计算（与 wiki 描述的计算方法比对） |
| `interaction` | 挖掘时间与 wiki Breaking 页示例一致（急迫 II、效率 V 钻石镐瞬间破坏石头）；序号确认的完成规则 |
| `loop` | 冻结、恢复、步进、`ClockGroup` 同步步进；在事件循环线程上调用阻塞方法时抛出异常 |
