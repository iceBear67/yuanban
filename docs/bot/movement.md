# 移动模拟与骑乘

[目录](README.md) · 上一篇：[动作](actions.md) · 下一篇：[查询与观察](observation.md)

本篇目录：

- [移动步骤](#移动步骤)
- [每 tick 的移动计算](#每-tick-的移动计算)
  - [脚下方块与支撑方块](#脚下方块与支撑方块)
  - [区块未加载](#区块未加载)
  - [与 wiki 数值的对照](#与-wiki-数值的对照)
- [疾跑](#疾跑)
- [Player Input](#player-input)
- [移动包](#移动包)
- [服务端施加的位置与速度](#服务端施加的位置与速度)
- [寻路与路径跟随](#寻路与路径跟随)
  - [寻路](#寻路)
  - [节点生成器](#节点生成器)
  - [路径的判断](#路径的判断)
  - [站立高度](#站立高度)
  - [跟随](#跟随)
  - [重新寻路](#重新寻路)
- [暂停移动](#暂停移动)
- [骑乘](#骑乘)
  - [骑乘时的移动步骤](#骑乘时的移动步骤)
  - [控制载具](#控制载具)
  - [座位位置](#座位位置)

相关源码：[`movement`](../../bot/control/src/main/java/io/ib67/yuanban/bot/movement)、[`player/Interpolation.java`](../../bot/model/src/main/java/io/ib67/yuanban/bot/player/Interpolation.java)。

## 移动步骤

`MovementController.tick(暂停, 手部朝向, 正在使用的物品)` 在 tick 的第 6 步执行，bot 加载之后才执行：

1. 骑乘时，最下层的载具或者中间的载具所在区块未加载，这一步什么都不做（见[骑乘](#骑乘)）。
2. 当前动作结束时开始队列中的下一个。暂停期间轮到的 `moveTo`、`moveForward`、`jumpTo` 不开始，留在队首。`moveTo`、`moveForward`、`jumpTo` 开始时清除 `rotate`、`lookAt` 留下的朝向。
3. 当前动作推进一步，得到这个 tick 的期望移动 `MoveIntent(方向, 移动方式, 是否起跳)`。暂停期间不推进，期望移动保持暂停开始时的值（不起跳）。`rotate`、`lookAt` 在这一步设置朝向。
4. `RotationArbiter` 决定朝向（[动作 · 朝向的归属](actions.md#朝向的归属)），写入本地玩家。
5. 潜行状态为「移动方式是 `SNEAK` 并且不在飞行」。
6. 决定本地的疾跑状态（[疾跑](#疾跑)）。
7. 骑乘时转到[骑乘时的移动步骤](#骑乘时的移动步骤)。
8. 确定 Player Input 的按键（在移动计算之前确定）。
9. 没有暂停并且玩家没有死亡时，执行移动计算（`Kinematics.step`），着地时停止飞行；位置变化时产生 `PlayerMoved`；通知当前动作这个 tick 的位移。移动范围涉及未加载区块时以 `CHUNK_NOT_LOADED` 取消当前的移动动作。
10. Player Input 变化时发送；疾跑状态与上次告知服务端的不同时发送 Player Command；`MovementSender` 发送移动包。
11. `rotate`、`lookAt` 在这个 tick 结束时执行完毕；结束的动作离开队列。

## 每 tick 的移动计算

`Kinematics.step` 按原版 `LocalPlayer.aiStep`、`LivingEntity.aiStep`、`Player.travel` 与 `LivingEntity.travelInAir` 的顺序计算一个 tick。移动与碰撞交给 Minestom 的 `PhysicsUtils.simulateMovement`：

1. **飞行时的竖直输入**：正在飞行时，期望起跳使竖直速度加 `3 × 飞行速度`，移动方式为 `SNEAK` 使它减同样的值。
2. **插值**：有正在进行的插值时（[世界模型 · 位置插值](world-model.md#位置插值)）先插值一步（原版 `InterpolationHandler.interpolate`）：从开始插值或上一步之后本地的位移加到目标位置上（加上之后玩家的包围盒不与方块碰撞、也不伸进世界边框时才加），同一期间本地的转向加到目标朝向上；然后位置与俯仰角向目标前进剩余步数分之一，偏航角沿较近的方向前进，朝向对 360 取余，俯仰角限制到 [-90, 90]。一次插值共 3 步，之后的计算从插值得到的位置与朝向开始。
3. **速度归零**：水平速度的平方小于 9.0E-6 时水平速度归零；竖直速度的绝对值小于 0.003 时归零。
4. **输入**：期望方向乘以 0.98；正在使用物品时再乘以物品 `use_effects` 组件的速度系数；蹲下时再乘以 `minecraft:sneaking_speed` 属性。
5. **起跳**（原版 `jumpFromGround`）：期望起跳、着地、不在飞行，并且起跳延迟为 0 时起跳，之后 10 tick 内持续期望起跳也不再起跳（不期望起跳的 tick 延迟归零）。起跳力为 `(float) jump_strength 属性 × 起跳系数 + 0.1 × 跳跃提升等级`；起跳力不大于 1.0E-5 时不起跳，否则竖直速度取起跳力与当前竖直速度中较大的一个；疾跑时再沿朝向增加 0.2 的水平速度。起跳系数为脚部所在方块的 `jumpFactor()`，它为 1 时取脚下方块的。
6. **水平加速**：着地时先读出脚下方块的摩擦 `f = clamp(1 − (1 − friction()) × friction_modifier 属性, 0, 1)`（移动之前读取），不着地时 `f = 1`。加速度为输入乘以：着地时 `movement_speed 属性`（`f` 大于 0.6 时再乘以 `0.21600002 / f³`）；空中飞行时为能力中的飞行速度，疾跑时乘以 2；空中不飞行时为 0.02，疾跑时为 0.026。
7. **移动与碰撞**：调用 `PhysicsUtils.simulateMovement`，`aerodynamics` 为 `(0, f × 空气阻力, 1)`，`onGround`、`flying`、`noGravity` 都传 false；旁观模式不碰撞。它只按速度移动、处理与方块碰撞箱及世界边框的碰撞，水平速度乘以原版的阻力，返回的竖直速度为碰撞之后的竖直速度。`onGround` 传 false 的原因：`PhysicsUtils` 按移动之后的位置读取方块摩擦，原版用移动之前读出的摩擦。空气阻力为 `clamp(1 − (1 − 0.91) × air_drag_modifier 属性, 0, 1)`。
8. **竖直速度**（原版 `travelInAir`）：有飘浮效果时为 `v + (0.05 × 等级 − v) × 0.2`，否则为 `v − 有效重力`，`v` 为碰撞之后的竖直速度；再乘以 `clamp(1 − (1 − 0.98) × air_drag_modifier 属性, 0, 1)`。有效重力：元数据 `HAS_NO_GRAVITY` 为真时为 0，否则为 gravity 属性；`v` 不大于 0 并且有缓降效果时不超过 0.01。正在飞行时竖直速度改为移动之前的竖直速度乘以 0.6（原版 `Player.travel`）。
9. **着地与支撑方块**：着地与顶住墙壁标记取碰撞结果；顶住墙壁时，期望的水平移动与实际移动的夹角小于 8 度为擦过墙壁（原版 `minorHorizontalCollision`）。着地时查找支撑方块（见下文）。
10. **方块速度系数**（按移动之后的位置）：脚部所在方块的 `speedFactor()` 不为 1、或者它是水或气泡柱时取它，否则取脚下方块的；再按 `minecraft:movement_efficiency` 属性向 1 插值，水平速度乘以这个系数。

**停止飞行**：模拟之后，旁观模式以外着地并且正在飞行时停止飞行，发送 `ServerboundPlayerAbilitiesPacket(false)`（原版 `onUpdateAbilities`）。

`PhysicsUtils` 的结果在两处与原版有很小的差别：它把绝对值小于 1.0E-6 的速度分量归零；位置完全没有变化时它把水平速度归零。前者小于原版下一 tick 的归零阈值，后者只在各方向都被挡住或者速度为零时发生，这时原版的水平速度同样为零。

### 脚下方块与支撑方块

- **脚下方块**（原版 `getBlockPosBelowThatAffectsMyMovement`）：有支撑方块时取它所在的列，脚部向下 0.500001 格的高度；支撑方块为墙或栅栏门（按 `TagTable` 中的 `minecraft:walls`、`minecraft:fence_gates` 判断）时取支撑方块本身；没有支撑方块时取脚部所在的列。
- **支撑方块**（原版 `checkSupportingBlock`）：着地时在包围盒底面以下查找碰撞箱与它相交的方块，取中心离玩家位置最近的一个（距离相同时依次比较 y、z、x，取较大的一个）。没有找到、并且上一次不是「着地但没有支撑方块」时，按这次的水平移动退回去再找一次。检查范围向四周多一格（碰撞箱可以超出所在的格子），未加载区块中的格子不读取。
- 原版的检查深度为 1.0E-6；Minestom 的碰撞让着地的实体停在方块顶面之上最多约 1.0E-5 处，`Shape.intersectBox` 又要求重叠超过 1.0E-6，所以本库的检查深度为 1.0E-4。

### 区块未加载

移动范围涉及未加载区块时不执行模拟，玩家的任何状态都不改变（起跳延迟与插值的进度在确认区块已加载之后才写回），执行中的移动动作以 `CHUNK_NOT_LOADED` 取消。移动范围为玩家包围盒（插值之后的位置）的水平范围，按交给 `PhysicsUtils` 的速度（加速与起跳之后）扩展：`PhysicsUtils` 只读取这个范围中的方块，移动之前与之后读取的脚下方块、方块速度系数也都在其中。原版在区块未加载时改为以 0.1 的速度下落，本库不覆盖这一点。

### 与 wiki 数值的对照

这个顺序复现 wiki 记载的数值（`KinematicsTest` 逐项检查）：

| 场景 | 数值 |
|---|---|
| 平地行走 | 4.317 m/s |
| 平地疾跑 | 5.612 m/s |
| 平地潜行 | 1.3 m/s |
| 跳跃高度 | 1.2522 格 |
| 跳跃提升 I | 1.8361 格 |
| 跳跃提升 II | 2.5168 格 |
| 飞行 | 10.89 m/s |
| 疾跑飞行 | 21.78 m/s |

本库按期望方向向量直接施加加速度，任何方向的速度都等于向前行走的速度，wiki 中的斜向行走数值（4.405 m/s）不适用。

## 疾跑

本地的疾跑状态在移动计算之前决定（原版 `LocalPlayer.aiStep`），先判断开始、再判断停止，所以顶住墙壁时开始之后在同一个 tick 里立即停止：

- **疾跑键**：本库没有单独的疾跑键，移动方式为 `SPRINT` 并且期望移动方向不为零时视为按住疾跑键。
- **向前的输入**：期望移动方向在当前朝向前方的分量大于 0.001。
- **可以疾跑**（原版 `isSprintingPossible`）：没有失明效果，并且骑乘时载具可以疾跑（骆驼）并且由本地玩家控制，不骑乘时饥饿值大于 6 或者允许飞行。
- **开始**（原版 `canStartSprinting`）：还没有疾跑，按住疾跑键，有向前的输入，可以疾跑，并且没有在使用不允许疾跑的物品（物品 `use_effects` 组件的 `can_sprint`）。
- **停止**：没有按住疾跑键，或者没有向前的输入，或者不能疾跑，或者上一 tick 顶住墙壁并且不是擦过墙壁。原版松开疾跑键不停止疾跑；本库的 `MovementMode` 表示这个动作要求的移动方式，所以移动方式不是 `SPRINT`（包括没有移动动作）时也停止。潜行与疾跑是不同的移动方式，原版「蹲下时不开始疾跑」的条件自然成立。
- 疾跑状态改变时在本地属性上加入或移除 `minecraft:sprinting` 修饰符（`ADD_MULTIPLIED_TOTAL` 0.3）；之后服务端的 Update Attributes 替换全部修饰符。
- 疾跑状态与上次告知服务端的不同时发送 Player Command「Start sprinting」或「Stop sprinting」（原版 `sendIsSprintingIfNeeded`）。

## Player Input

公开接口不提供按键输入，Player Input 由移动状态派生：

- 前、后、左、右：期望移动方向相对当前朝向的分量，大于 0.001 为按下前或左，小于 -0.001 为按下后或右；
- 跳跃：本 tick 期望起跳并且着地；
- 潜行：移动方式为 `SNEAK`；
- 疾跑：疾跑键（与是否真的在疾跑无关，原版 `keyPresses.sprint`）。

上一次发送的 Player Input 保存为数据包本身，初始为全部按键都为 false 的常量（原版新玩家的 `lastSentInput`），用 `equals` 比较，变化时发送。按键在移动计算之前确定，数据包在移动计算之后、移动包之前发送（原版 `LocalPlayer.tick` 在 `super.tick()` 之后发送）；骑乘时在 Set Player Rotation 之前发送。新的本地玩家使上一次发送的 Player Input 与疾跑状态重新开始（Respawn 带 Data kept 0x02 时保留）。

## 移动包

`MovementSender` 在每次移动步骤之后发送（wiki Set Player Position 与原版客户端）：

| 变化 | 发送 |
|---|---|
| 位置与朝向都变化 | Set Player Position and Rotation（`ServerboundMovePlayerPosRotPacket`） |
| 只有位置变化 | Set Player Position（`ServerboundMovePlayerPosPacket`） |
| 只有朝向变化 | Set Player Rotation（`ServerboundMovePlayerRotPacket`） |
| 只有着地或顶住墙壁标记变化 | Set Player Movement Flags（`ServerboundMovePlayerStatusOnlyPacket`） |

- 位置变化的阈值为位移平方大于 `(2.0E-4)²`；静止时每 20 tick 至少发送一次位置。
- 四种数据包的构造方法中，着地与顶住墙壁两个标记排在最前；顶住墙壁标记取 `PhysicsResult` 的 `collisionX` 或 `collisionZ`。
- 新的本地玩家使上一次发送记录重新开始。
- 服务端传送的两个应答由协议状态机直接发出，不计入上一次发送记录，着地与顶住墙壁标记也不变（原版 `handleMovePlayer` 直接发送的数据包不更新这些字段）。

## 服务端施加的位置与速度

| 来源 | 处理 |
|---|---|
| Synchronize Player Position | 见[协议状态机 · 任意阶段的自动应答](protocol.md#任意阶段的自动应答) |
| 指向本地玩家自己的 Synchronize Vehicle Position | 速度立即设置；位置与朝向在之后的移动计算中插值，不插值的情况下立即设置；然后设置着地标记；不发送数据包（[世界模型 · 位置插值](world-model.md#位置插值)） |
| 指向被移除的载具的 Synchronize Vehicle Position | 与 Synchronize Player Position 相同地计算，只发送 Set Player Position and Rotation（[世界模型 · 乘坐关系](world-model.md#乘坐关系)） |
| 自己实体编号的 Set Entity Velocity | 替换当前速度 |
| Player Info Update 把自己改成旁观模式 | 竖直速度清零 |
| Explosion 中的玩家击退 | 加到当前速度上 |

服务端施加位置之后，下一 tick 的 `previousResult` 清空，`moveTo` 重新寻路，`rotate`、`lookAt` 留下的朝向不再生效。暂停期间收到的速度变化累积在当前速度上，恢复后参与模拟。

## 寻路与路径跟随

### 寻路

`moveTo` 开始时（以及每次重新寻路时）调用 `PathPlanner.plan`：

- 在 `ClientWorld.snapshot(玩家位置, maxDistance)` 得到的世界快照上，在新的虚拟线程中调用 Minestom 的 `PathGenerator.generate(快照, 起点, 目标, closeDistance, maxDistance, 20, 玩家包围盒, 是否着地, new PlayerNodeGenerator(服务端的标签), null)`。`pathVariance` 20 与 `maxDistance` 默认 50 取自 Minestom `Navigator.setPathTo` 的默认参数。`PlayerNodeGenerator` 内部有可变状态，每次寻路新建一个。
- 寻路期间期望移动方向为零。结果由 `moveTo` 在之后的 tick 中取回；寻路线程上的异常在取回时抛出，bot 结束。
- 同时用这次的快照计算目标的站立高度（见下文），并记下目标所在区块此刻是否已加载。

### 节点生成器

`PlayerNodeGenerator` 继承 Minestom 的 `GroundNodeGenerator`，节点高度、起跳与下落的规则不变，改变能否走过去的判断。Minestom 的 `NodeGenerator.canMoveTowards` 把节点脚部任何不是 `Block.AIR` 的方块当作障碍，地表常见的草、花、单层雪以及 `cave_air` 都走不过去，寻路结果为空。`PlayerNodeGenerator` 的判断：

- **碰撞**：与 Minestom 的默认实现相同，用 `CollisionUtils.handlePhysics` 让包围盒从起点直线移动到终点，三个方向都没有碰撞。
- **本库不模拟其效果的方块**：`Kinematics` 不模拟这些方块的效果（见[限制与待办 · 不覆盖的移动](limitations.md#不覆盖的移动)），走进去之后的移动与原版客户端不同，所以避开它们。包围盒从起点移动到终点时新接触这些方块的节点被排除；起点已经接触的方块不算，否则站在这些方块中时走不出来。`canMoveTowards` 与 `getWalkable` 都做这个判断，`JUMP` 节点与 `FALL` 节点的落点由后者排除。
  - 包围盒放在起点与终点的范围合起来，各边向内收缩 1.0E-5（原版 `checkInsideBlocks`）之后接触的方块中：流体（`Block.fluid()`：水、熔岩、含水方块、海草、海带、气泡柱），以及原版 26.2 中覆盖 `entityInside`、碰撞箱为空，并且会减速、造成伤害或者传送的方块：蜘蛛网、甜浆果丛、细雪、火、灵魂火、凋灵玫瑰、下界传送门、末地传送门、末地折跃门。节点之间相邻，斜向移动时包围盒经过的四格、下落时经过的整列都在这个范围内。有碰撞箱的仙人掌、营火、蜂蜜块由碰撞判断处理。
  - 原版 `LivingEntity.onClimbable` 只看脚部所在方块：终点所在的列上，脚部在起点与终点高度之间经过的方块属于服务端标签 `minecraft:climbable`，或者是打开的活板门（名称以 `_trapdoor` 结尾的方块，即原版 26.2 的 `TrapDoorBlock`），下面是朝向相同的梯子。
- 草、花、单层雪、火把、铁轨、`cave_air` 等碰撞箱为空、没有上述效果的方块可以走过去。

### 路径的判断

Minestom 的 `PPath.getState()` 只在包内可见，所以按节点列表判断：

- **REPATH 节点**：已搜索的节点数达到 `maxDistance × 10` 而开放列表不为空时，Minestom 在最近节点之后接一个坐标为 (0, 0, 0)、类型为 `REPATH` 的节点，并且只在它位于第一个时把路径判定为无效。所以先去掉第一个 `REPATH` 节点及其之后的节点，剩下的路径一定不完整。
- 列表为空：以「找不到路径的原因」取消。
- 去掉过 `REPATH` 节点，或者最后一个节点没有到达目标（按执行完毕的条件判断，高度取这个节点的站立高度）：只走到最近点（与 Minestom 的 BEST_EFFORT 相同），走完之后以「找不到路径的原因」取消。
- 找不到路径的原因：寻路时目标所在区块未加载为 `CHUNK_NOT_LOADED`，否则为 `NO_PATH`。快照对未加载的区块返回屏障，目标的区块未加载时路径只能停在已加载区域的边缘；调用方等待这个区块加载之后再次 `moveTo` 即可。按寻路时的状态判断：目标在 `maxDistance` 之外时快照不包含它的区块，只能查 `ClientWorld`；走完最近点时这个区块可能已经加载，调用方等待区块加载会立即返回。
- 其余为完整路径，最后一个节点为目标本身；节点全部到达之后直接朝目标移动。

### 站立高度

`GroundNodeGenerator` 的节点高度是整数：它从上往下找到包围盒下方第一层有 `solid()` 方块的格子，节点高度为这一层加一，与方块碰撞箱的实际高度无关（半砖上的节点比半砖顶面高 0.5，栅栏上的节点比栅栏顶面低 0.5）。所以跟随时为每个节点计算站立高度：在节点的水平位置上，让玩家包围盒从节点高度加一格处向下移动两格（Minestom 的 `CollisionUtils.handlePhysics`，使用这次寻路的快照与包围盒），停下的高度。节点所在层的地毯、雪层等不算 `solid()` 但有碰撞箱的方块也计算在内。

目标的站立高度与路径终点的计算相同：按 `PathGenerator.generate` 的方式用 `GroundNodeGenerator.gravitySnap`（最多下落 100 格）把目标高度换成节点高度，找不到时保持原来的高度，再按上面的方法计算。所以站在半砖上时脚部高度与目标的站立高度相同；目标在正上方或者正下方的另一层时不算到达。

### 跟随

`PathFollower.next` 每 tick：

- 与节点中心的水平距离小于 0.3 时视为到达这个节点。高度条件：`JUMP` 要求脚部不低于站立高度 0.1 以上；`FALL` 要求已着地并且不高于站立高度 0.5 以上；其余要求与站立高度的差小于 1。
- 朝下一个节点的中心移动。
- **起跳**：`PhysicsUtils` 没有原版的台阶抬升，所以下一个节点的站立高度比脚部高出 0.001 以上时都要起跳，与节点类型无关：着地，并且与节点中心的水平距离不大于 0.9（玩家半宽 0.3 加上节点中心到格子边缘的 0.5，再留 0.1 的余量）或者已经顶住墙壁时起跳。0.001 的余量留给碰撞计算的误差。
- `FALL` 节点：向节点方向走出边缘。
- `GroundNodeGenerator` 不产生 `WALK`、`JUMP`、`FALL` 以外的节点类型。

`moveTo` 执行完毕的条件在每 tick 开始时检查：玩家着地，与目标的水平距离不大于 `closeDistance`，并且脚部与目标的站立高度相差小于 1。

### 重新寻路

以下情况重新寻路，最多 3 次，超过后以 `BLOCKED` 取消：

- 连续 20 tick 水平位移小于 0.05；
- 路径上的方块（节点所在格子及其上下各一格）被 Block Update 改变；
- 服务端传送了玩家。

寻路使用开始时的快照与玩家位置，所以寻路期间的传送让这次的结果作废，取回之后不使用它，直接重新寻路；寻路期间被改变的方块先记下来，取回结果之后其中有方块在路径上时重新寻路。

## 暂停移动

`Bot.suspendMovement()`/`resumeMovement()` 控制单个 bot，`BotRuntime.suspendMovement()`/`resumeMovement()` 控制全部 bot；任意一级处于暂停状态，该 bot 的移动就暂停。两者都只设置标记，在下一轮调度中生效；实际状态改变时产生 `MovementSuspensionChanged`，`StateReader.movementSuspended()` 为实际状态。

暂停移动与暂停 tick 相互独立。暂停 tick 时整个 `BotTicker` 不执行，暂停移动时仍会执行 tick，手部交互与一次性请求照常推进。

**暂停期间停止**：

- 移动计算整个不执行（包括插值），重力不生效，位置保持不变；骑乘时位置与速度不变，也不插值。
- `moveTo`、`moveForward`、`jumpTo` 不推进，「连续若干 tick 没有进展」的计数也暂停；它们的期望移动方向与移动方式保持暂停开始时的值。
- 暂停期间在队列中轮到的 `moveTo`、`moveForward`、`jumpTo` 不开始：不寻路，不接管朝向，也不清除 `rotate`、`lookAt` 设置的朝向；它留在队首，恢复之后才开始，排在它后面的动作（包括 `rotate`、`lookAt`）随之等待。

**暂停期间照常进行**：

- 数据包处理与全部自动应答，服务端传送照常生效；
- 朝向的决定，以及队列中轮到的 `rotate`、`lookAt`；
- 移动包（位置不变，所以没有位置变化；朝向变化时发送 Set Player Rotation；每 20 tick 一次的位置照常发送）；
- Player Input 与疾跑 Player Command（朝向改变时，前、后、左、右按新朝向重新计算）；
- 手部交互队列与一次性请求；
- Player Loaded 与 Client Tick End。

原版服务端会在玩家悬空 80 tick 之后以 `multiplayer.disconnect.flying` 踢出玩家（允许飞行的情况除外）。Minestom 没有这项检查。

## 骑乘

本地玩家乘坐模型中的一个实体时（乘坐关系见[世界模型 · 乘坐关系](world-model.md#乘坐关系)），移动步骤按原版 `LocalPlayer.tick` 中乘客的分支执行（`MovementController.rideTick`）。

### 骑乘时的移动步骤

原版的乘客只在最下层的载具执行之后沿乘坐关系逐层执行，这要求最下层的载具所在区块已加载，中间的载具（玩家除外）所在区块也已加载；任何一个未加载时，移动步骤什么都不做，除 Client Tick End 以外不发送数据包（手部交互与快捷栏同步照常）。否则：

1. 移动队列照常推进，朝向照常决定。
2. 疾跑：只有载具可以疾跑（骆驼）并且由本地玩家控制时才保持或开始疾跑，否则本地停止疾跑。
3. Player Input 照常在变化时发送。
4. 着地与顶住墙壁设为 false。没有暂停时：速度设为零，清空上一次模拟结果，有正在进行的插值时插值一步（位置随后被座位覆盖，朝向保留），位置设为座位位置；位置变化时产生 `PlayerMoved`，移动动作照常按位移判断是否被阻挡。
5. 发送 Set Player Rotation（插值时为插值之后的朝向）；不发送移动包，移动包的上一次发送记录不变。
6. 本地玩家控制最下层的载具时，再发送 Move Vehicle（载具在模型中的位置、偏航角、俯仰角、着地），然后在疾跑状态改变时发送 Player Command。不控制时不发送疾跑的 Player Command，下马之后的第一次移动包之前再发送。

控制载具时，本库不模拟载具自身的移动：Move Vehicle 发送的是模型中服务端给出的载具位置。

### 控制载具

`VehicleControl.controlledBy`（原版各实体的 `getControllingPassenger` 与 `isLocalInstanceAuthoritative`），前提都是本地玩家为载具的第一个乘客：

| 载具 | 条件 |
|---|---|
| 船与竹筏（`EntityType` 名称以 `_boat` 或 `_raft` 结尾） | 无 |
| 马、驴、骡、骷髅马、僵尸马、骆驼、骆驼尸壳、鹦鹉螺、僵尸鹦鹉螺 | 装有鞍；实体存活、不是幼年并且已被驯服 |
| 羊驼、行商羊驼 | 装有鞍 |
| 猪、炽足兽 | 装有鞍；实体存活、不是幼年；主手或副手拿着胡萝卜钓竿、诡异菌钓竿 |
| 快乐恶魂 | 身体槽位佩戴挽具；实体存活、不是幼年；没有保持静止 |
| 其余实体（包括矿车） | 不能控制 |

「装有鞍」「佩戴挽具」按原版 `Mob.hasValidEquippableItemForSlot`：该槽位有物品，物品的 `equippable` 组件对应这个槽位，并且允许这个实体类型装备（`allowedEntities` 为标签时按 `TagTable` 判断，为编号列表时按 `EntityType` 的序号判断，没有时允许全部）。存活按元数据 `LIVING_HEALTH` 大于 0 判断，幼年按 `AGEABLE_IS_BABY`，驯服按 `HORSE_IS_TAME`（马类）或 `TAMEABLE_IS_TAMED`（鹦鹉螺）。

本地玩家控制的载具不被 Teleport Entity 移动（[世界模型 · 实体数据包](world-model.md#实体数据包)）。

### 座位位置

`RidingSeat.seat`（原版 `Entity.positionRider`）：载具位置 + 载具的乘客挂点 − 玩家的载具挂点。载具位置取模型中服务端最后给出的值，不插值。

- **默认的乘客挂点**：按本地玩家在载具乘客列表中的序号取（超出时取最后一个），随载具偏航角旋转（绕 y 轴旋转 −偏航角，使用 `SineTable`）。挂点取 `EntityTypeData.passengerAttachments`，没有时为实体高度处。活体载具的挂点乘以 `minecraft:scale` 属性。幼年时使用原版各实体类的幼年挂点：马为高度 − 0.125 再乘 0.7；骷髅马与僵尸马为高度 − 0.25 再乘 0.7；驴与骡为 (0, 高度 + 0.03125, −0.3125) 乘 0.5；羊驼为 (0, 羊驼高度 − 0.25, −0.3) 乘 0.5；猪为 0.5；炽足兽为 0.65625；鹦鹉螺为 0.5；鸡为 0.375；其余为成年挂点乘年龄缩放（0.5，快乐恶魂为 0.2375）。
- **船与竹筏**（原版 `AbstractBoat`）：高度为船高度的三分之一、竹筏高度的 0.8888889；只有一个乘客时向前偏移 0（带箱子的为 0.15），多个乘客时第一个为 0.2、其余为 −0.6；随偏航角旋转。
- **史莱姆与岩浆怪**（原版 `AbstractCubeMob`）：尺寸按大小（`CUBE_SIZE`）与 scale 属性缩放，挂点在顶面向下 `0.015625 × 大小 × scale` 处，不旋转。
- **骆驼与骆驼尸壳**（原版 `Camel`）：高度随坐下与站起的过渡动画变化（由元数据 `CAMEL_LAST_POSE_CHANGE_TICK` 与 World Age 得到姿态时间），第一个乘客在前（0.5），多个乘客时其余在后（−0.7），乘以 scale 与年龄缩放（幼年 0.6），随偏航角旋转。World Age 在 tick 的最后推进，所以使用的是上一个 tick 结束时的值。
- **马、驴、骡、骷髅马、僵尸马**：在默认挂点上加上人立动画的偏移 `(0, 0.15 × standAnimO × scale, −0.7 × standAnimO × scale)`，随偏航角旋转。`standAnimO` 为载具上一 tick 的人立动画进度，由每 tick 的实体步骤更新，与本地玩家是否乘坐它无关。
- **羊驼、矿车、鸡**：使用默认挂点。
- **玩家的载具挂点**：(0, 0.6, 0) 乘以玩家的 scale 属性。

本库识别幼年状态的载具类型为马、驴、骡、骷髅马、僵尸马、羊驼、行商羊驼、骆驼、骆驼尸壳、猪、炽足兽、鸡、快乐恶魂、鹦鹉螺、僵尸鹦鹉螺、史莱姆、岩浆怪，其余实体按成年处理。幼年与坐下姿态的取值是本库内置的原版 26.2 数据（[限制与待办 · 对 Minestom 的绕过方法](limitations.md#对-minestom-的绕过方法)）。

登上船或竹筏时偏航角设为船的偏航角。`lookAt` 与挖掘使用的眼睛位置随座位位置变化。不覆盖炽足兽行走时乘客的上下起伏：原版按炽足兽每 tick 的位移计算行走动画，本库不插值实体位置，也不模拟载具的移动，没有这个位移。

[目录](README.md) · 上一篇：[动作](actions.md) · 下一篇：[查询与观察](observation.md)
