# item-recipe 设计

## 1. 职责

`item-recipe` 是 `datapack-loader` 的订阅者。它读取数据包中的配方（`data/<ns>/recipe/**.json`），按本次加载的物品标签解析原料，得到 `crafting` 模块定义的配方类型；提交阶段把配方交给 `crafting` 的 `RecipeManager`，由它替换全部配方并给在线玩家发送配方数据和配方书。

配方的类型、匹配、产出、配方书和客户端同步都在 `crafting` 模块中，见 [crafting/DESIGN.md](../crafting/DESIGN.md)。本模块不使用 Minestom 的 `RecipeManager`。

原版行为对照 26.2 服务端反编译代码：`net.minecraft.world.item.crafting` 包中的全部配方类、`Ingredient`、`ShapedRecipePattern`、`HolderSetCodec`、`ItemStackTemplate`、`ExtraCodecs`、`MinMaxBounds`、`SimpleJsonResourceReloadListener`。minecraft.wiki 的 *Recipe (Java Edition)* 页面已经混入 26.3 的改动（`cookingtime` 必填、`brewing` 配方等），不作为依据。

## 2. 依赖

```groovy
dependencies {
    api(project(":item-tag"))
    api(project(":crafting"))
    implementation(project(":dfu-codecs"))
    implementation("it.unimi.dsi:fastutil:8.5.19")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(testFixtures(project(":item-tag")))
    testImplementation(testFixtures(project(":crafting")))
}
```

配方用 `dfu-codecs` 解码。`dfu-codecs` 以 `api` 依赖原版 26.2 所用的 DataFixerUpper 10.0.21，并在它上面移植了原版的 `RegistryOps`、注册表编解码器（`HolderSetCodec`、`RegistryFileCodec`、`Registry.byNameCodec`）、`Item.CODEC`、`DataComponentPatch.CODEC`，以及 `ExtraCodecs`、`MinMaxBounds`、`StringRepresentable` 中的编解码器。DataFixerUpper 只发布在 `https://libraries.minecraft.net`，这个仓库在根目录的 `build.gradle.kts` 中声明。

本模块的 `RecipeCodecs` 在 `dfu-codecs` 的基础上移植原版 `Recipe.CODEC` 和各配方类的 `MAP_CODEC`，全部是静态的编解码器：字段顺序、缺省值、校验和错误文本都与原版相同；字段缺省、数字和布尔值的转换、未知字段被忽略、`Codec.either` 的尝试顺序、`simpleMap` 遇到未知键报错，这些规则由同一个库实现，和原版一致。

解码使用 `RegistryOps`，由 `RecipeCodecs.registryOps(registries, tags)` 建立：注册表是 Minestom 进程的注册表及其标签，物品标签换成 `item-tag` 本次加载的准备结果。原版重载时同样用待提交的标签解码配方。

fastutil 与 Minestom 运行时使用的版本相同，用于移植原版 `ShapedRecipePattern` 收集未使用符号的 `CharArraySet`。测试读取的原版服务端由根项目下载，见第 7 节。

## 3. 公开接口

包 `io.ib67.yuanban.recipe`：

```java
public final class RecipeSubscriber implements Subscriber<RecipeSet> {
    public static final SubscriberKey<RecipeSet> KEY = SubscriberKey.of("yuanban:recipes");
    public static final Topic.Single TOPIC = Topic.json("recipe");

    public RecipeSubscriber(ServerProcess process, RecipeManager recipes);
    public Subscription<RecipeSet> subscription();
    public RecipeSet current();
}

public record RecipeSet(NavigableMap<Key, Recipe<?>> recipes) {
    public static final RecipeSet EMPTY;
}

public final class RecipeException extends DataPackException {}
```

- `process` 提供解码时使用的注册表及其标签：注册表引用按它检查，物品组件按它的注册表解码；物品标签换成本次加载的结果。`recipes` 是 `crafting` 的 `RecipeManager`，通常为 `Crafting.recipes()`。
- `subscription()` 返回以这个对象为订阅者、生命周期为 `RELOADABLE`、读取 `TOPIC`、`after(ItemTagSubscriber.KEY)` 的订阅。宿主同时需要注册 `ItemTagSubscriber.subscription(process)`。
- `current()` 返回这个对象最近一次提交的配方，提交之前是 `RecipeSet.EMPTY`。

```java
var crafting = new Crafting(process, maps);
process.eventHandler().addChild(crafting.eventNode());
var recipes = new RecipeSubscriber(process, crafting.recipes());
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(...)
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(recipes.subscription())
        .build();
```

配方的数据类型在 `crafting` 模块的 `io.ib67.yuanban.crafting.recipe` 包与它的 `kind` 子包中，全部是不可变的 record。`ItemIngredient.OfTag.items()` 是这个标签在本次加载中展开得到的物品。配方结果的类型是 `ItemStackTemplate(material, count, components)`，`components` 原样保存配方文件中的组件补丁。

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

- `type`：命名空间 ID，省略命名空间时为 `minecraft`。按原版 `Registry.byNameCodec` 在 `recipe_serializer` 注册表中查找，不是上表中的类型时报错 `Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: <id>`。
- `show_notification`：原版 `Recipe.CommonInfo.MAP_CODEC`，必须是布尔值。它只决定配方被解锁时客户端是否弹出提示；`crafting` 让全部玩家已知全部配方，所以这里只检查，不保存。
- `category`：原版 `StringRepresentable` 的编解码器，未知名字报错 `Unknown element name:<name>`。
- 原料：原版 `Ingredient.CODEC`，即 `nonEmptyHolderSet(HolderSetCodec.create(ITEM, Item.CODEC, false))`。
  - `#` 开头的字符串是标签，在 `RegistryOps` 的物品注册表中查找，也就是 `ItemTags`（`item-tag` 本次加载的准备结果）；找不到报错 `Missing tag: '<id>' in 'minecraft:item'`。标签原料的物品在解码时取这个标签在本次加载中的内容，与原版把标签原料绑定到待提交的标签相同。
  - 否则是单个物品 ID，或者物品 ID 列表。列表不能为空（`List must have contents`）。
  - 物品 ID 按原版 `Item.CODEC` 在内置的物品注册表中查找，找不到报错 `Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: <id>`；不能是 `minecraft:air`（`Item must not be minecraft:air`）。
- `result`：原版 `ItemStackTemplate.CODEC`，完整写法 `{"id": ..., "count"?: 1, "components"?: {...}}`，或者只写物品 ID。`id` 的规则同上；`count` 必须在 [1, 99] 之内（`Value must be within range [1;99]: <n>`）。`components` 是原版 `DataComponentPatch.CODEC`，由 `dfu-codecs` 的 `ItemCodecs.DATA_COMPONENT_PATCH` 解码：键按原版解码，包括 `!` 开头的移除条目，未知组件报错 `No component with type: '<id>'`；每个组件的值通过适配器交给 Minestom 为这个组件提供的编解码器，注册表取自 `RegistryOps`。得到的补丁原样保存在 `ItemStackTemplate` 中。与物品原型相同的条目和移除条目都保留：原版产出时用这个补丁覆盖输入物品的组件，Minestom 的 `ItemStack` 会与原型求差而删掉这些条目。
- `key` 和 `pattern`：原版 `ShapedRecipePattern`。
  - `pattern` 是 1 到 3 个字符串，长度都相同并且不超过 3。
  - `key` 按原版 `ExtraCodecs.strictUnboundedMap` 解码：遇到第一个无法解码的条目就失败，结果保持文件中键的顺序。每个键是单个字符，并且不能是空格。
  - 图案中的字符（空格除外）必须在 `key` 中定义；`key` 中的字符必须在图案中用到。未用到的字符按原版收集在 fastutil 的 `CharArraySet` 中，错误文本按 `key` 中的顺序列出，例如 `Key defines symbols that aren't used in pattern: {z, a}`。
  - 图案按原版 `shrink` 去掉四周全是空格的行和列，得到宽、高和按行排列的原料（空格为空位）。
- `material_count`、`allowed_generations`：原版 `MinMaxBounds.Ints.CODEC`，整数 `N` 表示 `[N, N]`，或者对象 `{min?, max?}`；`min` 大于 `max` 时报错 `Swapped bounds in range: Optional[<min>] is higher than Optional[<max>]`。前者必须在 [1, 8] 之内，后者必须在 [0, 2] 之内，按原版 `MinMaxBounds.validateContainedInRange` 检查，两个范围按 Guava `Range` 的形式打印，例如 `Range must be within [1..8], but was [9..9]`、`Range must be within [1..8], but was [2..+∞)`。
- `shapes`：对象，键是 `small_ball`、`large_ball`、`star`、`creeper`、`burst` 之一（未知名字报错 `Unknown element name:<name>`），值是原料。保持文件中的顺序，查找爆炸类型时按这个顺序。
- `pattern`（锻造纹饰）：原版 `TrimPattern.CODEC`，即 `RegistryFileCodec`。纹饰样式 ID 在 `RegistryOps` 的 `trim_pattern` 注册表（Minestom 进程的 `trimPattern()`）中查找，找不到报错 `Failed to get element ResourceKey[minecraft:trim_pattern / <id>]`；不是 ID 的时候作为内联的纹饰样式，按原版 `TrimPattern.DIRECT_CODEC` 解码：`asset_id`（命名空间 ID）、`description`（文本组件，`dfu-codecs` 的 `ComponentSerialization.CODEC`）、`decal?`（默认 `false`），结果是 Minestom 的 `TrimPattern`。

### 4.2 错误

准备阶段读取并解码全部配方文件，只要有文件不是合法的 JSON 或者解码失败，就抛出 `RecipeException`。异常信息中每个失败的文件一条，按配方 ID 排序，条目之间用换行分开，格式参照原版日志：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/recipe/foo.json: <错误>
```

其中 `[file/pack] data/example/recipe/foo.json` 是 `Resource.toString()`（数据包 ID、overlay 目录、文件路径）。解码失败时 `<错误>` 是 DataFixerUpper 给出的错误；JSON 语法错误时是 `Resource.readJson()` 抛出的 `JsonParseException` 的信息，Gson 的这类信息可能带有换行和排查文档的链接。整次加载随之失败，游戏状态不变。

## 5. 提交阶段

调用 `RecipeManager.replaceRecipes(prepared.recipes())`，然后 `current()` 改为新的 `RecipeSet`。`RecipeManager` 算好查找用的数据、配方书条目、物品属性和切石机列表，给在线玩家发送 `DeclareRecipesPacket` 和替换整个配方书的 `RecipeBookAddPacket`。

`item-tag` 先于本模块提交，`RecipeManager` 网络编码标签原料时 Minestom 注册表中已经是新标签；在线玩家先收到新标签，再收到新配方，顺序和原版 `/reload` 相同。

## 6. 与原版的差异

1. 原版遇到 JSON 语法错误或者解码失败的配方文件，记录日志并跳过这个文件；这里抛出异常，整次加载失败。
2. `pattern` 全是空格的时候原版抛出未捕获的 `ArrayIndexOutOfBoundsException`，整次重载失败；这里报解码错误 `Invalid pattern: pattern contains only spaces`。
3. `show_notification` 不保存，见 4.1。
4. 组件补丁中每个组件的值通过 `dfu-codecs` 的适配器交给 Minestom 的编解码器解码。这些值内部只报告第一个错误，错误文本是 Minestom 的。

配方注册之后与原版的差异见 [crafting/DESIGN.md](../crafting/DESIGN.md)。

## 7. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程，客户端连接使用它提供的进程内 `TestConnection`，测试任务设置系统属性 `minestom.inside-test=true`。测试代码作为宿主，用 `crafting` 的 testFixtures 中的 `InMemoryMaps` 提供地图数据。

加载成功之后 `item-tag` 会删除 Minestom 自带的物品标签，同一个 JVM 中之后新建的 `ServerProcess` 解码附魔注册表时找不到它引用的物品标签。所以每个测试类都加上 `@ExtendWith(ResetItemTags.class)`，在每个测试方法开始之前把物品标签恢复成 Minestom 自带的标签。`ResetItemTags` 来自 `item-tag` 的 `testFixtures` 源码集，做法见 `item-tag/DESIGN.md` 第 7 节。全部测试类在同一个 JVM 中运行。

- 解码（`RecipeDecodingTest`）：直接调用 `RecipeCodecs.CODEC`，`RegistryOps` 由 `RecipeCodecs.registryOps` 按测试中构造的 `ItemTags` 建立，不修改 Minestom 的标签。每种配方至少一个正常用例，检查字段和缺省值，以及结果模板保留与物品原型相同的组件和移除条目；错误用例覆盖未知的 `type`、缺失的标签、未知物品、`minecraft:air`、空的原料列表、`count` 超出范围、`show_notification` 不是布尔值、`pattern` 的每条规则、`ingredients` 超过 9 个、`material_count` 和 `allowed_generations` 超出范围或上下限颠倒、未知的烟花爆炸类型、未知的纹饰样式，错误文本与原版相同。另外检查 `key` 中多个未用到的字符按文件中的顺序列出、内联的纹饰样式、标签原料取本次加载的标签（只在本次加载中定义的标签可以使用，Minestom 自带而本次加载没有的标签报错）。
- 加载失败（`LoadErrorTest`）：数据包是在 `@TempDir` 中实际写出的目录，经过 `datapack-loader` 的真实加载流程读取；异常列出全部失败的文件及其位置，JSON 语法错误与解码错误一起按配方 ID 排列，Minestom 的标签和 `crafting` 中的配方没有变化。
- 重载（`ReloadTest`）：新配方替换旧配方；在线玩家收到一个 `DeclareRecipesPacket`，其中有全部七种物品属性，熔炉输入物品包含重载中新加入标签的物品，切石机列表按配方 ID 排列并包含原料列出多个物品的配方；收到一个替换整个配方书的 `RecipeBookAddPacket`，条目按配方 ID 编号，分组 ID 按出现顺序分配，锻造纹饰配方没有条目（M2）；两个数据包都能按协议编码。
- 原版数据（`VanillaDataTest`）：根项目的 `extractVanillaServer` 任务从 Mojang 下载 26.2 服务端，按 SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 校验，取出 `META-INF/versions/26.2/server-26.2.jar`，全部模块使用这同一份；本模块的 `test` 任务依赖它，路径通过系统属性 `yuanban.vanilla-server` 交给测试。测试用 zip 文件系统打开它，构造 `BuiltInPackProvider`，注册 `item-tag` 和本模块的订阅者，以 `CREATE_WORLD` 模式启动：
  - 全部配方解码成功，每种类型的数量和直接列出 jar 中 `data/minecraft/recipe/*.json` 并读取 `type` 得到的数量相同，`crafting` 中的配方数量相同；
  - 提交之后 Minestom 的每个物品标签（内容和顺序）和加载前 Minestom 自带的标签相同；
  - 玩家首次出生时收到的 `DeclareRecipesPacket` 中切石机配方数量等于原版切石机配方的数量，`RecipeBookAddPacket` 的条目数等于全部配方的显示数据数量，两个数据包都能按协议编码。
