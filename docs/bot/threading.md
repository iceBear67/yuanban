# 线程与调度

[目录](README.md) · 上一篇：[架构参考](architecture.md) · 下一篇：[连接与解码](connection.md)

本篇目录：

- [线程一览](#线程一览)
- [调度循环](#调度循环)
- [唤醒调度线程](#唤醒调度线程)
- [tick 周期](#tick-周期)
- [线程封闭](#线程封闭)
- [跨线程调用](#跨线程调用)
- [阻塞等待与推进函数](#阻塞等待与推进函数)
- [BotRuntime 的关闭](#botruntime-的关闭)

相关源码：[`scheduler/Scheduler.java`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/Scheduler.java)、[`scheduler/BotInstance.java`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/BotInstance.java)、[`scheduler/Waits.java`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/Waits.java)、[`scheduler/WaitPump.java`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/WaitPump.java)。

## 线程一览

| 线程 | 创建位置 | 说明 |
|---|---|---|
| 调度线程 | `Scheduler` 的构造方法，`Thread.ofPlatform().daemon(true)`，名为 `yuanban-bot-scheduler-<序号>` | 每个 `BotRuntime` 一个，修改它调度的全部 bot 的状态 |
| Netty 事件循环 | `NettyGroup`，第一次建立 TCP 连接时创建，按 MCProtocolLib 的 `TransportHelper` 选择 epoll、kqueue 或 NIO，线程数为 Netty 的默认值，守护线程，名称以 `yuanban-bot-netty` 开头，不关闭 | JVM 中全部 TCP 连接共用。MCProtocolLib 自己的共享事件循环组的延迟创建不是线程安全的，所以本库自己持有一组 |
| 寻路线程 | `PathPlanner.plan`，虚拟线程，名为 `yuanban-bot-path` | 每次寻路一个，算完结束 |
| Transfer 线程 | `BotInstance.transfer`，虚拟线程，名为 `yuanban-bot-transfer` | 每次 Transfer 一个，新连接打开之后结束 |
| 调用线程 | 使用者的线程 | `BotRuntime.connect` 在调用线程上打开第一条连接；动作方法、查询、注册 observer 都只把任务放进队列 |

## 调度循环

`Scheduler.loop` 循环执行：

1. 记下此刻是否已经开始关闭（`closing`）。
2. 按创建顺序对每个 bot 调用 `round(System.nanoTime())`，取各个返回值中最早的时间。
3. 开始关闭时退出循环。
4. 没有到期时间时 `LockSupport.park`；有到期时间时 `parkNanos` 到那个时间。

`BotInstance.round` 的顺序：

| 步骤 | 内容 |
|---|---|
| 1 | 执行 `observerTasks` 中的任务（其它线程交来的 observer 注册与关闭），然后投递观察记录 |
| 2 | 取出入站队列的全部条目。`Received` 的代数与当前连接不同、或者 bot 已经结束时丢弃；否则先检查这条连接的握手已经发出（第 4 步的接入已经执行）、数据包属于当前连接状态对应的协议阶段，不满足时结束 bot（[协议状态机 · 连接阶段](protocol.md#连接阶段)），然后交给 `BundleBuffer`，得到一批数据包后逐个交给 `ProtocolMachine.handle`，处理过程中 bot 结束或者代数改变（Transfer）时停止处理这一批的其余数据包；每批之后投递观察记录。`Closed` 的代数为当前连接时，带异常的结束 bot（`fail`），不带异常的按「connection closed」断开 |
| 3 | 完成上一轮在调度线程上调用、调用时已经发生过的 `joined()` 与 `loaded()` |
| 4 | 执行 `requests` 中的任务（动作入队、取消、查询、等待 `joined`/`loaded`、新连接的接入、`disconnect`），然后投递观察记录 |
| 5 | bot 没有结束时：根据 `Bot` 与 `BotRuntime` 两级的请求更新实际的暂停状态，改变时产生 `MovementSuspensionChanged`；检查 TCP 连接的读取超时 |
| 6 | bot 处于 `PLAY` 并且已经加载时，按提交顺序执行全部一次性请求 |
| 7 | tick 到期时执行 `BotTicker.tick` |
| 8 | 把 `Outbox` 中的数据包交给连接，投递 `PacketSent`，安排写出即执行完毕的动作 |
| 9 | 本轮应用了数据包、执行了一次性请求、执行了 tick，或者实际的暂停状态改变时，产生并投递 `StateUpdated` |

整个 `round` 包在 `try` 中，任何异常都交给 `fail` 结束这个 bot。返回值为下一次需要调度这个 bot 的时间：下一次 tick 的时间与读取超时到期时间中较早的一个；bot 已经结束时为 `Long.MAX_VALUE`。

步骤 3 的存在是为了 `observeDuring(o, Bot::loaded)`：在调度线程上调用、已经发生过的 `joined()`、`loaded()` 推迟到下一轮执行完毕，这样 `observeDuring` 可以在动作结束之前把 observer 绑定到动作上。

## 唤醒调度线程

以下情况调用 `Scheduler.wakeup()`（`LockSupport.unpark`）：

- 连接交出数据包或关闭通知（`Sink.accept`、`Sink.closed`）；
- 其它线程提交任务（`BotInstance.submit`：动作、取消、查询、observer 注册与关闭、`disconnect`）；
- `suspendMovement`、`resumeMovement`（`Bot` 与 `BotRuntime` 两级）；
- `BotRuntime.connect` 加入新的 bot；
- 在调度线程上调用、已经发生过的 `joined()`、`loaded()`；
- `BotRuntime.close`。

登录与配置阶段不 tick，调度线程按 TCP 连接的读取超时到期时间醒来检查超时（[连接与解码 · 读取超时](connection.md#读取超时)）。

## tick 周期

- 每个 bot 单独维护下一次 tick 的时间 `nextTickNanos`，间隔为 `1_000_000_000 / min(tickRate, 20)` 纳秒。`tickRate` 取自这个世界收到的 Set Ticking State，默认 20；速率不是正数（包括 NaN）时抛出异常，bot 结束。
- 只在 `PLAY` 阶段并且在世界中时 tick。其余时间 `nextTickNanos` 为 0。
- 进入世界（Login (play) 与 Respawn）时 `nextTickNanos` 归零，所以处理这个数据包的那一轮不 tick，第一次 tick 在一个间隔之后。
- 每轮最多执行一次 tick。落后超过 5 个间隔时丢弃积压，下一次 tick 时间改为当前时间，不追赶。
- 依据为 wiki Commands/tick：高于 20 的 tick 速率不影响玩家；世界冻结时玩家照常运行（冻结只影响其它实体、世界边框与时间，见[世界模型 · tick 状态](world-model.md#tick-状态)）。
- tick 的计时只存在于调度器内部，公开接口不提供。

## 线程封闭

- 世界模型、动作队列、协议状态只在调度线程上读写。
- `BotState.checkThread()` 在其它线程调用时抛出 `IllegalStateException`。调用它的入口：`ProtocolMachine.handle`、`startLogin`、`leaveWorld`；`MovementController.enqueue`、`cancelAll`、`tick`、`onServerRotation`；`HandController.enqueue`、`tick`、`cancelAll`、`discardAll`、`acknowledge`、`newGameMode`、`newWorld`、`ensureHasSentCarriedItem`、`carriedItemSent`。
- observer 的字段只由调度线程修改。内置 observer 的读取方法在观察期间被其它线程调用时抛出 `IllegalStateException`（`ObserverContextImpl.checkReadable`）。
- 调度线程退出之后模型不再变化，查询与读取 observer 直接在调用线程上执行。

## 跨线程调用

| 调用 | 在其它线程 | 在调度线程上 |
|---|---|---|
| 动作方法 | 检查参数，创建 `ActionImpl`，把入队任务放进 `requests`，立即返回 | 直接入队 |
| `Action.cancel()` | 取消请求放进 `requests`，下一轮处理 | 立即处理 |
| `view()` 下的方法、`inspect` | 作为查询任务放进 `requests`，等待结果（`Waits.join`，不执行推进函数） | 直接执行 |
| `observe`、`observeDuring` | 放进 `observerTasks`，等到注册完成才返回（不执行推进函数） | 立即注册 |
| `ObserverHandle.read` | observer 还在观察时作为查询任务执行；已经关闭时直接执行 | 直接执行 |
| `ObserverHandle.close` | 放进 `observerTasks`，等到调度线程处理完才返回 | 立即关闭 |
| `Bot.disconnect` | 放进 `requests` | 立即断开 |
| `suspendMovement` 等 | 只设置 volatile 标记并唤醒，下一轮生效 | 同左 |

bot 因异常结束之后，查询立即抛出 `BotFailureException`；连接正常结束之后，查询仍可读取断开时的模型。

## 阻塞等待与推进函数

会阻塞的方法：`Action.await`、`ObservedAction.await`、`Bot.await`、`ObserverHandle.awaitClose`、`BotRuntime.close`。它们在调度线程上调用时抛出 `IllegalStateException`：调度线程等待自己永远等不到结果。

进程内测试常用手动推进的服务端（例如 `net.minestom:testing` 的 `Env.tick()`）。测试线程阻塞等待 bot 时，服务端如果不推进，等待就会超时。`RuntimeOptions.waitPump(Runnable)` 设置推进函数：

- `Action.await`、`ObservedAction.await`、`Bot.await`、`ObserverHandle.awaitClose` 在等待期间推进服务端（`Waits.await`）。
- 同一个 `BotRuntime` 中所有等待的线程共用一个 `WaitPump`：推进函数在任何时刻最多由一个线程执行（`ReentrantLock.tryLock`），两次执行的开始时间至少相隔 50 毫秒。到了推进的时间，由正在等待的线程中先检查到的一个在它自己的线程上执行，其余线程只检查完成状态。所以多个线程同时等待时，整体的推进间隔仍为 50 毫秒，与 bot 的 20 TPS 接近。
- 等待的线程每次最多等到下一次推进的时间（另一个线程正在推进、时间还没有更新时至少等 1 毫秒），然后再检查一次。
- 推进函数抛出的异常传给执行它的等待线程。
- 没有设置推进函数时，等待方法只检查完成状态。
- `observe`、`observeDuring`、查询、`ObserverHandle.close` 的等待不依赖服务端，只等调度线程处理完任务（`Waits.join`），不执行推进函数。

超时抛出 `BotTimeoutException`，信息为「timed out after 时长 waiting for 等待对象」。

## BotRuntime 的关闭

`BotRuntime.close()`（`Scheduler.close`）：

1. 在调度线程上调用时抛出 `IllegalStateException`；已经开始关闭时直接返回。
2. 对每个 bot 调用 `disconnect()`，然后标记开始关闭并唤醒调度线程，等调度线程退出（`Thread.join`）。
3. 调度线程处理完最后一轮之后退出循环，设置 `running = false`，对每个 bot 调用 `stopped()`。

`BotInstance.stopped()`（`synchronized`）断开还没有结束的 bot（关闭期间加入的 bot），然后在当前线程上执行仍在队列中的任务。调度线程退出之后才入队的任务（查询、注册与关闭 observer、动作请求、Transfer 线程打开的新连接），由提交任务的线程在 `submit` 中调用 `stopped()` 执行：这时 bot 都已经结束，等待这些任务的线程得到结果或异常，之后才打开的连接被关闭。

`BotRuntime.connect` 在 `BotRuntime` 已经关闭时抛出 `IllegalStateException`；打开连接期间 `BotRuntime` 开始关闭时，断开这个 bot 并抛出 `IllegalStateException`。

[目录](README.md) · 上一篇：[架构参考](architecture.md) · 下一篇：[连接与解码](connection.md)
