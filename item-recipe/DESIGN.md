# item-recipe 设计

## 1. 职责

`item-recipe` 是 `datapack-loader` 的订阅者。它读取数据包中的配方（`data/<ns>/recipe/**.json`），按本次加载的物品标签解析原料，提交阶段把配方注册到 Minestom 的 `RecipeManager`，并给在线玩家重新发送配方数据和配方书。

Minestom 的 `RecipeManager` 只负责客户端需要的数据：配方书条目、物品属性集合（熔炉输入等）、切石机配方列表。配方的匹配和合成不在 Minestom 中，也不在本模块的范围内。本模块解码出的配方数据通过 `RecipeSubscriber.current()` 提供给以后实现合成逻辑的模块。

原版行为对照 26.2 服务端反编译代码：`net.minecraft.world.item.crafting` 包中的全部配方类、`RecipeManager`、`Ingredient`、`PlacementInfo`、`ShapedRecipePattern`、`SlotDisplay`、`HolderSetCodec`、`ItemStackTemplate`、`ExtraCodecs`、`MinMaxBounds`、`SimpleJsonResourceReloadListener`。minecraft.wiki 的 *Recipe (Java Edition)* 页面已经混入 26.3 的改动（`cookingtime` 必填、`brewing` 配方等），不作为依据。

## 2. 依赖

```groovy
plugins {
    id("de.undercouch.download") version "5.7.0"
}

repositories {
    maven { url = "https://libraries.minecraft.net" }
}

dependencies {
    api(project(":item-tag"))
    implementation("com.mojang:datafixerupper:10.0.21")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(testFixtures(project(":item-tag")))
}
```

配方用原版 26.2 所用的 DataFixerUpper 10.0.21 解码。本模块按原版各配方类的 `MAP_CODEC` 写出结构相同的编解码器，字段缺省、数字和布尔值的转换、未知字段被忽略、`Codec.either` 的尝试顺序、`simpleMap` 遇到未知键报错，这些规则都由同一个库实现，和原版一致。下载插件只在测试中使用，见第 8 节。

## 3. 公开接口

包 `io.ib67.yuanban.recipe`：

```java
public final class RecipeSubscriber implements Subscriber<RecipeSet> {
    public static final SubscriberKey<RecipeSet> KEY = SubscriberKey.of("yuanban:recipes");
    public static final Topic.Single TOPIC = Topic.json("recipe");

    public RecipeSubscriber(ServerProcess process);
    public Subscription<RecipeSet> subscription();
    public RecipeSet current();
}

public record RecipeSet(NavigableMap<Key, RecipeDefinition> recipes) {
    public static final RecipeSet EMPTY;
}

public final class RecipeException extends DataPackException {}
```

- `subscription()` 返回以这个对象为订阅者、生命周期为 `RELOADABLE`、读取 `TOPIC`、`after(ItemTagSubscriber.KEY)` 的订阅。宿主同时需要注册 `ItemTagSubscriber.subscription(process)`。
- `current()` 返回这个对象最近一次提交的配方，提交之前是 `RecipeSet.EMPTY`。

```java
var recipes = new RecipeSubscriber(process);
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(...)
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(recipes.subscription())
        .build();
```

包 `io.ib67.yuanban.recipe.definition` 是配方的数据类型，全部是 record：

```java
public sealed interface RecipeDefinition {}

public sealed interface ItemIngredient {
    List<Material> items();
    record OfTag(Key tag, List<Material> items) implements ItemIngredient {}
    record OfItems(List<Material> items) implements ItemIngredient {}
}

public record CommonInfo(boolean showNotification) {}
public record CraftingBookInfo(CraftingCategory category, String group) {}
public record CookingBookInfo(CookingCategory category, String group) {}
public record IntBounds(Optional<Integer> min, Optional<Integer> max) {}
public enum CraftingCategory { BUILDING, REDSTONE, EQUIPMENT, MISC }
public enum CookingCategory { FOOD, BLOCKS, MISC }
public enum CookingKind { SMELTING, BLASTING, SMOKING, CAMPFIRE_COOKING }
```

`ItemIngredient.OfTag.items()` 是这个标签在本次加载中展开得到的物品。配方结果的类型是 Minestom 的 `ItemStack`。

## 4. 配方格式

原版 26.2 注册了 21 个配方序列化器。通用三项指 `show_notification?`（默认 `true`）、`category?`（`building` / `redstone` / `equipment` / `misc`，默认 `misc`）、`group?`（默认空字符串）。`?` 表示可选。

| `type` | record | 字段 |
|---|---|---|
| `crafting_shaped` | `ShapedRecipe` | 通用三项、`key`、`pattern`、`result` |
| `crafting_shapeless` | `ShapelessRecipe` | 通用三项、`result`、`ingredients`（1 到 9 个原料） |
| `crafting_transmute` | `TransmuteRecipe` | 通用三项、`input`、`material`、`material_count?`（默认 1）、`result`、`add_material_count_to_result?`（默认 `false`） |
| `crafting_dye` | `DyeRecipe` | 通用三项、`target`、`dye`、`result` |
| `crafting_imbue` | `ImbueRecipe` | 通用三项、`source`、`material`、`result` |
| `crafting_decorated_pot` | `DecoratedPotRecipe` | `back`、`left`、`right`、`front`、`result` |
| `crafting_special_bookcloning` | `BookCloningRecipe` | `source`、`material`、`allowed_generations?`（默认 `{min:0,max:1}`）、`result` |
| `crafting_special_mapextending` | `MapExtendingRecipe` | `map`、`material`、`result` |
| `crafting_special_firework_rocket` | `FireworkRocketRecipe` | `shell`、`fuel`、`star`、`result` |
| `crafting_special_firework_star` | `FireworkStarRecipe` | `shapes`、`trail`、`twinkle`、`fuel`、`dye`、`result` |
| `crafting_special_firework_star_fade` | `FireworkStarFadeRecipe` | `target`、`dye`、`result` |
| `crafting_special_bannerduplicate` | `BannerDuplicateRecipe` | `banner`、`result` |
| `crafting_special_shielddecoration` | `ShieldDecorationRecipe` | `banner`、`target`、`result` |
| `crafting_special_repairitem` | `RepairItemRecipe` | 无 |
| `smelting`、`blasting`、`smoking`、`campfire_cooking` | `CookingRecipe` | `show_notification?`、`category?`（`food` / `blocks` / `misc`，默认 `misc`）、`group?`、`ingredient`、`result`、`experience?`（默认 0）、`cookingtime?`（默认依次为 200、100、100、100） |
| `stonecutting` | `StonecutterRecipe` | `show_notification?`、`ingredient`、`result` |
| `smithing_transform` | `SmithingTransformRecipe` | `show_notification?`、`template?`、`base`、`addition?`、`result` |
| `smithing_trim` | `SmithingTrimRecipe` | `show_notification?`、`template`、`base`、`addition`、`pattern` |

### 4.1 各字段的规则

- `type`：命名空间 ID，省略命名空间时为 `minecraft`。不是上表中的类型时报错 `Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: <id>`。
- 原料：原版 `Ingredient.CODEC`。
  - `#` 开头的字符串是标签，在 `ItemTags`（`item-tag` 本次加载的准备结果）中查找，找不到报错 `Missing tag: '<id>' in 'minecraft:item'`。
  - 否则是单个物品 ID，或者物品 ID 列表。列表不能为空（`List must have contents`）。
  - 物品 ID 用 `Material.fromKey` 查找，找不到报错 `Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: <id>`；不能是 `minecraft:air`（`Item must not be minecraft:air`）。
- `result`：原版 `ItemStackTemplate.CODEC`，完整写法 `{"id": ..., "count"?: 1, "components"?: {...}}`，或者只写物品 ID。`id` 的规则同上；`count` 必须在 [1, 99] 之内。`components` 交给 Minestom 的 `DataComponent.PATCH_CODEC`，用 `RegistryTranscoder(Transcoder.JSON, process)` 解码，最后组成 Minestom 的 `ItemStack`。
- `key` 和 `pattern`：原版 `ShapedRecipePattern`。
  - `pattern` 是 1 到 3 个字符串，长度都相同并且不超过 3。
  - `key` 的每个键是单个字符，并且不能是空格。
  - 图案中的字符（空格除外）必须在 `key` 中定义；`key` 中的字符必须在图案中用到。
  - 图案按原版 `shrink` 去掉四周全是空格的行和列，得到宽、高和按行排列的原料（空格为空位）。
- `material_count`、`allowed_generations`：原版 `MinMaxBounds.Ints.CODEC`，整数 `N` 表示 `[N, N]`，或者对象 `{min?, max?}`；`min` 大于 `max` 报错。前者必须在 [1, 8] 之内，后者必须在 [0, 2] 之内。
- `shapes`：对象，键是 `small_ball`、`large_ball`、`star`、`creeper`、`burst` 之一，值是原料。
- `pattern`（锻造纹饰）：纹饰样式 ID 或内联对象，用 Minestom 的 `TrimPattern.CODEC` 按 `process.trimPattern()` 注册表解析。

### 4.2 错误

准备阶段解码全部配方文件，只要有文件解码失败，就抛出 `RecipeException`。异常信息每行一个文件，按配方 ID 排序，格式参照原版日志：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/recipe/foo.json: <DataFixerUpper 给出的错误>
```

其中 `[file/pack] data/example/recipe/foo.json` 是 `Resource.toString()`（数据包 ID、overlay 目录、文件路径）。整次加载随之失败，游戏状态不变。

## 5. 注册到 Minestom

每个配方包装成内部类 `MinestomRecipe`，实现 `net.minestom.server.recipe.Recipe`。`RecipeManager` 以配方对象为键，`MinestomRecipe` 使用对象自身的相等判断，内容相同的两个配方不会冲突。

`MinestomRecipe` 在提交阶段由 `RecipeSet` 构造，构造时算好显示数据、类别、分组和物品属性。这些计算只组合已经解码的数据，不会失败。准备结果保持为公开的 `RecipeSet`，依赖本模块的订阅者通过 `PrepareContext.dependency(RecipeSubscriber.KEY)` 读到的就是它。原料需求由 Minestom 在 `addRecipe` 时读取，这时 `item-tag` 已经提交，标签原料直接使用 Minestom 注册表中的标签对象。

### 5.1 原料的表示

| 用途 | 标签原料 | 物品列表原料 |
|---|---|---|
| 显示 | `SlotDisplay.Tag(TagKey.of(tag))` | `SlotDisplay.Composite`，每个物品为 `SlotDisplay.Item`，有合成剩余物的物品为 `SlotDisplay.WithRemainder(Item, ItemStack(剩余物))` |
| 原料需求 | `new Ingredient(Material.staticRegistry().getTag(...))` | `new Ingredient(items)` |
| 物品属性 | 展开后的物品 | 列表中的物品 |

合成剩余物取自原版 26.2 的 `Items`，Minestom 没有这项数据：

```java
// FIXME(Minestom M6): Minestom 的 Material 没有合成剩余物数据，这里内置原版 26.2 的取值，见 MINESTOM-BUG-REPORT.md
```

| 物品 | 剩余物 |
|---|---|
| `water_bucket`、`lava_bucket`、`milk_bucket` | `bucket` |
| `dragon_breath`、`honey_bottle` | `glass_bottle` |

### 5.2 显示数据（`createRecipeDisplays`）

按原版各配方类的 `display()` 构造，`craftingStation` 依次为工作台、熔炉 / 高炉 / 烟熏炉 / 营火、切石机、锻造台：

| 配方 | 显示 |
|---|---|
| 有序 | `CraftingShaped(宽, 高, 各格原料显示（空位为 Empty）, ItemStack(result), 工作台)` |
| 无序 | `CraftingShapeless(各原料显示, ItemStack(result), 工作台)` |
| 嬗变 | 材料数量 `n` 从最小值（缺省 1）到最大值（缺省 8）各一个 `CraftingShapeless([input, material × n], ItemStack(result 数量改为结果数量), 工作台)`；`add_material_count_to_result` 为真的时候结果数量是 `n + result.count`，否则是 `result.count` |
| 染色 | `CraftingShapeless([target, OnlyWithComponent(dye, DYE)], Dyed(OnlyWithComponent(dye, DYE), target), 工作台)` |
| 浸染 | `CraftingShaped(3, 3, [m, m, m, m, WithAnyPotion(source), m, m, m, m], WithAnyPotion(ItemStack(result)), 工作台)` |
| 烧炼 | `Furnace(ingredient, AnyFuel, ItemStack(result), 对应的方块, cookingtime, experience)` |
| 切石 | `Stonecutter(ingredient, ItemStack(result), 切石机)` |
| 锻造升级 | `Smithing(template 或 Empty, base, addition 或 Empty, ItemStack(result), 锻造台)` |
| 锻造纹饰 | 无，见下文 |
| 特殊配方（`crafting_special_*`、`crafting_decorated_pot`） | 无，和原版相同 |

两处绕过 Minestom 问题的地方：

- 锻造纹饰：Minestom 的 `SlotDisplay.SmithingTrim` 编码错误，发出去会破坏数据包，所以不生成显示。
  ```java
  // FIXME(Minestom M2): SlotDisplay.SmithingTrim 把纹饰样式编码成 SlotDisplay，见 MINESTOM-BUG-REPORT.md
  ```
- 切石：Minestom 从显示数据反推切石机原料，只接受 `Item` 和 `Tag`。原料是单个物品并且没有合成剩余物的时候，显示用 `SlotDisplay.Item`（原版为只含一项的 `Composite`，客户端显示相同）。原料列出多个物品的切石机配方仍然不会出现在客户端的切石机中。
  ```java
  // FIXME(Minestom M3): DeclareRecipesPacket 从显示数据反推切石机原料，丢弃 Composite，见 MINESTOM-BUG-REPORT.md
  ```

### 5.3 其他方法

| 方法 | 内容 |
|---|---|
| `recipeBookCategory()` | 合成配方按类别映射为 `CRAFTING_BUILDING_BLOCKS` / `CRAFTING_REDSTONE` / `CRAFTING_EQUIPMENT` / `CRAFTING_MISC`，特殊配方为 `CRAFTING_MISC`；熔炉为 `FURNACE_BLOCKS` / `FURNACE_FOOD` / `FURNACE_MISC`；高炉 `blocks` 为 `BLAST_FURNACE_BLOCKS`，其余为 `BLAST_FURNACE_MISC`；烟熏炉为 `SMOKER_FOOD`；营火为 `CAMPFIRE`；切石为 `STONECUTTER`；锻造为 `SMITHING` |
| `recipeBookGroup()` | `group` 为空时返回 `null`，否则返回 `group` |
| `itemProperties()` | 原版 `RecipeManager.RECIPE_PROPERTY_SETS`：熔炉 / 高炉 / 烟熏炉 / 营火配方的原料物品分别属于 `FURNACE_INPUT` / `BLAST_FURNACE_INPUT` / `SMOKER_INPUT` / `CAMPFIRE_INPUT`；锻造配方的模板、基础、附加物品分别属于 `SMITHING_TEMPLATE` / `SMITHING_BASE` / `SMITHING_ADDITION`（可选项缺省时不加入） |
| `craftingRequirements()` | 原版 `PlacementInfo.ingredients()`：有序为非空格位的原料；无序为全部原料；嬗变为 `input` 加上最大数量个 `material`；染色为 `[target, dye]`；浸染为 9 格原料；烧炼和切石为原料；锻造升级为存在的模板、基础、附加；锻造纹饰为模板、基础、附加。特殊配方返回 `null` |

Minestom 不读取 `recipeBookGroup()`，配方书条目没有分组。这一处无法在本模块中绕过，实现处加注释：

```java
// FIXME(Minestom M4): RecipeManager.addRecipe 不读取 recipeBookGroup()，见 MINESTOM-BUG-REPORT.md
```

不能放置的配方：非特殊配方中有原料是空标签的时候，原版 `PlacementInfo` 为 `NOT_PLACEABLE`。原版仍然为它生成配方书显示，原料需求为空列表，不把它的原料加入物品属性集合，不把它加入切石机列表。这里的原料需求和物品属性同样为空；切石机列表由 Minestom 从显示数据生成，这类切石机配方仍然会出现在列表中（原料是空标签，客户端没有可以放入的物品）。

原版按特性开关过滤显示数据、物品属性和切石机配方。Minestom 的物品数据中没有物品所需的特性开关，26.2 中也没有需要 `minecraft:vanilla` 以外特性的物品，所以这里不过滤：

```java
// FIXME(Minestom M7): Material 没有所需特性开关数据，26.2 中过滤不改变结果，见 MINESTOM-BUG-REPORT.md
```

## 6. 提交阶段

1. 从 `RecipeManager` 移除这个订阅者上一次注册的全部 `MinestomRecipe`。
2. 按配方 ID 的顺序构造 `MinestomRecipe` 并调用 `addRecipe`，不带可见条件，配方对全部玩家可见。
3. 用反射读出 `RecipeManager.declareRecipesPacket` 并调用 `invalidate()`。
   ```java
   // FIXME(Minestom M1): RecipeManager 增删配方后不刷新 DeclareRecipesPacket 的缓存，见 MINESTOM-BUG-REPORT.md
   ```
   这要求 Minestom 位于 classpath 上；放在模块路径上时需要 `--add-opens net.minestom.server/net.minestom.server.recipe=ALL-UNNAMED`。
4. 对每个在线玩家调用 `Player.refreshRecipes()`，重新发送 `DeclareRecipesPacket` 和替换整个配方书的 `RecipeBookAddPacket`。
5. `current()` 改为新的 `RecipeSet`。

`item-tag` 先于本模块提交，在线玩家先收到新标签，再收到新配方，顺序和原版 `/reload` 相同。

## 7. 与原版行为的差异

1. 原版遇到解码失败的配方文件，记录日志并跳过这个文件；这里抛出异常。`pattern` 全是空格的时候原版抛出未捕获的 `ArrayIndexOutOfBoundsException`，整次重载失败；这里报解码错误。
2. 原版只把玩家已解锁的配方加入配方书；这里全部玩家可以看到全部配方。
3. 第 5 节列出的 Minestom 问题造成的差异：锻造纹饰没有配方书条目，原料列出多个物品的切石机配方不发给客户端，原料是空标签的切石机配方仍然发给客户端，配方书条目没有分组。

## 8. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程，客户端连接使用它提供的进程内 `TestConnection`，测试任务设置系统属性 `minestom.inside-test=true`。

加载成功之后 `item-tag` 会删除 Minestom 自带的物品标签，同一个 JVM 中之后新建的 `ServerProcess` 解码附魔注册表时找不到它引用的物品标签。所以每个测试类都加上 `@ExtendWith(ResetItemTags.class)`，在每个测试方法开始之前把物品标签恢复成 Minestom 自带的标签。`ResetItemTags` 来自 `item-tag` 的 `testFixtures` 源码集，做法见 `item-tag/DESIGN.md` 第 7 节。全部测试类在同一个 JVM 中运行。

- 解码（`RecipeDecodingTest`）：直接使用 `RecipeCodecs`，不修改 Minestom 的标签。每种配方至少一个正常用例，检查字段和缺省值；错误用例覆盖未知的 `type`、缺失的标签、未知物品、`minecraft:air`、空的原料列表、`count` 超出范围、`pattern` 的每条规则、`ingredients` 超过 9 个、`material_count` 和 `allowed_generations` 超出范围或上下限颠倒、未知的烟花形状、未知的纹饰样式。
- 显示（`MinestomRecipeTest`）：直接构造 `MinestomRecipe`，检查显示数据、类别、分组、物品属性、原料需求，覆盖合成剩余物、切石机的单个物品原料（M3）、锻造纹饰没有显示（M2）、特殊配方、不能放置的配方；配方书条目能够按协议编码。
- 加载失败（`LoadErrorTest`）：数据包是在 `@TempDir` 中实际写出的目录，经过 `datapack-loader` 的真实加载流程读取；异常列出全部失败的文件及其位置，Minestom 的标签和配方没有变化。
- 重载（`ReloadTest`）：新配方替换旧配方；在线玩家收到一个 `DeclareRecipesPacket`，其中熔炉输入物品包含重载中新加入标签的物品（M1），单个物品原料的切石机配方出现在其中（M3）；收到一个替换整个配方书的 `RecipeBookAddPacket`，锻造纹饰配方没有条目（M2）；两个数据包都能按协议编码。
- 原版数据（`VanillaDataTest`）：`build.gradle` 中的下载任务从 Mojang 下载 26.2 服务端，按 SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 校验，取出 `META-INF/versions/26.2/server-26.2.jar`，路径通过系统属性 `yuanban.vanilla-server` 交给测试。测试用 zip 文件系统打开它，构造 `BuiltInPackProvider`，注册 `item-tag` 和本模块的订阅者，以 `CREATE_WORLD` 模式启动：
  - 全部配方解码成功，每种类型的数量和直接列出 jar 中 `data/minecraft/recipe/*.json` 并读取 `type` 得到的数量相同，`RecipeManager` 中的配方数量相同；
  - 提交之后 Minestom 的每个物品标签（内容和顺序）和加载前 Minestom 自带的标签相同；
  - `DeclareRecipesPacket` 中的切石机配方数量等于原版切石机配方的数量，数据包能够按协议编码。
