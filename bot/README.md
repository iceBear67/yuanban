# bot

`bot` （库）使用 [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) 处理数据包与 TCP 连接，使用 [Minestom](https://github.com/Minestom/Minestom) 的物理计算、寻路、坐标与静态方块数据，实现玩家的[网络交互协议](https://minecraft.wiki/w/Java_Edition_protocol)，使得库的使用者可以创建逻辑上的玩家对象并让他们加入特定 Minecraft 服务器以服务于集成测试。除此之外，此库有如下特点：

1. Transport 可以替换：外部模块实现 `Transport` 之后，bot 可以与同一进程中的 Minecraft（或 Minestom）实例直接沟通，不需要经过操作系统的网络连接。
2. 维护一个世界模型，使得开发者可以用直观的办法让虚拟玩家与服务器对象交互。
3. 考虑到其用于集成测试的用途，客户端本身维护世界模型的时钟（如物理计算）应当可以被冻结。
4. 轻量的资源占用。

## 基本用法

### 运行时与连接

`BotRuntime` 持有一个调度线程，调度在它之中创建的全部 bot。关闭时断开全部 bot，等调度线程退出之后返回。

```java
try (BotRuntime runtime = BotRuntime.create(RuntimeOptions.builder()
        .waitPump(env::tick)   // 服务端手动推进时设置：阻塞等待期间每 50 毫秒执行一次，多个线程同时等待时也不并发执行
        .build())) {
    Bot bot = runtime.connect(BotOptions.builder()
            .username("alice")
            .transport(new TcpTransport("127.0.0.1", 25565))
            .build());
    bot.loaded().await(Duration.ofSeconds(10));   // 等到 bot 发送 Player Loaded
    // ...
}
```

- `BotOptions` 中只有 `username` 与 `transport` 必须设置。其余选项都有默认值：`transportResolver`（处理 Transfer 时为新地址选择 Transport，默认使用 TCP）、`readTimeout`（TCP 连接在任何阶段收不到数据的时限，默认 30 秒，与原版客户端相同）、`loginPluginHandler`、`resourcePackPolicy`、`acceptCodeOfConduct`、`brand`、`chatLogSize`，以及 Client Information 的各项内容（`locale`、`viewDistance`、`chatMode`、`skinParts` 等，类型为 MCProtocolLib 的 `ChatVisibility`、`SkinPart`、`HandPreference`、`ParticleStatus`）。`locale` 按原版的语言代码格式发送：语言与国家用下划线连接，全部小写（`Locale.US` 发送为 `en_us`）。
- 本库提供的 Transport 为 `new TcpTransport(host, port)`：服务端必须使用离线模式，本库不支持加密。数据包的编码、分帧与压缩由 MCProtocolLib 的 Netty 管线完成，入站与出站都在 Netty 的事件循环上处理。
- 进程内连接由外部模块实现 `Transport` 提供。实现要求见 `Transport`、`ClientConnection`、`InboundSink` 的注释：`InboundSink.accept` 必须串行调用，交给它的是 MCProtocolLib 的数据包对象。
- `runtime.connect` 在调用线程上打开连接，建立 TCP 连接时会阻塞调用线程；连接失败时抛出 `UncheckedIOException`。
- `bot.joined()` 在 Login (play) 处理完成时执行完毕，`bot.loaded()` 在 bot 发送 Player Loaded 时执行完毕。与原版客户端相同，bot 在收到 Game Event 13（开始等待区块）之后、玩家所在区块已经加载时发送 Player Loaded，自 Login (play) 或 Respawn 起超过 30 秒时不等区块加载也发送；服务端不发送 Game Event 13 时 bot 一直不加载。bot 已经在世界中、当前世界已经加载时，两者在下一轮调度中执行完毕。不在任何世界中时（重新配置、Transfer 期间）调用的两者等待下一个世界的 Login (play) 与 Player Loaded，Respawn 之后、加载之前调用的 `loaded()` 等待这次重生之后的加载。
- `bot.status()` 给出连接所处的阶段。连接结束之后，`bot.disconnectReason()` 为断开原因，`bot.failure()` 为 bot 因异常结束时的异常。连接结束时某个 observer 的 `onClose` 抛出异常的，两者都有值，未结束的动作与之后的调用都得到 `BotFailureException`。

### 动作

动作方法只把请求放进队列，立即返回 `Action`：

- `await(timeout)`：执行完毕返回 true，被取消返回 false。连接结束时抛出 `BotDisconnectedException`，bot 因异常结束时抛出 `BotFailureException`，超时抛出 `BotTimeoutException`。
- `future()`：被取消时以 `ActionCancelledException` 完成，异常信息为取消原因（该类中的常量，例如 `NO_PATH`、`BLOCKED`）。
- `cancel()`：取消动作。

`Action` 只表示请求的执行过程是否走完，动作在游戏中的效果由 observer 观察（见下文）。

| 类别 | 方法 | 执行方式 |
|---|---|---|
| 移动 | `moveTo`（使用 Minestom 的寻路）、`moveForward`、`jumpTo`、`rotate`、`lookAt` | 先进先出，一次运行一个，按 tick 推进 |
| 手部交互 | `attemptBreakBlock`、`placeBlock`、`useItemOn`、`useItem`、`attack`、`interact` | 先进先出，一次运行一个，按 tick 推进；与移动队列同时运行 |
| 一次性请求 | `chat`、`command`、`selectHotbar`、`swapHands`、`drop`、`respawn`、`inventory()` 下的物品栏操作、`send`（发送任意客户端发往服务端的数据包，其它数据包立即抛出 `IllegalArgumentException`） | 按提交顺序在下一轮调度中执行 |

- bot 加载完成（`view().loaded()` 为 true）之前，三类请求都不开始执行。bot 不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）提交的请求同样等待，进入世界并加载之后执行。
- 离开世界（重新配置、Transfer）、在世界中收到 Login (play) 或 Respawn、玩家死亡时，移动与手部交互队列中在这个世界里提交的动作全部被取消（`WORLD_REBUILT`、`PLAYER_DIED`）；离开世界与 Login (play) 引起的取消不发送清理数据包（与原版客户端相同）。
- Transfer 之前已经交给旧连接、还没有写出的数据包，因为旧连接断开而没有写出时，在写出时执行完毕的动作以 `BotDisconnectedException` 结束，bot 继续在新连接上登录。
- `MoveOptions` 设置移动方式（`MovementMode` 的 `WALK`、`SPRINT`、`SNEAK`）、`moveTo` 判断到达的水平距离与寻路的最大距离、`moveForward` 的移动方向。`moveTo` 到达目标还要求玩家着地，并且脚部与目标的站立高度（站在目标位置时脚部的高度，按目标下方方块的碰撞箱计算，与寻路的终点相同）相差小于 1，所以目标在正上方或者正下方的另一层时不算到达。

### 查询

```java
Pos position = bot.view().player().position();
Block below = bot.view().block(position.sub(0, 1, 0));

// 多项读取在同一时刻完成；返回值中不要包含 StateReader 给出的可变对象。空槽位为 null
record Status(float health, @Nullable ItemStack firstHotbarSlot) {}
Status status = bot.inspect(state -> new Status(state.player().health(), state.inventory().slot(36)));

// 等到条件成立，超时抛出 BotTimeoutException
bot.await(state -> state.player().onGround(), Duration.ofSeconds(5));
```

- `view()` 下的方法在其它线程调用时，交给调度线程执行并等待结果，返回不可变的快照。
- `block` 返回不带 NBT 的方块，方块实体发给客户端的 NBT 用 `blockEntity` 读取（Cloudburst 的 `NbtMap`，没有方块实体数据时为 null）。两者在所在区块没有加载时抛出 `ChunkNotLoadedException`；`player`、`block` 与 `blockEntity` 在 bot 不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）抛出 `IllegalStateException`，这时 `world()` 为 null。
- 物品为 MCProtocolLib 的 `ItemStack`，空槽位、空光标与空装备为 null。`inventory()` 为窗口 0（玩家物品栏）；身体护甲与鞍在窗口 0 中没有槽位，用 `equipment(EquipmentSlot.BODY)` 与 `equipment(EquipmentSlot.SADDLE)` 读取。
- 实体的元数据为 MCProtocolLib 的 `EntityMetadata`，按下标保存；`EntitySnapshot.meta(EntityMetaIndex.POSE)` 等读取本库用到的条目，没有收到时为默认值。
- `chat()` 为聊天日志（`ChatReceived` 给出同样的 `ChatLine`）：玩家聊天与伪装聊天记录聊天类型、发送者名称与目标名称，消息内容不经过聊天类型的修饰。聊天类型来自注册表时 `chatType()` 为它的名称，随数据包给出定义时 `inlineChatType()` 为这个定义。与原版客户端相同，完全过滤的玩家聊天不记录，发送者不在玩家列表中的玩家聊天记录为 `chat.validation_error`。部分过滤的玩家聊天让 bot 以 `UnsupportedOperationException` 结束（待办 T2）。

### 观察

`bot.observe(observer)` 注册 observer，返回 `ObserverHandle`。observer 订阅观察记录（`Observation` 中的嵌套 record，例如 `BlockChanged`、`ChatReceived`、`HealthChanged`）或数据包，在调度线程上更新自己的状态。通常的顺序是：先注册 observer，再提交动作，等动作结束之后检查 observer 的状态。

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

// 只做记录的 observer 由句柄关闭，关闭之后可以在任何线程上直接读取
damage.close();
assertTrue(damage.observer().damages().isEmpty());
```

- 读取状态：observer 关闭之后，任何线程都可以通过 `handle.observer()` 直接读取。观察期间使用 `handle.read(o -> ...)`，读取函数在调度线程上执行；内置 observer 在观察期间被其它线程直接读取时抛出 `IllegalStateException`。
- `ObserverHandle` 实现了 `AutoCloseable`，`closeReason()` 给出关闭原因（`CloseReason`）。连接结束时全部 observer 以 `DISCONNECTED` 关闭。`onClose` 抛出的异常结束 bot，这时 `handle.close()` 抛出 `BotFailureException`。
- `Observers` 提供内置 observer：
  - 只做记录：`particlesWithin`、`sounds`、`blockChanges`、`blockAt`、`movementOf`、`chatMessages`、`actionBars`、`damageTaken`、`packets`、`sentPackets`、`observations`；除 `blockAt`、`movementOf` 以外都可以用 `limit(n)` 限制保存的记录数，必须在注册之前调用，注册之后调用抛出 `IllegalStateException`；
  - 得出结论时关闭自己：带关闭条件的 `movementOf` 与 `blockAt`，以及 `blockBroken`、`first`、`firstPacket`、`firstChat`、`firstTranslatedChat`、`condition`；
  - `first`、`observations`、`listener` 接收 `Observation` 中的观察记录类型，传入 `PacketReceived` 或 `PacketSent` 时立即抛出 `IllegalArgumentException`，数据包使用 `firstPacket`、`packets`、`sentPackets`、`packetListener`；
  - `ChatLine.plainText()` 不翻译翻译组件（翻译组件与它的参数都不出现在文本中），加入消息、死亡消息等由翻译键组成的消息用 `firstTranslatedChat(翻译键)` 匹配：消息的组件中（包括子组件与翻译参数）有这个翻译键的翻译组件即为匹配；
  - `distanceWithIn`、`distanceOutOf`：玩家进入或离开目标的指定距离时取消传入的动作并关闭，在提交动作之后构造并注册；
  - `listener`、`packetListener`：只执行回调，不保存状态。
- 只观察一个动作执行期间的变化时使用 `observeDuring`，动作结束之后 observer 随之关闭：

```java
ObservedAction<BlockBrokenObserver> dig = bot.observeDuring(Observers.blockBroken(pos), b -> b.attemptBreakBlock(pos));
assertTrue(dig.await(Duration.ofSeconds(5)).broken());   // 与 bot.breakBlock(pos) 相同
```

- 自定义 observer 实现 `Observer`：

```java
final class PickupCounter implements Observer {
    private int picked;

    @Override
    public Subscription subscription() {
        return Subscription.of(ItemPickedUp.class);
    }

    @Override
    public void onObservation(Observation observation, ObserverContext context) {
        if (observation instanceof ItemPickedUp(_, int collector, int count)
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
- 状态与实体编号、方块坐标有关，需要在进入新世界时重新开始的 observer 继承 `WorldBoundObserver`。
- observer 的全部回调在调度线程上执行。回调阻塞会让同一个 `BotRuntime` 中的全部 bot 停住。在回调中注册的 observer 从这个回调返回之后开始接收观察记录。

### 暂停移动

`bot.suspendMovement()` 与 `bot.resumeMovement()` 控制单个 bot，`runtime.suspendMovement()` 与 `runtime.resumeMovement()` 控制全部 bot；任意一级处于暂停状态时，该 bot 的移动就暂停。暂停与恢复在下一轮调度中生效。

暂停只停止速度与位置的计算（包括重力），`moveTo`、`moveForward`、`jumpTo` 不再推进。暂停期间在移动队列中轮到的 `moveTo`、`moveForward`、`jumpTo` 不开始（不寻路，不改变 `rotate`、`lookAt` 设置的朝向），恢复之后才开始，排在它们后面的移动动作随之等待。朝向、手部交互、一次性请求与协议应答照常进行。原版服务端会在玩家悬空 80 tick 之后踢出玩家（允许飞行的情况除外），Minestom 没有这项检查。

### 线程约定

- 会阻塞的方法（`Action.await`、`ObservedAction.await`、`Bot.await`、`ObserverHandle.awaitClose`）在调度线程上调用时抛出 `IllegalStateException`，所以不能在 observer 的回调中调用。
- `Action.future()` 上不带 async 的后续回调在调度线程上执行，同样不能阻塞。

## 针对 Minestom 的绕过方法

以下位置绕过了 Minestom `2026.10.05-26.2` 的数据（`net.minestom:data` 的 JSON）缺少的内容。Minestom 提供对应数据之后应当删除这些绕过。

| 位置 | 原因 | 绕过方法 | 限制 |
|---|---|---|---|
| `movement.RidingSeat` 与 `entity.EntityDimensions` 的实体尺寸与挂点（M12，代码标注 `FIXME(Minestom M12)`，详见根目录 `MINESTOM-BUG-REPORT.md`） | Minestom 的实体类型数据（`entity_type.json`）只有成年实体默认姿态的尺寸与挂点，没有幼年、坐下姿态与玩家各姿势的尺寸和挂点 | 骑乘位置内置原版 26.2 各实体类（马、骷髅马、僵尸马、驴、骡、羊驼、猪、炽足兽、鹦鹉螺、鸡、骆驼、快乐恶魂）的幼年与坐下姿态的取值；实体包围盒内置原版 26.2 全部可以是幼年的实体类的幼年尺寸，以及玩家与人体模型各姿势的尺寸（死亡与睡觉为不受 scale 属性影响的固定尺寸） | 只对 26.2 正确；骑乘位置只识别这些载具类型的幼年状态；包围盒不覆盖其它随状态变化的尺寸（史莱姆的大小、成年骆驼坐下等，见 DESIGN 7.4） |

## 待办

以下问题暂不处理。代码中的位置标注 `TODO(T<编号>)`。

| 编号 | 位置 | 问题 | 现在的处理 |
|---|---|---|---|
| T1 | `world.SectionRef.biome` | MCProtocolLib 的 `DataPalette.index`（`DataPalette.java:118-120`）对生物群系调色板算错下标：用 `y << 3 \| z << 1 \| x`，4×4×4 的格子应为 `y << 4 \| z << 2 \| x` | 不处理，生物群系照常用 `DataPalette.get` 读取，等待复核。一个区段中只有一种生物群系时结果正确 |
| T2 | `protocol.ProtocolMachine.playerChat` | MCProtocolLib 的 `ClientboundPlayerChatPacket` 在过滤类型为 PARTIALLY_FILTERED 时不读取过滤标记的 BitSet（`ClientboundPlayerChatPacket.java:59-60`），之后的聊天类型、发送者名称与目标名称都从错误的位置读出 | 不支持。收到部分过滤的玩家聊天时抛出 `UnsupportedOperationException`，bot 以这个异常结束 |
| T3 | `data.Items.sameItemSameComponents` | 原版解码物品时去掉补丁中与默认组件相同的条目，MCProtocolLib 保留原始补丁 | 物品按原始补丁比较，null 补丁与空补丁视为相同；服务端发来与默认值相同的补丁条目时，比较结果与原版不同 |
| T4 | `interaction.BlockPredicates` | can_break 中针对方块实体组件的条件（exact components 与 component predicates） | 不支持。该位置有方块实体时，遇到带有组件条件的谓词抛出 `UnsupportedOperationException`；没有方块实体时与原版相同，直接不匹配 |

TO AGENTS: 此库不需要编写 e2e 测试。
