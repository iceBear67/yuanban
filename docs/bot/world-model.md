# 世界模型

[目录](README.md) · 上一篇：[注册表与静态数据](registries-and-data.md) · 下一篇：[动作](actions.md)

本篇目录：

- [BotState](#botstate)
- [区块](#区块)
- [区段共享与写入时复制](#区段共享与写入时复制)
- [世界快照](#世界快照)
- [实体](#实体)
  - [实体的包围盒](#实体的包围盒)
  - [元数据的命名条目](#元数据的命名条目)
  - [每 tick 的实体步骤](#每-tick-的实体步骤)
- [实体数据包](#实体数据包)
- [乘坐关系](#乘坐关系)
- [本地玩家](#本地玩家)
  - [新的本地玩家](#新的本地玩家)
  - [游戏模式与能力](#游戏模式与能力)
  - [属性](#属性)
  - [药水效果与物品冷却](#药水效果与物品冷却)
  - [本地玩家的数据包](#本地玩家的数据包)
  - [位置插值](#位置插值)
- [加载判断](#加载判断)
- [物品栏](#物品栏)
- [聊天日志](#聊天日志)
- [界面状态](#界面状态)
- [时间与世界边框](#时间与世界边框)
  - [World Age 与时钟](#world-age-与时钟)
  - [tick 状态](#tick-状态)
  - [世界边框](#世界边框)
  - [天气](#天气)
  - [难度、默认出生点与距离](#难度默认出生点与距离)

相关源码：[`state/BotState.java`](../../bot/control/src/main/java/io/ib67/yuanban/bot/state/BotState.java)、[`world`](../../bot/model/src/main/java/io/ib67/yuanban/bot/world)、[`entity`](../../bot/model/src/main/java/io/ib67/yuanban/bot/entity)、[`player`](../../bot/model/src/main/java/io/ib67/yuanban/bot/player)、[`inventory`](../../bot/model/src/main/java/io/ib67/yuanban/bot/inventory)、[`chat`](../../bot/model/src/main/java/io/ib67/yuanban/bot/chat)、[`hud`](../../bot/model/src/main/java/io/ib67/yuanban/bot/hud)。

## BotState

`BotState` 是一个 bot 的全部模型，只在调度线程上读写。组成见[架构参考 · 模型的组成](architecture.md#模型的组成)，各部分在什么事件下重建见[架构参考 · 世界与玩家的重建](architecture.md#世界与玩家的重建)。

- `world()` 在不在任何世界中时抛出 `IllegalStateException`，`worldOrNull()` 返回 null。
- `registries()` 在完成配置之前抛出 `IllegalStateException`。
- `newPlayer(LocalPlayer)` 同时换成空的 `InventoryModel`。
- `checkThread()` 供修改模型的入口检查当前线程。

## 区块

`ClientWorld` 实现 Minestom 的 `Block.Getter`，区块保存在 `Long2ObjectMap<ChunkColumn>` 中（键为 `CoordConversion.chunkIndex`），每列为从维度最低 y 开始的 `SectionRef` 数组。

- **安装**（Chunk Data and Update Light）：区段已由解码上下文解码，调度线程只安装 `SectionRef` 数组与方块实体的 NBT。加载区域外的区块数据直接忽略（原版客户端行为）。区段数不等于维度高度除以 16 时抛出 `IllegalArgumentException`。安装时先删除这个区块列中原有的方块实体数据。产生 `ChunkLoaded`。
- **卸载**：Unload Chunk；或者 Set Center Chunk、Set Render Distance 使区块落到加载区域外时立即卸载（wiki 写明客户端可以这样做）。卸载时删除其中的方块实体数据，产生 `ChunkUnloaded`。
- **加载区域**：以中心区块为中心、半径为 `max(2, 服务端视距) + 3` 的正方形（原版 `ClientChunkCache.calculateStorageRange`）。服务端视距取自 Login (play) 与 Set Render Distance。wiki Set Center Chunk 写的宽度为 `2 × 服务端视距 + 7`，服务端视距小于 2 时与原版不同，本库按原版。
- **改变方块**（Block Update、Update Section Blocks；后者由 MCProtocolLib 解码为绝对坐标）：所在区块没有加载或者超出维度高度时什么都不做；否则先读取旧方块再写入，新方块的方块实体类型与旧方块不同时删除这个位置的方块实体数据（原版客户端行为）。产生 `BlockChanged(位置, 旧方块, 新方块)`，并通知当前的移动动作（`moveTo` 据此判断是否重新寻路）。
- **Chunk Biomes**：对区块列中的每个区段依次用 `MinecraftTypes.readDataPalette(数据, PaletteType.BIOME, 生物群系数量)` 读出生物群系调色板，替换这个区段的生物群系；区块没有加载时忽略。
- **生物群系的读取**（`ClientWorld.biome`）：照常用 `DataPalette.get` 读取。MCProtocolLib 的 `DataPalette.index` 对 4×4×4 的生物群系调色板算错下标，一个区段中不止一种生物群系时读出的结果错误（待办 T1）。模型中保存生物群系的编号，`StateReader.biome` 按同步的 `worldgen/biome` 注册表换成名称。
- **Block Entity Data**：只在所在区块已加载、位置在维度高度范围之内、并且该位置的方块有同一类型的方块实体时（`Block.blockEntityType().id()` 等于数据包中类型的序号），替换这个位置的 NBT（数据包中没有 NBT 时为空的 `NbtMap`），产生 `BlockEntityChanged`；其余情况忽略（原版 `handleBlockEntityData` 只应用到 `level.getBlockEntity(pos, type)` 存在的方块实体）。
- **读取方块**（`getBlock(x, y, z, condition)`）：返回 `Block.fromStateId(...)`，不带 NBT，`condition` 不影响结果；维度高度范围之外返回空气；未加载的区块抛出 `ChunkNotLoadedException`。
- **读取方块实体**（`blockEntity(x, y, z)`）：返回 `BlockEntityStore` 中的 `NbtMap`，没有时为 null；未加载的区块抛出 `ChunkNotLoadedException`。方块实体的 NBT 单独保存，不附加到 `Block` 上。
- **碰撞检查**（`collidesWithBlocks(位置, 包围盒)`）：包围盒放在这个位置时是否与方块的碰撞箱相交（原版 `BlockCollisions`）。碰撞箱可以超出所在的格子（例如栅栏），所以向四周多检查一格；未加载的区块中没有方块。`resetPos` 与位置插值使用它。

`BlockEntityStore` 按原版 `BlockPos.asLong` 的布局（x、z 各 26 位，y 12 位）编码坐标作为键。

## 区段共享与写入时复制

- `SharedSection(blocks, biomes, blockCount, fluidCount)` 是一个区段的方块与生物群系两个 `DataPalette`，以及数据包给出的非空气方块数与流体数。来自区段池的实例在全部 bot 之间共享，永远不被写入。
- `SectionRef` 持有一个 `SharedSection` 和一个「共享」标记。第一次写入（`setBlock`、`setBiomes`）时用 `new DataPalette(原调色板)` 复制出私有副本再写入。
- 写入方块时不使用 `DataPalette.set` 的返回值（它的返回值为调色板中的下标，不能当作方块状态），也不使用 `ChunkSection.setBlock`（它按这个返回值维护方块数量）；需要旧状态时先读取。

## 世界快照

`ClientWorld.snapshot(中心, 半径)` 返回 `WorldSnapshot implements Block.Getter`，供寻路线程使用：

- 收集水平范围内各区块的 `SectionRef` 当前持有的 `SharedSection`，并把这些 `SectionRef` 都标记为共享（`share()`）。之后调度线程再写入时会先复制，快照中的 `DataPalette` 因此不会再变化。
- 复制的只是引用，开销与区段数量成正比。
- 范围内与范围外未加载的区块返回 `Block.BARRIER`，寻路把它视为无法通过；维度高度范围之外返回空气。

## 实体

`EntityStore` 按编号排列保存 `ClientEntity`（`Int2ObjectAVLTreeMap`），所以 `StateReader.entities()` 按编号排列。本地玩家不在 `EntityStore` 中。

`ClientEntity` 保存：

- 编号、UUID、MCProtocolLib 的 `EntityType`、Spawn Entity 的附加数据（`ObjectData`）；
- 位置与朝向、头部朝向、速度、是否着地。位置保存服务端最后给出的值，不插值；
- 元数据：`Map<Integer, EntityMetadata<?, ?>>`，按下标保存 MCProtocolLib 解码出的条目；
- 装备：`EnumMap<EquipmentSlot, ItemStack>`，没有空物品；
- 属性（`AttributeMap`，基础值为属性的默认值）、药水效果（`Map<Effect, ActiveEffect>`，不随 tick 递减）；
- 乘坐的实体、乘客列表、拴住它的实体；
- 马类的人立动画进度及其上一 tick 的值（原版 `standAnim`、`standAnimO`），供骑乘位置使用。

`snapshot()` 生成不可变的 `EntitySnapshot`，其中的 `boundingBox` 按下一节计算。

### 实体的包围盒

`EntityDimensions.boundingBox(类型, 元数据, scale 属性)` 按原版 `Entity.getDimensions`、`LivingEntity.getDimensions` 与各实体类的 `getDefaultDimensions` 计算。`lookAt(int entityId)`、`attack`、`interact` 按它计算瞄准点（包围盒中心）。

- 活体以外的实体：实体类型的宽高。
- 活体：睡觉的姿势为固定尺寸 0.2 × 0.2；其余为默认尺寸乘以 `minecraft:scale` 属性，默认尺寸为固定尺寸时不乘。
- 默认尺寸：
  - 玩家与人体模型（原版 `Avatar.POSES`）：潜行 0.6 × 1.5，滑翔、游泳、激流冲刺 0.6 × 0.6，死亡为固定尺寸 0.2 × 0.2，其余 0.6 × 1.8；
  - 幼年的骆驼：坐下 0.95 × 0.425，其余 0.95 × 1.4；
  - 其余实体幼年时取原版各实体类的幼年尺寸（`BABY_DIMENSIONS`，例如僵尸一类与村民 0.49 × 0.98、马为成年尺寸乘 0.7），否则取实体类型的宽高。
- 幼年按原版各实体类的 `isBaby` 判断：`AgeableMob` 读取 `AGEABLE_IS_BABY`；僵尸一类、猪灵、僵尸疣猪兽读取各自的幼年标记；盔甲架为小型盔甲架。史莱姆、岩浆怪、鹦鹉、僵尸鹦鹉螺、青蛙、骆驼尸壳不会是幼年。
- 不覆盖其它随状态变化的尺寸（史莱姆一类与幻翼的大小、河豚的膨胀、成年骆驼坐下、潜影贝等），这些情况取上面的尺寸。
- 与原版 `EntityDimensions` 相同，尺寸以 float 保存与计算：实体类型的宽高换成 float，`minecraft:scale` 属性先换成 float（原版 `LivingEntity.getScale`）再相乘，最后换成 double 交给 `BoundingBox`。

幼年与姿势的尺寸是本库内置的原版 26.2 取值，见[限制与待办 · 对 Minestom 的绕过方法](limitations.md#对-minestom-的绕过方法)。

### 元数据的命名条目

`view.EntityMetaIndex` 列出本库读取的条目，每项为 `EntityMeta<T>(下标, 默认值, 掩码)`：掩码为 0 时按下标取值，否则取标记字节中的这一位。`EntityMetaIndex.get(元数据, 条目)` 与 `EntitySnapshot.meta(条目)` 读取，没有收到时返回默认值。下标与 Minestom 的 `MetadataDef` 相同（测试中逐项比对）。

| 常量 | 下标 | 掩码或默认值 |
|---|---|---|
| `HAS_NO_GRAVITY` | 5 | 默认 false |
| `POSE` | 6 | 默认 `Pose.STANDING` |
| `LIVING_HEALTH` | 9 | 默认 1 |
| `ARMOR_STAND_IS_SMALL` | 15 | 掩码 0x01 |
| `AGEABLE_IS_BABY`、`ZOMBIE_IS_BABY`、`ZOGLIN_IS_BABY` | 16 | 默认 false |
| `PIGLIN_IS_BABY` | 17 | 默认 false |
| `CUBE_SIZE`（史莱姆、岩浆怪） | 18 | 默认 1 |
| `HORSE_FLAGS` | 18 | 默认 0 |
| `HORSE_IS_TAME` | 18 | 掩码 0x02 |
| `HORSE_IS_REARING` | 18 | 掩码 0x20 |
| `TAMEABLE_IS_TAMED`（包括鹦鹉螺） | 18 | 掩码 0x04 |
| `HAPPY_GHAST_STAYS_STILL` | 19 | 默认 false |
| `CAMEL_LAST_POSE_CHANGE_TICK` | 20 | 默认 0 |

### 每 tick 的实体步骤

`ClientWorld.tickEntities`（原版 `ClientLevel.tickEntities`），在 tick 的移动步骤之前执行，与加载状态无关：

- 所在区块已加载、没有乘坐其它实体的实体执行一步，然后依次执行它的乘客（玩家以外的乘客所在区块也要已加载）。
- 世界冻结并且不在步进中时，玩家以外、没有玩家乘客（直接或间接，包括本地玩家）的实体不执行。
- 一步的内容（`ClientEntity.tick`）：本库只模拟马类（马、驴、骡、骷髅马、僵尸马、羊驼、行商羊驼、骆驼、骆驼尸壳）的人立动画。人立标记为真时进度向 1 靠近（`standAnim + ((1 − standAnim) × 0.4 + 0.05)`，不超过 1），否则向 0 回落（`standAnim + ((0.8 × standAnim³ − standAnim) × 0.6 − 0.05)`，不低于 0），以 float 计算。

## 实体数据包

目标为本地玩家实体编号的实体移动包被忽略（原版客户端行为）；速度、元数据、属性、效果写入 `LocalPlayer`。

| 数据包 | 处理 | 观察记录 |
|---|---|---|
| Spawn Entity | 这个编号已有实体时，先按 Remove Entities 的方式移除旧实体（不记下本地玩家乘坐过的载具）；再加入新实体：位置与朝向、头部朝向、速度、附加数据。这个编号是记下的被移除载具时清除记录 | 旧实体的 `EntityRemoved`，然后 `EntitySpawned` |
| Update Entity Position、Update Entity Position and Rotation | MCProtocolLib 已经把位移除以 4096，加到当前位置上 | `EntityMoved` |
| Update Entity Rotation | 设置朝向与着地 | 无 |
| Set Head Rotation | 设置头部朝向 | 无 |
| Teleport Entity（`ClientboundEntityPositionSyncPacket`） | 设置绝对位置、朝向、着地；不使用数据包中的速度。本地玩家控制的载具不改变（原版只更新相对移动的基准位置，本库不记录它） | `EntityMoved` |
| Synchronize Vehicle Position（`ClientboundTeleportEntityPacket`） | 指向其它实体：按[传送的计算](protocol.md#传送的计算)设置位置、朝向与速度。指向本地玩家自己：见[位置插值](#位置插值)。实体不在模型中、并且是记下的被移除载具：应用到本地玩家，见[乘坐关系](#乘坐关系) | `EntityMoved` |
| Set Entity Velocity | 替换速度 | 无 |
| Set Entity Metadata | 按下标写入；实体不在模型中时忽略 | `EntityMetadataChanged`（数据包中的条目） |
| Set Equipment | 物品为空时移除这个槽位 | 每个槽位一条 `EntityEquipmentChanged` |
| Update Attributes | 替换列出的属性的基础值与全部修饰符 | 无 |
| Entity Effect、Remove Entity Effect | 写入或移除效果 | 目标为本地玩家时 `EffectChanged` |
| Set Passengers、Remove Entities | 见[乘坐关系](#乘坐关系) | Remove Entities 为每个实体一条 `EntityRemoved` |
| Link Entities | `getEntityId()` 是被拴住的实体，`getAttachedToId()` 是拴住它的实体，为 -1 时解开 | 无 |
| Pickup Item | 不改变模型 | `ItemPickedUp` |
| Entity Event | 不改变模型；MCProtocolLib 遇到不认识的编号时解码失败，bot 结束 | `EntityStatusReceived` |
| Damage Event | 不改变模型；伤害类型的编号换成名称，不在注册表中时 bot 结束；来源编号 -1 换成 null | `EntityDamaged` |

## 乘坐关系

**Set Passengers**（原版 `handleSetEntityPassengersPacket`）：

- 载具不在模型中时整个数据包被忽略。
- 载具现有的乘客全部停止乘坐，然后列出的乘客按顺序乘坐它（原版 `Entity.startRiding`，强制乘坐）：
  - 模型中没有的编号被跳过；已经乘坐这个载具的不重复加入；
  - 载具为标记或不祥之物生成器时不乘坐；
  - 乘客是载具直接或间接乘坐的实体时不乘坐，所以乘坐关系不会形成循环；
  - 其余乘客先离开原来的载具，再加入这个载具乘客列表的末尾；
  - 实体乘坐自己时 bot 因异常结束（原版之后的乘坐计算会无限循环）。
- 本地玩家在列出的乘客中时，清除记下的被移除载具编号；新登上船或竹筏时（之前没有直接或间接乘坐它），偏航角设为船的偏航角。这两项与乘坐是否成功无关。

**Remove Entities**（原版 `Entity.setRemoved`）：被移除的实体离开它乘坐的载具，它的乘客（包括本地玩家）停止乘坐。所以模型中的乘坐关系只指向存在的实体。

**被移除的载具**：被移除的实体是本地玩家直接或间接乘坐的实体时，记下它的编号（原版 `removedPlayerVehicleId`）。之后模型中没有这个编号的实体时，对它的 Synchronize Vehicle Position 应用到本地玩家：按[传送的计算](protocol.md#传送的计算)设置位置、朝向与速度，发送当前位置与朝向的 Set Player Position and Rotation（着地与顶住墙壁为 false），不发送 Confirm Teleportation；产生 `PlayerTeleported`，清除 `rotate`、`lookAt` 留下的朝向，清空上一次模拟结果并触发 `moveTo` 重新寻路。记下的编号在这个编号的实体重新生成、本地玩家登上任何载具、离开世界时清除。

本地玩家骑乘时的移动见[移动模拟与骑乘 · 骑乘](movement.md#骑乘)。

## 本地玩家

`LocalPlayer` 保存实体编号、UUID 与名称、游戏模式、能力、生命值、饥饿值、饱和度、死亡状态、经验、属性、药水效果、物品冷却、元数据、快捷栏槽位、位置与朝向、速度、着地与顶住墙壁标记、擦过墙壁标记、支撑方块、起跳延迟、正在进行的插值、潜行状态、上一次 `PhysicsResult`、乘坐的实体。

- 眼睛高度：站立 1.62、潜行 1.27，乘以 `minecraft:scale` 属性。
- 包围盒：宽 0.6，高 1.8（潜行时 1.5），乘以 `minecraft:scale` 属性。
- 与原版 `Avatar` 的尺寸相同，以上数值都是 float（0.6F、1.8F、1.5F、1.62F、1.27F），`minecraft:scale` 属性先换成 float 再相乘。包围盒的半宽因此为 0.30000001192092896：原版服务端按这个半宽计算玩家的包围盒，按 double 的 0.3 计算时，贴墙的玩家在服务端的包围盒伸进墙内约 1.2E-8，Paper 判定玩家卡进方块，把玩家传送回去。
- `snapshot()` 生成 `PlayerSnapshot`：实体编号、UUID、名称、位置、速度、着地、生命值、饥饿值、饱和度、死亡、游戏模式、能力（`AbilitiesSnapshot`）、经验进度、等级、总经验、属性取值、药水效果（`duration` 为剩余 tick 数）、快捷栏槽位、物品冷却、元数据。

### 新的本地玩家

新的连接或重新配置之后的 Login (play)，以及 Respawn，都创建新的本地玩家（原版客户端新建 `LocalPlayer`）。新玩家位于原点，偏航角 -180，俯仰角 0，速度为零，不着地，没有乘坐，站立，存活；元数据为空，没有药水效果与物品冷却；属性为玩家的基础值、没有修饰符；生命值 20，饥饿值 20，饱和度 5，经验为 0；快捷栏槽位 0；物品栏为空，没有打开的窗口。这些重置不产生 `SlotChanged` 等观察记录。Respawn 按 Data kept 保留的部分见[协议状态机 · Login (play) 与 Respawn](protocol.md#login-play-与-respawn)。

### 游戏模式与能力

- 游戏模式（MCProtocolLib 的 `GameMode`）取自 Login (play)、Respawn、Game Event 3，以及自己 UUID 的 Player Info Update（自己不在玩家列表中时这项更新被忽略）。改变时产生 `GameModeChanged`。
- Login (play)、Respawn、Game Event 3 设置游戏模式时同时按原版 `GameType.updatePlayerAbilities` 设置能力：创造模式可以飞行、瞬间破坏、无敌；旁观模式可以飞行、无敌并且正在飞行，不能瞬间破坏；生存与冒险模式这几项都为 false；冒险模式与旁观模式不能建造。Player Info Update 只改变游戏模式，不改变能力。
- Player Info Update 把自己改成旁观模式时（玩家列表中原来的游戏模式与新的不同，新条目按初始值生存模式计算），竖直速度清零（原版 `LocalPlayer.onGameModeChanged`）。
- 能力的初始值与原版 `Abilities` 相同：飞行速度 0.05，行走速度 0.1，可以建造，其余为 false。Player Abilities 覆盖无敌、正在飞行、允许飞行、瞬间破坏、飞行速度、行走速度；它不包含「可以建造」，这一项只由游戏模式决定。

### 属性

`AttributeMap` 保存每个属性的基础值与按名称（`Key`）排列的 MCProtocolLib `AttributeModifier`，同名的修饰符替换之前的：

- 取值（wiki Attribute）：基础值加上全部 `ADD`；再对每个 `ADD_MULTIPLIED_BASE` 加上「加法之后的值 × 数值」；再对每个 `ADD_MULTIPLIED_TOTAL` 乘以「1 + 数值」；最后限制到 `AttributeData` 的最小值与最大值。
- 没有收到过的属性取基础值（同样限制到范围内）。本地玩家的基础值取原版 `Player.createAttributes`：移动速度为 `(double) 0.1F`，攻击伤害为 1，路径点的发送与接收距离为 6.0E7，其余为属性的默认值。其它实体的基础值为属性的默认值。
- Update Attributes 替换列出的属性的基础值与全部修饰符（原版客户端行为）。
- 疾跑状态改变时，移动控制器在本地加入或移除 `minecraft:sprinting` 修饰符（`ADD_MULTIPLIED_TOTAL` 0.3），见[移动模拟与骑乘 · 疾跑](movement.md#疾跑)。
- Respawn 带 Data kept 0x01 时复制基础值与全部修饰符（`copyAll`），否则只复制基础值（`copyBaseValues`）。
- `values()` 只给出已经收到或者本地修改过的属性，所以快照中的属性表不包含从没收到过的属性。

### 药水效果与物品冷却

- `EffectMap`：`Map<Effect, ActiveEffect>`，每 tick 剩余时间减一，无限的效果（`duration` 为 -1）不减，减到 0 之后停在 0。`level(效果)` 为放大器加一，没有时为 0。
- 物品冷却：Set Cooldown 以冷却组的 `Key` 为键保存剩余 tick 数，每 tick 减一，减到 0 时移除；数据包给出的 tick 数不大于 0 时直接移除。

### 本地玩家的数据包

| 数据包 | 处理 | 观察记录 |
|---|---|---|
| Synchronize Player Position | 见[协议状态机 · 任意阶段的自动应答](protocol.md#任意阶段的自动应答) | `PlayerTeleported` |
| Player Rotation | 按相对标记设置朝向，俯仰角限制到 [-90, 90]；清除 `rotate`、`lookAt` 留下的朝向 | 无 |
| Set Health | 设置生命值、饥饿值、饱和度；生命值不大于 0 时玩家死亡 | `HealthChanged` |
| Combat Death（目标为自己） | 玩家死亡 | `PlayerDied` |
| Player Abilities | 覆盖能力 | 无 |
| Set Experience | 设置经验 | `ExperienceChanged` |
| Set Cooldown | 设置或移除冷却 | 无 |
| Player Info Update / Remove | 玩家列表；自己的游戏模式 | `GameModeChanged` |
| Explosion | 玩家击退加到当前速度上 | 无 |
| Set Held Item | 设置快捷栏槽位 | `HeldSlotChanged` |
| Acknowledge Block Change | 交给手部控制器完成序号不大于确认值的步骤 | 无 |

**玩家死亡**：标记死亡，移动与手部两条队列中执行中和排队中的动作以 `PLAYER_DIED` 取消（手部动作照常清理）。死亡期间不执行移动模拟。Login (play) 清除死亡状态；Respawn 创建的新玩家是存活的。

### 位置插值

Synchronize Vehicle Position 指向本地玩家自己时（原版本地玩家也在世界的实体中，由 `handleTeleportEntity` 照常处理）：

- 按[传送的计算](protocol.md#传送的计算)得出新的位置、朝向与速度，速度立即设置。
- 本地玩家所在区块已加载、或者 X、Y、Z 中有相对字段，并且新位置与当前位置的距离不超过 64 时，位置与朝向在之后的移动计算中插值（原版 `moveOrInterpolateTo`）：正在进行的插值目标相同时继续，否则从当前的位置与朝向开始新的 3 步插值（`Interpolation.start`）。不产生 `PlayerTeleported`。
- 其余情况立即设置位置与朝向，产生 `PlayerTeleported`。
- 然后设置着地标记（原版 `setOnGround`）：着地时在当前位置查找支撑方块，找不到并且上一次不是「着地但没有支撑方块」时保留原来的支撑方块。
- 不发送任何数据包；清除 `rotate`、`lookAt` 留下的朝向，清空上一次模拟结果并触发 `moveTo` 重新寻路。

插值每一步的计算见[移动模拟与骑乘 · 每 tick 的移动计算](movement.md#每-tick-的移动计算)。

## 加载判断

`PlayerLoadTracker`（原版 `LevelLoadTracker`）决定何时发送 Player Loaded：

| 状态 | 进入 |
|---|---|
| `IDLE` | 初始；离开世界时 |
| `WAITING_FOR_SERVER` | Login (play)、Respawn 时开始等待，记下此刻的墙钟时间 |
| `WAITING_FOR_PLAYER_CHUNK` | 处于 `WAITING_FOR_SERVER` 时收到 Game Event 13（`LEVEL_CHUNKS_LOAD_START`）；其余时候收到的 Game Event 13 被忽略 |
| `LOADED` | 处于 `WAITING_FOR_PLAYER_CHUNK` 时，某个 tick 满足加载条件，或者自开始等待起超过 30 秒（墙钟时间，与 tick 速率无关）；这个 tick 发送 Player Loaded |

加载条件（wiki FAQ），满足任意一条即可：玩家所在区块已加载；玩家在维度高度范围之外；玩家为旁观模式；玩家已死亡。

没有收到 Game Event 13 时一直等待，不发送 Player Loaded，也不超时。发送 Player Loaded 时完成等待中的 `loaded()`，产生 `PlayerLoaded`，动作队列开始执行。wiki Player Loaded 记载的「60 tick 后开始模拟玩家」在原版 26.2 客户端中不存在，本库按原版客户端。

## 物品栏

`InventoryModel` 保存窗口 0、打开的窗口，以及窗口 0 中没有槽位的身体护甲与鞍。物品栏内容只由服务端数据更新，本库不预测点击结果。空物品为 null；槽位是否改变按 `Items.sameStack` 判断（待办 T3）。

**窗口布局**（`Window`，wiki Inventory）：

- 窗口 0 共 46 格：合成输出 1、合成格 4、盔甲 4（槽位 5 到 8 依次为头、胸、腿、脚）、主物品栏 27（9 到 35）、快捷栏 9（36 到 44）、副手 1（45）。
- 一般的窗口：独有槽位，然后主物品栏 27 格，然后快捷栏 9 格。
- 合成器（`CRAFTER_3x3`）：输入 0 到 8，主物品栏 9 到 35，快捷栏 36 到 44，结果槽位为 45。
- 讲台：只有书一格，不包含玩家物品栏。
- 马的窗口：鞍与身体护甲 2 格，然后每列 3 格，然后主物品栏与快捷栏；鹦鹉螺的窗口：鞍与身体护甲 2 格，然后主物品栏与快捷栏。

独有槽位数（`ContainerSlots.unique`，测试中逐项与 wiki 比对）：

| 窗口类型 | 独有槽位数 |
|---|---|
| `GENERIC_9X1` 到 `GENERIC_9X6` | 9、18、27、36、45、54 |
| `GENERIC_3X3`、`CRAFTER_3x3` | 9 |
| `ANVIL`、`BLAST_FURNACE`、`FURNACE`、`GRINDSTONE`、`MERCHANT`、`SMOKER`、`CARTOGRAPHY` | 3 |
| `BEACON`、`LECTERN` | 1 |
| `BREWING_STAND`、`HOPPER` | 5 |
| `CRAFTING` | 10 |
| `ENCHANTMENT`、`STONECUTTER` | 2 |
| `LOOM`、`SMITHING` | 4 |
| `SHULKER_BOX` | 27 |

**服务端数据**：

| 数据包 | 处理 |
|---|---|
| Open Screen | 打开新的窗口（状态编号 0，光标为空），产生 `WindowOpened` |
| Open Horse Screen | 实体是马类（原版 `AbstractHorse`）时打开马的窗口；是鹦鹉螺或僵尸鹦鹉螺时打开鹦鹉螺的窗口，不看列数；实体不在模型中或者是其它类型时什么都不做，打开着的窗口保持打开。产生的 `WindowOpened` 的类型为 null |
| Close Container | 不看窗口编号，关闭打开的窗口；没有打开的窗口时什么都不做。产生 `WindowClosed` |
| Set Container Content | 写入全部槽位、状态编号与这个窗口的光标物品；物品数多于槽位数时 bot 结束 |
| Set Container Slot | 写入一个槽位与状态编号；槽位不在窗口中时 bot 结束 |
| Set Player Inventory Slot | 下标 0 到 8 为快捷栏，9 到 35 为主物品栏，36 到 39 依次为脚、腿、胸、头（窗口 0 的槽位 8 到 5），40 为副手，41、42 为身体护甲与鞍；大于 42 的下标被忽略，负数时 bot 结束 |
| Set Cursor Item | 写入当前窗口的光标 |
| Set Container Property | 写入窗口的属性 |
| Merchant Offers | 保存在打开的同编号窗口上（`WindowSnapshot.trades`） |

窗口编号与窗口 0 及打开的窗口都不同的数据包被忽略（原版客户端行为）。

- **两边同步**：原版客户端的容器与窗口 0 共用同一个玩家物品栏，wiki Set Container Slot 规定服务端对其中一边的修改不会针对另一边重发。所以一边的主物品栏与快捷栏修改同时写入另一边，两边各产生一条 `SlotChanged`。
- **打开时的玩家物品栏部分**：打开窗口（Open Screen、Open Horse Screen）时，新窗口的主物品栏与快捷栏取窗口 0 的物品（原版客户端的容器菜单直接使用玩家的 `Inventory`），不产生 `SlotChanged`；之后 Set Container Content 中这一部分与已有的物品相同时同样不产生。服务端打开窗口时不一定发送这一部分：Minestom 的 `AbstractInventory.update` 只发送容器自己的槽位。
- **状态编号与光标**：每个窗口各自记录（原版每个 `AbstractContainerMenu` 各有 `stateId` 与 `carried`）。点击使用当前窗口的状态编号；`StateReader.cursor()` 为当前窗口（打开的窗口，没有打开窗口时为窗口 0）的光标，它改变时（包括打开与关闭窗口使当前窗口改变）产生 `CursorChanged`，排在 `WindowOpened`、`WindowClosed` 之前。
- **身体护甲与鞍**：在窗口 0 中没有槽位，保存在 `InventoryModel` 中，由 `StateReader.equipment(BODY 或 SADDLE)` 读取，改变时产生 `EntityEquipmentChanged`（实体编号为本地玩家的）。其它装备槽位在窗口 0 中，`equipment` 对它们抛出 `IllegalArgumentException`。

物品栏操作见[动作 · 一次性请求](actions.md#一次性请求)。

## 聊天日志

`ChatModel` 保存 Player Chat Message、Disguised Chat Message、不带 overlay 的 System Chat Message，条数上限为 `BotOptions.chatLogSize`（默认 256），超出时删除最早的。Delete Message 按签名删除；配置阶段的 Clear Chat 清空日志。

日志中的每一条为 `ChatLine`：

| 字段 | 说明 |
|---|---|
| `kind` | `PLAYER`、`DISGUISED`、`SYSTEM` |
| `sender` | 玩家聊天的发送者 UUID；其余为 null |
| `chatType` | 玩家聊天与伪装聊天的聊天类型来自注册表时，为它在 `minecraft:chat_type` 中的名称；随数据包给出定义时与系统消息为 null |
| `inlineChatType` | 随数据包给出的聊天类型定义（MCProtocolLib 的 `ChatType`）；聊天类型来自注册表时与系统消息为 null |
| `senderName`、`targetName` | 聊天类型修饰消息时的 sender 与 target 参数；系统消息为 null，没有目标名称时 `targetName` 为 null |
| `message` | 消息内容，不经过聊天类型的修饰 |
| `plainText` | adventure 的 `PlainTextComponentSerializer` 得到的文本 |

- 聊天类型字段为「ID or Chat Type」：MCProtocolLib 解码为 `Holder<ChatType>`，编号已经减去 1，按 `requireEntry(CHAT_TYPE, 编号)` 换成名称，编号不在注册表中时 bot 结束。
- `plainText` 不翻译翻译组件：本库没有语言文件，翻译组件本身与它的参数都不出现在文本中，只保留它的子组件。加入消息、死亡消息等由翻译键组成的系统消息的 `plainText` 通常为空字符串，按翻译键匹配使用 `Observers.firstTranslatedChat`。
- 玩家聊天的显示规则见[协议状态机 · 聊天与命令](protocol.md#聊天与命令)。

## 界面状态

`HudModel` 保存界面状态，`snapshot()` 生成 `HudSnapshot`：

| 内容 | 数据包与规则 |
|---|---|
| Boss Bar | Boss Bar 数据包按动作处理，每种动作只读取它带有的字段。状态为 `BossBarSnapshot(title, health, color, division, darkenSky, playEndMusic, showFog)`，后三项为标记 0x1、0x2、0x4。更新没有添加过的 Boss Bar 时 bot 结束；移除没有添加过的 Boss Bar 什么都不做，也不产生 `BossBarChanged` |
| 标题 | Set Title Text、Set Subtitle Text（产生 `TitleShown`）、Set Title Animation Times；Clear Titles 清除标题与副标题，带重置标记时同时清除标题时间 |
| 动作栏 | Set Action Bar Text 与带 overlay 的 System Chat Message，产生 `ActionBarShown` |
| 玩家列表 | Player Info Update：先加入带 ADD_PLAYER 的条目（已在列表中的玩家保留原来的条目），再把各项更新应用到已知的玩家；不在列表中、又不带 ADD_PLAYER 的玩家的更新被忽略。新条目的初始值与原版 `PlayerInfo` 相同：生存模式，延迟 0，不显示在列表中，没有显示名称，排序 0。Player Info Remove 删除条目 |
| 页眉页脚 | Set Tab List Header And Footer |
| 计分板目标 | Update Objectives：ADD 创建（已经存在时 bot 结束），REMOVE 删除（同时清空指向它的显示位置），UPDATE 更新显示文本与类型；删除与更新不存在的目标时什么都不做 |
| 显示位置 | Display Objective：名称为空或者没有这个目标时清空这个位置 |
| 分数 | Update Score 写入存在的目标并产生 `ScoreChanged`；Reset Score 删除持有者在指定目标（没有指定时为全部目标）中的分数，每个被删除的分数一条 `ScoreChanged`（分数为 null） |
| 队伍 | Update Teams：创建已经存在的队伍时沿用它与它的成员，只更新显示设置；一个名称只属于一个队伍，加入队伍时先离开原来的队伍；移除不在这个队伍中的成员时 bot 结束；创建以外的操作指向不存在的队伍时什么都不做 |
| 服务器描述 | Server Data |

这些数据包中出现不认识的枚举编号时，MCProtocolLib 解码失败，bot 结束。

## 时间与世界边框

本节各项由 `StateReader.worldState()` 读取（`WorldStateSnapshot`），时钟由 `StateReader.clocks()` 读取，见[查询与观察 · 查询](observation.md#查询)。

### World Age 与时钟

- **World Age** 属于世界（原版 `ClientLevelData.gameTime`）：新的世界从 0 开始；Update Time 把它设为数据包的值；每个正常运行的 tick 在实体与移动步骤之后加一。
- **`WorldClocks`**（原版 `ClientClockManager`）：Update Time 给出的各时钟，每个时钟有总 tick 数（long）、小数部分（float）与速率。时钟按 `requireEntry(WORLD_CLOCK, 编号)` 换成名称保存。
  - 时钟属于连接：每次 Finish Configuration 时重新创建，Login (play) 与 Respawn（包括换维度）时保留。
  - 按 World Age 的差值推进：记下上次推进时的 World Age，每个时钟前进「差值 × 速率」，整数部分计入总 tick 数，小数部分以 float 保存。正常运行的 tick 中差值通常为 1。
  - Update Time 先把世界的 World Age 设为数据包的值，再按这个值推进全部时钟，然后用数据包中的值替换其中列出的时钟。
  - 换维度之后新世界的 World Age 从 0 开始，下一次推进时差值为负数，时钟随之后退，直到服务端发来新的 Update Time，与原版相同。

### tick 状态

`TickState`（原版 `TickRateManager`）保存 Set Ticking State 的速率与冻结标记、Step Tick 的步数：

- 速率决定 bot 的 tick 周期，见[线程与调度 · tick 周期](threading.md#tick-周期)。
- 每个 tick 开始时决定这个 tick 是否正常运行：没有冻结，或者还有要步进的 tick；还有要步进的 tick 时步数减一。所以 Step Tick 给出几步就正常运行几个 tick。
- 不正常运行的 tick 中，没有玩家乘客的其它实体不执行实体步骤，世界边框与时间不推进。本地玩家照常运行。

### 世界边框

`WorldBorderState`（原版 `WorldBorder`）：

- Initialize World Border 的插值时间大于 0 时从旧的大小插值到新的大小，否则直接设为新的大小；Set Border Lerp Size 的两个大小相同时直接设为这个大小，否则开始插值；Set Border Size 直接设为这个大小。Portal Teleport Boundary 字段在 MCProtocolLib 中名为 `getNewAbsoluteMaxSize()`。
- 插值时间以 tick 计：原版客户端与 Minestom 都以 tick 计，wiki 写的毫秒数与它们不同，本库按原版。
- 每次推进（原版 `MovingBorderExtent.update`）：剩余时间减一，记下推进之前的大小，再算出新的大小；剩余时间不大于 0 时静止在目标大小。插值时间小于等于 0 时同样开始插值，第一次推进之后静止在目标大小。
- 范围判断（`withinBounds`：挖掘与使用物品的检查）与碰撞（`toWorldBorder` 交给 `PhysicsUtils`；`collides` 用于位置插值）使用的大小，在插值期间为推进之前的大小，即上一 tick 的大小（原版 `MovingBorderExtent` 取 `previousSize`），静止时为当前大小。
- `snapshot()` 生成 `WorldStateSnapshot.Border`：`size` 为当前大小，`boundsSize` 为范围判断与碰撞使用的大小；不在插值中时 `lerpTarget` 等于 `size`，`lerpRemainingTicks` 为 0。

### 天气

`Weather` 使用 Game Event 7（雨量）与 8（雷暴强度），MCProtocolLib 解码时限制到 [0, 1]。事件 1、2 的效果与名称相反，原版服务端紧接着会发送事件 7，所以忽略。

### 难度、默认出生点与距离

以下各项属于世界：

- 难度：Change Difficulty 给出的 MCProtocolLib `Difficulty`，不保存是否锁定；新的世界中为 null，直到收到 Change Difficulty。
- 默认出生点：Set Default Spawn Position 给出的方块坐标，不保存维度与朝向；新的世界中为 null，直到收到 Set Default Spawn Position。
- 服务端视距：取自 Login (play) 与 Set Render Distance，决定加载区域（见[区块](#区块)）；维度名称改变的 Respawn 创建的新世界沿用之前的值。
- 模拟距离：取自 Login (play) 与 Set Simulation Distance，只保存，不影响 bot 的行为；维度名称改变的 Respawn 创建的新世界沿用之前的值。

[目录](README.md) · 上一篇：[注册表与静态数据](registries-and-data.md) · 下一篇：[动作](actions.md)
