# 协议状态机

[目录](README.md) · 上一篇：[连接与解码](connection.md) · 下一篇：[注册表与静态数据](registries-and-data.md)

本篇目录：

- [概述](#概述)
- [连接阶段](#连接阶段)
- [登录与配置](#登录与配置)
- [Client Information](#client-information)
- [任意阶段的自动应答](#任意阶段的自动应答)
- [传送的计算](#传送的计算)
- [区块批次](#区块批次)
- [Transfer](#transfer)
- [离开世界](#离开世界)
- [Login (play) 与 Respawn](#login-play-与-respawn)
- [其余数据包的去向](#其余数据包的去向)
- [聊天与命令](#聊天与命令)
- [Bundle](#bundle)

相关源码：[`protocol/ProtocolMachine.java`](../../bot/src/main/java/io/ib67/yuanban/bot/protocol/ProtocolMachine.java)、[`scheduler/BotInstance.java`](../../bot/src/main/java/io/ib67/yuanban/bot/scheduler/BotInstance.java)、[`chat`](../../bot/src/main/java/io/ib67/yuanban/bot/chat)。

## 概述

`ProtocolMachine` 在调度线程上运行，一个 bot 的全部连接共用一个。`handle(InboundPacket)` 先应用数据包（`apply`，一个按数据包类分派的 `switch`），再产生这个数据包的 `PacketReceived`。应用过程中：

- 修改 `BotState` 中的模型；
- 把应答写入 `Outbox`；
- 按变化发生的顺序产生模型层的观察记录（产生之前先用 `dispatcher.wants` 确认有订阅者）；
- 通知两个控制器（方块改变、服务端传送、服务端设置朝向、取消队列、新玩家）。

没有对应模型状态的数据包只产生 `PacketReceived`。

## 连接阶段

`BotStatus` 的取值与切换：

| 状态 | 进入时机 |
|---|---|
| `LOGIN` | 新连接接入时（`startLogin`），包括 Transfer 之后的新连接 |
| `CONFIGURATION` | 收到 Login Success；收到 Start Configuration |
| `PLAY` | 收到 Finish Configuration |
| `DISCONNECTED` | 连接结束或 bot 因异常结束，见[错误处理](errors.md) |

`startLogin(地址, 端口, transfer)` 写入：

1. `ClientIntentionPacket`：协议号取 `MinecraftCodec.CODEC.getProtocolVersion()`（776），意图为 `LOGIN`，Transfer 之后为 `TRANSFER`；
2. `ServerboundHelloPacket`：名称与离线 UUID（由 `OfflinePlayer:<名称>` 的 UTF-8 字节生成的 UUID v3）。

## 登录与配置

| 阶段 | 收到 | 处理 |
|---|---|---|
| Login | Encryption Request | 解码上下文抛出 `UnsupportedOperationException`，bot 结束 |
| Login | Set Compression | TCP 连接在事件循环上已经启用压缩，状态机不处理 |
| Login | Login Plugin Request | 回应 `ServerboundCustomQueryAnswerPacket(编号, BotOptions.loginPluginHandler.respond(频道, 数据))`。默认的处理器返回 null，表示未理解（原版客户端总是回应未理解）。必须使用两个参数的构造方法：一个参数的构造方法写出空数组，表示理解 |
| Login | Login Success | 发送 Login Acknowledged；进入配置阶段；发送 `minecraft:brand`（`BotOptions.brand` 按协议的 String 格式编码）与 Client Information |
| Login | Disconnect (login) | 记录原因，bot 断开 |
| Configuration | Known Packs | 回应服务端列出的包中命名空间为 `minecraft`、名称为 `core`、版本等于 `MinecraftCodec.CODEC.getMinecraftVersion()` 的那一项，其余省略 |
| Configuration | Registry Data | 由解码上下文累积 |
| Configuration / Play | Update Tags | 换成解码上下文更新后的 `TagTable` |
| Configuration | Feature Flags | 保存为 `List<Key>` |
| Configuration | Clear Chat | 清空聊天日志 |
| Configuration | Code of Conduct | 发送 Accept Code of Conduct；`BotOptions.acceptCodeOfConduct` 为 false 时不回应，服务端在收到接受之前不继续 |
| Configuration | Finish Configuration | 换成解码上下文构建的 `SyncedRegistries` 与此刻的 `TagTable`；重新创建时钟（原版进入游戏阶段时新建 `ClientPacketListener` 与它的 `ClientClockManager`）；发送 Finish Configuration；进入游戏阶段 |
| Configuration / Play | Custom Payload | 频道为 `minecraft:brand` 时按 String 格式读出服务端的 brand 保存；其余频道不处理 |
| Configuration / Play | Server Links | 保存为 `List<ServerLink>` |
| Play | Start Configuration | 未确认的消息数大于 0 时先发送 Acknowledge Message；然后[离开世界](#离开世界)；再发送 Configuration Acknowledged，回到配置阶段（原版 `handleConfigurationStart`） |
| Play | Login (play) | 进入新的世界，见 [Login (play) 与 Respawn](#login-play-与-respawn) |

## Client Information

`BotOptions.clientInformation()` 生成 `ServerboundClientInformationPacket`：

| 字段 | 取值 | 默认值 |
|---|---|---|
| 语言 | `BotOptions.locale` 转换为原版的语言代码：语言与国家用下划线连接，全部小写；没有国家时只有语言 | `Locale.US`，发送 `en_us` |
| 视距 | `viewDistance` | 2 |
| 聊天模式 | `chatMode`（`ChatVisibility`） | `FULL` |
| 聊天颜色 | `chatColors` | true |
| 显示的皮肤部分 | `skinParts`（`Set<SkinPart>`） | 全部 7 项 |
| 主手 | `mainHand`（`HandPreference`） | `RIGHT_HAND` |
| 文本过滤 | 固定为 false | |
| 出现在服务器列表中 | `allowServerListings` | true |
| 粒子等级 | `particleSetting`（`ParticleStatus`） | `ALL` |

## 任意阶段的自动应答

收到时在本轮立即写入应答，不受暂停移动影响：

| 收到 | 应答 |
|---|---|
| Keep Alive | 相同 ID 的 Keep Alive |
| Ping | 相同 ID 的 Pong |
| Cookie Request | 携带 `CookieStore` 中对应数据的 Cookie Response，没有时数据缺省 |
| Store Cookie | 写入 `CookieStore`：以 `Key` 为键，每个 bot 一个，跨 Transfer 保留；数据超过 5 KiB 时抛出 `IllegalArgumentException`（MCProtocolLib 解码时不检查长度），bot 结束 |
| Add Resource Pack | `ResourcePackPolicy.ACCEPT` 时依次发送 `ACCEPTED`、`DOWNLOADED`、`SUCCESSFULLY_LOADED`；`DECLINE` 时发送 `DECLINED`。本库不下载资源包 |
| Chunk Batch Start / Finished | Chunk Batch Received，见[区块批次](#区块批次) |
| Synchronize Player Position | 骑乘时位置、朝向、速度都不改变；不骑乘时按[传送的计算](#传送的计算)更新。然后发送 Confirm Teleportation，再发送当前位置与朝向的 Set Player Position and Rotation（着地与顶住墙壁为 false）。通知移动控制器：清除 `rotate`、`lookAt` 留下的朝向（不骑乘时），清空上一次的模拟结果，触发 `moveTo` 重新寻路；产生 `PlayerTeleported` |
| Transfer | 见 [Transfer](#transfer) |
| Disconnect / Disconnect (login) | 记录原因，bot 断开 |

传送的两个应答不计入移动包的上一次发送记录，见[移动模拟与骑乘 · 移动包](movement.md#移动包)。

## 传送的计算

Synchronize Player Position、Synchronize Vehicle Position 都按原版 `PositionMoveRotation.calculateAbsolute` 计算（`ProtocolMachine.calculateAbsolute`）：

- Teleport Flags 为 MCProtocolLib 解码出的 `List<PositionElement>`，计算时转为集合。
- 带 `X`、`Y`、`Z` 的坐标为当前坐标加数据包的值，其余取数据包的值。
- 带 `Y_ROT` 的偏航角为当前偏航角加数据包的值，带 `X_ROT` 的俯仰角同理；俯仰角再限制到 [-90, 90]，都按 float 计算。
- 带 `ROTATE_DELTA` 时，先把当前速度绕 x 轴旋转「当前俯仰角 − 新俯仰角」，再绕 y 轴旋转「当前偏航角 − 新偏航角」。旋转使用 `SineTable` 实现的原版 `Vec3.xRot`、`Vec3.yRot`（65536 项 float 正弦表）。Minestom 的 `Vec.rotateAroundX(a)` 等于 `Vec3.xRot(-a)`，方向相反，所以不使用。
- 带 `DELTA_X`、`DELTA_Y`、`DELTA_Z` 的速度分量为旋转后的速度加数据包的值，其余取数据包的值。

Player Rotation 同样按相对标记计算：`isRelativeY()` 对应偏航角，`isRelativeX()` 对应俯仰角，俯仰角限制到 [-90, 90]，之后清除 `rotate`、`lookAt` 留下的朝向。

Synchronize Vehicle Position 指向其它实体、指向本地玩家自己、指向被移除的载具时的处理不同，见[世界模型 · 实体数据包](world-model.md#实体数据包)。

## 区块批次

`ChunkBatchTracker` 在调度线程处理 Chunk Batch Start 时记录时间，在 Chunk Batch Finished 时用耗时除以批次中的区块数得到每区块毫秒数，保留最近 15 批，回应 `25 / 平均每区块毫秒数`（wiki Chunk Batch Finished）。批次为空时不记录样本；耗时为 0 时结果为正无穷，原版服务端与 Minestom 都把它限制到各自的上限。

## Transfer

收到 Transfer 时（`BotInstance.transfer`），按原版客户端 `handleTransfer` 的顺序：

1. 连接代数加一。
2. 把 `Outbox` 中 Transfer 之前产生的数据包交给旧连接（产生 `PacketSent`，安排写出即执行完毕的动作），然后关闭旧连接：写到套接字不能再立即接受数据为止，其余丢弃。
3. 状态回到 `LOGIN`，[离开世界](#离开世界)，不发送清理数据包。
4. 用 `TransportResolver` 得到新的 `Transport`，在 Transfer 线程上打开新连接。打开失败时在下一轮结束 bot。
5. 新连接打开之后在调度线程上接入，以握手意图 `TRANSFER` 重新登录。`CookieStore` 保留。新连接上的第一个数据包是 Handshake。

同一个 bundle 中排在 Transfer 之后的数据包属于旧连接，与旧连接之后的数据包一样被忽略。交给旧连接的批次在写出之前连接断开时，其中写出即执行完毕的动作以 `BotDisconnectedException` 结束，bot 继续在新连接上登录。

## 离开世界

Start Configuration 与 Transfer 使 bot 离开当前世界（`ProtocolMachine.leaveWorld`）。原版客户端这时清除世界、本地玩家与游戏模式控制器，不发送任何数据包：

- 移动队列中的动作以 `WORLD_REBUILT` 取消；手部交互队列中的动作同样取消，执行中的动作不做清理（例如不发送 Cancelled digging），等待中的方块交互确认被丢弃，方块交互序号归零；
- 世界被清除（`StateReader.world()` 为 null），加载状态重置；
- 记下的被移除载具编号清除。

到下一次 Login (play) 之前 bot 不在任何世界中：不 tick；`player()`、`block()`、`blockEntity()` 抛出 `IllegalStateException`；这期间提交的移动与手部动作、一次性请求等待进入下一个世界并加载之后执行。重新配置的 Finish Configuration 之后、Login (play) 之前同样如此。

已经不在任何世界中时（进入第一个世界之前、重新配置期间收到 Transfer），什么都不做，队列中这期间提交的动作不被取消。

## Login (play) 与 Respawn

**Login (play)**（`join`，原版 `handleLogin`）：

- 世界的进入原因：一个连接上的第一个 Login (play) 为 `JOIN`（Transfer 之后的连接为 `TRANSFER`），之后的为 `RECONFIGURATION`。
- 每次都新建世界（维度名称、维度类型编号、按维度类型得到的最低 y 与高度、视距、模拟距离）。
- 不在任何世界中时（新的连接，或者 Start Configuration 之后）：创建新的本地玩家，物品栏随之为空；移动控制器的上一次发送记录、Player Input 与疾跑状态重新开始。
- 已经在世界中时（服务端不经过重新配置再次发送 Login (play)）：沿用当前玩家；移动队列以 `WORLD_REBUILT` 取消，手部队列以 `WORLD_REBUILT` 丢弃（不发送清理数据包）；清除乘坐状态；只做原版 `resetPos`。
- 两种情况都：新建游戏模式控制器（挖掘延迟与已经发送的快捷栏槽位归零）；方块交互序号归零；已读跟踪、签名缓存与聊天全局编号重新开始；设置实体编号；清除死亡状态；进入世界。
- 之后完成等待中的 `joined()`。

**原版 `resetPos`**：姿态为站立；在维度高度范围内从当前高度逐格向上，直到玩家包围盒不与方块的碰撞箱相交（未加载区块的位置没有方块）；然后速度与俯仰角归零，生命值设为最大生命值。

**Respawn**（`respawn`，原版 `handleRespawn`），进入原因为 `RESPAWN`（死亡后重生与换维度都是这个值）：

1. 打开着窗口时，先发送带该窗口编号的 Close Container，并在本地关闭窗口（产生 `WindowClosed`）。
2. 移动队列以 `WORLD_REBUILT` 取消；手部队列以 `WORLD_REBUILT` 取消，执行中的动作照常清理（例如挖掘中发送 Cancelled digging）。
3. 维度名称改变时新建世界（视距与模拟距离沿用），方块交互序号归零；维度名称不变时保留区块、方块实体与其它实体。
4. 创建新的本地玩家，沿用实体编号与游戏模式：
   - Data kept 0x02（`isKeepMetadata`）：保留元数据、生命值、速度、偏航角与俯仰角，以及上次发送的 Player Input 与疾跑状态；没有 0x02 时做 `resetPos`，偏航角 -180，上次发送的 Player Input 为空、疾跑为 false。
   - Data kept 0x01（`isKeepAttributeModifiers`）：保留属性的基础值与全部修饰符；没有 0x01 时只保留基础值。
   - 其余为新玩家的初始值：物品栏为空，快捷栏槽位 0，没有物品冷却与药水效果，饥饿值 20，饱和度 5，经验为 0。
5. 游戏模式控制器不重建：挖掘延迟与已经发送的快捷栏槽位不变。所以服务端没有发送 Set Held Item 时，下一 tick 因为快捷栏槽位 0 与已经发送的槽位不同而发送 Set Held Item(0)。

**进入世界**（`enterWorld`）：`WorldInfo` 的 `epoch` 加一，设置当前世界；`PlayerLoadTracker` 开始等待（30 秒超时从这里计算）；下一次 tick 在一个间隔之后；按 Spawn Info 的游戏模式设置游戏模式与能力；产生 `WorldEntered`。

## 其余数据包的去向

| 数据包 | 处理 | 详见 |
|---|---|---|
| Chunk Data、Unload Chunk、Set Center Chunk、Set Render Distance、Set Simulation Distance、Block Update、Update Section Blocks、Chunk Biomes、Block Entity Data | 世界的区块与方块 | [世界模型 · 区块](world-model.md#区块) |
| Update Time、Set Ticking State、Step Tick、各个 World Border、Change Difficulty、Set Default Spawn Position、Game Event | 时间、tick 状态、世界边框、天气、游戏模式、加载判断 | [世界模型 · 时间与世界边框](world-model.md#时间与世界边框) |
| Spawn Entity、各个实体移动、Rotate Head、Teleport Entity、Synchronize Vehicle Position、Set Entity Velocity、Set Entity Metadata、Set Equipment、Update Attributes、Entity Effect、Remove Entity Effect、Set Passengers、Link Entities、Remove Entities、Pickup Item、Entity Event、Damage Event | 实体与乘坐关系 | [世界模型 · 实体数据包](world-model.md#实体数据包) |
| Player Rotation、Set Health、Combat Death、Player Abilities、Set Experience、Set Cooldown、Player Info Update、Player Info Remove、Explosion、Acknowledge Block Change | 本地玩家；方块交互确认交给手部控制器 | [世界模型 · 本地玩家](world-model.md#本地玩家) |
| Open Screen、Open Horse Screen、Close Container、Set Container Content、Set Container Slot、Set Player Inventory Slot、Set Cursor Item、Set Container Property、Merchant Offers、Set Held Item | 物品栏 | [世界模型 · 物品栏](world-model.md#物品栏) |
| Player Chat Message、Disguised Chat Message、System Chat Message、Delete Message | 聊天日志 | [聊天与命令](#聊天与命令)、[世界模型 · 聊天日志](world-model.md#聊天日志) |
| Set Action Bar Text、Set Title Text、Set Subtitle Text、Set Title Animation Times、Clear Titles、Boss Bar、Set Tab List Header And Footer、Update Objectives、Display Objective、Update Score、Reset Score、Update Teams、Server Data | 界面状态 | [世界模型 · 界面状态](world-model.md#界面状态) |

## 聊天与命令

**发送**：

- `chat(message)` 发送 `ServerboundChatPacket`：不签名（签名缺省），时间戳为当前毫秒数，salt 取自 `SecureRandom`；Message Count、Acknowledged（20 位）、Checksum 取自 `LastSeenTracker.update()`。不发送 Player Session，不处理服务端的安全聊天要求。消息超过 256 个字符时在提交时抛出 `IllegalArgumentException`（wiki：服务端会踢出客户端）。
- `command(command)` 发送 `ServerboundChatCommandPacket`（不签名，不带开头的斜线），超过 32767 个字符时在提交时抛出 `IllegalArgumentException`。

**已读跟踪**（`LastSeenTracker`，原版 `LastSeenMessagesTracker`，窗口 20 条）：

- 每条带签名的 Player Chat Message 处理完后加入跟踪：显示的消息计入已读；没有显示的消息（完全过滤、发送者不在玩家列表中）只计入未确认的消息数，在窗口中占一个空位。与上一条签名相同的不重复计入。
- `update()` 取出并清零未确认的消息数，给出窗口中已读条目的标记，以及 Checksum：对已读签名依次计算 `31 × h + Arrays.hashCode(签名)`（初值 1），转为 byte，结果为 0 时改为 1。
- 未确认的消息超过 64 条时发送 Acknowledge Message；收到 Start Configuration 时未确认的消息数大于 0 也先发送一次。
- Delete Message 删除的消息还没有随 Chat Message 告知服务端时，把它从跟踪中去掉（原版 `ignorePending`）；已经告知过的不变。

**签名缓存**（`SignatureCache`，原版 `MessageSignatureCache`，容量 128）：每条玩家聊天把它的已读签名与自己的签名移到最前面。按编号引用的签名从缓存中取出，编号为 MCProtocolLib 的 `MessageSignature.getId()`（已经减去 1）。编号超出范围、或者缓存中这个编号没有签名时 bot 结束（原版客户端以无效数据包断开连接）。签名保存为 `Signature`，`equals` 与 `hashCode` 按数组内容。

**全局编号**：Player Chat Message 的 Global Index 从 0 开始逐条加一，与预期不同时 bot 结束（原版以 `multiplayer.disconnect.bad_chat_index` 断开连接）。

**Login (play)** 时已读跟踪、签名缓存与全局编号重新开始，聊天日志保留。

**Player Chat Message 的处理顺序**（`playerChat`）：过滤类型为 PARTIALLY_FILTERED 时抛出 `UnsupportedOperationException`（待办 T2）；把聊天类型换成名称；检查全局编号；解析已读列表中的签名并放进签名缓存；按下表决定显示与记录；带签名时交给已读跟踪。

| 情况 | 聊天日志与 `ChatReceived` |
|---|---|
| 发送者不在玩家列表中 | 记录原版代替它显示的 `chat.validation_error`（红色斜体的翻译组件，没有签名） |
| 完全过滤（FULLY_FILTERED） | 不记录，不产生 `ChatReceived` |
| 其余 | 记录未签名内容，没有时为消息正文 |

聊天类型、`ChatLine` 的字段见[世界模型 · 聊天日志](world-model.md#聊天日志)。

## Bundle

wiki Bundle Delimiter 规定两个分隔符之间的数据包在同一 tick 内处理，原版客户端不接受超过 4096 个数据包的 bundle。

- `BundleBuffer` 在两个 `ClientboundDelimiterPacket` 之间缓存数据包，收到结束分隔符时整批交出；bundle 之外的数据包单独成批。
- 超过 4096 个时抛出 `IllegalStateException`，bot 结束。
- 入站数据包的处理在 tick 之前完成，一个 bundle 的处理过程中不会插入 tick。
- 一批数据包全部应用之后才开始投递观察记录，所以 observer 回调中读到的模型包含整个 bundle 的结果。
- 每条连接一个 `BundleBuffer`，新连接接入时新建。

[目录](README.md) · 上一篇：[连接与解码](connection.md) · 下一篇：[注册表与静态数据](registries-and-data.md)
