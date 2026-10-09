# 词汇扩展指南

[目录](README.md) · 上一篇：[原版词汇表](vanilla-vocabulary.md) · 下一篇：[线程模型与运行时](threading-and-runtime.md)

本篇目录：

- [为什么需要扩展词汇](#为什么需要扩展词汇)
- [工程组织与 testFixtures 约定](#工程组织与-testfixtures-约定)
- [编写角色扩展动作](#编写角色扩展动作)
- [上下文参数（Context Parameters）与顶层动作](#上下文参数context-parameters与顶层动作)
- [定义领域 Actor](#定义领域-actor)
- [编写自定义事实与断言](#编写自定义事实与断言)
- [完整实战案例：配方合成与小游戏](#完整实战案例配方合成与小游戏)

---

## 为什么需要扩展词汇

如果直接在故事测试代码中操作底层 Minestom API（如 `inventory.setItemStack` 或处理复杂的事件监听），会带来两个致命问题：
1. **测试逻辑晦涩难懂**：测试代码充斥着底层数据结构的样板代码，失去「需求即规格」的叙事价值。
2. **重构脆弱性**：一旦底层机制实现改变，测试代码需大面积修改。

通过将领域动作抽象为**可复用的词汇（Vocabulary）**，故事代码只声明高层业务意图，底层复杂逻辑完全内聚在词汇实现中。

---

## 工程组织与 testFixtures 约定

当模块实现某项机制并希望提供配套的故事测试词汇时，推荐在 `build.gradle` 中应用 `java-test-fixtures`：

```groovy
plugins {
    id("java-test-fixtures")
}

dependencies {
    // 词汇库自身需要依赖 story 模块
    testFixturesApi(project(":story"))
}
```

词汇代码应存放在模块的 `src/testFixtures/kotlin/io/ib67/yuanban/<module>/story/` 目录下。其他模块只需要声明依赖即可复用：

```groovy
dependencies {
    testImplementation(testFixtures(project(":crafting")))
}
```

---

## 编写角色扩展动作

所有以玩家为主体的动作，推荐作为 `PlayerActor` 的扩展函数实现。

### 核心规范

1. **包裹在 `story.step` 中**：确保测试报告中会记录该动作的名称与耗时。
2. **显式检查先决条件**：不满足条件时抛出 `StepFailure`。
3. **等待完成条件**：通过 `await` 或 `awaitFact` 确保动作完成后才返回。

```kotlin
package io.ib67.yuanban.economy.story

import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.StepFailure
import io.ib67.yuanban.story.Spot
import io.ib67.yuanban.story.vanilla.opens

/**
 * 业务动作：向商人购买指定物品
 */
fun PlayerActor.buys(merchant: Spot, item: Material) = story.step("$name 向商人购买 ${item.key().value()}") {
    // 1. 打开商店窗口
    val shop = opens(merchant)

    // 2. 检查商店中是否有存货（基于窗口槽位事实）
    val targetSlot = (0 until 9).firstOrNull { shop.slot(it).read(clientState).material == item }
        ?: throw StepFailure("商店中没有上架 ${item.key().value()}")

    // 3. 点击购买并关闭
    shop.shiftClick(targetSlot)
    shop.close()

    // 4. 等待客户端物品栏确认拥有该物品
    awaitFact(inventory, "包含 ${item.key().value()}") { it.count(item) > 0 }
}
```

---

## 上下文参数（Context Parameters）与顶层动作

Kotlin 2.4.0 引入了稳定的上下文参数（Context Parameters）。对于创建场景、触发全局事件等不属于特定单一玩家的动作，可以定义带有 `context(story: StoryScope)` 的顶层函数：

```kotlin
package io.ib67.yuanban.weather.story

import io.ib67.yuanban.story.StoryScope

/**
 * 全局动作：触发雷暴天气
 */
context(story: StoryScope)
fun summonThunderstorm() = story.step("世界降下雷暴") {
    story.server("在服务端触发雷暴") {
        story.world.instance.setWeather(Weather.THUNDER)
    }
}
```

在故事中直接调用即可：

```kotlin
@Story
fun `雷暴天气下三叉戟引雷测试`() = story {
    summonThunderstorm()
    // ...
}
```

---

## 定义领域 Actor

对于复杂系统（如小游戏、竞技场、副本房间），建议创建专门的领域 Actor 包装器：

```kotlin
package io.ib67.yuanban.minigame.story

import io.ib67.yuanban.story.PlayerActor
import io.ib67.yuanban.story.ServerFact
import io.ib67.yuanban.story.StoryScope

class ArenaActor(val story: StoryScope, val arenaId: String) {
    // 领域事实：获取当前竞技场的获胜者
    val winner: ServerFact<String?>
        get() = ServerFact("竞技场 $arenaId 的获胜者") {
            // 在服务端线程读取领域模型
            ArenaManager.get(arenaId)?.winner?.username
        }
}

// 场景创建
context(story: StoryScope)
fun arena(mapName: String): ArenaActor = story.step("创建竞技场地图 $mapName") {
    val id = story.server { ArenaManager.create(mapName) }
    ArenaActor(story, id)
}

// 玩家加入竞技场动作
fun PlayerActor.joins(arena: ArenaActor) = story.step("$name 加入竞技场 ${arena.arenaId}") {
    runs("arena join ${arena.arenaId}")
    // 等待加入成功的领域事实
    awaitFact(arena.story.fact("玩家已在竞技场") { ... })
}
```

---

## 编写自定义事实与断言

如果被测机制包含自定义状态（例如金币余额、公会积分、技能冷却），可以定义为领域事实与 Kotest Matcher：

```kotlin
// 自定义玩家金币事实
val PlayerActor.balance: ServerFact<Int>
    get() = ServerFact("$name 的金币余额") {
        EconomyService.getBalance(player.uuid)
    }

// 自定义匹配器
fun haveSufficientFunds(min: Int) = Matcher<Int> { actual ->
    MatcherResult(
        actual >= min,
        { "金币余额至少应为 $min，实际为 $actual" },
        { "金币余额不应达到 $min" }
    )
}
```

在故事中便可直接使用：

```kotlin
alice.balance should haveSufficientFunds(100)
```

---

## 完整实战案例：配方合成与小游戏

### 案例 1：合成模块扩展（`crafting`）

```kotlin
// 扩展 PlayerActor 动作：在指定工作台中按照配方摆放材料并拿取产物
fun PlayerActor.crafts(at: Spot, pattern: List<String>, key: Map<Char, Material>) =
    story.step("$name 在 $at 的工作台中合成物品") {
        val window = opens(at)
        pattern.forEachIndexed { row, line ->
            line.forEachIndexed { col, symbol ->
                key[symbol]?.let { material ->
                    window.put(material * 1, into = CraftingTableSlots.grid(row, col))
                }
            }
        }
        window.take(CraftingTableSlots.RESULT)
        window.close()
    }
```

业务测试代码：

```kotlin
@Story
fun `使用两块木板合成四根木棍`() = story {
    val table = place(Block.CRAFTING_TABLE, at = spawn.north(2))
    val alice = player("alice") { inventory(Material.OAK_PLANKS * 2) }

    alice.crafts(
        at = table,
        pattern = listOf("P", "P"),
        key = mapOf('P' to Material.OAK_PLANKS)
    )

    alice.inventory shouldContain (Material.STICK * 4)
}
```

### 案例 2：小游戏淘汰测试

```kotlin
@Story
fun `Spleef 竞技场中最后留下的玩家获胜`() = story {
    val arena = arena("Spleef_Classic")
    val alice = player("alice").also { it.joins(arena) }
    val bob = player("bob").also { it.joins(arena) }

    "bob 挖掉 alice 脚下的雪块，alice 坠入虚空淘汰" {
        bob.breaks(alice.below)
    }

    // 断言竞技场胜利者
    arena.winner shouldBe bob.username
}
```

[目录](README.md) · 上一篇：[原版词汇表](vanilla-vocabulary.md) · 下一篇：[线程模型与运行时](threading-and-runtime.md)
