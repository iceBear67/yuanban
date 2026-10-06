# 用 Minestom 网络层实现 bot 客户端的评估

## 前提

- 对照的 Minestom 版本：`net.minestom:minestom:2026.10.05-26.2`（提交 `fcd35c05`，2026-10-05），`MinecraftConstants.PROTOCOL_VERSION = 776`，与 MCProtocolLib `26.2-SNAPSHOT` 的协议号相同。
- 对照的设计：`ARCH.md`（以 MCProtocolLib 为编解码库）。
- 客户端行为仍以 minecraft.wiki 为准，规则与 `ARCH.md` 相同。Minestom 在本方案中只作为编解码库和数据库使用，它的服务端逻辑（例如 `BlockBreakCalculation`、`InventoryClickProcessor`）不作为客户端行为的依据。
- 文中标注 `@Internal` 的类或方法，在 Minestom 源码中带有 `@ApiStatus.Internal`。

---

## 1. 简化程度

按 `ARCH.md` 的包逐个对照：

| ARCH.md 中的包 | 换用 Minestom 后 | 原因 |
|---|---|---|
| `data`（`GameData`、`GameDataSource`、`MinestomDataSource`、`BlockStateTable`、`BoxTable`、`ItemTable`、`EntityTypeTable`） | **整个删除** | `Block`、`Material`、`EntityType` 直接给出状态编号、属性、碰撞箱、轮廓箱、摩擦系数、速度系数、跳跃系数、硬度、是否需要工具、物品默认组件、实体尺寸，见 2.5 |
| `data/server`（`ServerRegistries`、`DimensionTypes`） | 换成用 Registry Data 构造 Minestom 的 `Registries` | 解码游戏阶段数据包需要 `Registries`（见 2.4），所以这部分必须做，做完之后维度类型和生物群系数量直接从 `Registries` 读取 |
| `data/server`（`TagTable`） | 保留 | Minestom 静态注册表中的标签是它自带的原版标签，服务端下发的标签仍需单独保存，见 4.4 |
| `world` | 结构不变，区段类型换成 Minestom 的 `Palette` | `ChunkData.Section.networkType(biomeCount)` 直接解码区段；`ChunkStore` 实现 `Block.Getter` 后可以使用 Minestom 的几何工具 |
| `world` 中的区段共享 | 改为按原始字节去重 | Minestom 的 `Palette` 没有 `equals`/`hashCode`，见 3 |
| `entity` | 元数据读取变简单 | `MetadataDef` 给每个元数据索引起了名字（例如 `MetadataDef.IS_CROUCHING`、`MetadataDef.POSE`），不再需要自己维护索引常量 |
| `interaction` 中的 `DigSpeed` | 工具规则查询变简单 | `ItemStack.get(DataComponents.TOOL)` 得到 `Tool`，`Tool.getSpeed`、`Tool.isCorrectForDrops` 直接可用；公式本身仍按 wiki 编写 |
| `inventory` | 不变 | 点击仍然不预测结果，见 3 |
| `physics` | 计算过程不变，几何类型换成 `BoundingBox`、`Vec`、`Pos` | Minestom 的碰撞工具不能替代原版玩家物理，见 3 |
| `chat` | 不变 | Minestom 的 `LastSeenMessages` 没有 Checksum 计算 |
| `loop`、`event`、公开接口、错误处理 | 不变 | 与编解码库无关 |
| `network`、`transport` | **增加代码** | Minestom 没有客户端连接，也没有客户端协议状态机，MCProtocolLib 的 `ClientListener` 和 Netty 管线承担的工作都要自己写，见 3 |
| 线程模型 | 改写 | 去掉 MCProtocolLib 后不再有 Netty，每个 Bot 的串行执行需要自己实现，见 3 |
| 进程内连接 Minestom 服务端 | **从只有接口变为可以实现** | 两端使用同一套数据包类和同一个 `SocketChannel` 抽象，见 4 |

依赖变化：

| ARCH.md | 换用 Minestom 后 |
|---|---|
| `org.geysermc.mcprotocollib:protocol`（带来 Netty 4.2） | 删除 |
| `net.minestom:data` | 由 `net.minestom:minestom` 在运行时带入 |
| `gson` | 由 Minestom 以 `api` 方式带入 |
| `fastutil` | Minestom 以 `implementation` 方式依赖，bot 代码直接使用时仍需声明 |
| `caffeine` | 区段去重仍需要 |
| 无 | 新增 `net.minestom:minestom:2026.10.05-26.2` |

---

## 2. Minestom 能直接提供的部分

### 2.1 双向的数据包定义

- `ClientPacket`（客户端发往服务端）与 `ServerPacket`（服务端发往客户端）都是带 `SERIALIZER`（`NetworkBuffer.Type`）的 record，同一个类型既能写也能读。
- `PacketVanilla.CLIENT_PACKET_PARSER` 与 `PacketVanilla.SERVER_PACKET_PARSER` 分别列出两个方向各协议阶段的全部数据包（`@Internal`）。
- `PacketVanilla.nextServerState(ServerPacket, ConnectionState)` 给出客户端视角的状态切换：`LoginSuccessPacket`、`StartConfigurationPacket` 进入配置阶段，`FinishConfigurationPacket` 进入游戏阶段（`@Internal`）。
- Minestom 的测试 `PacketWriteReadRegistriesTest` 对两个方向的每一个数据包都做了编码再解码的往返校验，服务端数据包的解码路径有测试覆盖。

### 2.2 帧格式与压缩

| 方法 | 用途 |
|---|---|
| `PacketReading.readServers(NetworkBuffer, ConnectionState, boolean compressed)` | 从缓冲区读出若干个完整的服务端数据包，处理长度前缀与压缩格式，并在数据包之间更新协议阶段（`@Internal`） |
| `PacketWriting.writeFramedPacket(NetworkBuffer, ConnectionState, ClientPacket, int compressionThreshold)` | 按 wiki Packet format 写出带长度前缀的客户端数据包，阈值大于 0 时使用压缩格式（`@Internal`） |
| `NetworkBuffer.cipher(Cipher, long start, long length)` | 原地加解密，用于启用加密之后的读写 |
| `NetworkBuffer.readChannel(ReadableByteChannel)` / `writeChannel(WritableByteChannel)` | 与 `java.nio` 通道之间读写字节 |

### 2.3 NetworkBuffer 与运行环境

- 解码数据包不需要启动 `MinecraftServer`。在定义了序列化器的类中搜索对 `MinecraftServer` 的访问，结果如下，客户端只使用显式传入 `Registries` 的方法即可：
  - `SelectKnownPacksPacket.MINECRAFT_CORE` 读取常量 `VERSION_NAME`，不需要服务端进程；
  - `ItemStack` 与 `PacketWriting.allocateTrimmedPacket` 中不带 `Registries` 参数的重载；
  - `BlockPredicate.test`，属于判断逻辑，不在解码路径上。
- 引用注册表编号的类型（物品组件、伤害类型、附魔等）在读写时从 `NetworkBuffer` 关联的 `Registries` 中查找。

### 2.4 注册表

- `Registries.vanilla()`（公开）不需要服务端进程，得到一份原版注册表。
- `Registries.Delegating`（公开）可以组合出自定义的 `Registries`。
- `DynamicRegistry.fromMap(...)` 与 `register(Key, T)` 是公开的；`DynamicRegistry.create(key, codec)` 为 `@Internal`。
- 每种同步注册表的值类型提供 `REGISTRY_CODEC`（例如 `DimensionType.REGISTRY_CODEC`），可以把 Registry Data 中的 NBT 解码成对象。

客户端连接任意服务端时，注册表条目的顺序决定数字编号（wiki Registry Data），必须用服务端发来的 Registry Data 按顺序构造 `Registries`，不能直接使用 `Registries.vanilla()`：服务端注册了自定义条目时编号会不同。

### 2.5 静态数据

- `Block.fromStateId(int)` 得到方块状态，`Block` 提供：
  - `stateId()`、`properties()`、`collisionShape()`、`outlineShape()`
  - `friction()`、`speedFactor()`、`jumpFactor()`、`hardness()`、`requiresTool()`
  - `replaceable()`、`liquid()`、`fluid()`
- `Material` 提供物品默认组件，但 `Material.prototype()` 要等 `VanillaRegistries` 构造之后才能调用（`Registries.vanilla()` 或 `MinecraftServer.init()`）。
- `EntityType` 提供 `width()`、`height()`、`eyeHeight()`。
- 这些静态注册表在类初始化时从 `net.minestom:data` 加载，不依赖服务端进程，Minestom 自己的 `BlockTest` 就是在没有服务端的情况下调用 `Block.TORCH.collisionShape()`。
- 与服务端在同一 JVM 中运行时，客户端和服务端共用同一份静态注册表，不会重复加载。

### 2.6 区块、物品、元数据、几何

- **区块**：`ChunkData` 解码 Chunk Data and Update Light 中的高度图、区段字节数组和方块实体（方块实体解码为带 NBT 的 `Block`）；`ChunkData.Section.networkType(biomeCount)` 把区段字节解码为方块与生物群系两个 `Palette`。`Palette` 支持读写和 `clone()`。
- **物品**：`ItemStack` 带完整组件；`ItemStack.Hash.of(ItemStack, Registries)` 计算 Click Container 需要的 Hashed Slot。
- **元数据**：`MetadataDef` 为每种实体的元数据定义了带名字的条目。
- **几何**：`Pos`、`Vec`、`BoundingBox`、`Shape.intersectBox`、`ShapeImpl.boundingBoxes()`（碰撞箱的长方体列表）、`Block.Getter` 接口。

---

## 3. 仍需自己编写或不能使用的部分

**客户端连接与协议状态机**

- Minestom 只有服务端连接 `PlayerSocketConnection`。
- 下面的工作在 `ARCH.md` 中由 MCProtocolLib 的 `ClientListener` 与 Netty 管线完成，换用 Minestom 后都要自己写：
  - 打开连接；
  - 读写循环；
  - 按 wiki FAQ 的登录顺序发送 Handshake、Login Start、Login Acknowledged、Known Packs、Acknowledge Finish Configuration；
  - 收到 Set Compression 后切换读写格式；
  - 正版模式的加密与会话认证；
  - Keep Alive 应答；
  - Start Configuration 之后的重新配置。
- `ARCH.md` 4.3 的其余自动应答原本就要自己写，换用 Minestom 后不变。
- 集成测试使用离线模式时，加密与会话认证可以不实现；需要正版模式时，Minestom 的 `MojangCrypt` 只提供加密工具，会话认证的 HTTP 请求仍要自己写。

**线程模型**

- 去掉 MCProtocolLib 后不再有 Netty 的 `EventLoop`。Minestom 服务端的做法是每个连接一个读线程和一个写线程（虚拟线程），阻塞读写 `SocketChannel`。
- 客户端照此实现时，数据包处理与 tick 仍必须串行执行，每个 Bot 还需要一个串行执行器：用一个虚拟线程消费任务队列，任务包括「处理一批数据包」「执行一次 tick」「执行一个动作」；自动 tick 由定时提交任务完成。
- 每个 Bot 因此有三个虚拟线程（读、写、串行执行）。线程本身占用很小，但调度、关闭、异常传递这些原本由 Netty 处理的工作都要自己写。

**玩家物理**

- Minestom 的 `PhysicsUtils.simulateMovement` 与 `CollisionUtils.handlePhysics`（`@Internal`）是为服务端实体写的：
  - 碰撞处理按速度方向逐格扫描，没有台阶抬升；
  - 阻力由 `Aerodynamics` 统一给出。
- 这与原版玩家移动不同，用它计算出的位置会触发 wiki Set Player Position 中服务端的「移动错误」检查。
- `PlayerPhysics` 仍按 `ARCH.md` 第 8 节编写，只是改用 Minestom 的几何类型和方块数据。

**挖掘时间**

`BlockBreakCalculation.breakTicks(Block, Player)` 需要服务端的 `Player` 与 `Instance`，客户端无法调用。`DigSpeed` 仍按 wiki Breaking 页的公式编写。

**物品栏点击**

- `InventoryClickProcessor` 的左键、右键方法是纯函数，但 Shift 点击、拖动、双击、丢弃都需要服务端的 `AbstractInventory` 与 `Player`，并且它实现的是 Minestom 服务端自己的规则。
- `ARCH.md` 6.5 的做法（不预测，变化槽位列表为空，以服务端发回的数据为准）不变。
- `ItemStack.Hash` 可以让客户端在 Carried item 字段如实声明光标物品，省掉服务端多发的一次 Set Cursor Item，这一项可以不做。

**区段共享**

- Minestom 的 `Palette` 实现类 `PaletteImpl` 没有定义 `equals`/`hashCode`，`ARCH.md` 6.2 依赖 `equals`/`hashCode` 的驻留方式不能沿用。
- 改为在解码之前，按 `ChunkData.data` 中每个区段的原始字节去重：字节内容相同的区段共享同一个 `Palette`，写入时用 `clone()` 复制。

---

## 4. 进程内连接 Minestom 服务端

两端都使用 Minestom 之后，`ARCH.md` 第 13 节只留了接口的进程内传输，有两种可以实现的形式。

### 4.1 字节级：内存中的 SocketChannel

**做法**

- 客户端只依赖一个已连接的 `java.nio.channels.SocketChannel`：
  - TCP 连接使用 `SocketChannel.open(address)`；
  - 进程内连接使用一对在内存中互相连接的 `SocketChannel` 子类。
  - 客户端代码在两种情况下完全相同，`Transport` 接口只剩下「给出一个已连接的 `SocketChannel`」。
- 服务端一侧：
  - 用内存通道的另一端构造 `new PlayerSocketConnection(serverEnd, remoteAddress, readThread, writeThread)`（`@Internal`）；
  - 读线程循环调用 `connection.read(MinecraftServer.process().packetParser())`；
  - 写线程循环调用 `connection.flushSync()`，下线后调用 `cleanup()`。
  - 这两个循环在 Minestom 中是 `Server` 类的私有方法 `playerReadLoop`、`playerWriteLoop`，需要照着复制。
- 内存通道需要实现的方法：
  - 在 Minestom 源码中搜索 `PlayerSocketConnection` 对通道的使用，只有 `read(ByteBuffer)`、`write(ByteBuffer)`、`isConnected()`、`close()` 四个方法；
  - `SocketChannel` 的其它抽象方法直接抛出 `UnsupportedOperationException`；
  - `java.nio.channels.SocketChannel` 有受保护的构造方法 `SocketChannel(SelectorProvider)`，可以继承。

**得到的效果**

- 握手、登录、配置、游戏四个阶段的完整流程，以及两个方向的编码和解码，都按真实连接执行，不经过操作系统的网络层。

**需要注意的服务端设置**

- `minestom.new-socket-write-lock` 默认为 `false`，写线程在没有数据时休眠 `1000 / TPS / 2` 毫秒（20 TPS 下为 25 毫秒）。设为 `true` 后写线程改为挂起，有数据时立即被唤醒；这种模式要求调用 `flushSync()` 的线程就是构造时传入的 `writeThread`。
- 压缩阈值默认 256，`MinecraftServer.setCompressionThreshold(0)` 可以关闭压缩，省掉进程内连接上的压缩开销。
- 服务端的 tick 由测试自己驱动：调用 `MinecraftServer.start(...)`，或者像 `net.minestom:testing` 那样手动调用 `process.ticker().tick(nanoTime)`。

### 4.2 对象级：直接传递数据包对象

**做法**

- 服务端一侧实现一个 `PlayerConnection` 子类：
  - `sendPacket(SendablePacket)` 用 `SendablePacket.extractServerPacket(state, packet)` 取出 `ServerPacket` 对象交给客户端；
  - 客户端发出的 `ClientPacket` 对象，在游戏阶段之前交给 `PacketListenerManager.processClientPacket`（公开），在游戏阶段交给 `player.addPacketToQueue(...)`，在服务端 tick 中处理。
- 不经过编码、帧格式和压缩。这正是 `net.minestom:testing` 中 `TestConnectionImpl` 的做法。

**限制**

- **登录流程无法走通**：`LoginListener.loginAckListener` 遇到 `PlayerSocketConnection` 以外的连接类型时抛出 `UnsupportedOperationException("Only socket")`。登录到配置阶段的转换只能直接调用 `ConnectionManager.createPlayer`、`doConfiguration`、`transitionConfigToPlay`、`updateWaitingPlayers`（均为 `@Internal`）。`TestConnectionImpl` 因此完全跳过握手和登录。
- **部分数据包会丢失**：
  - `extractServerPacket` 遇到 `BufferedPacket` 时返回 `null`，这类数据包无法交给客户端；
  - `minestom.viewable-packet` 为默认值 `true` 时，`PacketViewableUtils` 对非 socket 连接的分组数据包直接丢弃（源码注释为 `TODO for non-socket connection`），`net.minestom:testing` 的 `EnvTestExt` 因此把它设为 `false`。
- **处理规则需要复制**：哪些数据包立即处理、哪些放进队列，在 `PlayerSocketConnection` 中由私有常量 `IMMEDIATE_PROCESS_PACKETS` 决定，需要复制一份。
- **测试覆盖不到序列化**：编码、解码与帧格式不参与测试，序列化相关的问题在这种连接下不会出现。
- **只适用于同一 JVM 中的 Minestom 服务端。**

---

## 5. 代价

**使用的 `@Internal` 接口**

| 用途 | 类与方法 |
|---|---|
| 编解码与状态切换 | `PacketVanilla`、`PacketReading`、`PacketWriting` |
| 构造注册表 | `DynamicRegistry.create` |
| 4.1 进程内连接 | `PlayerSocketConnection` 的构造方法、`flushSync`、`cleanup`，以及复制 `Server` 的私有读写循环 |
| 4.2 进程内连接 | `ConnectionManager.createPlayer`、`doConfiguration`、`transitionConfigToPlay`、`updateWaitingPlayers` |

这些接口在 Minestom 的任何一次发布中都可能改变。

**版本绑定**

- Minestom 的版本号形如 `YYYY.MM.DD-<游戏版本>`，bot 必须固定在某一个 Minestom 版本上。
- 进程内测试时客户端与服务端在同一个类路径中，bot 使用的 Minestom 版本就是被测服务端的版本。

**连接非 Minestom 服务端时的注册表解码**

- 客户端要用 Minestom 各注册表的 `REGISTRY_CODEC` 解码服务端发来的全部同步注册表。
- Minestom 用同一批编解码器加载自带的原版注册表 JSON，原版数据可以解码。
- 服务端使用自定义数据包时，如果其中有 Minestom 编解码器不支持的字段，解码会失败。

**标签**

- `Tool` 规则中的 `RegistryTag<Block>` 按 Minestom 静态注册表自带的标签判断。
- 服务端通过 Update Tags 修改了方块标签时，结果与服务端不一致。
- 要得到与服务端一致的结果，`DigSpeed` 需要用 `TagTable` 中服务端下发的标签重新判断规则，只把 `Tool` 当作规则列表使用。

**公开接口的类型**

- 公开接口使用的类型从 MCProtocolLib 的 `ItemStack`、`EntityType`、cloudburst math 向量换成 Minestom 的 `ItemStack`、`EntityType`、`Block`、`Pos`、`Vec`。
- yuanban 本身基于 Minestom，测试代码可以直接拿 bot 读到的数据与服务端对象比较，不需要类型转换。
