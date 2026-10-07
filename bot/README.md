# bot

`bot` （库）使用 [Minestom](https://github.com/Minestom/Minestom) 的数据包编解码与游戏数据实现玩家的[网络交互协议](https://minecraft.wiki/w/Java_Edition_protocol)，使得库的使用者可以创建逻辑上的玩家对象并让他们加入特定 Minecraft 服务器以服务于集成测试。除此之外，此库有如下特点：

1. 支持通过替换 Transport 实现进程内 Minecraft（或与 Minestom）实例直接沟通，不需要经过系统网络栈。
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
            .transport(EmbeddedTransport.create())
            .build());
    bot.loaded().await(Duration.ofSeconds(10));   // 等到 bot 发送 Player Loaded
    // ...
}
```

- `BotOptions` 中只有 `username` 与 `transport` 必须设置。其余选项都有默认值：`transportResolver`（处理 Transfer 时为新地址选择 Transport，默认使用 TCP）、`readTimeout`（TCP 连接在任何阶段收不到数据的时限，默认 30 秒，与原版客户端相同）、`loginPluginHandler`、`resourcePackPolicy`、`acceptCodeOfConduct`、`brand`、`chatLogSize`，以及 Client Information 的各项内容（`locale`、`viewDistance` 等）。
- Transport 有两种实现：
  - `new TcpTransport(host, port)`：服务端必须使用离线模式，本库不支持加密。
  - `EmbeddedTransport.create()`：与同一 JVM 中的 Minestom 服务端直接传递数据包对象。打开连接时要求服务端进程已经创建、使用 `Auth.Offline`，并且 `minestom.viewable-packet` 为 `false`。Minestom 的属性在类初始化时固定取值，需要在 JVM 启动参数或测试任务的系统属性中设置。服务端一侧与 socket 连接的处理方式相同：服务端发出的数据包先放进队列，`PlayerPacketOutEvent` 监听器在 bot 的入站线程上执行（相当于 socket 连接的写线程）；bot 关闭连接时由服务端入站线程在处理完之前收到的数据包之后断开，`PlayerDisconnectEvent` 监听器不在 bot 的调度线程上执行。服务端进程停止时，还没有创建玩家的连接被关闭，已经创建玩家的连接由服务端踢出；进程停止之前没有打开过对象级连接时，之后打开的连接不会被关闭。
- 同一个 JVM 中需要 Minestom 服务端进程时，要先创建服务端进程，再使用 bot（包括使用 TCP 连接的 bot）：没有服务端进程时 bot 自己构造一份原版注册表，它会成为全部物品默认组件的依据，之后创建的服务端进程也引用它（M17，见下文的绕过表格）；bot 发现这种情况时抛出 `IllegalStateException`。
- `runtime.connect` 在调用线程上打开连接，建立 TCP 连接时会阻塞调用线程。
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
| 一次性请求 | `chat`、`command`、`selectHotbar`、`swapHands`、`drop`、`respawn`、`inventory()` 下的物品栏操作、`send`（发送任意数据包） | 按提交顺序在下一轮调度中执行 |

- bot 加载完成（`view().loaded()` 为 true）之前，三类请求都不开始执行。bot 不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）提交的请求同样等待，进入世界并加载之后执行。
- 离开世界（重新配置、Transfer）、在世界中收到 Login (play) 或 Respawn、玩家死亡时，移动与手部交互队列中在这个世界里提交的动作全部被取消（`WORLD_REBUILT`、`PLAYER_DIED`）；离开世界与 Login (play) 引起的取消不发送清理数据包（与原版客户端相同）。
- Transfer 之前已经交给旧连接、还没有写出的数据包，因为旧连接断开而没有写出时，在写出时执行完毕的动作以 `BotDisconnectedException` 结束，bot 继续在新连接上登录。
- `MoveOptions` 设置移动方式（`MovementMode` 的 `WALK`、`SPRINT`、`SNEAK`）、`moveTo` 判断到达的水平距离与寻路的最大距离、`moveForward` 的移动方向。`moveTo` 到达目标还要求玩家着地，并且脚部与目标的站立高度（站在目标位置时脚部的高度，按目标下方方块的碰撞箱计算，与寻路的终点相同）相差小于 1，所以目标在正上方或者正下方的另一层时不算到达。

### 查询

```java
Pos position = bot.view().player().position();
Block below = bot.view().block(position.sub(0, 1, 0));

// 多项读取在同一时刻完成；返回值中不要包含 StateReader 给出的可变对象
record Status(float health, ItemStack firstHotbarSlot) {}
Status status = bot.inspect(state -> new Status(state.player().health(), state.inventory().slot(36)));

// 等到条件成立，超时抛出 BotTimeoutException
bot.await(state -> state.player().onGround(), Duration.ofSeconds(5));
```

- `view()` 下的方法在其它线程调用时，交给调度线程执行并等待结果，返回不可变的快照。
- `block` 在所在区块没有加载时抛出 `ChunkNotLoadedException`；`player` 与 `block` 在 bot 不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）抛出 `IllegalStateException`，这时 `world()` 为 null。
- `inventory()` 为窗口 0（玩家物品栏）；身体护甲与鞍在窗口 0 中没有槽位，用 `equipment(EquipmentSlot.BODY)` 与 `equipment(EquipmentSlot.SADDLE)` 读取。
- `chat()` 为聊天日志（`ChatReceived` 给出同样的 `ChatLine`）：玩家聊天与伪装聊天记录聊天类型、发送者名称与目标名称，消息内容不经过聊天类型的修饰。与原版客户端相同，完全过滤的玩家聊天不记录，部分过滤的记录过滤之后的文本（被过滤的字符为 `#`），发送者不在玩家列表中的玩家聊天记录为 `chat.validation_error`。

### 观察

`bot.observe(observer)` 注册 observer，返回 `ObserverHandle`。observer 订阅观察记录（`observe` 包中的 record，例如 `BlockChanged`、`ChatReceived`、`HealthChanged`）或数据包，在调度线程上更新自己的状态。通常的顺序是：先注册 observer，再提交动作，等动作结束之后检查 observer 的状态。

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
  - `first`、`observations`、`listener` 接收 `observe` 包中的观察记录类型，传入 `PacketReceived` 或 `PacketSent` 时立即抛出 `IllegalArgumentException`，数据包使用 `firstPacket`、`packets`、`sentPackets`、`packetListener`；
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

以下位置绕过了 Minestom `2026.10.05-26.2` 缺少的功能或者只面向服务端的实现。Minestom 修复或提供对应接口之后应当删除这些绕过。

| 位置 | 原因 | 绕过方法 | 限制 |
|---|---|---|---|
| `net.minestom.server.registry.ClientDynamicRegistry`（M8，代码标注 `FIXME(Minestom M8)`，详见根目录 `MINESTOM-BUG-REPORT.md`） | `DynamicRegistry` 是 sealed 接口，唯一的实现只能通过 `register` 加入条目；同一 JVM 中的服务端启动之后 `register` 抛出异常。`DynamicRegistry.fromMap` 在全部条目解码之后才创建注册表，附魔的 `exclusive_set` 引用本注册表的标签，无法解码；引用其它条目的编解码器（`RegistryKey`、`Holder`）解码时按名称查找，`getKey` 只认得已经有值的名称，排在后面的条目无法被引用 | 在 Minestom 的同名包中调用 `DynamicRegistryImpl` 包内可见的构造方法，传入自己持有的集合，先创建标签，再登记全部同步注册表的条目名称，最后按服务端顺序给出条目的值，所以条目可以引用排在后面的条目 | Minestom 必须位于 classpath 上，放到模块路径上时无法访问包内可见的构造方法 |
| `transport.embedded.ServerSideInbound` 拦截 Login Acknowledged | `LoginListener.loginAckListener` 遇到 `PlayerSocketConnection` 以外的连接类型时抛出 `UnsupportedOperationException("Only socket")` | LOGIN 阶段收到时不交给监听器，照搬它的逻辑：设置连接阶段为配置阶段，用 Login Success 中的档案调用 `ConnectionManager.createPlayer`，在新的虚拟线程中调用 `ConnectionManager.doConfiguration`；出错时与原监听器相同，以 `Error during login!` 踢出。其它阶段收到时照常交给 `processClientPacket`（这些阶段没有它的监听器，与 Minestom 相同只记一条警告） | 依赖两个 `@ApiStatus.Internal` 方法；Minestom 改变登录流程时需要同步修改 |
| `transport.embedded.ServerSideInbound.IMMEDIATE_PROCESS_PACKETS` | 决定哪些数据包立即处理的集合是 `PlayerSocketConnection` 的私有常量 | 复制一份 | Minestom 修改这个集合时需要同步修改 |
| `transport.embedded.EmbeddedPlayerConnection` 记录握手中的地址、端口与协议号 | `HandshakeListener` 只为 `PlayerSocketConnection` 记录这三个值，其它连接类型返回服务端自己的地址 | 服务端入站线程在把 Handshake 交给监听器之前记录，连接重写 `getServerAddress`、`getServerPort`、`getProtocolVersion` | 无 |
| `transport.embedded.EmbeddedTransport` 要求 `minestom.viewable-packet=false` | 这个属性为默认值 `true` 时，`PacketViewableUtils` 把发给观察者的数据包合并成 `BufferedPacket`，只写给 `PlayerSocketConnection`，其它连接直接跳过 | 打开对象级连接时检查属性，不满足时抛出 `IllegalStateException`（`net.minestom:testing` 同样把它设为 `false`） | 使用对象级连接的服务端必须关闭这个属性 |
| `transport.TcpConnection` 的压缩阈值 | `PacketWriting.writeFramedPacket` 把小于等于 0 的阈值当作不压缩的帧格式，而原版的阈值 0 表示全部压缩 | 启用压缩时传入 `max(阈值, 1)`：每个数据包至少有 1 个字节的编号，效果与阈值 0 相同 | 无 |
| `decode.TcpInbound` 读取区块数据的方块实体（M10，代码标注 `FIXME(Minestom M10)`） | `ChunkData` 读取方块实体时用 `Block.fromKey(方块实体类型的名称)` 查找方块，名称与方块不同的类型（告示牌、悬挂式告示牌、旗帜、头颅、刷怪笼、可疑的方块、展示架等）被丢弃，其余取同名方块的默认状态 | TCP 入站线程自己读取 Chunk Data and Update Light：方块实体的方块取区段调色板中该位置的方块，NBT 附加到这个方块上；该位置的方块没有同一类型的方块实体时忽略这条数据（原版客户端行为） | 依赖 `@ApiStatus.Internal` 的 `PacketVanilla`；Chunk Data 的格式改变时需要同步修改 |
| `decode.TcpInbound` 的分帧与长度上限（M11，代码标注 `FIXME(Minestom M11)`） | `PacketReading` 按服务端的全局属性 `ServerProperties` 限制长度：HANDSHAKE、STATUS、LOGIN 阶段 8192 字节，其余阶段与解压后的长度 2097151 字节；长度 VarInt 可以占 4 到 5 个字节，剩余字节只记警告。较大的 Login Plugin Request 或 Login Success 会让 bot 结束 | 不使用 `PacketReading`，按原版客户端的规则自己分帧、解压与解析：长度 VarInt 最多 3 个字节，不检查解压后的长度上限，解压出的数据少于声明长度、读完数据包之后有剩余字节时出错，解压出的数据更长时只取声明长度的部分 | 依赖 `@ApiStatus.Internal` 的 `PacketVanilla`；不修改全局属性 |
| `movement.RidingSeat` 与 `entity.EntityDimensions` 的实体尺寸与挂点（M12，代码标注 `FIXME(Minestom M12)`） | Minestom 的实体类型数据只有成年实体默认姿态的尺寸与挂点，没有幼年、坐下姿态与玩家各姿势的尺寸和挂点 | 骑乘位置内置原版 26.2 各实体类（马、骷髅马、僵尸马、驴、骡、羊驼、猪、炽足兽、鹦鹉螺、鸡、骆驼、快乐恶魂）的幼年与坐下姿态的取值；实体包围盒内置原版 26.2 全部可以是幼年的实体类的幼年尺寸，以及玩家与人体模型各姿势的尺寸（死亡与睡觉为不受 scale 属性影响的固定尺寸） | 只对 26.2 正确；骑乘位置只识别这些载具类型的幼年状态；包围盒不覆盖其它随状态变化的尺寸（史莱姆的大小、成年骆驼坐下等，见 DESIGN 7.4） |
| 测试中的 `TestServer.profile`（M13，代码标注 `FIXME(Minestom M13)`） | `GameProfile$Property`、`Codec`、`ComponentCodecs`、`ResolvableProfile` 的静态初始化互相依赖：JVM 中第一个初始化的是 `GameProfile`（包括 `new LoginSuccessPacket(...)`）或 `ResolvableProfile` 时，`ResolvableProfile.Partial` 读到还没有赋值的 `GameProfile.Property.LIST_CODEC`，这几个类全部初始化失败，之后用到它们的地方都抛出 `NoClassDefFoundError` | 测试先初始化 `Codec` 再构造 `GameProfile`。本库自身在用到这些类之前已经初始化了 `Codec`（创建 bot 时初始化属性、注册表与数据包类），不受影响 | 使用者自己的代码在初始化其它 Minestom 类之前构造 `GameProfile` 时仍会失败 |
| `registry.VanillaReference`（M17，代码标注 `FIXME(Minestom M17)`） | JVM 中第一个构造的 `Registries.vanilla()` 把全局静态的物品原型（物品的默认组件）绑定到自己身上，之后构造的实例（包括服务端进程的）不再改变它们，Minestom 也没有重新绑定的方法 | 有服务端进程时使用它的注册表；没有时才构造自己的一份。构造过自己的一份之后再遇到服务端进程时抛出 `IllegalStateException`：这时服务端的物品原型已经引用本库的实例（下界合金的 `damage_resistant`、盾牌的 `blocks_attacks` 中的伤害类型标签），服务端修改自己的标签不会反映到这些组件上 | 同一个 JVM 中需要服务端进程时，要先创建服务端进程，再使用 bot |
| `protocol.ProtocolMachine.chatType`（M18，代码标注 `FIXME(Minestom M18)`） | Player Chat Message 与 Disguised Chat Message 的聊天类型字段为「ID or Chat Type」（0 表示数据包随后给出聊天类型的定义），`PlayerChatMessagePacket` 与 `DisguisedChatPacket` 把它当作普通的 VarInt：不能表示随数据包给出的定义，TCP 连接中这个定义被当作之后的字段读取 | 聊天类型按「编号减一」从 `minecraft:chat_type` 注册表中取得；编号为 0 时 bot 因异常结束（TCP 连接中这个定义先被当作发送者名称读取，对 `chat.type.text` 这样的翻译键在解码时就出错） | 带有随数据包给出的聊天类型的聊天消息让 bot 结束；原版服务端发送的聊天类型都来自注册表 |
| `transport.embedded.EmbeddedPlayerConnection` 的 Known Packs 请求（M19，代码标注 `FIXME(Minestom M19)`） | `PlayerConnection.requestKnownPacks` 先发送 Known Packs 请求，再给记录等待回应的字段（不是 volatile）赋值；回应在赋值之前到达时被丢弃，配置等到 `KNOWN_PACKS_RESPONSE_TIMEOUT`（默认 5 分钟）之后断开连接 | 对象级连接重写 `requestKnownPacks` 与 `receiveKnownPacksResponse`：先记下等待回应的 future，再发送请求 | TCP 连接的服务端一侧是 Minestom 的 `PlayerSocketConnection`，无法避开（回应要经过网络，通常晚于赋值到达，但没有保证）；重写的两个方法标注了 `@ApiStatus.Internal` |
| `decode.SectionDecoder.biomeSerializer`（M20，代码标注 `FIXME(Minestom M20)`） | `Palette.biomeSerializer` 用 `bitsToRepresent(生物群系数量)` 计算直接调色板的位数，原版用 `ceillog2(生物群系数量)`；数量是 2 的幂时多一位（64 个生物群系：Minestom 7 位，原版 6 位），原版服务端发来的直接调色板被拒绝 | 区块数据与 Chunk Biomes 的生物群系调色板用 `Palette.serializer` 按原版的位数读取 | Minestom 服务端在生物群系数量为 2 的幂时写出的直接调色板（区段中超过 8 种生物群系）让 bot 结束，原版客户端同样无法读取 |

此外，TCP 连接的帧写出使用 Minestom 标注 `@ApiStatus.Internal` 的 `PacketWriting`，读写都使用 `PacketVanilla`，对象级连接使用 `ConnectionManager.createPlayer` 与 `doConfiguration`，并重写 `PlayerConnection.requestKnownPacks` 与 `receiveKnownPacksResponse`（M19）；升级 Minestom 时需要检查这些方法是否变化。

测试中构造「Registry Data 条目不带 NBT」的输入时，`Registries.registryDataPackets(registries, true)` 返回的 `CachedPacket` 需要服务端进程才能取出数据包，所以测试改为去掉完整输出中全部条目的 NBT（原版注册表的条目全部来自 `minecraft:core`，两者内容相同）。

TO AGENTS: 此库不需要编写 e2e 测试。
