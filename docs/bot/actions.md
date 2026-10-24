# 动作

[目录](README.md) · 上一篇：[世界模型](world-model.md) · 下一篇：[移动模拟与骑乘](movement.md)

本篇目录：

- [Action 句柄](#action-句柄)
- [动作的结束情况](#动作的结束情况)
- [取消](#取消)
- [三类队列](#三类队列)
- [参数检查](#参数检查)
- [朝向的归属](#朝向的归属)
- [移动动作](#移动动作)
- [手部交互动作](#手部交互动作)
  - [方块交互序号](#方块交互序号)
  - [先看向目标](#先看向目标)
  - [区块未加载](#区块未加载)
  - [挖掘](#挖掘)
  - [挖掘速度](#挖掘速度)
  - [冒险模式的 can_break](#冒险模式的-can_break)
  - [放置与对方块使用物品](#放置与对方块使用物品)
  - [使用物品](#使用物品)
  - [攻击与交互](#攻击与交互)
- [一次性请求](#一次性请求)
- [快捷栏同步](#快捷栏同步)

相关源码：[`Bot.java`](../../bot/api/src/main/java/io/ib67/yuanban/bot/Bot.java)、[`scheduler/ActionImpl.java`](../../bot/runtime/src/main/java/io/ib67/yuanban/bot/scheduler/ActionImpl.java)、[`movement`](../../bot/control/src/main/java/io/ib67/yuanban/bot/movement)、[`interaction`](../../bot/control/src/main/java/io/ib67/yuanban/bot/interaction)、[`scheduler/InventoryActionsImpl.java`](../../bot/runtime/src/main/java/io/ib67/yuanban/bot/scheduler/InventoryActionsImpl.java)。

## Action 句柄

```java
public interface Action {
    boolean await(Duration timeout);      // 执行完毕返回 true，被取消返回 false；等待期间执行推进函数
    CompletableFuture<Void> future();     // 内部 future 的副本
    void cancel();                        // 取消；对已结束的动作不做任何事
}
```

`Action` 只用于等待和取消这个请求，不返回值，也不表示动作在游戏中是否达到了目的；动作的效果由 observer 观察。

实现为 `ActionImpl`：

- 每个动作有一个在同一个 bot 中按提交顺序递增的编号，`observeDuring` 用它判断 `submit` 返回的动作是否在调用期间提交。
- 内部 future 只在调度线程上完成：`succeed()`（执行完毕）、`cancelled(原因)`（以 `ActionCancelledException` 结束）、`terminate(异常)`（连接结束或 bot 因异常结束）。
- 持有动作的队列通过 `canceller` 设置取消时的清理，`requestCancel` 交给它处理。
- 动作创建时加入 bot 的 `unfinished` 集合，结束时移除；结束时产生 `ActionCompleted`（有 observer 订阅它、有 observer 绑定在这个动作上，或者动作在 `observeDuring` 的 `submit` 执行期间提交时）。

## 动作的结束情况

| 情况 | `future()` | `await` |
|---|---|---|
| 执行完毕：bot 把这个动作的执行过程走完了 | 正常完成 | 返回 true |
| 用户调用 `cancel()`（`BY_USER`） | 以 `ActionCancelledException` 完成 | 返回 false |
| 内置 observer（`distanceWithIn`、`distanceOutOf`）因条件满足取消动作（`BY_OBSERVER`） | 同上 | 返回 false |
| 寻路计算抛出异常（`PATH_FAILED`） | 同上 | 返回 false |
| `jumpTo` 起跳后在空中的第 100 个 tick 仍未着地（`AIR_TIMEOUT`） | 同上 | 返回 false |
| 寻路找不到到达目标的路径（`NO_PATH`） | 同上 | 返回 false |
| 移动连续被阻挡，重新寻路次数用完（`BLOCKED`） | 同上 | 返回 false |
| `lookAt(int entityId)`、`attack`、`interact` 的目标实体不在模型中（`NO_ENTITY`） | 同上 | 返回 false |
| 移动范围涉及未加载的区块；`moveTo` 找不到路径（或者只能走到最近点）并且寻路时目标所在区块未加载；或者手部交互动作读取的方块所在区块未加载（`CHUNK_NOT_LOADED`） | 同上 | 返回 false |
| `attack` 的目标不在原版客户端的攻击距离内（`OUT_OF_RANGE`） | 同上 | 返回 false |
| 世界重建（`WORLD_REBUILT`）或玩家死亡（`PLAYER_DIED`），队列被清空 | 同上 | 返回 false |
| 连接结束：服务端断开、`Bot.disconnect()`、`BotRuntime.close()` | 以 `BotDisconnectedException` 完成，异常信息为断开原因 | 抛出该异常 |
| Transfer 关闭的旧连接在写出之前断开，这一批中写出即执行完毕的动作 | 以 `BotDisconnectedException` 完成，异常信息说明连接在写出之前关闭；bot 继续在新连接上登录 | 抛出该异常 |
| bot 因异常结束 | 以 `BotFailureException` 完成 | 抛出该异常 |
| 等待超时 | 不受影响 | 抛出 `BotTimeoutException` |

取消原因是 `ActionCancelledException` 中的字符串常量，作为异常信息给出。

## 取消

- 排队中的动作直接取消。执行中的动作由所在的控制器完成清理后取消：挖掘中发送 Cancelled digging；保持使用物品中发送 Shoot arrow / finish eating；移动动作直接离开队列。
- 一次性请求已经执行、数据包已经交给连接之前被取消时，`future()` 以取消结束，已经发出的数据包不收回。
- 在其它线程调用时，取消请求在下一轮调度中处理；在调度线程上（例如 observer 的回调中）调用时立即处理。两种情况下，被取消的动作都不会再被下一个 tick 推进。
- `future()` 返回内部 future 的副本（`CompletableFuture.copy()`），对它调用 `complete`、`cancel` 不影响动作。内部 future 在调度线程上完成，不带 async 的后续回调在调度线程上执行。

## 三类队列

| 队列 | 动作 | 执行方式 |
|---|---|---|
| 移动（`MovementController`） | `moveTo`、`moveForward`、`jumpTo`、`rotate`、`lookAt` | 先进先出，一次运行一个，按 tick 推进；暂停移动期间 `moveTo`、`moveForward`、`jumpTo` 不推进，轮到的也不开始，排在它们后面的动作随之等待 |
| 手部交互（`HandController`） | `attemptBreakBlock`、`placeBlock`、`useItemOn`、`useItem`、`attack`、`interact` | 先进先出，一次运行一个，按 tick 推进 |
| 一次性请求（`BotInstance.oneShots`） | `chat`、`command`、`selectHotbar`、`swapHands`、`drop`、`respawn`、`inventory()` 下的操作、`send` | 按提交顺序在下一轮调度中执行，产生的数据包本轮发出，数据包交给连接时执行完毕 |

- 移动与手部交互两条队列互相独立，同时运行。一个动作结束之后，同一条队列中的下一个动作在下一个 tick 开始。
- bot 发送 Player Loaded 之前，三类请求都不开始执行。不在任何世界中时（进入第一个世界之前，以及重新配置、Transfer 之后到下一次 Login (play) 之前）提交的请求同样留在队列中，进入世界并加载之后执行。
- **队列清空**：离开世界、在世界中收到 Login (play) 或 Respawn、玩家死亡时，移动与手部两条队列中执行中和排队中的动作以 `WORLD_REBUILT` 或 `PLAYER_DIED` 取消；一次性请求不受影响。离开世界与 Login (play) 引起的取消不发送清理数据包，Respawn 与玩家死亡引起的取消照常清理。不在任何世界中时收到的 Login (play) 不取消队列中的动作。
- bot 已经结束时提交动作立即抛出 `BotDisconnectedException` 或 `BotFailureException`；提交之后、入队之前 bot 结束的，动作以同样的异常结束。

## 参数检查

参数在提交时检查，不合法时在调用线程上抛出 `IllegalArgumentException`：

| 方法 | 条件 |
|---|---|
| `rotate` | 俯仰角在 [-90, 90] 之内（NaN 不在范围之内） |
| `moveForward` | `MoveOptions.direction` 指定时有水平分量 |
| `useItem(hand, holdTicks)` | `holdTicks` 为正数 |
| `chat` | 不超过 256 个字符 |
| `command` | 不超过 32767 个字符 |
| `selectHotbar`、`inventory().swapWithHotbar` | 快捷栏槽位在 [0, 8] 之内 |
| `inventory().click` | 按键的类型属于这个模式；点击窗口外的两种 `DropItemAction` 的槽位为 -999 |
| `send` | 是客户端发往服务端的数据包（`PacketDirection.requireServerbound`） |

## 朝向的归属

`RotationArbiter` 每个 tick 按以下优先级决定朝向：

1. 正在运行、需要对准目标的手部动作（挖掘、放置、对方块使用物品、攻击、交互）：看向目标。
2. 正在运行的 `moveTo`、`moveForward`、`jumpTo`：看向移动方向（俯仰角为 0）；这个 tick 的移动方向为零时保持上一 tick 的朝向。
3. 最近一次 `rotate` 或 `lookAt` 设置的朝向，持续到下一个 `moveTo`、`moveForward`、`jumpTo` 开始，或者服务端设置玩家的朝向（不骑乘时的 Synchronize Player Position、Player Rotation、应用到本地玩家的 Synchronize Vehicle Position）。原版 `handleMovePlayer`、`handleRotatePlayer` 设置的朝向一直保持，所以服务端设置朝向之后，之前 `rotate`、`lookAt` 的朝向不再生效。
4. 保持上一 tick 的朝向（包括服务端设置的朝向）。

移动方向与朝向互相独立：移动由期望方向向量直接计算，不需要先转身。

## 移动动作

`MoveOptions`：`mode`（`WALK`、`SPRINT`、`SNEAK`；未指定时 `moveTo`、`moveForward` 为 `WALK`，`jumpTo` 按距离选择）、`closeDistance`（默认 0.5）、`maxDistance`（默认 50）、`direction`。

| 动作 | 执行过程 | 执行完毕 | 取消 |
|---|---|---|---|
| `moveTo(target)` | 开始时在寻路线程上寻路，之后每 tick 沿路径节点移动，必要时起跳；连续 20 tick 水平位移小于 0.05、路径上的方块被改变、服务端传送了玩家时重新寻路 | 玩家着地，与目标的水平距离不大于 `closeDistance`，并且脚部与目标的站立高度相差小于 1 | 找不到路径，或者只能走到最近点并且已经走完：寻路时目标所在区块已加载为 `NO_PATH`，未加载为 `CHUNK_NOT_LOADED`；重新寻路超过 3 次：`BLOCKED`；移动范围涉及未加载区块：`CHUNK_NOT_LOADED`；寻路计算抛出异常：`PATH_FAILED` |
| `moveForward(blocks)` | 方向取开始时的水平朝向，或 `MoveOptions.direction` 的水平分量；沿直线移动，不寻路、不跳跃 | 累计水平位移达到 `blocks` | 连续 20 tick 水平位移小于 0.05：`BLOCKED`；`CHUNK_NOT_LOADED` |
| `jumpTo(target)` | 以落在目标方块的顶面为目标：着地时起跳，起跳前按水平距离选择行走或疾跑（未指定移动方式时超过 2 格选择疾跑）；空中朝目标顶面中心加速，水平距离小于 0.1 时不再加速 | 起跳后再次着地；起跳的 tick 移动之后仍然着地（起跳没有离开地面，例如跳跃力为 0 或者头顶有方块） | `CHUNK_NOT_LOADED`；起跳后在空中的第 100 个 tick 仍未着地：`AIR_TIMEOUT` |
| `rotate(yaw, pitch)` | 轮到它的那个 tick 设置朝向，单位为度，取值含义见 wiki Set Player Rotation | 设置朝向的 tick 结束时 | 无 |
| `lookAt(point)` | 轮到它的那个 tick 由玩家眼睛位置指向目标计算一次朝向 | 同上 | 无 |
| `lookAt(entityId)` | 目标取实体包围盒的中心 | 同上 | 实体不在模型中：`NO_ENTITY` |

- `rotate`、`lookAt` 的朝向只计算一次，之后玩家或目标实体移动时不再更新。没有手部动作占用朝向时，新朝向由这个 tick 的移动包发出。暂停移动期间照常执行。
- 是否落在 `jumpTo` 的目标方块上由用户通过 observer 或查询判断。
- 寻路、路径跟随、站立高度与移动计算见[移动模拟与骑乘](movement.md)。

## 手部交互动作

`HandController` 在 tick 的手部步骤中推进队列：当前动作没有结束时调用它的 `tick`；否则从队列中取出下一个没有结束的动作，调用它的 `start`。`HandController` 同时保存原版 `MultiPlayerGameMode` 中跨动作的状态：挖掘延迟与已经发送的快捷栏槽位（Login (play) 时归零，Respawn 时不变）。

### 方块交互序号

`BlockSequence` 维护 wiki 定义的 Sequence 计数：用手左键方块、右键使用物品、开始或结束挖掘时加一，第一个为 1。收到 Acknowledge Block Change 时完成序号小于等于确认值的全部等待步骤。序号属于世界（原版的序号在 `ClientLevel` 中）：新建世界与离开世界时归零，丢弃等待中的确认。

带序号的动作在收到确认时执行完毕：确认表示服务端已经处理了这个请求，并且服务端在确认之前发出的方块更新都已经应用到模型中，此时检查 observer 或查询得到的就是服务端处理之后的结果。

### 先看向目标

`useItemOn`、`placeBlock`、`attack`、`interact` 的数据包不带朝向，服务端按它最后收到的玩家朝向处理（放置有方向的方块、攻击的击退方向）。手部步骤在移动步骤之前，同一 tick 中手部数据包先于移动包发出。所以这四个动作开始的 tick 只确定要看向的朝向（开始时计算一次），由这个 tick 的移动步骤设置并发出；之后每个 tick 的手部步骤检查本地玩家的朝向，等于这个朝向时（它已经由之前的移动包发出）才发送数据包，通常是开始之后的下一个 tick。

本地玩家的位置为 Minestom 的 `Pos`，它把偏航角规范到 (-180, 180]、俯仰角限制到 [-90, 90]，所以 `HandController.aimed` 比较之前对要看向的朝向做同样的规范：目标正好在正北时 `Rotation.lookAt` 的偏航角为 -180，位置中为 180。

挖掘不等待：服务端处理 Player Action 时不使用玩家朝向，挖掘在开始的 tick 就发送 Started digging。

### 区块未加载

手部动作读取的方块所在区块未加载时，读取抛出 `ChunkNotLoadedException`。`HandController.tick` 在调用当前动作的开始与每 tick 步骤时捕获这个异常，以 `CHUNK_NOT_LOADED` 取消这个动作，取消时照常执行清理（与移动步骤处理 `Kinematics` 抛出的同一个异常的方式相同）。读取方块的动作：

- `attemptBreakBlock`：目标方块，以及计算进度时眼睛所在的方块。开始的 tick 中这些读取都在发送数据包之前（进度在发送 Started digging 之前计算，与原版 `startDestroyBlock` 的顺序相同），什么都不发送；挖掘中取消时发送 Cancelled digging，与原版准星离开方块时 `stopDestroyBlock` 发送的中止相同。
- `useItemOn`、`placeBlock`：计算 Inside block 时读取目标方块，什么都不发送。

### 挖掘

`attemptBreakBlock(pos[, face])`（`DigTask`）是一次点击，按原版 `Minecraft.startAttack`、`continueAttack` 与 `MultiPlayerGameMode` 的挖掘逻辑执行。挖掘的 Player Action 为 `ServerboundPlayerActionPacket(动作, 方块位置, 面, 序号)`，挥手为 `ServerboundSwingPacket(MAIN_HAND)`。

- **面**：未指定时取朝向玩家眼睛的那一面（方块中心指向眼睛的向量中绝对值最大的分量）。挖掘期间看向方块中心。
- **挖掘延迟**：完成挖掘或者瞬间破坏时设为 5，只在「继续」中减少；开始新的挖掘不需要等待，延迟只推迟进度的累加。

**开始**（第 1 tick）：

| 情况 | 发送 | 结束 |
|---|---|---|
| 目标已经是空气 | 无 | 执行完毕 |
| 旁观模式 | 不带实体编号的 Spectator Action | 写出即执行完毕 |
| 主手物品带 `piercing_weapon` 组件 | Player Action「Stab」与挥手 | 写出即执行完毕 |
| 冒险模式不能破坏目标，或者目标在世界边框之外 | 挥手；挖掘延迟大于 0 时减一并再挥手一次 | 执行完毕 |
| 能力中有瞬间破坏 | Started digging（带序号）与挥手，挖掘延迟设为 5 | 收到确认即执行完毕 |
| 本 tick 的进度已经达到 1（在发送 Started digging 之前计算） | Started digging 与挥手 | 收到确认即执行完毕 |
| 其余 | Started digging 与挥手，记下主手物品，然后在同一 tick 执行一次「继续」 | |

读取目标时目标所在区块未加载，或者计算进度时眼睛所在区块未加载，什么都不发送，以 `CHUNK_NOT_LOADED` 取消（[区块未加载](#区块未加载)）。

**继续**（之后每个 tick）：

1. 主手拿着穿刺武器时什么都不做，不累加、不挥手。
2. 目标变成空气时执行完毕，不发送数据包。
3. 挖掘延迟大于 0 时减一并挥手，不累加进度。
4. 能力中有瞬间破坏并且目标在世界边框之内时（例如挖掘中改成创造模式）：挖掘延迟设为 5，以新的序号发送 Started digging 并挥手，收到确认即执行完毕。
5. 主手物品与开始挖掘时不同（`Items.sameItemSameComponents`，例如中途 `selectHotbar`）时，这个 tick 不累加，重新开始：冒险模式不能破坏目标、目标在世界边框之外时什么都不发送，下一 tick 再次尝试；否则发送 Cancelled digging（当前的面，序号 0），再以新的序号发送 Started digging；本 tick 的进度已经达到 1 时挥手，收到确认即执行完毕；否则进度归零，记下现在的主手物品，挥手。
6. 否则累加进度（以 float 累加，与原版相同）：达到 1 时发送 Finished digging（带序号），挖掘延迟设为 5，再挥手，收到确认即执行完毕；未达到时只挥手。

**取消**：挖掘进行中被取消时发送 Cancelled digging，面为 `DOWN`；等待确认时被取消只丢弃等待中的确认。挖掘进行中目标或者眼睛所在的区块被卸载时，以 `CHUNK_NOT_LOADED` 取消，同样发送 Cancelled digging。

**示例**：空手挖泥土、延迟为 0 时每 tick 进度为 1/15。第 1 tick 发送 Started digging、挥手，累加第 1 次，再挥手；第 2 至 14 tick 每 tick 累加并挥手；第 15 tick 累加达到 1，先发送 Finished digging，再挥手。紧接着的下一次挖掘在开始的 tick 发送 Started digging，之后 5 次「继续」只减少延迟，第 6 次起才累加进度。

**执行完毕的含义**：只表示服务端处理了挖掘请求。服务端拒绝破坏时同样发送确认，所以 `await` 返回 true 不能说明方块被破坏。判断方块是否被破坏使用 `Bot.breakBlock`（[查询与观察 · 动作期间的观察](observation.md#动作期间的观察)）。Minestom 与原版服务端都在确认之前发出这次破坏引起的方块更新，所以这些 `BlockChanged` 都在动作结束之前投递。

### 挖掘速度

`DigSpeed.progress` 按 wiki Breaking 页的公式计算每 tick 的进度：

1. 硬度小于 0 时进度为 0。
2. 速度取主手物品 `tool` 组件中第一条匹配并给出速度的规则的速度；没有匹配时为默认速度；没有 `tool` 组件时为 1。规则中的方块集合为标签时在 `TagTable` 中判断，为编号列表时按方块编号判断。
3. 速度大于 1 时加上 `minecraft:mining_efficiency` 属性。
4. 急迫与潮涌能量取较高的等级，乘以 `1 + 0.2 × 等级`。
5. 挖掘疲劳乘以固定系数 0.3、0.09、0.0027、0.00081（IV 及以上取最后一个）。
6. 乘以 `minecraft:block_break_speed` 属性；眼睛在水中（眼睛所在方块为水或含水方块）时乘以 `minecraft:submerged_mining_speed` 属性；不着地时除以 5。
7. 进度 = 速度 / 硬度 / 30；方块需要工具并且主手物品不是合适的工具（没有匹配并给出 `correct_for_drops` 为 true 的规则）时除以 100。

### 冒险模式的 can_break

冒险模式并且不能建造时，主手物品的 `can_break` 组件必须匹配目标方块，否则不能破坏（原版 `Player.blockActionRestricted`）。`BlockPredicates.test`（原版 `AdventureModePredicate` 与 `BlockPredicate`）：任意一个谓词匹配即为匹配，没有谓词时不匹配。一个谓词的各项都满足时匹配：

- 方块集合：为标签时按服务端下发的 `TagTable` 判断，为编号列表时按方块编号判断。
- 方块状态属性（原版 `StatePropertiesPredicate`）：方块没有这个属性、给出的值不是它的取值时不匹配；精确值按字符串比较；范围按取值的顺序比较（布尔值 false 小于 true；整数从小到大；枚举按声明顺序，由 `Block.possibleStates()` 中第一次出现的顺序得出，按方块缓存）。
- NBT：用 `NbtCompare` 做原版 `NbtUtils.compareNbt` 的部分匹配，与方块实体的 NBT 加上 `id`、`x`、`y`、`z` 之后比较（原版与 `saveWithFullMetadata` 的结果比较）。该位置没有方块实体时不匹配。
- 组件条件：不支持（待办 T4）。有方块实体时遇到组件条件抛出 `UnsupportedOperationException`，bot 结束；没有方块实体时与原版相同，直接不匹配。

### 放置与对方块使用物品

`useItemOn(pos, face, hand, cursor)`（`UseItemOnTask`），`placeBlock(against, face, hand)` 等同于光标取该面中心的 `useItemOn`：

1. 先看向光标位置（`方块位置 + cursor`）。
2. 朝向已经发出之后：目标方块在世界边框之外时不发送任何数据包，直接执行完毕（原版 `MultiPlayerGameMode.useItemOn` 返回 FAIL）；目标所在区块未加载时不发送任何数据包，以 `CHUNK_NOT_LOADED` 取消（[区块未加载](#区块未加载)）。
3. 否则发送 Use Item On：`ServerboundUseItemOnPacket(方块位置, 面, 手, 光标 x, y, z, Inside block, World Border Hit = false, 序号)`，然后发送挥手（本库不预测交互结果，所以总是发送）。
4. 收到确认即执行完毕。

Inside block 按原版准星选取方块时的计算：射线从眼睛出发，沿当前朝向的视线，长度为 `block_interaction_range` 与 `entity_interaction_range` 属性中较大的一个；射线上前进全长千分之一的点在目标方块的轮廓（`outlineShape`）之内时为 true。wiki 写的是碰撞箱，原版选取方块使用轮廓，本库按原版。

### 使用物品

`useItem(hand[, holdTicks])`（`UseItemTask`）：

- 发送 Use Item（`ServerboundUseItemPacket(手, 序号, 偏航角, 俯仰角)`，当前朝向）。
- 未指定 `holdTicks` 时，收到确认即执行完毕。
- 指定 `holdTicks` 时，收到确认后再保持 `holdTicks` 个 tick，然后发送 Player Action「Shoot arrow / finish eating」，写出即执行完毕。保持期间被取消时同样发送它；收到确认之前被取消时只丢弃等待中的确认。
- 指定了 `holdTicks` 时，从发送 Use Item 到发送 Shoot arrow / finish eating 之间视为正在使用发送 Use Item 时这只手上的物品：移动按物品的 `use_effects` 组件减速，不允许疾跑的物品让疾跑不能开始（[移动模拟与骑乘 · 疾跑](movement.md#疾跑)）。没有这个组件时（包括空手）取原版默认值 `(false, true, 0.2F)`。原版只对能够使用的物品进入使用状态，本库不预测物品能否使用。
- 吃完、拉满弓这类结果由用户通过 observer 判断。

### 攻击与交互

- `attack(entityId)`（`AttackTask`）：先看向目标实体包围盒的中心，朝向发出之后发送 Attack 与挥手，写出即执行完毕。发送时为旁观模式的，只发送带这个实体编号的 Spectator Action，不攻击、不挥手。
- `attack` 发送之前按原版 26.2 客户端检查攻击距离（`AttackReach`，见下文）。不在距离内时按原版 `Minecraft.startAttack` 没有选中实体的处理，只发送挥手（旁观模式下发送不带实体编号的 Spectator Action，原版 `spectatorNoAction`），并以 `OUT_OF_RANGE` 取消。
- `interact(entityId, hand)`（`InteractTask`）：先看向目标，朝向发出之后发送 `ServerboundInteractPacket(实体编号, 手, 目标偏移, Sneak Key Pressed)`，写出即执行完毕。目标偏移为包围盒中心相对实体位置的偏移 `(0, 高度 / 2, 0)`；Sneak Key Pressed 为当前正在运行的移动动作是否以 `SNEAK` 方式移动。
- 两者开始时，或者到了发送数据包的 tick，目标实体不在模型中时以 `NO_ENTITY` 取消。

**攻击距离**（`AttackReach`）：原版客户端只攻击准星选中的实体（`LocalPlayer.raycastHitResult`），`startAttack` 再按主手物品的 `attack_range` 组件检查选中位置的距离。本库只对目标实体计算这两步，视线为发送时的朝向（`Rotation.viewVector`）：

1. 当前物品有 `attack_range` 组件时，先沿它的距离选取（原版 `AttackRange.getClosesetHit`）：线段从 `眼睛 + 视线 × 最小距离` 到 `眼睛 + 视线 × (最大距离 + max(0, 速度 · 视线))`，创造模式取 creative 的最小与最大距离；线段起点在目标包围盒内时选中起点，否则取线段与包围盒的交点；都没有时，线段与按 `hitbox_margin` 扩大的包围盒相交，就从这个交点向包围盒中心取与包围盒的交点。手部队列一次运行一个动作，攻击时不在使用物品，当前物品就是主手物品（原版 `getActiveItem`；旁观模式为空）。
2. 没有这个组件，或者第 1 步没有选中时（原版 `LocalPlayer.pick`）：线段从眼睛沿视线到 `block_interaction_range` 与 `entity_interaction_range` 中较大的一个，目标包围盒按选取半径扩大。眼睛在框内时选中线段与框的交点（没有时为眼睛），身体槽位有物品的硫磺立方不能从内部选取；否则取交点，目标与玩家骑乘同一个最下层载具时不选中。选中位置到眼睛的距离平方小于 `entity_interaction_range` 的平方才算选中（原版 `filterHitResult`）。
3. 选中之后，非旁观模式下主手物品有 `attack_range` 组件时，选中位置到眼睛的距离还要在 `[最小距离 − hitbox_margin, 最大距离 + hitbox_margin]` 之内（原版 `AttackRange.isInRange`）。

选取半径（原版 `getPickRadius`）：可选取的 `Projectile` 为 1，其余为 0。`Projectile.isPickable` 为实体类型属于服务端标签 `minecraft:redirectable_projectile`；潜影贝子弹总是可选取；箭、光灵箭、三叉戟还要求没有插在方块上（元数据 `IN_GROUND`）。线段与包围盒的交点移植原版 `AABB.clip`。

本库不判断准星射线上的方块与其它实体，也不判断目标能否被选取（原版 `isPickable`）。wiki 记录了服务端对交互与挖掘的距离检查，本库不在客户端预先检查这两种距离，结果由服务端决定。

## 一次性请求

| 方法 | 发送 |
|---|---|
| `chat(message)` | Chat Message，见[协议状态机 · 聊天与命令](protocol.md#聊天与命令) |
| `command(command)` | Chat Command |
| `selectHotbar(slot)` | Set Held Item；同时更新本地的快捷栏槽位与已经发送的槽位，产生 `HeldSlotChanged` |
| `swapHands()` | Player Action「Swap item in hand」 |
| `drop(wholeStack)` | Player Action「Drop item stack」或「Drop item」 |
| `respawn()` | Client Status「Perform respawn」；是否重生由 observer 观察 `WorldEntered`（进入原因为 `RESPAWN`） |
| `inventory().click(slot, type, action)` | Click Container：执行时的当前窗口编号（没有打开窗口时为 0）、这个窗口的状态编号、槽位、模式、按键；声明的光标物品为空，变化槽位为空 |
| `inventory().leftClick`、`rightClick`、`shiftClick`、`swapWithHotbar`、`dropSlot` | 按对应的模式与按键构造的 Click Container |
| `inventory().clickButton(buttonId)` | Click Container Button |
| `inventory().close()` | Close Container（没有打开窗口时编号为 0），同时在本地关闭打开的窗口，产生 `CursorChanged`（光标改变时）与 `WindowClosed` |
| `send(packet)` | 任意发往服务端的数据包，用于本库没有封装的操作 |

- 一次性请求在 bot 加载之后的下一轮调度中按提交顺序执行，数据包在这一轮交给连接，交给连接（TCP 连接在写进套接字之后）时执行完毕。
- 点击之后物品栏内容只由服务端发回的数据更新：wiki Click Container 写明服务端把点击改变的每个槽位都发回，状态编号不一致时发回完整内容。

## 快捷栏同步

`HandController` 记录已经发送的快捷栏槽位（原版 `MultiPlayerGameMode.carriedIndex`），每个 tick 开始时与本地的快捷栏槽位比较，不同时发送 Set Held Item（原版 `ensureHasSentCarriedItem`，在 Player Loaded 之前，与加载状态无关）。所以服务端用 Set Held Item 改变快捷栏槽位之后，bot 在下一 tick 发回同一个槽位；Respawn 之后快捷栏槽位为 0 时发送 Set Held Item(0)。

[目录](README.md) · 上一篇：[世界模型](world-model.md) · 下一篇：[移动模拟与骑乘](movement.md)
