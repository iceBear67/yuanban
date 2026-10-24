# 快速上手

[目录](README.md) · 下一篇：[架构参考](architecture.md)

本篇目录：

- [依赖](#依赖)
- [创建运行时与 bot](#创建运行时与-bot)
- [等待进入世界](#等待进入世界)
- [提交动作](#提交动作)
- [查询模型](#查询模型)
- [观察变化](#观察变化)
- [自定义 observer](#自定义-observer)
- [暂停移动](#暂停移动)
- [线程约定](#线程约定)

## 依赖

在模块的 `build.gradle` 中依赖 `project(":bot:runtime")`。`:bot:runtime` 以 `api` 方式传递 `:bot:api`、`:bot:transport` 与 MCProtocolLib、Minestom、fastutil，公开接口中出现的类型（数据包、`ItemStack`、`EntityType`、`Point`、`Block`、adventure 的 `Component` 与 `Key`、Cloudburst 的 `NbtMap`）都随之可用。内部模块（`:bot:data`、`:bot:model`、`:bot:control`、`:bot:protocol`）不在使用者的编译类路径上，见[架构参考 · 公开接口与内部实现](architecture.md#公开接口与内部实现)。依赖的版本与理由见[架构参考 · 外部库的使用范围](architecture.md#外部库的使用范围)。

## 创建运行时与 bot

`BotRuntime` 持有一个调度线程，由它调度在这个运行时中创建的全部 bot。`close()` 断开全部 bot，等调度线程退出之后返回。

```java
try (BotRuntime runtime = BotRuntime.create(RuntimeOptions.builder()
        .waitPump(env::tick)   // 服务端由测试手动推进时设置；不需要时用 BotRuntime.create()
        .build())) {
    Bot bot = runtime.connect(BotOptions.builder()
            .username("alice")
            .transport(new TcpTransport("127.0.0.1", 25565))
            .build());
    // ...
}
```

- `BotOptions` 中只有 `username` 与 `transport` 必须设置，其余选项的默认值见 [`BotOptions`](../../bot/api/src/main/java/io/ib67/yuanban/bot/BotOptions.java)：`transportResolver`（Transfer 时为新地址选择连接方式，默认 TCP）、`readTimeout`（默认 30 秒）、`loginPluginHandler`、`resourcePackPolicy`、`acceptCodeOfConduct`、`brand`、`chatLogSize`，以及 Client Information 的各项内容。
- `TcpTransport` 只支持离线模式的服务端，不支持加密。连接到同一进程中的服务端时，由外部模块实现 `Transport`，要求见[连接与解码 · 进程内连接](connection.md#进程内连接)。
- `runtime.connect` 在调用线程上打开连接，建立 TCP 连接时阻塞；连接失败时抛出 `UncheckedIOException`。握手数据包在下一轮调度中发出。
- `waitPump` 设置的推进函数只在阻塞等待期间执行，规则见[线程与调度 · 阻塞等待与推进函数](threading.md#阻塞等待与推进函数)。

## 等待进入世界

```java
bot.joined().await(Duration.ofSeconds(10));   // Login (play) 处理完成
bot.loaded().await(Duration.ofSeconds(10));   // bot 发送了 Player Loaded
```

bot 发送 Player Loaded 之前，动作队列都不开始执行。收到 Game Event 13（开始等待区块）之后，玩家所在区块已经加载，或者自 Login (play) 或 Respawn 起已经超过 30 秒时，bot 发送 Player Loaded。服务端从不发送 Game Event 13 时，bot 一直不加载。完整的条件见[世界模型 · 加载判断](world-model.md#加载判断)。

## 提交动作

动作方法只把请求放进队列，立即返回 `Action`：

```java
Action walk = bot.moveTo(new Vec(10, 64, 10));
boolean done = walk.await(Duration.ofSeconds(10));   // 执行完毕为 true，被取消为 false
```

- `await` 在连接结束时抛出 `BotDisconnectedException`，bot 因异常结束时抛出 `BotFailureException`，超时抛出 `BotTimeoutException`。
- `future()` 在动作被取消时以 `ActionCancelledException` 完成，异常信息是取消原因，取值为该类中的常量（`NO_PATH`、`BLOCKED` 等）。
- `cancel()` 取消动作。

动作分为三类：移动（`moveTo`、`moveForward`、`jumpTo`、`rotate`、`lookAt`）、手部交互（`attemptBreakBlock`、`placeBlock`、`useItemOn`、`useItem`、`attack`、`interact`）、一次性请求（`chat`、`command`、`selectHotbar`、`swapHands`、`drop`、`respawn`、`inventory()` 下的物品栏操作、`send`）。移动与手部交互各有一条先进先出的队列，两条队列同时运行。执行规则与每个动作「执行完毕」的含义见[动作](actions.md)。

`Action` 只表示请求的执行过程是否走完，例如挖掘执行完毕只表示服务端处理了挖掘请求。动作在游戏中的效果由 observer 观察。

## 查询模型

```java
Pos position = bot.inspect(StateReader::player).position();
Block below = bot.inspect(state -> state.block(position.sub(0, 1, 0)));
NbtMap sign = bot.inspect(state -> state.blockEntity(new Vec(3, 64, 3)));   // 方块实体发给客户端的 NBT，没有时为 null

// 多项读取在同一时刻完成；返回值中不要包含 StateReader 给出的可变对象
record Status(float health, @Nullable ItemStack firstHotbarSlot) {}
Status status = bot.inspect(state -> new Status(state.player().health(), state.inventory().slot(36)));

// 等到条件成立，超时抛出 BotTimeoutException
bot.await(state -> state.player().onGround(), Duration.ofSeconds(5));
```

- `inspect` 在其它线程调用时把函数交给调度线程执行并等待结果。`StateReader` 的方法返回不可变的快照。
- 物品为 MCProtocolLib 的 `ItemStack`，空槽位为 null。`inventory()` 是窗口 0（玩家物品栏），槽位编号按 wiki Inventory。
- `block` 与 `blockEntity` 在区块没有加载时抛出 `ChunkNotLoadedException`；bot 不在任何世界中时，`player`、`block`、`blockEntity` 抛出 `IllegalStateException`。

各个快照的字段见[查询与观察 · 查询](observation.md#查询)。

## 观察变化

`bot.observe(observer)` 注册 observer，返回 `ObserverHandle`。observer 订阅观察记录（`Observation` 中的嵌套 record）或数据包，在调度线程上更新自己的状态。通常的顺序是：先注册 observer，再提交动作，等动作结束之后检查 observer 的状态。

```java
Point trap = new Vec(10, 64, 10);
var damage = bot.observe(Observers.damageTaken());
var zombie = bot.observe(Observers.movementOf(
        e -> e.type() == EntityType.ZOMBIE,
        MovementCriteria.create().atLeast(2.0).towards(trap, 30)));

assertTrue(bot.moveTo(trap).await(Duration.ofSeconds(10)));

// 能自行得出结论的 observer 在条件满足时关闭自己
zombie.awaitClose(Duration.ofSeconds(3));
assertTrue(zombie.observer().met());

// 只做记录的 observer 由句柄关闭，关闭之后任何线程都可以直接读取
damage.close();
assertTrue(damage.observer().items().isEmpty());
```

只观察一个动作执行期间的变化时使用 `observeDuring`，动作结束之后 observer 随之关闭：

```java
ObservedAction<BlockBrokenObserver> dig = bot.observeDuring(Observers.blockBroken(pos), b -> b.attemptBreakBlock(pos));
assertTrue(dig.await(Duration.ofSeconds(5)).broken());   // 等同于 bot.breakBlock(pos)
```

观察期间读取 observer 的状态使用 `handle.read(o -> ...)`，读取函数在调度线程上执行。内置 observer 的完整列表与各自的结论方法见[查询与观察 · 内置 observer](observation.md#内置-observer)。

## 自定义 observer

```java
final class PickupCounter implements Observer {
    private int picked;

    @Override
    public Subscription subscription() {
        return Subscription.of(Observation.ItemPickedUp.class);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (observation instanceof Observation.ItemPickedUp(_, int collector, int count)
                && collector == context.state().player().entityId()) {
            picked += count;
        }
    }

    int picked() {
        return picked;
    }
}

var counter = bot.observe(new PickupCounter());
// ...
int picked = counter.read(PickupCounter::picked);
```

- 订阅数据包使用 `Subscription.NONE.received(...)` 与 `sent(...)`，多个订阅用 `and` 合并。
- 状态与实体编号、方块坐标有关的 observer 继承 `WorldBoundObserver`，bot 进入新世界时它自动结束旧世界的状态、创建新世界的状态。
- 同一个 observer 实例可以多次注册，每次注册各自投递观察记录。

## 暂停 tick 与步进

`BotRuntime.suspendTicking()` 暂停全部 bot 的 tick，`resumeTicking()` 恢复。暂停时入站数据包、自动应答与查询继续处理；一次性请求等到恢复或步进时执行。`stepTicks(n)` 返回按创建顺序排列的动作，让每个尚未结束的 bot 执行 n 次 tick（已经断开的 bot 不会得到动作），这 n 次 tick 连续执行，中间没有 tick 间隔。

## 暂停移动

```java
bot.suspendMovement();      // 只暂停这个 bot
runtime.suspendMovement();  // 暂停全部 bot
bot.resumeMovement();
runtime.resumeMovement();
```

任意一级处于暂停状态时，该 bot 的移动就暂停；暂停与恢复在下一轮调度中生效。暂停只停止速度与位置的计算（包括重力），`moveTo`、`moveForward`、`jumpTo` 不再推进；朝向、手部交互、一次性请求与协议应答照常进行。原版服务端会在玩家悬空 80 tick 之后踢出玩家（允许飞行的情况除外），Minestom 没有这项检查。细节见[移动模拟与骑乘 · 暂停移动](movement.md#暂停移动)。

## 线程约定

- 会阻塞的方法（`Action.await`、`ObservedAction.await`、`Bot.await`、`ObserverHandle.awaitClose`、`BotRuntime.close`）在调度线程上调用时抛出 `IllegalStateException`，所以不能在 observer 的回调中调用。
- observer 的全部回调，以及 `Action.future()` 上不带 async 的后续回调，都在调度线程上执行。回调阻塞会让同一个 `BotRuntime` 中的全部 bot 停住。

[目录](README.md) · 下一篇：[架构参考](architecture.md)
