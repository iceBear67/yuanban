# 测试

[目录](README.md) · 上一篇：[错误处理](errors.md) · 下一篇：[限制与待办](limitations.md)

本篇目录：

- [测试方式](#测试方式)
- [运行测试](#运行测试)
- [测试工具](#测试工具)
  - [TestPeer](#testpeer)
  - [TestServer](#testserver)
  - [ChunkBuilder](#chunkbuilder)
- [编写一个运行 bot 的测试](#编写一个运行-bot-的测试)
- [各测试类的范围](#各测试类的范围)

测试源码：[`bot/src/test/java/io/ib67/yuanban/bot`](../../bot/src/test/java/io/ib67/yuanban/bot)。

## 测试方式

- 本库只编写单元测试，不编写端到端测试（[`bot/README.md`](../../bot/README.md) 的规定）。进程内连接由外部模块提供，TCP 连接接入真实服务端的验证同样不在本库测试中。
- 测试使用真实输入：
  - 数据包为 MCProtocolLib 的数据包对象；需要字节时用 MCProtocolLib 的 `MinecraftTypes` 与 `MinecraftCodec` 生成；区块数据用 `DataPalette` 与 `MinecraftTypes.writeChunkSection` 生成（`ChunkBuilder`）。
  - 同步注册表与标签取自 MCProtocolLib 自带的原版数据（`MinecraftProtocol.loadNetworkCodec()`、`loadNetworkTags()`），按 MCProtocolLib `ServerListener` 的方法转换为 Registry Data 与 Update Tags。
  - 静态数据取自 Minestom 的方块数据与 `net.minestom:data`，与运行时相同。
  - 档案为 MCProtocolLib 的 `GameProfile`。
- 组件（解码、注册表、世界模型、移动计算、挖掘速度、已读跟踪、窗口布局、bundle 与批次、物品数据、属性、方块谓词、数据包方向、元数据下标）直接构造并检查。
- 需要运行 bot 的测试由测试代码扮演服务端一侧：`TestPeer` 实现 `Transport`，测试线程串行地把数据包对象交给 bot，并检查 bot 交给连接的数据包。
- `TcpConnection` 的测试在回环地址上打开 `ServerSocketChannel`，由测试代码扮演服务端，用 MCProtocolLib 的 `MinecraftCodec` 与 `MinecraftTypes` 按阶段与压缩阈值读写帧。

## 运行测试

```bash
./gradlew :bot:test
./gradlew :bot:test --tests 'io.ib67.yuanban.bot.movement.MovementBotTest'
```

测试使用 JUnit Jupiter，工具链为 Java 25，配置在根目录的 [`build.gradle.kts`](../../build.gradle.kts)。

## 测试工具

### TestPeer

[`TestPeer`](../../bot/src/test/java/io/ib67/yuanban/bot/TestPeer.java) 实现 `Transport`，它打开的 `ClientConnection` 按顺序记录 bot 交出的全部数据包。

| 方法 | 作用 |
|---|---|
| `push(packets...)` | 测试线程按顺序把数据包交给 bot（串行调用 `InboundSink.accept`） |
| `close(error)` | 服务端关闭连接 |
| `sent()`、`mark()`、`sentSince(mark[, type])` | 读取 bot 发出的数据包；`mark()` 记下当前位置 |
| `awaitSent(mark, type[, filter], timeout)` | 等到 bot 从 `mark` 开始发出一个满足条件的数据包，超时时以 `AssertionError` 失败 |
| `barrier()` | 发送一个 Keep Alive 并等它的应答：应答发出时，之前交给 bot 的数据包都已经应用，观察记录都已经投递 |
| `holdWrites()` | 之后交出的数据包照常记录，写出要等返回的 future 完成：正常完成表示写出，异常完成表示写出之前连接断开。用来检查 Transfer 时旧连接没有写出数据包的情况 |
| `closedByBot()` | bot 是否关闭了连接 |

### TestServer

[`TestServer`](../../bot/src/test/java/io/ib67/yuanban/bot/TestServer.java) 是测试线程扮演服务端时使用的数据包脚本：

| 方法 | 作用 |
|---|---|
| `registryData(omitData)`、`tags()` | 原版的全部 Registry Data 与 Update Tags；`omitData` 为 true 时条目的数据为 null |
| `registryEntries`、`registryId`、`overworldId`、`netherId`、`biomeCount` | 原版注册表中的条目与编号 |
| `configure(peer, username)` | 从 Login Finished 到 Finish Configuration，等到 bot 回应 Finish Configuration |
| `joinPacket(gameMode)`、`netherRespawn(gameMode, copyData)` | 进入主世界的 Login (play)；重生到下界的 Respawn |
| `flatChunk()` | y 小于 0 为石头、其余为空气的区块 |
| `join(peer, spawn[, gameMode, chunk])` | Login (play)、传送到出生点、加载中心 ±1 的区块、Game Event 13，等到 bot 发送 Player Loaded |
| `joinedBot(runtime, peer, username, spawn)` | 新建 bot、完成配置、进入平地世界 |
| `configuredBot(runtime, peer, username, options)` | 新建 bot 并完成配置，可以修改 `BotOptions` |

常量：本地玩家的实体编号 `ENTITY_ID` 为 42，等待时限 `TIMEOUT` 为 10 秒。

### ChunkBuilder

[`ChunkBuilder`](../../bot/src/test/java/io/ib67/yuanban/bot/ChunkBuilder.java) 生成 Chunk Data and Update Light 数据包：`overworld()`（最低 y −64，高度 384）、`set(x, y, z, Block)`、`fill(fromY, toY, Block)`、`blockEntity(x, y, z, 类型, NBT)`、`biome(编号)`、`data(biomeCount)`、`biomeData(biomeCount)`、`packet(chunkX, chunkZ, biomeCount)`。方块数按非空气状态计数，方块实体为 `BlockEntityInfo`。

## 编写一个运行 bot 的测试

```java
@Test
void chatIsSent() {
    try (var runtime = BotRuntime.create()) {
        var peer = new TestPeer();
        var bot = TestServer.joinedBot(runtime, peer, "alice", new Pos(0.5, 0, 0.5));

        var mark = peer.mark();
        assertTrue(bot.chat("hello").await(TestServer.TIMEOUT));
        var chat = peer.awaitSent(mark, ServerboundChatPacket.class, TestServer.TIMEOUT);
        assertEquals("hello", chat.getMessage());
    }
}
```

- 服务端发来的数据包用 `peer.push` 交给 bot；需要确认 bot 已经处理完这些数据包时调用 `peer.barrier()`，然后再查询模型或读取 observer。
- bot 发出的数据包用 `mark` 与 `awaitSent`、`sentSince` 检查。
- 动作的结束用 `Action.await` 等待；observer 用 `awaitClose` 或者 `close` 之后读取。

## 各测试类的范围

| 测试类 | 范围 |
|---|---|
| `decode.DecodeTest` | 方块实体取区段中的方块；相同区段共享；区段字节重新写出后相同；区段之后有剩余字节时出错；Encryption Request 不支持；区块按最近一次 Login (play) 或 Respawn 的维度确定区段数；重新配置删除重新发送的注册表的标签 |
| `transport.TcpConnectionTest` | 两侧的阶段与压缩；`close()` 之前交出的批次照常写出；写出中连接断开时批次以异常结束；`close()` 丢弃套接字不能接受的数据并释放连接；编码失败关闭连接；连接失败时抛出异常；登录阶段的读取超时 |
| `protocol.LoginFlowTest` | 登录、配置与进入世界的发送顺序与内容；Known Packs 只回应匹配的 core |
| `protocol.PacketDirectionTest` | 按全部协议阶段判断数据包方向；方向不对与没有注册的类被拒绝；`MinecraftPacket.class` 两个方向都接受 |
| `protocol.ProtocolRepliesTest` | Ping 与 Cookie；资源包的两种回应；区块批次与传送的应答；Transfer 重新连接并保留 cookie |
| `protocol.BufferAndBatchTest` | bundle 在结束分隔符处整批交出；超过 4096 个时拒绝；区块批次的数值；cookie 的 5 KiB 上限 |
| `protocol.WorldPacketsTest` | 世界、实体、本地玩家、聊天、界面相关数据包的处理与 bot 因异常结束的情况（共 35 项，见方法名） |
| `protocol.WorldTransitionTest` | Transfer、重新配置、时钟、`joined`/`loaded` 等待下一个世界、Respawn 的各项保留与重建、重复的 Login (play)、传送速度的旋转、乘坐状态的清除、实体移除与重新生成 |
| `registry.SyncedRegistriesTest` | 注册表的顺序与编号；省略数据时取自原版；数据包中的维度类型数据优先；原版中没有的条目省略数据时失败；重新配置清空之前的注册表；编号换成名称；生物群系数量；标签的逐个替换与删除 |
| `data.ItemDataTest` | 物品编号；四种默认组件按原版编解码器补全；其它组件被拒绝；补丁覆盖与移除默认组件；物品的比较 |
| `player.AttributeMapTest` | 默认值与取值范围；三种修饰符的顺序；Respawn 时的复制 |
| `player.PlayerLoadTrackerTest` | 收到 Game Event 13 之前不发送也不超时；条件满足或超过 30 秒时发送；不在世界中时的 Game Event 13 被忽略 |
| `view.EntityMetaIndexTest` | 各常量的下标、掩码与默认值与 Minestom 的 `MetadataDef` 相同；没有收到的条目读出默认值 |
| `world.ClientWorldTest` | 写入时复制；快照不受之后写入的影响；高度范围之外与未加载区块；加载区域的移动与半径；Chunk Biomes；世界边框插值期间使用上一 tick 的大小；方块实体单独读取 |
| `movement.KinematicsTest` | wiki 的移动数值；飞行；使用物品时的减速；缓降、飘浮与 NoGravity；移动相关属性与起跳规则；`movement_efficiency`；支撑方块决定脚下方块 |
| `movement.MovementBotTest` | `rotate` 与 `lookAt`；服务端设置的朝向；指向本地玩家的 Synchronize Vehicle Position 插值；`moveTo` 的平地、台阶、半砖与楼梯、高度比较、跳下、找不到路径、未加载区块、重新寻路、寻路期间的传送与方块改变；`moveForward` 被阻挡；`jumpTo`；着地停止飞行；疾跑条件；暂停移动；骑乘船、矿车、马与骆驼；马的人立动画；手部动作的朝向优先 |
| `interaction.DigSpeedTest` | wiki Breaking 页的公式与示例 |
| `interaction.BlockPredicatesTest` | `can_break` 的方块集合、属性范围、NBT 部分匹配与组件条件 |
| `interaction.InteractionBotTest` | 挖掘每个 tick 的数据包与挖掘延迟；更换手持物品时重新开始；穿刺武器；冒险模式；旁观模式；挖掘中变成瞬间破坏；`breakBlock` 的结论；`placeBlock`；Inside block 与世界边框；`useItem` 的保持；`attack` 的目标 |
| `inventory.WindowLayoutTest` | 独有槽位数与 wiki 相同；打开窗口中玩家物品栏部分写入窗口 0 |
| `inventory.InventoryBotTest` | 点击与关闭使用打开的窗口；按键与模式不对应时拒绝；每个窗口各自的状态编号与光标；Close Container 不看编号；槽位越界时 bot 结束；身体护甲与鞍；马与鹦鹉螺的窗口；选择快捷栏 |
| `chat.LastSeenTrackerTest` | Checksum 的计算；结果为 0 时改为 1；已读窗口与新增消息数；`ignorePending`；没有显示的消息 |
| `scheduler.SchedulerTest` | 一次性请求等到 Player Loaded；没有 Game Event 13 时不加载；不 tick 时暂停状态改变也更新状态；等待线程共用推进函数；两条队列互相独立；各种取消的结果；世界重建与玩家死亡取消队列；进入世界之前的请求；`BotRuntime` 关闭期间的连接与查询；在调度线程上阻塞时抛出异常；一个 bot 失败不影响其它 bot；模型入口拒绝其它线程 |
| `observe.ObserveTest` | 只投递注册之后的记录；模型记录排在数据包之前，bundle 先整体应用；在回调中注册的 observer；只投递订阅的类型；`Subscription` 的检查；关闭原因；回调抛出异常时 bot 结束；`onClose` 与 `subscription()` 抛出异常的各种情况；`observeDuring` 的关闭时机；只能注册一次；跨线程读取内置 observer；`WorldBoundObserver` 切换世界 |
| `observe.builtin.BuiltinObserversTest` | 粒子与音效的范围；`limit`；数据包类型在构造时拒绝；`movementOf`；`blockAt`；`blockBroken` 换世界时关闭；距离 observer；不在世界中时注册的 observer 观察下一个世界；`condition`、`firstChat`、`damageTaken`、`firstTranslatedChat` |

[目录](README.md) · 上一篇：[错误处理](errors.md) · 下一篇：[限制与待办](limitations.md)
