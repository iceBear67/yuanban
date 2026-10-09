# 原版词汇表

[目录](README.md) · 上一篇：[角色与世界](actors-and-world.md) · 下一篇：[词汇扩展指南](extending-vocabulary.md)

本篇目录：

- [动作设计原则](#动作设计原则)
- [交互距离与视距检查（Reach）](#交互距离与视距检查reach)
- [移动与朝向（Movement）](#移动与朝向movement)
- [方块操作（Blocks）](#方块操作blocks)
- [手部与物品操作（Hands）](#手部与物品操作hands)
- [战斗与实体交互（Combat）](#战斗与实体交互combat)
- [聊天与命令（Chat）](#聊天与命令chat)
- [窗口与容器操作（Windows）](#窗口与容器操作windows)
- [生命周期与会话（Life）](#生命周期与会话life)

---

## 动作设计原则

`io.ib67.yuanban.story.vanilla` 包包含了原版 Minecraft 玩家可执行动作的标准词汇库。每一个动作均严格遵循三段式设计：

1. **先决条件检查（Preconditions）**：
   在发包前校验物理距离、物品栏库存或存活状态。若不满足，立即抛出附带明确修改指引的 `StepFailure`，而不是让动作静默超时。
2. **底层调用与发包（Bot Action）**：
   向逻辑客户端 `Bot` 提交协议动作或状态变更。
3. **完成条件等待（Completion Conditions）**：
   等待底层动作执行完成、观察器达成结论、或双端状态推进至目标事实。等待 bot 超时时，先取消 bot 的动作并等到它结束，再抛出 `StepFailure`，被 `attempt` 捕获之后同一个玩家的动作照常执行。

---

## 交互距离与视距检查（Reach）

原版动作严格校验客户端交互距离，避免由于「神仙操作」导致测试失去仿真意义：

- **眼睛位置**：$\text{eye} = \text{position} + (0, 1.62 \times \text{scale}, 0)$。
- **方块交互上限**：读取客户端 `minecraft:block_interaction_range` 属性（默认 $4.5$ 格）。
- **实体交互上限**：读取客户端 `minecraft:entity_interaction_range` 属性（默认 $3.0$ 格）。

若距离超出，框架会精准提示：
```text
world (0, 64, 10) 不在 alice 的交互距离之内：眼睛到方块的距离为 10.00，上限为 4.50；先让 alice 走近
```

---

## 移动与朝向（Movement）

定义于 `vanilla/Movement.kt`，主要包含玩家寻路与位移：

| 方法 | 说明 | 完成条件 |
|---|---|---|
| `PlayerActor.walksTo(spot: Spot)` | 寻路并走到目标方块格的中心底面（`spot.center`） | Bot 到达目标坐标 |
| `PlayerActor.walks(blocks: Int)` | 沿当前水平朝向直线向前走指定格数 | 动作执行完毕 |
| `PlayerActor.jumpsTo(spot: Spot)` | 以目标方块顶面为落地点起跳 | 起跳并在目标处落地 |
| `PlayerActor.looksAt(spot: Spot)` | 调整头部朝向，视线对准方块中心点（`spot.middle`） | 朝向数据包同步完成 |
| `PlayerActor.looksAt(entity: EntityActor)` | 视线对准目标实体的眼睛/中心 | 朝向数据包同步完成 |

---

## 方块操作（Blocks）

定义于 `vanilla/Blocks.kt`：

### 1. `breaks(spot: Spot)`
- **先决条件**：目标方块在玩家方块交互距离内。
- **动作**：Bot 发起挖掘动作并开始破坏过程。
- **完成条件**：`BlockBrokenObserver.broken() == true`（客户端收到方块破碎确认）。
- **说明**：挖掘速度自动遵循当前手持工具及方块硬度。

### 2. `places(material: Material, against: Spot, face: Direction = Direction.UP)`
- **先决条件**：背包中有该方块物品；`material.block() != null`；目标接触方块在交互距离内。
- **动作**：自动将该物品拿在主手，对准 `against` 的 `face` 表面发送右键放置包。
- **完成条件**：服务端 `against.relative(face)` 处的方块变为对应类型。

### 3. `uses(spot: Spot)`
- **先决条件**：目标方块在交互距离内。
- **动作**：计算方块朝向玩家视线的一面，发送 `useItemOn`（右键点击方块）。
- **完成条件**：发包动作执行完成。

---

## 手部与物品操作（Hands）

定义于 `vanilla/Hands.kt`：

### 1. `holds(material: Material)`
- **逻辑**：
  - 如果该物品已在快捷栏（槽位 36~44），自动切换选中的快捷栏槽位。
  - 如果该物品在背包中（槽位 9~35），且当前未打开任何 GUI 窗口，自动发包将其与当前快捷栏手持槽位交换。
- **完成条件**：客户端主手物品（`client.hand`）变为该材质。

### 2. `usesItem(holdTicks: Int = 0)`
- **说明**：右键使用主手中的物品。`holdTicks == 0` 为快速右键点击一下；大于 0 时表示按住右键持续指定 Tick。

### 3. `eats(material: Material)`
- **说明**：吃掉主手持有的食物。自动读取物品组件中的 `DataComponents.CONSUMABLE` 计算所需食用 Tick（默认 32 tick），按住使用直至吃完。

### 4. `drops(material: Material, all: Boolean = false)`
- **说明**：丢出手中的物品。`all = false` 丢出单个，`all = true` 丢出整组（Ctrl+Q）。

### 5. `swapsHands()`
- **说明**：按下 F 键，交换主手与副手槽位中的物品。

---

## 战斗与实体交互（Combat）

定义于 `vanilla/Combat.kt`：

### 1. `attacks(entity: EntityActor)`
- **先决条件**：目标实体在实体交互距离内。
- **动作**：看向实体，随后发送左键攻击包。

### 2. `interactsWith(entity: EntityActor)`
- **先决条件**：目标实体在实体交互距离内。
- **动作**：对实体点击右键（如骑乘、喂食、交易）。

---

## 聊天与命令（Chat）

定义于 `vanilla/Chat.kt`：

```kotlin
// 发送公开聊天
alice.says("大家好！")

// 执行指令（不需要开头的斜杠）
alice.runs("gamemode creative")

// 事实断言：获取 alice 聊天框内接收到的所有文本
alice.messages shouldContain "欢迎加入服务器"

// 事实断言：仅筛选由 bob 发送的玩家聊天
alice.messagesFrom(bob) shouldContain "你好 alice"
```

---

## 窗口与容器操作（Windows）

定义于 `vanilla/Windows.kt`。

```kotlin
// 打开方块容器并获取句柄
val window = alice.opens(chestSpot)

// 基础点击
window.click(slot)        // 左键
window.rightClick(slot)   // 右键
window.shiftClick(slot)   // Shift 点击

// 智能转移
window.put(Material.IRON_INGOT * 3, into = 0) // 将背包中的铁锭放入容器 0 号格
window.take(index = 0)                        // 将 0 号格的物品拿回玩家背包

// 关闭窗口
window.close()
```

---

## 生命周期与会话（Life）

定义于 `vanilla/Life.kt`：

### 1. `respawns()`
- **先决条件**：玩家处于死亡状态。
- **动作**：发送客户端重生包（Respawn）。
- **完成条件**：双端玩家状态恢复存活。

### 2. `leaves()`
- **动作**：客户端主动断开连接。
- **完成条件**：服务端 `player.isOnline == false`。

### 3. `rejoins(at: Spot = story.spawn)`
- **先决条件**：玩家此前已调用 `leaves()` 离线。
- **动作**：以相同的用户名建立新的 Bot 重新连接进入服务端。
- **完成条件**：新客户端完成登录并进入目标世界的 `at` 坐标。

[目录](README.md) · 上一篇：[角色与世界](actors-and-world.md) · 下一篇：[词汇扩展指南](extending-vocabulary.md)
