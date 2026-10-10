# 注册表与静态数据

[目录](README.md) · 上一篇：[协议状态机](protocol.md) · 下一篇：[世界模型](world-model.md)

本篇目录：

- [同步注册表](#同步注册表)
  - [累积与构建](#累积与构建)
  - [原版注册表数据](#原版注册表数据)
  - [查询](#查询)
- [标签表](#标签表)
- [静态数据](#静态数据)
  - [方块](#方块)
  - [物品](#物品)
  - [物品的组件与比较](#物品的组件与比较)
  - [属性](#属性)
  - [实体类型](#实体类型)
- [协议枚举与注册表编号](#协议枚举与注册表编号)

相关源码：[`registry`](../../bot/src/main/java/io/ib67/yuanban/bot/registry)、[`data`](../../bot/src/main/java/io/ib67/yuanban/bot/data)。

## 同步注册表

服务端在配置阶段用 Registry Data 发来同步注册表，条目的顺序决定数字编号（wiki Registry Data）。数据包中按编号引用条目的字段由 MCProtocolLib 解码为编号，本库按服务端的顺序把编号换成名称。本库只使用条目名称，以及维度类型的最低 y 与高度，不解码其余的条目数据。

### 累积与构建

`SyncedRegistriesBuilder` 由解码上下文使用，运行在连接的入站线程上：

1. `startConfiguration()`：Login Success 与 Start Configuration 时调用，开始一个新的配置阶段。
2. `accept(Registry Data)`：同一个配置阶段中的第一个 Registry Data 清空之前的同步注册表并返回 true，解码上下文随之删除这些注册表的全部标签（wiki Update Tags：重新配置期间收到任何 Registry Data，之前的同步注册表与标签全部作废）。之后按注册表名称累积条目列表（名称加可以缺省的 NBT）。
3. `build()`：Finish Configuration 时调用。按服务端顺序记录每个注册表全部条目的名称。条目不带 NBT 时（服务端因为 Known Packs 省略），要求它存在于原版的同一注册表中，否则抛出 `IllegalStateException`。维度类型的 `min_y` 与 `height` 取自条目的 NBT，省略时取自原版，得到 `DimensionInfo(名称, 最低 y, 高度)`。

构建失败时连接以这个异常关闭，bot 结束。所以服务端使用自定义数据包时，没有带数据、原版中又不存在的条目让 bot 结束。

### 原版注册表数据

`VanillaNetworkCodec` 在第一次使用时加载 MCProtocolLib 自带的 `MinecraftProtocol.loadNetworkCodec()`（原版 26.2 全部同步注册表的条目与数据），按注册表名称与条目名称查找。

### 查询

`SyncedRegistries` 不可变：

| 方法 | 结果 |
|---|---|
| `entries(注册表)` | 按编号排列的条目名称；服务端没有发送这个注册表时为空列表 |
| `size(注册表)` | 条目数 |
| `entry(注册表, 编号)` | 条目名称；编号不在注册表中时为 null |
| `requireEntry(注册表, 编号)` | 条目名称；编号不在注册表中时抛出 `IllegalStateException`（原版解码时出错，客户端断开连接） |
| `biomeCount()` | `worldgen/biome` 的条目数，决定区块数据与 Chunk Biomes 中生物群系全局调色板的位数 |
| `dimensionType(编号)` | 维度类型的 `DimensionInfo`；编号不存在时抛出 `IllegalStateException` |

用到编号的地方：

| 数据包 | 注册表 | 结果 |
|---|---|---|
| Player Chat Message、Disguised Chat Message 的聊天类型 | `chat_type` | `ChatLine.chatType` |
| Damage Event 的伤害类型 | `damage_type` | `EntityDamaged.type`；编号不在注册表中时 bot 结束，与是否有 observer 订阅无关 |
| Update Time 的时钟 | `world_clock` | `WorldClocks` 中时钟的名称 |
| Login (play)、Respawn 的维度类型 | `dimension_type` | 世界的最低 y 与高度 |

## 标签表

`TagTable` 保存 Update Tags 中的全部标签：注册表名称 → 标签名称 → 编号集合，名称为 `Key`。不可变，每次更新返回新的实例：

- `apply(ClientboundUpdateTagsPacket)`：配置阶段与游戏阶段都按标签逐个替换，数据包中没有提到的标签保留。
- `without(注册表名称的集合)`：删除这些注册表的全部标签，重新配置的第一个 Registry Data 时使用。
- `contains(注册表, 标签, 编号)`、`tagNames(注册表)`。

客户端自己的判断使用这里的成员，与服务端相同：

| 判断 | 注册表与标签 |
|---|---|
| 挖掘速度的工具规则（`DigSpeed`） | `block` 注册表中规则给出的标签 |
| 冒险模式的 `can_break`（`BlockPredicates`） | `block` 注册表中谓词给出的标签 |
| 脚下方块是否为墙或栅栏门（`Kinematics.below`） | `block` 注册表的 `minecraft:walls`、`minecraft:fence_gates` |
| `equippable` 允许的实体类型（`VehicleControl`） | `entity_type` 注册表中组件给出的标签 |

方块、实体类型等静态注册表的标签同样按服务端下发的标签判断，服务端自定义的标签照常生效。直接列出的方块与实体按编号判断：方块为 Minestom `Block.id()`，实体为 MCProtocolLib `EntityType` 的序号。

## 静态数据

静态数据都在类初始化时加载，不需要服务端进程。`data` 包通过 `MinestomDataFiles` 读取 `net.minestom:data` 中的 JSON（原版 26.2 导出的数据），其余的 Minestom 代码不参与。

### 方块

使用 Minestom 的静态方块数据（同样读取 `net.minestom:data`）：`Block.fromStateId(int)` 给出状态编号、属性、`collisionShape()`、`outlineShape()`、`friction()`、`speedFactor()`、`jumpFactor()`、`hardness()`、`requiresTool()`、`blockEntityType()` 等。`BlockStates.REGISTRY_SIZE`（`Block.statesCount()`）是方块状态的总数，决定区块数据中全局调色板的位数，MCProtocolLib 解码区段时需要它。

`Block` 出现在公开接口中，`StateReader.block` 返回的 `Block` 不带 NBT。

### 物品

`ItemData`（`item.json`）：

- `id(Key)`：物品编号；没有这个物品时抛出 `IllegalArgumentException`。
- `prototype(物品编号, DataComponentType)`：物品的默认组件。只转换本库读取的五种：`tool`、`equippable`、`use_effects`、`piercing_weapon`、`attack_range`，其它类型抛出 `IllegalArgumentException`。JSON 是原版编解码器的格式，取默认值的字段被省略，转换时按原版编解码器的默认值补上：

| 组件 | 省略的字段的默认值 |
|---|---|
| `tool` | `default_mining_speed` 1.0、`damage_per_block` 1、`can_destroy_blocks_in_creative` true；规则的 `speed` 与 `correct_for_drops` 省略时为 null |
| `equippable` | `equip_sound` 为 `item.armor.equip_generic`；`dispensable`、`swappable`、`damage_on_hurt` 为 true；`equip_on_interact`、`can_be_sheared` 为 false；`shearing_sound` 为 `item.shears.snip`；`asset_id`（MCProtocolLib 中的 model）、`camera_overlay`、`allowed_entities` 省略时为 null |
| `use_effects` | `(false, true, 0.2F)`，与原版 `UseEffects.DEFAULT` 相同 |
| `piercing_weapon` | `deals_knockback` true、`dismounts` false；两个声音省略时为 null |
| `attack_range` | `min_reach` 0、`max_reach` 3、`min_creative_reach` 0、`max_creative_reach` 5、`hitbox_margin` 0.3、`mob_factor` 1 |

JSON 内容的转换：

| JSON 内容 | 转换为 |
|---|---|
| 方块集合与实体集合 | `"#标签"` 转为 `HolderSet(Key)`；名称或名称列表转为编号列表（方块用 `Block.fromKey(名称).id()`，实体用 MCProtocolLib `EntityType` 的序号） |
| 声音 | 按名称找 `BuiltinSound`，找不到时抛出 `IllegalStateException` |
| 装备槽位名称 | `head`、`chest`、`legs`、`feet`、`mainhand`、`offhand`、`body`、`saddle` 映射到 MCProtocolLib 的 `EquipmentSlot` |

### 物品的组件与比较

MCProtocolLib 的 `ItemStack` 只带有相对默认组件的补丁，空槽位为 null。`Items` 提供：

- `component(物品, 类型)`：物品实际的组件。补丁中有这个类型时取补丁中的值（值为 null 表示补丁移除了它），否则取 `ItemData` 的默认组件；空槽位没有任何组件。只支持上面五种与 `can_break`（原版物品都没有这个默认组件，只在补丁中出现）。
- `has(物品, 类型)`：组件是否存在。
- `sameItemSameComponents(a, b)`：原版 `ItemStack.isSameItemSameComponents`，同一物品并且组件相同，不比较数量；两个都为 null 时相同。
- `sameStack(a, b)`：再要求数量相同。

**按原始补丁比较（待办 T3）**：原版解码物品时去掉补丁中与默认组件相同的条目，MCProtocolLib 保留原始补丁。`Items` 按原始补丁比较，没有补丁与空补丁视为相同；服务端发来与默认值相同的补丁条目时，比较结果与原版不同。挖掘中判断主手物品是否改变、物品栏判断槽位是否改变都使用它。

### 属性

`AttributeData`（`attribute.json`）给出每个属性的默认值、最小值与最大值，按 MCProtocolLib `AttributeType.Builtin` 索引，加载时逐项检查名称与编号。MCProtocolLib 的 `AttributeType.Builtin` 也带有这些数值，但其中 `jump_strength` 的默认值为 0.42，原版为 `(double) 0.42F`，所以使用 JSON 中的数值。

属性值的计算见[世界模型 · 属性](world-model.md#属性)。

### 实体类型

`EntityTypeData`（`entity_type.json`）按 MCProtocolLib `EntityType` 的序号索引，加载时逐项检查名称与编号：

- `width`、`height`：成年默认姿态的宽与高；
- `living`：数据中 `packetType` 为 LIVING 或 PLAYER 的类型是活体（原版只有活体有属性）；
- `passengerAttachments`：乘客挂点；数据中没有时为 null，由调用方按实体高度计算。

这份数据只有成年实体默认姿态的尺寸与挂点，没有幼年、坐下姿态与玩家各姿势的尺寸（Minestom 问题 M12）。本库在 `EntityDimensions` 与 `RidingSeat` 中内置原版 26.2 的取值，见[限制与待办 · 对 Minestom 的绕过方法](limitations.md#对-minestom-的绕过方法)。

## 协议枚举与注册表编号

MCProtocolLib 中以下枚举的序号与原版注册表编号相同，本库直接按序号与编号互相转换：`EntityType`、`BlockEntityType`、`Effect`、`BuiltinSound`、`AttributeType.Builtin`、`ParticleType`、`ContainerType`。`ParticleType` 中有两个常量名与原版名称不同，所以只按编号转换。

例如：

- 区块数据与 Block Entity Data 中方块实体的类型，按 `Block.blockEntityType().id()` 与 `BlockEntityType.ordinal()` 比较；
- `EntityType` 的常量名小写即为注册表名称，`RidingSeat` 按它识别船与竹筏（以 `_boat`、`_raft` 结尾）。

[目录](README.md) · 上一篇：[协议状态机](protocol.md) · 下一篇：[世界模型](world-model.md)
