# 错误处理

[目录](README.md) · 上一篇：[查询与观察](observation.md) · 下一篇：[测试](testing.md)

本篇目录：

- [原则](#原则)
- [异常类型](#异常类型)
- [bot 因异常结束](#bot-因异常结束)
- [observer 回调中的异常](#observer-回调中的异常)
- [连接结束](#连接结束)
- [结束之后的调用](#结束之后的调用)
- [结束 bot 的输入](#结束-bot-的输入)
- [调用线程上抛出的异常](#调用线程上抛出的异常)

相关源码：[`scheduler/BotInstance.java`](../../bot/runtime/src/main/java/io/ib67/yuanban/bot/scheduler/BotInstance.java) 中的 `fail`、`disconnected`、`end`。

## 原则

就地失败：出错的位置抛出异常，所属的 bot 结束，异常传到测试代码；同一个 `BotRuntime` 中的其它 bot 不受影响。observer 回调中的异常只关闭这个 observer，交给持有它的一方，bot 不受影响（见[下文](#observer-回调中的异常)）。本库不提供替代值：查询未加载区块的方块直接抛出 `ChunkNotLoadedException`，注册表条目取不到值时直接失败；动作读到未加载的区块时以 `CHUNK_NOT_LOADED` 取消这个动作。服务端断开连接属于游戏中可能发生的结果，不算程序错误。

## 异常类型

| 异常 | 含义 | 出现位置 |
|---|---|---|
| `ActionCancelledException` | 动作被取消，异常信息为取消原因常量 | `Action.future()`；`await` 不抛出它，返回 false |
| `BotDisconnectedException` | 连接已经结束；`reason()` 为断开原因的 `Component`，异常信息为它的纯文本 | `Action.await`、`future()`、`ObservedAction.await`、`Bot.await`、提交动作、`observe` |
| `BotFailureException` | bot 因程序错误结束，`getCause()` 为原因 | 同上，以及查询 |
| `ObserverFailureException` | observer 的回调抛出了异常，这个 observer 以 `FAILED` 关闭，bot 不受影响；`getCause()` 为回调抛出的异常 | `observe`、`observeDuring`（注册时）、`ObserverHandle.awaitClose`、`ObserverHandle.close`（这次关闭中 `onClose` 抛出异常时）、`ObservedAction.await`、`Bot.await` |
| `BotTimeoutException` | 阻塞等待超过时限 | 各个 `await`、`awaitClose` |
| `ChunkNotLoadedException` | 读取了没有加载的区块；`chunkX()`、`chunkZ()` 给出区块坐标 | `StateReader.block`、`blockEntity`；动作读到未加载的区块时不抛出它，以 `CHUNK_NOT_LOADED` 取消 |
| `IllegalStateException` | 状态不允许这个调用，例如不在任何世界中时读取玩家、在调度线程上阻塞、重复注册 observer | 调用线程 |
| `IllegalArgumentException` | 参数不合法 | 调用线程 |
| `UncheckedIOException` | TCP 连接失败 | `BotRuntime.connect` |

## bot 因异常结束

调度线程上的异常（数据包处理、请求执行、tick）在 `round` 中被捕获，交给 `fail`；连接上的异常经 `InboundSink.closed(error)` 进入入站队列，同样交给 `fail`。`fail(原因)`：

1. 已经有失败原因时什么都不做。
2. 记录原因（`Bot.failure()`），状态进入 `DISCONNECTED`，关闭连接。
3. 连接还没有结束时，执行[结束的过程](#连接结束)，未结束的动作以 `BotFailureException(原因)` 结束。

异常的来源：

- **调度线程**：数据包处理中的异常（例如编号不在注册表中、聊天全局编号不连续、更新不存在的 Boss Bar、物品栏槽位越界）；`observeDuring` 的 `submit` 抛出的异常；observer 回调期间这个 observer 已经被关闭之后回调又抛出的异常（见下一节）。
- **连接**：解码失败（包括 MCProtocolLib 遇到不认识的枚举编号）、`InboundSink.accept` 中解码上下文的异常（注册表构建失败、Encryption Request）、写出失败、套接字错误。
- **其它线程**：寻路线程上的异常在移动动作取回结果时抛出；Transfer 打开新连接失败时在下一轮抛出。

调度线程是所有 bot 共用的，异常不会让它退出。

## observer 回调中的异常

相关源码：[`observe/dispatch/ObservationDispatcher.java`](../../bot/api/src/main/java/io/ib67/yuanban/bot/observe/dispatch/ObservationDispatcher.java) 中的 `register`、`call`、`close`。

observer 的 `subscription()`、`onStart`、`onObservation`、`onClose` 抛出异常时，只有这个 observer 以 `FAILED` 关闭：从订阅索引中移除，不调用它的 `onClose`（`onClose` 本身抛出异常的除外），异常记在 `ObserverHandle.failure()` 中。bot 与其余 observer 不受影响，同一条观察记录照常交给其余 observer。异常交给持有这个 observer 的一方，以下方法抛出 `ObserverFailureException`，`getCause()` 为回调抛出的异常：

| 方法 | 情况 |
|---|---|
| `observe`、`observeDuring` | 注册时 `subscription()` 或 `onStart` 抛出异常；`observeDuring` 这时不调用 `submit`。observer 算作注册过 |
| `ObserverHandle.awaitClose` | observer 以 `FAILED` 关闭，包括调用之前已经这样关闭的情况 |
| `ObserverHandle.close` | 这次关闭中 `onClose` 抛出异常；在这之前已经关闭的（包括已经以 `FAILED` 关闭的）什么都不做 |
| `ObservedAction.await` | observer 以 `FAILED` 关闭：立即抛出，不等动作结束，也不取消动作 |
| `Bot.await` | 条件抛出异常（注册时的检查或之后的检查） |

连接结束时 `onClose` 抛出的异常同样只记在这个 observer 上，见[连接结束](#连接结束)。

例外：回调期间这个 observer 已经被关闭（在自己的回调中调用 `handle.close()`，或者连接在回调中结束），之后回调又抛出异常。这时关闭原因已经确定，异常无法再随关闭交给持有者，所以原样抛出，按[上一节](#bot-因异常结束)结束 bot。

## 连接结束

服务端断开（Disconnect、Disconnect (login)、对端关闭连接、读取超时）、`Bot.disconnect()`、`BotRuntime.close()` 都调用 `disconnected(原因)`：记录断开原因（`Bot.disconnectReason()`），状态进入 `DISCONNECTED`，关闭连接，然后执行 `end`：

1. 丢弃一次性请求队列，停止 tick。
2. 丢弃待投递的观察记录，以 `DISCONNECTED` 关闭全部 observer。某个 observer 的 `onClose` 抛出异常时，只有它以 `FAILED` 关闭，异常记在它的句柄上，其余 observer 照常关闭。
3. 全部未结束的动作以 `BotDisconnectedException(原因)` 结束。

observer 在动作之前关闭，所以等待动作的线程得到结果时，observer 已经可以直接读取。

本地断开的原因为「disconnected by the client」，连接被对端正常关闭的原因为「connection closed」。

## 结束之后的调用

| 调用 | 连接正常结束之后 | bot 因异常结束之后 |
|---|---|---|
| 提交动作、`observe`、`observeDuring`、`joined`、`loaded` | 抛出 `BotDisconnectedException` | 抛出 `BotFailureException` |
| 查询（`inspect`） | 照常读取断开时的模型 | 抛出 `BotFailureException` |
| `status()`、`disconnectReason()`、`failure()` | 可以调用 | 可以调用 |
| 读取 observer（`handle.observer()`、`handle.read`） | observer 都已关闭，直接读取 | 同左 |

`observe` 因 bot 已经结束而失败时，observer 不算注册过，可以交给其它 bot。

## 结束 bot 的输入

以下情况让 bot 以表中的异常为原因结束（`Bot.failure()`，动作与之后的调用得到 `BotFailureException`）。其中来自服务端的数据大多对应原版客户端断开连接的情况，其余属于本库不支持的功能：

| 输入 | 异常 |
|---|---|
| Encryption Request（服务端开启正版验证） | `UnsupportedOperationException` |
| 部分过滤（PARTIALLY_FILTERED）的 Player Chat Message（待办 T2） | `UnsupportedOperationException` |
| 冒险模式下有方块实体的位置遇到带组件条件的 `can_break`（待办 T4） | `UnsupportedOperationException` |
| Player Chat Message 的全局编号不连续 | `IllegalStateException` |
| 按编号引用的签名超出范围或不在缓存中 | `IllegalStateException` |
| 聊天类型、伤害类型、世界时钟、维度类型的编号不在注册表中 | `IllegalStateException` |
| Registry Data 中省略数据、原版中又不存在的条目 | `IllegalStateException` |
| 更新没有添加过的 Boss Bar | `IllegalStateException` |
| 创建已经存在的计分板目标；移除不在队伍中的成员 | `IllegalStateException` |
| Set Container Content 的物品数多于槽位数；Set Container Slot 的槽位不在窗口中；Set Player Inventory Slot 的下标为负数 | `IndexOutOfBoundsException` |
| 实体乘坐自己 | `IllegalStateException` |
| Store Cookie 的数据超过 5 KiB | `IllegalArgumentException` |
| bundle 超过 4096 个数据包 | `IllegalStateException` |
| Set Ticking State 的速率不是正数 | `IllegalStateException` |
| 连接的握手发出之前收到数据包；收到不属于当前协议阶段的数据包；不允许的连接状态转移（[协议状态机 · 连接阶段](protocol.md#连接阶段)） | `IllegalStateException` |

## 调用线程上抛出的异常

以下异常在调用线程上抛出，bot 不受影响：

- 参数检查失败（[动作 · 参数检查](actions.md#参数检查)）；
- `Subscription` 中数据包类的方向不对，或者直接订阅 `PacketReceived`、`PacketSent`；内置 observer 的工厂方法收到这样的类型；
- 重复注册 observer；注册之后调用 `limit`；`distanceWithIn`、`distanceOutOf` 的动作不属于这个 bot；`observeDuring` 的 `submit` 返回了其它动作；
- 在调度线程上调用阻塞方法；在调度线程以外调用 `ObserverContext.close()`；观察中跨线程读取内置 observer；
- 不在任何世界中时读取玩家、方块、方块实体；读取未加载的区块；`equipment` 传入窗口 0 中有槽位的装备槽位；
- 在调度线程以外调用修改模型的内部入口（`BotState.checkThread`）；
- observer 的回调抛出异常（[observer 回调中的异常](#observer-回调中的异常)，`ObserverFailureException`）。

[目录](README.md) · 上一篇：[查询与观察](observation.md) · 下一篇：[测试](testing.md)
