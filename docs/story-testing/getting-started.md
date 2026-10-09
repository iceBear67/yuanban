# 快速上手

[目录](README.md) · 上一篇：[核心原语教程](tutorial.md) · 下一篇：[架构参考](architecture.md)

本篇目录：

- [引入依赖与配置](#引入依赖与配置)
- [编写第一个故事](#编写第一个故事)
- [搭建世界与场景](#搭建世界与场景)
- [创建与配置玩家角色](#创建与配置玩家角色)
- [执行玩家动作](#执行玩家动作)
- [断言事实与期望](#断言事实与期望)
- [叙述与结构化分组](#叙述与结构化分组)
- [运行测试](#运行测试)
- [阅读测试报告与故障转储](#阅读测试报告与故障转储)
- [常见错误与排查](#常见错误与排查)

---

## 引入依赖与配置

在需要使用故事测试的业务子模块的 `build.gradle`（或 `build.gradle.kts`）中加入依赖：

```groovy
dependencies {
    testImplementation(project(":story"))
    // 如果该模块需要导出可供其他模块复用的测试词汇：
    // testFixturesApi(project(":story"))
}
```

根项目的 Gradle 统一配置已将 `java.toolchain` 设为 Java 25，并在测试环境中应用了 Kotlin 插件、JUnit 6 (BOM) 与 Kotest Matcher 依赖。

在模块的 `src/test/resources/junit-platform.properties` 中启用并发测试支持与模块级服务端配置：

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.timeout.default=2 m

# 配置模块的服务端准备钩子（可选，若有数据包、配方或注册表需要加载）
# yuanban.story.setup=io.ib67.yuanban.crafting.story.CraftingSetup
```

---

## 编写第一个故事

故事以 Kotlin 编写，测试方法标注 `@Story` 注解，方法体通过 `story { ... }` 展开：

```kotlin
package io.ib67.yuanban.example

import io.ib67.yuanban.story.Story
import io.ib67.yuanban.story.story
import io.ib67.yuanban.story.vanilla.breaks
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

class DiggingStories {

    @Story
    fun `玩家使用木镐挖掉脚下的石头之后获得圆石`() = story {
        // 1. 角色加入世界，手持木镐
        val alice = player("alice") {
            hand(Material.WOODEN_PICKAXE)
        }

        // 2. 锚定方块位置：alice 脚下的方块
        val stone = alice.below

        // 3. 执行挖掘动作
        alice.breaks(stone)

        // 4. 期望在同一个 tick 内满足以下事实
        expect {
            stone.block shouldBe Block.AIR
            alice.inventory shouldContain Material.COBBLESTONE
        }
    }
}
```

---

## 搭建世界与场景

每个故事执行时都会获得独立隔离的世界（Minestom `InstanceContainer`），不同故事的世界互不干扰。

### 默认世界与出生点

通过 `world` 访问默认世界，出生点为 `spawn`（默认位于 `(0, 64, 0)`）：

```kotlin
story {
    // 自定义默认世界结构（必须在首次访问 world/spawn 之前配置）
    world {
        layer(Block.BEDROCK, thickness = 1)
        layer(Block.STONE, thickness = 62)
        layer(Block.GRASS_BLOCK, thickness = 1)
        time = 6000L // 中午
    }

    // 默认世界的出生点
    val origin = spawn
}
```

### 多世界与自定义生成器

```kotlin
story {
    // 创建另一个独立世界
    val nether = world("nether") {
        dimension = DimensionType.THE_NETHER
        empty() // 虚空世界
        spawnAt(0, 64, 0)
    }
}
```

### 快速布置场景

```kotlin
story {
    // 放置单个方块
    val chestSpot = place(Block.CHEST, at = spawn.north(2))

    // 区域方块填充
    fill(from = spawn.east(1), to = spawn.east(5), block = Block.STONE)
}
```

---

## 创建与配置玩家角色

玩家由逻辑客户端库 `bot` 驱动。通过 `player("name") { ... }` 接入：

```kotlin
story {
    val alice = player("alice") {
        at = spawn.east(2)                  // 出生坐标
        facing = Direction.SOUTH            // 水平初始朝向 (SOUTH/WEST/NORTH/EAST)
        gameMode = GameMode.SURVIVAL        // 游戏模式 (SURVIVAL/CREATIVE/ADVENTURE/SPECTATOR)
        health = 20.0f
        food = 20
        op = false

        // 装备与物品
        hand(Material.DIAMOND_SWORD)        // 快捷栏第 0 格并选中
        inventory(Material.APPLE * 5)       // 背包装入物品
        equip(EquipmentSlot.CHESTPLATE, Material.DIAMOND_CHESTPLATE)
    }
}
```

> [!NOTE]
> 即使并发的多个故事同时创建名为 `"alice"` 的玩家，框架也会自动生成唯一实际用户名（如 `alice_1a`、`alice_1b`），避免同名冲突。

---

## 执行玩家动作

原版动作位于 `io.ib67.yuanban.story.vanilla` 包下，设计为 `PlayerActor` 的扩展方法。每个动作都会在满足先决条件、Bot 发包执行完毕、且达到游戏完成状态后才返回：

```kotlin
import io.ib67.yuanban.story.vanilla.*

story {
    val alice = player("alice")

    // 移动与朝向
    alice.walksTo(spawn.north(3))
    alice.walks(blocks = 2)
    alice.jumpsTo(spawn.up(1))
    alice.looksAt(spawn.east(5))

    // 物品与双手
    alice.holds(Material.BREAD)        // 拿在主手（背包中会自动换到当前快捷栏）
    alice.eats(Material.BREAD)         // 按住右键吃下食物
    alice.drops(Material.BREAD)        // 丢出一个物品
    alice.swapsHands()                 // 交换主副手

    // 方块与实体交互
    alice.places(Material.TORCH, against = spawn.north(), face = Direction.UP)
    alice.breaks(spawn.north(2))
    alice.uses(spawn.south())

    // 聊天与指令
    alice.says("Hello world!")
    alice.runs("time set day")         // 执行指令（不需要开头的 /）
}
```

---

## 断言事实与期望

在 story 中，测试断言不需要也不应该显式调用 `Thread.sleep`。断言会在服务端与客户端各 Tick 推进时进行确定性核验。

### 单项事实断言

当故事中只有单项结论时，直接在故事顶级写出：

```kotlin
story {
    val alice = player("alice")
    alice.position shouldBeAt spawn
    alice.alive shouldBe true
    alice.health shouldBe 20.0f
    alice.hand shouldBe Material.DIAMOND * 1
}
```

### 多项复合期望（`expect`）

多项事实必须在**同一个 Tick** 内同时成立：

```kotlin
story {
    val furnace = place(Block.FURNACE, at = spawn.north(2))
    val alice = player("alice") { inventory(Material.RAW_IRON * 1, Material.COAL * 1) }

    val window = alice.opens(furnace)
    window.put(Material.RAW_IRON * 1, into = FurnaceSlots.INPUT)
    window.put(Material.COAL * 1, into = FurnaceSlots.FUEL)

    expect(within = 15.seconds) {
        window.slot(FurnaceSlots.RESULT) shouldBe Material.IRON_INGOT * 1
        alice.inventory shouldContain (Material.COAL * 0)
    }
}
```

### 持续状态检验（`expectThroughout`）

验证某个条件在指定的时间窗口或连续 Tick 内**一直成立**（中途任何一次失败立即报错）：

```kotlin
story {
    val alice = player("alice")
    // 验证随后的 100 个 tick 内 alice 的生命值一直为满血
    expectThroughout(ticks = 100) {
        alice.health shouldBe 20.0f
    }
}
```

---

## 叙述与结构化分组

### 需求叙述块

使用字符串调用 `"需求文字" { ... }` 组织代码，失败时的报告会层级展示当前所处的业务上下文：

```kotlin
story {
    val alice = player("alice")

    "熔炼时间检验" {
        "粗铁在熔炉中消耗 200 tick 产出铁锭" {
            expect(within = 12.seconds) {
                // ...
            }
        }
    }
}
```

### 并发分支（`simultaneously`）

测试多个玩家同时执行操作（由平台虚拟线程驱动）：

```kotlin
story {
    val alice = player("alice")
    val bob = player("bob")

    simultaneously(
        { alice.walksTo(spawn.east(5)) },
        { bob.walksTo(spawn.west(5)) },
    )

    // 两者均到达后才继续向下执行
}
```

### 预期失败测试（`attempt`）

捕获 `StepFailure` 并验证其确实未满足条件（例如基岩不可破坏、超出交互距离等）：

```kotlin
story {
    val bedrock = place(Block.BEDROCK, at = spawn.north())
    val alice = player("alice")

    val result = attempt {
        alice.breaks(bedrock)
    }

    result.failed shouldBe true
    bedrock.block shouldBe Block.BEDROCK
}
```

---

## 运行测试

### Gradle 任务

```bash
# 运行常规共享服务端测试（通用机制、玩法、规则测试）
./gradlew test

# 运行隔离测试（修改了全局静态属性、注册表、系统属性等的测试）
./gradlew isolatedTest

# 运行指定测试类
./gradlew test --tests "io.ib67.yuanban.crafting.*"
```

### IDE 运行

在 IntelliJ IDEA 中，直接点击方法旁的绿色运行图标即可启动 `@Story` 方法。由于 `StoryExtension` 实现了 JUnit 5 的完整扩展模型，IDE 的单个测试运行与调试体验完全无缝。

---

## 阅读测试报告与故障转储

当故事出现断言失败或超时时，`story` 会抛出格式化极其清晰的 `StoryFailure`，并在控制台与 Gradle 报告中打印出**执行轨迹与故障转储**：

```text
故事：熔炉使用煤炭把粗铁烧成铁锭（FurnaceStories）
  ✔ 创建世界 world                                                          0.14 秒
  ✔ 放置 furnace：world (0, 64, -2)                                         0.06 秒
  ✔ alice 加入 world (0, 64, 0)，生存模式，物品：raw_iron ×1，coal ×1       0.41 秒
  ✔ alice 打开 world (0, 64, -2) 处的方块                                   0.12 秒
    ✔ alice 使用 world (0, 64, -2) 处的方块                                 0.10 秒
  ✔ alice 把 raw_iron ×1 放入窗口槽位 0                                     0.20 秒
  ✔ alice 把 coal ×1 放入窗口槽位 1                                         0.19 秒
  ✘ 一个物品的烧炼时间为 200 tick                                           15.01 秒
    ✘ 期望（15 秒之内）                                                     15.00 秒
      ✘ alice 的窗口槽位 2 应为 iron_ingot ×1；最后一次检查（tick 4127）：空
          alice：Expected iron_ingot ×1 but actual was null
失败时的状态：
  alice（alice_1f）
    服务端：world (0.50, 64.00, 0.50)，生命 20.0，饥饿 20，生存模式，主手为空，在线
    客户端：overworld (0.50, 64.00, 0.50)，生命 20.0，生存模式，主手为空
            打开的窗口 furnace：槽位 0 raw_iron ×1，槽位 1 coal ×1
            最近的聊天：无
故事期间服务端记录的异常：无
```

该报告提供以下关键调试信息：
1. **步骤层级与耗时**：清晰指明失败发生在哪个步骤、哪个叙述块，每步消耗的时间。
2. **末次检查现场**：打印出断言最后一次核验的精确 Tick 编号、服务端实际值与客户端实际值。
3. **玩家双端状态镜像**：输出失败瞬间玩家在服务端及客户端看到的世界、坐标、生命、游戏模式、已打开的容器槽位内容及最近聊天记录。
4. **服务端隐藏异常**：列出在测试执行期间 Minestom 调度器或事件线程捕获并记录的所有后台异常。

---

## 常见错误与排查

1. **`story { } 只能在 @Story 方法中调用`**
   - 原因：测试方法上遗漏了 `@Story` 注解，误用了 `@Test`。请确保测试方法使用 `@Story` 注解。
2. **`不能在 Minestom 的服务端线程上调用会等待的方法`**
   - 原因：在 `onServer { ... }` 或同步事件回调中直接调用了 `walksTo`、`expect`、`barrier` 等阻塞性方法。服务端线程阻塞会导致整个世界停摆。阻塞与等待逻辑必须运行在测试线程上。
3. **`交互距离超出：眼睛到实体的距离为 x.xx，上限为 3.0`**
   - 原因：玩家站位离目标方块或实体过远。原版动作遵循客户端交互距离限制，请先调用 `walksTo` 让玩家靠近目标。
4. **`isolated 类需要单独的 JVM`**
   - 原因：在同一个 JVM 中混合运行了普通故事和带有 `@IsolatedJvm` 的故事。隔离测试请通过 `./gradlew isolatedTest` 运行。

[目录](README.md) · 上一篇：[核心原语教程](tutorial.md) · 下一篇：[架构参考](architecture.md)
