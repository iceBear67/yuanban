# 角色与世界

[目录](README.md) · 上一篇：[事实与期望](facts-and-expectations.md) · 下一篇：[原版词汇表](vanilla-vocabulary.md)

本篇目录：

- [世界（WorldActor）与世界规范（WorldSpec）](#世界worldactor与世界规范worldspec)
- [方块坐标格（Spot）](#方块坐标格spot)
- [玩家角色（PlayerActor）与加入配置（PlayerSetup）](#玩家角色playeractor与加入配置playersetup)
- [玩家客户端视角（ClientView）](#玩家客户端视角clientview)
- [实体角色（EntityActor）与召唤配置（EntitySetup）](#实体角色entityactor与召唤配置entitysetup)
- [容器与窗口（WindowActor）](#容器与窗口windowactor)

---

## 世界（WorldActor）与世界规范（WorldSpec）

每个故事在执行时都是一个完全隔离的维度空间。世界由 Minestom 的 `InstanceContainer` 支持。

### 世界规范配置（`WorldSpec`）

通过 `world { ... }` 可以定制世界的生成属性：

```kotlin
story {
    // 自定义默认世界（必须在访问默认世界的任何属性之前调用）
    world {
        dimension = DimensionType.OVERWORLD   // 维度类型
        time = 1000L                           // 初始时间（tick）
        weather = Weather.CLEAR               // 天气
        preload = 3                           // 预加载区块半径（以出生点为中心，半径为 3 即 7x7 区块）

        // 地形层（从 y = 0 向上依次叠放）
        layer(Block.BEDROCK, thickness = 1)
        layer(Block.STONE, thickness = 59)
        layer(Block.DIRT, thickness = 3)
        layer(Block.GRASS_BLOCK, thickness = 1)

        // 指定出生点方块格
        spawnAt(0, 64, 0)

        // 可以在创建后直接执行 Minestom 原生配置
        configure {
            // this: InstanceContainer
        }
    }
}
```

- **默认地形兜底**：如果没有调用 `layer`、`generator` 或 `empty`，默认按如下结构生成：$y=0$ 基岩、$y=1\dots59$ 石头、$y=60\dots62$ 泥土、$y=63$ 草方块，出生点自动定为 $(0, 64, 0)$。
- **虚空世界**：调用 `empty()` 可创建无方块的纯虚空世界，默认出生点为 $(0, 64, 0)$。
- **多世界支持**：
  ```kotlin
  val arena = world("arena") {
      empty()
      spawnAt(10, 100, 10)
  }
  ```

---

## 方块坐标格（Spot）

`Spot` 代表指定世界中的一个**整数方块格**（`x, y, z` 均为 `Int`）：

```kotlin
data class Spot(val world: WorldActor, val x: Int, val y: Int, val z: Int)
```

### 相对方向导航

为了摆脱魔法数字、让测试代码具有极高的可读性，`Spot` 支持链式相对移动：

```kotlin
val origin = spawn

val northSpot = origin.north(2)     // z - 2
val southSpot = origin.south(1)     // z + 1
val eastSpot  = origin.east(3)      // x + 3
val westSpot  = origin.west(1)      // x - 1
val above     = origin.up(1)        // y + 1
val below     = origin.down(1)      // y - 1

// 沿任意原版面位移
val relative = origin.relative(Direction.UP)
```

### 坐标锚点与几何属性

- `center: Pos`：方块底面的水平中心点 `(x + 0.5, y.toDouble(), z + 0.5)`，即实体站立在这一格时的标准站立坐标。
- `middle: Vec`：方块 3D 盒子的正中心 `(x + 0.5, y + 0.5, z + 0.5)`，用于射线瞄准与视线朝向。
- `point: Vec`：方块的原点 `(x, y, z)`。
- `block: SyncedFact<Block>`：该方块的双端同步事实。

---

## 玩家角色（PlayerActor）与加入配置（PlayerSetup）

玩家角色由 `PlayerActor` 抽象表示，底层连接真实的 `bot` 逻辑客户端与服务端的 `Player` 实例。

### 玩家配置项（`PlayerSetup`）

```kotlin
val alice = player("alice") {
    at = spawn.east(2)                        // 出生位置（默认为默认世界出生点）
    facing = Direction.NORTH                  // 初始朝向（仅允许水平方向 SOUTH/WEST/NORTH/EAST）
    gameMode = GameMode.SURVIVAL              // 游戏模式
    health = 20.0f                            // 初始生命值
    food = 20                                 // 初始饥饿度
    op = true                                 // 赋予 4 级管理员权限

    // 手部与背包
    hand(Material.DIAMOND_SWORD)              // 放入快捷栏第 0 格并选中
    hand(Material.TORCH * 16)                 // 支持带数量的物品
    inventory(Material.BREAD * 5, Material.GOLD_INGOT * 2) // 放入背包
    equip(EquipmentSlot.HELMET, Material.IRON_HELMET)

    // 状态效果
    effect(PotionEffect.SPEED, amplifier = 1, duration = 30.seconds)

    // 底层 Bot 协议选项定制（如语言、视距）
    client {
        // this: BotOptions.BotOptionsBuilder
    }
}
```

### 玩家位置快捷导航

- `alice.feet: Spot`：玩家双脚所在的方块格。
- `alice.below: Spot`：玩家脚下踩着的方块格（`feet.down()`）。
- `alice.front(blocks: Int = 1): Spot`：沿玩家当前面朝的水平方向向前数第 $N$ 格。

### 玩家事实属性

- **双端同步事实（`SyncedFact`）**：
  - `alice.position`（坐标）
  - `alice.health`（生命值）
  - `alice.food`（饥饿度）
  - `alice.gameMode`（游戏模式）
  - `alice.inventory`（物品栏）
  - `alice.hand`（主手物品）
  - `alice.alive`（存活状态）
- **服务端事实**：
  - `alice.online`（服务端在线状态）

---

## 玩家客户端视角（ClientView）

通过 `alice.client`，可以单独核验仅由该玩家本地客户端感知的视图：

```kotlin
// 该玩家客户端渲染看到的方块
alice.client.block(spot) shouldBe Block.STONE

// 该玩家客户端打开的窗口快照
alice.client.window shouldNotBe null

// 该玩家客户端 HUD 文本
alice.client.actionBar shouldBe "Action Text"
alice.client.title shouldBe "Welcome"

// 客户端中是否存在该实体快照
alice.client.entity(zombie) shouldNotBe null

// 自定义查询 BotState 中的任意底层模型数据
alice.client.fact("玩家视距") { it.worldState().viewDistance() } shouldBe 8
```

---

## 实体角色（EntityActor）与召唤配置（EntitySetup）

通过 `summon` 在指定世界生成非玩家实体：

```kotlin
val zombie = summon(EntityType.ZOMBIE, at = spawn.east(3)) {
    health = 10.0f
    customName = Component.text("精英僵尸")
    configure {
        // this: Entity (Minestom 服务端原生实体对象)
    }
}
```

### 实体事实

- `zombie.position: ServerFact<Pos>`：实体服务端坐标。
- `zombie.health: ServerFact<Float>`：实体生命值（要求必须是 `LivingEntity`）。
- `zombie.alive: ServerFact<Boolean>`：实体存活状态。
- `zombie.onFire: ServerFact<Boolean>`：实体着火状态。

---

## 容器与窗口（WindowActor）

玩家与方块交互（右键打开）后，会返回 `WindowActor` 句柄，槽位编号完全遵循原版协议规范（与 Minecraft Wiki 一致）。

```kotlin
// 打开方块（如箱子、工作台、熔炉）
val window: WindowActor = alice.opens(chestSpot)

// 槽位事实核验
window.slot(0) shouldBe Material.IRON_INGOT * 1
window.slots shouldContain Material.DIAMOND

// 槽位交互动作
window.click(0)                // 左键点击
window.rightClick(0)           // 右键点击
window.shiftClick(0)           // 按住 Shift 点击（快捷移入/移出）

// 智能存取动作
window.put(Material.IRON_INGOT * 3, into = 0) // 从背包寻找对应物品并放入容器槽位 0
window.take(index = 0)                        // Shift 点击槽位 0 将物品取回玩家背包

// 关闭窗口
window.close()
```

### 常用容器槽位常量

框架在 `io.ib67.yuanban.story.vanilla` 中内置了标准容器的槽位工具：

```kotlin
// 熔炉槽位
FurnaceSlots.INPUT  // 0: 原料输入格
FurnaceSlots.FUEL   // 1: 燃料格
FurnaceSlots.RESULT // 2: 产物输出格

// 工作台槽位
CraftingTableSlots.RESULT         // 0: 合成产物
CraftingTableSlots.grid(row, col) // 1~9: 3x3 合成格 (row: 0..2, col: 0..2)

// 箱子槽位
ChestSlots.at(row, col)           // 9xN 网格槽位
```

[目录](README.md) · 上一篇：[事实与期望](facts-and-expectations.md) · 下一篇：[原版词汇表](vanilla-vocabulary.md)
