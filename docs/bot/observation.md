# 查询与观察

[目录](README.md) · 上一篇：[移动模拟与骑乘](movement.md) · 下一篇：[错误处理](errors.md)

本篇目录：

- [查询](#查询)
- [观察记录](#观察记录)
- [投递](#投递)
- [Observer 与 Subscription](#observer-与-subscription)
- [observer 的生命周期](#observer-的生命周期)
  - [注册](#注册)
  - [关闭](#关闭)
  - [读取状态](#读取状态)
- [WorldBoundObserver](#worldboundobserver)
- [判断结果](#判断结果)
- [内置 observer](#内置-observer)
  - [只记录的 observer](#只记录的-observer)
  - [得出结论时关闭自己的 observer](#得出结论时关闭自己的-observer)
  - [MovementCriteria](#movementcriteria)
  - [监听](#监听)
- [动作期间的观察](#动作期间的观察)
- [等待](#等待)
- [开销](#开销)

动作是 bot 的输出；服务端发来的数据包和由此引起的模型变化是 bot 的输入。查询读取某一时刻的模型；观察接收一段时间内发生的变化。接收变化只有 `observe` 一种方式，没有单独的事件接口。

相关源码：[`view`](../../bot/src/main/java/io/ib67/yuanban/bot/view)、[`observe`](../../bot/src/main/java/io/ib67/yuanban/bot/observe)、[`observe/builtin`](../../bot/src/main/java/io/ib67/yuanban/bot/observe/builtin)、[`observe/dispatch`](../../bot/src/main/java/io/ib67/yuanban/bot/observe/dispatch)。

## 查询

`StateReader` 是只读接口，方法在调度线程上执行，返回不可变的值。`BotView extends StateReader`：`Bot.view()` 返回的实现把每个方法作为查询任务交给调度线程并等待结果；在调度线程上调用时直接执行。需要在同一时刻完成多项读取时使用 `Bot.inspect(Function<StateReader, T>)`，函数返回值不应包含 `StateReader` 给出的可变对象。observer 回调中的 `context.state()` 也是 `StateReader`，只能在回调中使用。

| 方法 | 结果 |
|---|---|
| `status()` | `BotStatus` |
| `loaded()` | 自上次 Login (play) 或 Respawn 以来是否已经发送 Player Loaded |
| `movementSuspended()` | 实际的暂停状态 |
| `ticks()` | bot 已经执行的 tick 数 |
| `serverBrand()` | 最近一次收到的服务端 brand；没有收到时为 null |
| `serverLinks()` | 最近一次收到的 Server Links，`List<ServerLink>`；没有收到时为空列表 |
| `featureFlags()` | 最近一次收到的 Feature Flags，`List<Key>`；没有收到时为空列表 |
| `clocks()` | 各时钟当前的 tick 数（含小数部分），`Map<Key, Double>`；时钟属于连接，见[世界模型 · World Age 与时钟](world-model.md#world-age-与时钟) |
| `world()` | 当前世界的 `WorldInfo`；不在任何世界中时为 null |
| `worldState()` | `WorldStateSnapshot`；不在任何世界中时抛出 `IllegalStateException` |
| `player()` | `PlayerSnapshot`；不在任何世界中时抛出 `IllegalStateException` |
| `block(x, y, z)`、`block(Point)` | Minestom 的 `Block`，不带 NBT；维度高度范围之外为空气；区块没有加载时抛出 `ChunkNotLoadedException`；不在任何世界中时抛出 `IllegalStateException` |
| `blockEntity(x, y, z)`、`blockEntity(Point)` | 方块实体发给客户端的 `NbtMap`，没有时为 null；异常同上 |
| `biome(x, y, z)`、`biome(Point)` | 生物群系的名称：模型中的编号按同步的 `worldgen/biome` 注册表换成 `Key`；超出维度高度范围时抛出 `IllegalArgumentException`；区块没有加载时抛出 `ChunkNotLoadedException`；不在任何世界中时抛出 `IllegalStateException`。一个区段中不止一种生物群系时结果错误（[待办 T1](limitations.md#待办)） |
| `chunkLoaded(x, z)` | 区块是否已经加载；不在任何世界中时为 false |
| `entity(id)` | `EntitySnapshot`；不在模型中时为 null |
| `entities()` | 当前世界中的全部实体，按编号排列；不在任何世界中时为空列表 |
| `inventory()` | 窗口 0 的 `WindowSnapshot` |
| `openWindow()` | 打开的窗口；没有时为 null |
| `cursor()` | 当前窗口的光标物品；空时为 null |
| `equipment(BODY 或 SADDLE)` | 身体护甲与鞍；其它槽位抛出 `IllegalArgumentException` |
| `chat()` | 聊天日志，按收到顺序 |
| `hud()` | `HudSnapshot` |

快照：

- `PlayerSnapshot`：见[世界模型 · 本地玩家](world-model.md#本地玩家)。速度为每 tick 的速度；属性为 `Map<AttributeType, Double>`（只包含收到过或者本地修改过的属性）；效果为 `Map<Effect, ActiveEffect>`，`duration` 为剩余 tick 数；物品冷却为 `Map<Key, Integer>`（冷却组到剩余 tick 数）；元数据为按索引的 `Map<Integer, EntityMetadata<?, ?>>`，`meta(EntityMeta)` 读取命名条目，与 `EntitySnapshot` 相同。
- `WorldStateSnapshot`：World Age、难度（没有收到时为 null）、默认出生点的方块坐标（没有收到时为 null）、服务端视距、模拟距离、tick 状态（`Ticking`：tick 速率、是否冻结、还要步进的 tick 数、最近一个 tick 是否正常运行）、世界边框（`Border`：中心、当前大小、范围判断与碰撞使用的大小、是否正在插值、插值的目标大小与剩余 tick 数、Portal Teleport Boundary、警告时间、警告距离）、雨量、雷暴强度。各项的含义见[世界模型 · 时间与世界边框](world-model.md#时间与世界边框)。
- `EntitySnapshot`：编号、UUID、`EntityType`、`ObjectData`、位置与朝向、头部朝向、速度、着地、元数据（`meta(EntityMeta)` 读取命名条目）、装备（没有空装备）、属性、效果（`duration` 为收到时的值）、乘坐的实体、乘客、拴住它的实体、包围盒；`center()` 为包围盒中心。位置为服务端最后给出的值，不插值。
- `WindowSnapshot`：窗口编号、类型（窗口 0 与马、鹦鹉螺的窗口为 null）、标题、状态编号、槽位（空槽位为 null）、属性、交易（`ClientboundMerchantOffersPacket`）。
- `HudSnapshot`：Boss Bar、标题、副标题、标题时间、动作栏、玩家列表、页眉、页脚、计分板目标、显示位置、队伍、服务器描述。
- `ChatLine`：见[世界模型 · 聊天日志](world-model.md#聊天日志)。

## 观察记录

`Observation` 是 sealed 接口，全部子类型为不可变 record，声明为 `Observation` 的嵌套类型。

| 类别 | 观察记录 | 产生时机 |
|---|---|---|
| 数据包 | `PacketReceived(MinecraftPacket packet)` | 服务端发来的数据包应用到模型之后，排在它产生的模型层记录之后 |
| | `PacketSent(MinecraftPacket packet)` | 发往服务端的数据包交给连接之后 |
| 世界 | `WorldEntered(WorldInfo world)` | Login (play) 或 Respawn 应用之后 |
| | `ChunkLoaded(int x, int z)`、`ChunkUnloaded(int x, int z)` | 区块安装与卸载（包括落到加载区域外） |
| | `BlockChanged(Point pos, Block old, Block now)` | Block Update、Update Section Blocks；区块加载与卸载不产生 |
| | `BlockEntityChanged(Point pos, NbtMap nbt)` | 被应用的 Block Entity Data |
| 实体 | `EntitySpawned(EntitySnapshot entity)`、`EntityRemoved(EntitySnapshot last)` | Spawn Entity 的编号已有实体时，先产生旧实体的 `EntityRemoved` |
| | `EntityMoved(int entityId, Point from, Point to, boolean onGround)` | 每个改变实体位置的数据包；坐标为模型算出的绝对位置 |
| | `EntityMetadataChanged(int entityId, Map<Integer, EntityMetadata<?, ?>> changed)` | Set Entity Metadata（包括本地玩家） |
| | `EntityEquipmentChanged(int entityId, EquipmentSlot slot, ItemStack old, ItemStack now)` | Set Equipment；Set Player Inventory Slot 改变本地玩家的身体护甲与鞍时，实体编号为本地玩家的。空物品为 null |
| | `EntityDamaged(int entityId, Key type, Integer sourceId, Integer directSourceId, Point sourcePos)` | Damage Event；`type` 为伤害类型的名称，没有来源时为 null |
| | `EntityStatusReceived(int entityId, EntityEvent event)` | Entity Event |
| | `ItemPickedUp(int collectedId, int collectorId, int count)` | Pickup Item |
| 本地玩家 | `PlayerLoaded()` | 发送 Player Loaded |
| | `PlayerMoved(Pos from, Pos to, boolean onGround)` | 移动模拟或骑乘的座位使位置变化的 tick |
| | `PlayerTeleported(Pos from, Pos to)` | Synchronize Player Position；应用到本地玩家的 Synchronize Vehicle Position（指向被移除的载具，或者指向本地玩家自己并且立即设置位置；插值时不产生） |
| | `HealthChanged(float oldHealth, float health, int food, float saturation)` | Set Health |
| | `PlayerDied(Component message)` | Combat Death，目标为自己 |
| | `GameModeChanged(GameMode old, GameMode now)` | 游戏模式改变 |
| | `EffectChanged(Effect effect, ActiveEffect now, long tick)` | Entity Effect、Remove Entity Effect，目标为自己；移除时 `now` 为 null；`tick` 为收到时 bot 已经执行的 tick 数 |
| | `ExperienceChanged(float progress, int level, int total)` | Set Experience |
| 物品栏 | `SlotChanged(int windowId, int slot, ItemStack old, ItemStack now)` | 槽位改变，包括同步到另一个窗口的那一条；空槽位为 null |
| | `CursorChanged(ItemStack old, ItemStack now)` | 当前窗口的光标改变，包括打开与关闭窗口使当前窗口改变（这时排在 `WindowOpened`、`WindowClosed` 之前） |
| | `WindowOpened(int windowId, ContainerType type, Component title)`、`WindowClosed(int windowId)` | 马与鹦鹉螺的窗口类型为 null；`WindowClosed` 也在 `inventory().close()` 与 Respawn 本地关闭窗口时产生 |
| | `HeldSlotChanged(int old, int now)` | Set Held Item（两个方向） |
| 聊天与界面 | `ChatReceived(ChatLine line)` | 写入聊天日志的消息；完全过滤的 Player Chat Message 不产生 |
| | `ActionBarShown(Component text)` | Set Action Bar Text、带 overlay 的 System Chat Message |
| | `TitleShown(Component title, Component subtitle)` | Set Title Text、Set Subtitle Text 之后的标题与副标题 |
| | `BossBarChanged(UUID id, BossBarSnapshot now)` | 移除时 `now` 为 null；移除没有添加过的 Boss Bar 不产生 |
| | `ScoreChanged(String objective, String holder, Integer score)` | Update Score、Reset Score；移除时 `score` 为 null |
| bot 自身 | `ActionCompleted(Action action, Throwable error)` | 动作结束；执行完毕时 `error` 为 null，其余为 `future()` 完成时的异常 |
| | `MovementSuspensionChanged(boolean suspended)` | 实际的暂停状态改变 |
| | `BotTicked(long tick)` | 每次 tick 结束，`tick` 从 1 开始 |
| | `StateUpdated()` | 一轮调度中该 bot 的状态有变化时一条，在这一轮的最后：应用了入站数据包、执行了一次性请求、执行了 tick，或者实际的暂停状态改变；注册 observer 与查询不算变化 |

粒子、音效、世界事件没有对应的模型状态，通过 `PacketReceived` 观察 `ClientboundLevelParticlesPacket`、`ClientboundSoundPacket`、`ClientboundSoundEntityPacket`、`ClientboundLevelEventPacket` 等数据包；内置 observer 把粒子与音效整理成记录。

## 投递

`ObservationDispatcher` 每个 bot 一个，只在调度线程上使用：

- **按需产生**：模型在产生观察记录之前先调用 `wants(类型)`（数据包为 `wantsReceived`、`wantsSent`），没有订阅者的类型不创建对象。`ActionCompleted` 在有 observer 订阅它、或者有 observer 绑定在这个动作上时产生；`observeDuring` 调用 `submit` 期间提交的动作结束时总是产生。
- **排队**：产生的记录带递增的序号进入待投递队列，`flush()` 按产生顺序投递，投递期间产生的记录排在队列末尾，在同一次 `flush` 中投递。`flush` 在投递过程中再次被调用时直接返回。
- **订阅索引**：`SubscriptionIndex` 按观察记录类型与数据包类查找订阅者（按 `Class.isAssignableFrom` 匹配，结果按具体类缓存，注册与关闭时清空缓存），按注册顺序投递。
- **起始序号**：每个 observer 只接收序号大于它注册时序号的记录。在另一个 observer 的回调中注册时，起始序号先设为最大值，等注册它的那个回调返回之后才设为当时的序号，所以同一个回调中注册之后产生的记录（例如在回调中取消动作产生的 `ActionCompleted`）不投递给它。
- **绑定的动作**：投递完一条 `ActionCompleted` 之后，绑定在这个动作上的 observer 以 `ACTION_COMPLETED` 关闭。

**投递顺序**：

- 一个数据包产生的模型层记录按变化发生的顺序投递，最后投递它的 `PacketReceived`。
- bundle 中的数据包全部应用之后才开始投递，仍按「数据包 1 的模型记录、数据包 1 的 `PacketReceived`、数据包 2 的模型记录……」的顺序；回调中读到的模型包含整个 bundle 的结果。
- tick 中每一步产生的记录在这一步完成后投递（[架构参考 · 一次 tick](architecture.md#一次-tick)）。
- 同一个 observer 收到的记录顺序与产生顺序相同。

## Observer 与 Subscription

```java
public interface Observer {
    Subscription subscription();                                        // 注册时读取一次
    default void onStart(ObserverContext context) {}                    // 注册生效时调用一次
    void onObservation(Observation observation, ObserverContext context);
    default void onClose(CloseReason reason, ObserverContext context) {} // 关闭时调用一次；因回调抛出异常而关闭时不调用
}

public interface ObserverContext {
    Bot bot();
    StateReader state();          // 回调时刻的模型，只读，只能在回调中使用
    @Nullable WorldInfo world();  // 当前世界；不在任何世界中时为 null
    void close();                 // observer 关闭自己
}
```

`ObserverContext.close()` 在 observer 自己的回调中调用时，回调返回后关闭；在调度线程上的其它时刻调用时立即关闭；在其它线程调用时抛出 `IllegalStateException`。

`Subscription` 是 record，包含三个集合：模型层与 bot 自身的观察记录类型、入站数据包类、出站数据包类。

```java
Subscription.of(EntityMoved.class, EntitySpawned.class)    // 观察记录类型
        .received(ClientboundLevelParticlesPacket.class)    // PacketReceived：只投递这些数据包类
        .sent(ServerboundChatPacket.class)                  // PacketSent：只投递这些数据包类
        .and(other);                                        // 合并
```

- 全部按 `Class.isInstance` 匹配；传入 `MinecraftPacket.class` 表示接收这一方向的全部数据包。`Subscription.NONE` 不订阅任何类型。
- MCProtocolLib 的数据包类没有区分方向的类型，所以构造时用 `PacketDirection` 检查：`received` 中的类必须是服务端发往客户端的数据包，`sent` 中的类必须是客户端发往服务端的数据包，否则抛出 `IllegalArgumentException`。`PacketDirection` 遍历全部协议阶段，按 `MinecraftCodec.CODEC` 中这个类的注册判断方向，结果按类缓存。
- 观察记录类型中不能直接写 `PacketReceived.class` 或 `PacketSent.class`，否则构造时抛出 `IllegalArgumentException`。

## observer 的生命周期

```java
public interface ObserverHandle<O extends Observer> extends AutoCloseable {
    O observer();
    boolean isOpen();
    @Nullable CloseReason closeReason();
    <R> R read(Function<? super O, R> reader);   // 观察期间读取：reader 在调度线程上执行
    CloseReason awaitClose(Duration timeout);     // 等到关闭；等待期间执行推进函数
    void close();                                 // 关闭；对已关闭的 observer 不做任何事
}
```

### 注册

- 每个 observer 实例只能注册一次，并且只属于一个 bot。重复注册（包括关闭之后再次注册）抛出 `IllegalStateException`。按对象身份判断，以弱引用记录已经注册过的 observer（Caffeine 的弱键缓存）。
- 在其它线程调用 `observe` 时，注册请求交给调度线程，在下一轮处理该 bot 的第一步完成；`observe` 等到注册完成才返回。`observe` 返回之后应用的数据包和发生的变化都会被观察到，之前发生的不补发。所以「先注册 observer，再提交动作」可以保证动作引起的全部变化都被观察到。
- 在调度线程上调用时立即注册，在回调中注册的规则见[投递](#投递)。
- 注册完成时调用 `onStart`。注册时调用的 `subscription()` 与 `onStart` 抛出的异常结束 bot，`observe` 抛出 `BotFailureException`。
- bot 已经断开时，`observe` 抛出 `BotDisconnectedException`；bot 因异常结束时抛出 `BotFailureException`。这两种情况下 observer 不算注册过，可以再交给其它 bot。
- 实现了内部接口 `RegistrationCheck` 的内置 observer（`distanceWithIn`、`distanceOutOf`）在调用 `observe` 的线程上先做检查；检查失败时 `observe` 抛出异常，bot 不受影响，observer 也不算注册过。

### 关闭

关闭之后不再有任何回调，observer 立即从订阅索引中移除。

| 原因 | 触发 | 时机 |
|---|---|---|
| `BY_HANDLE` | 用户调用 `handle.close()` | 在其它线程调用时，等到调度线程处理完才返回，返回时 `onClose` 已经执行；在调度线程上调用时立即关闭。`onClose` 抛出的异常结束 bot，`close()` 抛出 `BotFailureException` |
| `BY_OBSERVER` | observer 调用 `context.close()` | 当前回调返回之后 |
| `ACTION_COMPLETED` | 通过 `observeDuring` 注册的 observer，它对应的动作结束 | 这个动作的 `ActionCompleted` 投递之后 |
| `DISCONNECTED` | bot 的连接结束（服务端断开、`Bot.disconnect()`、`BotRuntime.close()`），或者 bot 因异常结束 | 先丢弃待投递的记录，再关闭全部 observer，然后结束未结束的动作 |
| `FAILED` | observer 的回调抛出异常 | 立即关闭，不调用它的 `onClose`；这个异常同时结束整个 bot |

bot 进入新世界时 observer 继续观察，订阅了 `WorldEntered` 的会收到这条记录。

### 读取状态

- 观察中：observer 的字段只由调度线程修改，其它线程通过 `handle.read(fn)` 读取，`fn` 在调度线程上执行。
- 已关闭：`close()` 或 `awaitClose()` 返回之后，任何线程都可以直接读取 observer，因为关闭发生在这两个方法返回之前。
- 内置 observer 的读取方法在观察中被调度线程以外的线程调用时抛出 `IllegalStateException`。

## WorldBoundObserver

实体每次出现在一个世界中都会重新分配编号（wiki），方块坐标也只在一个维度内有意义。与它们有关的 observer 继承 `WorldBoundObserver<W>`，在 bot 进入新世界时丢弃旧世界的跟踪状态、重新开始：

```java
public abstract class WorldBoundObserver<W> implements Observer {
    protected abstract Subscription worldSubscription();                              // 基类自动加入 WorldEntered
    protected abstract W openWorld(WorldInfo world, ObserverContext context);         // 进入一个世界时创建状态
    protected abstract void observe(W world, Observation observation, ObserverContext context);
    protected void closeWorld(W world, WorldClosed closed, ObserverContext context) {} // 一个世界的观察结束

    public final List<WorldRecord<W>> worlds();     // 按进入顺序排列的全部世界及其状态
    public final @Nullable W currentWorld();        // 最后一个世界的状态
    protected final void checkReadable();           // 观察中被其它线程调用时抛出 IllegalStateException
}
```

| 时机 | 基类的行为 |
|---|---|
| `subscription()`（final） | `worldSubscription()` 加上 `WorldEntered` |
| `onStart`（final） | bot 已在某个世界中时，调用 `openWorld(当前世界)` |
| 收到 `WorldEntered` | 对当前世界调用 `closeWorld(..., Left(新世界))`，再调用 `openWorld(新世界)` |
| 收到其它记录 | 交给 `observe(当前世界状态, ...)`；还没有打开任何世界时丢弃。bot 离开世界之后、进入下一个世界之前的记录交给最后打开的世界 |
| `onClose`（final） | 对当前世界调用 `closeWorld(..., ObserverClosed(原因))` |

相关类型：`WorldInfo(epoch, dimension, dimensionTypeId, cause)`（`epoch` 每个 bot 从 1 开始，每进入一个世界加一）、`WorldEntryCause`（`JOIN`、`RECONFIGURATION`、`RESPAWN`、`TRANSFER`）、`WorldRecord<W>(world, state, closed)`、`WorldClosed`（`Left(next)` 或 `ObserverClosed(reason)`）。

## 判断结果

库中没有通用的「成功」概念，也不提供断言。动作结束之后，测试代码按两种方式之一决定后续代码：

1. **检查 observer 的状态**：observer 只记录发生了什么，测试代码读取后自行判断。
2. **observer 自己得出结论**：observer 收到足够的记录、能够得出结论时调用 `context.close()`，并通过它自己的方法给出结论（例如 `met()`、`matched()`）。测试代码用 `awaitClose(timeout)` 等它关闭，再读取结论。

observer 没有自己的时限，等多久由测试代码决定；「某件事没有发生」由测试代码在关闭 observer 之后检查记录确定。

## 内置 observer

`Observers` 提供工厂方法，每次调用返回一个新的实例。「按世界」为「是」的继承 `WorldBoundObserver`，读取方法返回当前世界的状态，各世界的完整状态见 `worlds()`。

### 只记录的 observer

不会自行关闭。

| 工厂方法 | 按世界 | 订阅 | 读取方法 |
|---|---|---|---|
| `particlesWithin(Region[, Predicate<Particle>])` | 是 | `ClientboundLevelParticlesPacket` | `particles()`：`ParticleRecord(particle, center, offset, speed, count, longDistance)`；按数据包中的中心坐标判断范围 |
| `sounds(Region[, Predicate<Sound>])` | 是 | `ClientboundSoundPacket`、`ClientboundSoundEntityPacket` | `sounds()`：`SoundRecord(sound, category, position, volume, pitch, entityId)`；实体音效按收到时该实体在模型中的位置判断范围，实体不在模型中时不记录 |
| `blockChanges(Region)` | 是 | `BlockChanged` | `changes()` |
| `blockAt(Point)` | 是 | `BlockChanged` | `current()`：当前方块（进入世界时区块未加载、之后也没有变化时为 null）；`history()`：该位置的每次变化 |
| `movementOf(Predicate<EntitySnapshot>)` | 是 | `EntitySpawned`、`EntityMoved`、`EntityRemoved` | `tracks()`：`MovementTrack(entityId, type, start, current, removed)`，`displacement()` 为位移向量 |
| `chatMessages([Predicate<ChatLine>])` | 否 | `ChatReceived` | `lines()` |
| `actionBars()` | 否 | `ActionBarShown` | `texts()` |
| `damageTaken()` | 否 | `EntityDamaged`（目标为自己）、`HealthChanged`（生命值下降） | `damages()`：`DamageRecord.Event` 或 `DamageRecord.HealthDrop` |
| `packets(Class<P>[, Predicate<P>])` | 否 | `received(P)` | `packets()` |
| `sentPackets(Class<P>[, Predicate<P>])` | 否 | `sent(P)` | `packets()` |
| `observations(Class<O>[, Predicate<O>])` | 否 | `of(O)` | `observations()` |

- 记录默认不限制数量。`limit(int)` 在注册之前设置上限，超出的记录只计数不保存，`truncated()` 返回 true。按世界的 observer 对每个世界分别计数，每个世界的记录为 `Records`（`items()`、`truncated()`、`count()`）。注册之后再调用 `limit` 抛出 `IllegalStateException`：注册之后 observer 的字段由调度线程读写。`blockAt`、`movementOf` 没有 `limit`。
- `Region(min, max)` 为各边与坐标轴平行的长方体，两端包含在内；`Region.around(center, radius)`、`contains(point)`。
- `movementOf` 跟踪的实体：开始跟踪时（注册时以及每次进入新世界时）模型中已有的、满足条件的实体，以及之后 `EntitySpawned` 中满足条件的实体；条件只在开始跟踪时判断一次。实体被移除时 `removed` 变为 true，记录保留。

### 得出结论时关闭自己的 observer

| 工厂方法 | 按世界 | 关闭条件 | 结论 |
|---|---|---|---|
| `movementOf(who, MovementCriteria)` | 是 | 当前世界中满足条件的实体数达到要求 | `met()`；`metBy()`：满足条件的实体的记录 |
| `blockAt(Point, Predicate<Block>)` | 是 | 该位置的方块满足条件；进入世界时先检查一次当前方块 | `met()` |
| `blockBroken(Point)` | 否 | 该位置的方块变成方块编号（`Block.id()`）与 `original()` 不同的方块；进入新世界时也关闭 | `broken()`、`original()`、`changedTo()` |
| `first(Class<O>, Predicate<O>)` | 否 | 出现匹配的观察记录 | `matched()`：`Optional<O>` |
| `firstPacket(Class<P>, Predicate<P>)` | 否 | 收到匹配的数据包 | `matched()`：`Optional<P>` |
| `firstChat(Predicate<ChatLine>)` | 否 | 收到匹配的聊天消息 | `matched()`：`Optional<ChatLine>` |
| `firstTranslatedChat(String)` | 否 | 消息的组件中（包括子组件与翻译参数）有这个翻译键的翻译组件 | `matched()`：`Optional<ChatLine>` |
| `condition(Predicate<StateReader>)` | 否 | 条件成立；注册时先检查一次，之后在每条 `StateUpdated` 时检查 | `met()` |
| `distanceWithIn(range, target, action)` | 否 | 玩家位置（脚部）与目标的直线距离不大于 `range` 时取消 `action` 并关闭；`action` 先结束时关闭 | `met()` |
| `distanceOutOf(range, target, action)` | 否 | 距离大于 `range` 时取消 `action` 并关闭；`action` 先结束时关闭 | `met()` |

- 在得出结论之前被句柄关闭、因连接断开而关闭或以 `ACTION_COMPLETED` 关闭时，`met()`、`broken()` 为 false，`matched()` 为空，关闭原因见 `handle.closeReason()`。
- 只记录的 `movementOf`、`blockAt` 没有结论，调用 `met()`、`metBy()` 抛出 `IllegalStateException`。
- `first`、`observations`、`listener` 的类型为 `PacketReceived` 或 `PacketSent` 时，调用工厂方法时就在调用线程上抛出 `IllegalArgumentException`；`packets`、`sentPackets`、`firstPacket`、`packetListener` 的数据包类方向不对时同样如此。bot 不受影响。
- `blockBroken`：
  - 订阅 `BlockChanged`、`ChunkLoaded`、`WorldEntered`。只改变方块状态（例如朝向、含水）不算变成不同的方块。
  - 在世界中注册时，`original()` 在 `onStart` 中读取；该位置所在区块未加载时抛出 `ChunkNotLoadedException`，bot 结束。
  - 不在任何世界中时注册：坐标属于下一个世界。收到的第一条 `WorldEntered` 不关闭；`original()` 为进入之后该位置所在区块第一次加载时的方块，在这之前为 null；之后的 `WorldEntered` 照常关闭。
- `distanceWithIn`、`distanceOutOf`：
  - 订阅 `PlayerMoved`、`PlayerTeleported`、`ActionCompleted`。在提交动作之后构造并注册；`action` 必须由同一个 bot 提交，否则注册时抛出 `IllegalArgumentException`。
  - 注册时先检查一次距离条件（不在任何世界中时没有玩家位置，不做这次检查），之后在每条 `PlayerMoved`、`PlayerTeleported` 之后检查。条件满足时在回调中调用 `action.cancel()`（调度线程上立即处理），然后关闭自己。
  - 注册时条件不满足并且 `action` 已经结束，或者之后收到 `action` 的 `ActionCompleted` 时，关闭自己，`met()` 为 false。
  - 世界重建时动作被取消，observer 随之关闭，所以它们不需要按世界切换状态。

### MovementCriteria

不可变对象，由 `MovementCriteria.create()` 开始，每个方法返回新的对象。至少设置前三项中的一项，否则构造 observer 时抛出 `IllegalArgumentException`。设置的各项同时满足时，这个实体满足条件：

| 方法 | 条件 |
|---|---|
| `atLeast(blocks)` | 位移长度不小于 `blocks` |
| `towards(target, maxAngleDegrees)` | 位移方向与「起点指向 target」的方向夹角不超过 `maxAngleDegrees`；位移为零时不满足 |
| `into(Region)` | 实体当前位置在区域内 |
| `entities(count)` | 满足条件的实体数达到 `count` 时关闭，默认 1；不是正数时抛出 `IllegalArgumentException` |

每条 `EntityMoved` 之后对相应实体重新判断；实体被移除时，已经满足条件的计数保留。

### 监听

`listener(Class<O>, Consumer<O>)` 与 `packetListener(Class<P>, Consumer<P>)`：回调在调度线程上执行，不保存状态。

## 动作期间的观察

```java
<O extends Observer> ObservedAction<O> observeDuring(O observer, Function<Bot, Action> submit);

public interface ObservedAction<O extends Observer> {
    Action action();
    ObserverHandle<O> handle();
    O await(Duration timeout);   // 等到动作结束并且 observer 关闭，返回 observer
}
```

- 在调度线程上的同一个任务中依次完成：注册 observer（调用 `onStart`）、调用 `submit` 提交一个动作、把 observer 绑定到这个动作上。动作开始执行之前 observer 已经在观察，中间没有可以被其它线程插入的间隔。在其它线程调用时等到任务完成才返回，不执行推进函数。
- `submit` 必须返回它执行期间由这个 bot 提交的一个动作（按动作的编号判断），否则以 `BY_HANDLE` 关闭 observer 并抛出 `IllegalArgumentException`，bot 不受影响。`subscription()`、`onStart` 与 `submit` 抛出的异常结束 bot，`observeDuring` 抛出 `BotFailureException`。
- 动作结束时产生的 `ActionCompleted` 投递完成后，observer 以 `ACTION_COMPLETED` 关闭。在此之前到达的数据包产生的记录都已经投递给它，包括同一个 bundle 中排在确认之前的数据包。动作在 `submit` 执行期间就已经结束时同样如此。
- `ObservedAction.await`：动作被取消时照常返回；连接结束或 bot 因异常结束时抛出与 `Action.await` 相同的异常；等待期间执行推进函数；超时抛出 `BotTimeoutException`，不关闭 observer，也不取消动作。

**破坏方块**：`breakBlock(pos[, face])` 等同于 `observeDuring(Observers.blockBroken(pos), bot -> bot.attemptBreakBlock(pos[, face]))`。`breakBlock(pos).await(timeout).broken()` 为 true 表示：从提交 `attemptBreakBlock` 到它结束这段时间内，`pos` 处的方块在 bot 的世界模型中至少有一次变成了方块编号不同的方块。

- 挖掘完成时，这次破坏引起的 Block Update 都在确认之前到达，所以都在观察范围内。
- 服务端拒绝破坏时只发送确认，没有 `BlockChanged`，结果为 false。
- 原版服务端在一个 tick 中对每个位置只发送最终状态：同一 tick 内被破坏又被恢复的方块，客户端看不到变化，结果为 false。Minestom 每次设置方块都发送 Block Update，这种情况结果为 true。
- 不在任何世界中时调用：`attemptBreakBlock` 等待进入下一个世界并加载之后执行，`blockBroken` 观察下一个世界中的这个位置。

## 等待

| 方法 | 行为 |
|---|---|
| `Bot.await(Predicate<StateReader>, Duration)` | 注册一个 `condition(predicate)` 并等它关闭；超时时关闭它并抛出 `BotTimeoutException`；它因连接结束或 bot 因异常结束而关闭时抛出与 `Action.await` 相同的异常 |
| `Bot.joined()` | 返回 `Action`，Login (play) 处理完成时执行完毕；bot 已经在世界中时在下一轮调度中执行完毕 |
| `Bot.loaded()` | 返回 `Action`，发送 Player Loaded 时执行完毕；当前世界已经加载时在下一轮调度中执行完毕 |

- 不在任何世界中时调用的 `joined()`、`loaded()` 等待下一个世界的 Login (play) 与 Player Loaded；Respawn 之后、加载之前调用的 `loaded()` 等待这次重生之后的加载。
- 已经发生过时，在其它线程调用的请求本来就在下一轮处理；在调度线程上调用时推迟到下一轮执行完毕，所以 `observeDuring(o, Bot::loaded)` 在动作结束之前已经把 observer 绑定到动作上，observer 以 `ACTION_COMPLETED` 关闭。
- `Bot.await`、`ObserverHandle.awaitClose`、`Action.await`、`ObservedAction.await` 都执行推进函数，见[线程与调度 · 阻塞等待与推进函数](threading.md#阻塞等待与推进函数)。

## 开销

- 每个 bot 有一个 `SubscriptionIndex`，注册与关闭时更新。
- 没有订阅者的类型不创建观察记录对象，`EntityMoved` 等高频记录只有在被订阅时才有开销。
- 观察记录在同一轮内投递，不跨线程。

[目录](README.md) · 上一篇：[移动模拟与骑乘](movement.md) · 下一篇：[错误处理](errors.md)
