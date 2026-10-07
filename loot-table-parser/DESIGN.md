# loot-table-parser 设计

## 1. 职责

`loot-table-parser` 是 `datapack-loader` 的订阅者。它读取数据包中的战利品表（`data/<ns>/loot_table/**.json`），用 `loot` 模块注册的类型把每个 JSON 文件解码成 `LootTable`；物品标签取自 `item-tag` 在同一次加载中展开的结果，准备阶段核对 `tag` 条目和 `match_tool` 条件里的标签引用存在；提交阶段把战利品表集合交给 `loot` 的 `LootManager`。

数据类型、`LootManager`、引用和递归校验、求值都在 `loot` 模块，见 [loot/DESIGN.md](../loot/DESIGN.md)。本模块只负责数据包这一侧：topic 声明、JSON 解码、错误聚合、提交。

原版行为对照 26.2 服务端反编译代码：`SimpleJsonResourceReloadListener`（按 topic 扫描 JSON 文件）、`LootDataType`（loot 资源类型）、`ReloadableServerResources`（加载次序：物品标签先于战利品表）、`net.minecraft.world.level.storage.loot` 包中各类型的 `MapCodec`（字段名、缺省值），以及 minecraft.wiki 的 *Loot table* 页面；字段名和取值以服务端 jar 注册的为准。

## 2. 依赖

```groovy
plugins {
    id("de.undercouch.download") version "5.7.0"
}

repositories {
    maven {
        name = "minecraft"
        url = "https://libraries.minecraft.net"
    }
}

dependencies {
    api(project(":loot"))
    api(project(":item-tag"))
    implementation("com.mojang:datafixerupper:10.0.21")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(testFixtures(project(":item-tag")))
    testImplementation(testFixtures(project(":loot")))
}
```

解码用原版 26.2 所用的 DataFixerUpper 10.0.21，和 `item-recipe` 相同：本模块按原版各 `MapCodec` 写出结构相同的编解码器，字段缺省、`Codec.either` 的尝试顺序、嵌套路径都交给这个库，保证和原版一致。下载插件只在测试中使用，见第 6 节。

Minestom 不在 `api`：解码结果是 `loot` 模块的 record，不引用 Minestom 类型；物品 ID 解码成 `Key`，到求值的时候才交给 `Material.fromKey` 之类的查找。`ServerProcess` 出现在构造参数里只是因为 `set_components` 的 `components` 字段要用 `RegistryTranscoder(Transcoder.JSON, process)` 解码组件补丁，和 `RecipeSubscriber` 相同；订阅者本身不暴露 Minestom 类型。

## 3. 公开接口

包 `io.ib67.yuanban.lootparser`：

```java
public final class LootTableSubscriber implements Subscriber<LootTableSet> {
    public static final SubscriberKey<LootTableSet> KEY = SubscriberKey.of("yuanban:loot_tables");
    public static final Topic.Single TOPIC = Topic.json("loot_table");

    public LootTableSubscriber(ServerProcess process, LootManager manager);
    public Subscription<LootTableSet> subscription();
    public LootTableSet current();
}

public record LootTableSet(NavigableMap<Key, LootTable> tables) {
    public static final LootTableSet EMPTY;
}

public final class LootException extends DataPackException {}
```

- `subscription()` 返回以这个对象为订阅者、生命周期为 `RELOADABLE`、读取 `TOPIC`、`after(ItemTagSubscriber.KEY)` 的订阅。物品标签先于本模块准备：`tag` 条目和 `match_tool` 里的 `#id` 按本次加载的标签结果核对，和原版把新标签放进 `PendingTags` 再交给战利品表加载的次序相同。
- `process` 提供解码组件补丁用的注册表。`manager` 是 `loot` 的 `LootManager`，提交阶段把新集合交给它。
- `current()` 是最近一次提交的战利品表集合，提交之前是 `LootTableSet.EMPTY`。

用法：

```java
var loot = new LootManager(LootTypeRegistry.vanilla());
var lootTables = new LootTableSubscriber(process, loot);
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(...)
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(lootTables.subscription())
        .build();
```

## 4. 战利品表格式

战利品表是 `data/<ns>/loot_table/**.json` 中的 JSON 文件，资源 ID 是路径去掉 `loot_table/` 前缀和 `.json` 后缀。

### 4.1 战利品表顶层

| 字段 | 必填 | 规则 |
|---|---|---|
| `type` | 可选，默认 `minecraft:generic` | 值在原版 `LootContextParamSets` 注册的 20 个名字里；未知值报错 |
| `random_sequence` | 可选 | 命名空间 ID |
| `functions` | 可选 | 修饰器对象或者修饰器列表，按列表处理 |
| `pools` | 可选 | 池列表，按顺序处理 |

### 4.2 战利品池

| 字段 | 必填 | 规则 |
|---|---|---|
| `rolls` | 必填 | 整型数字提供器；裸整数读作 `constant` |
| `bonus_rolls` | 可选，默认 `constant(0)` | 浮点数字提供器；抽取次数是 `rolls + floor(bonus_rolls * luck)` |
| `conditions` | 可选 | 条件列表 |
| `functions` | 可选 | 修饰器列表 |
| `entries` | 必填 | 条目列表，至少一个 |

### 4.3 条目

`type` 必填，取值在原版注册的 9 种之内。共有字段：

| 字段 | 规则 |
|---|---|
| `conditions` | 可选，条件列表 |
| `functions` | 可选，修饰器列表 |
| `weight` | 可选，默认 1，整型 |
| `quality` | 可选，默认 0，整型 |

- `item`：`name`（物品 ID），必须在 Minestom 物品注册表里能找到（按 `ItemTags` 也一样）。
- `loot_table`：`value` 是表 ID（字符串）或内联表（对象）。
- `dynamic`：`name` 是 `contents` 或 `sherds`，其他取值报错。
- `empty`：无字段。
- `slots`：`slot_source` 内联对象，原版数据不使用，按 record 保留。
- `tag`：`name`（物品标签 ID）、`expand`（布尔必填）；`expand=false` 按单例、`expand=true` 按复合条目处理。
- `group` / `alternatives` / `sequence`：`children` 至少一个条目。

### 4.4 条件

`condition` 必填，取值在原版注册的 20 种之内。其余字段按见 [loot/DESIGN.md](../loot/DESIGN.md) 5.3 节保留的 record 一一解析。

- `reference` 的 `name` 是 `predicate` 资源 ID。本模块不加载 `predicate` 文件，只在 `reference` 里查这个 ID 是合法的命名空间 ID；加载和展开由将来的订阅者模块负责。
- `random_chance` 的 `chance` 是浮点数字提供器；原版允许负数，按不通过处理。

### 4.5 修饰器

`function` 必填，取值在原版注册的 44 种之内。其余字段按原版各自的 `MapCodec` 解析。

- `set_components` 的 `components` 是组件补丁，用 Minestom 的 `DataComponent.PATCH_CODEC` 经 `RegistryTranscoder(Transcoder.JSON, process)` 解码后原样保存。解码这个字段需要 `ServerProcess`，这是 `LootTableSubscriber` 构造参数要 `process` 的原因，和 `RecipeSubscriber` 相同。
- `reference` 的 `name` 是 `item_modifier` 资源 ID，处理方式同条件的 `reference`。
- 修饰器内嵌的数字提供器按 4.6。

### 4.6 数字提供器

在 `rolls`、`bonus_rolls`、`random_chance.chance`、`set_count.count` 等位置出现：

- 对象形式的 `type` 字段取值在原版注册的 8 种之内，未知值报错。
- 裸数字按 `constant` 读进去。

## 5. 错误处理与提交

准备阶段解码全部战利品表文件。任何文件解码失败抛出 `LootException`，按资源 ID 排序，每行一个：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/loot_table/foo.json: <DataFixerUpper 给出的错误>
```

其中 `[file/pack] data/example/loot_table/foo.json` 是 `Resource.toString()`，和 `item-recipe`、`item-tag` 的格式相同。整次加载失败（`datapack-loader` 抛出 `LoadFailedException`），`LootManager` 中的集合不变。

`tag` 条目的 `name`、`match_tool` 里出现的 `#id` 必须在 `ItemTags` 里能找到，找不到按上面同一格式在准备阶段报错：`Missing tag: '<id>' in 'minecraft:item'`。

提交阶段按顺序执行（在 `datapack-loader` 的提交线程上）：

1. `LootManager.replaceAll(prepared.tables())`——第 7 节的校验（引用存在、无递归、复合条目尺寸有限）在这里做，失败抛 `LootException`，本次 `apply` 失败。
2. `current()` 指向新的 `LootTableSet`。

物品标签先于本模块提交：`ItemTagSubscriber.apply` 先把新标签写进 Minestom 并通知在线玩家；本模块提交时 Minestom 注册表里已经是新标签。

## 6. 与原版行为的差异

1. 原版遇到解码失败的战利品表记录日志并跳过这个文件；本模块抛出异常，整次加载失败。
2. 原版把 `predicate` 和 `item_modifier` 作为独立资源加载并允许 `reference` 互相引用。本模块不加载这两种文件；`reference` 类型只记 ID，求值时经 `LootEnvironment.findFunction` / `findCondition` 到对应注册表查找。原版 26.2 的原版数据包本身不含这两种文件，所以对原版数据没有影响。
3. 原版校验阶段为每个 `LootContextUser` 检查上下文参数是否被当前类型覆盖，不匹配的写日志，加载仍然继续；本模块只在解析阶段拦截未知的 `type`，上下文字段检查在求值时按原版文案报错。
4. 原版的递归检查在验证阶段写日志 "Detected infinite loop in loot tables"（加载仍然继续，取用的时候才抛 `IllegalStateException`）；本模块在注册阶段静态检测，任何递归都视为加载失败。

## 7. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程（解码组件补丁需要 `ServerProcess` 的注册表）；`minestom.inside-test=true`。数据包是在 `@TempDir` 中实际写出的目录，经过 `datapack-loader` 的真实加载流程读取，物品标签由 `ItemTagSubscriber` 在同一次加载中准备。每个测试类加 `@ExtendWith(ResetItemTags.class)`，原因和 `item-recipe` 相同。

- 解码（`LootDecodingTest`）：每个原版条目、条件、修饰器、数字提供器至少一个正常用例，检查字段和缺省值（`weight` 默认 1、`quality` 默认 0、`bonus_rolls` 默认 `constant(0)`、`add` 默认 `false`、`include_compatible` 默认 `true`、`limit` 默认 0 表不限制、`scale` 默认 1.0 等按原版 `MapCodec` 的缺省）；错误用例覆盖未知条目类型、未知条件类型、未知修饰器类型、未知数字提供器类型、未知上下文类型、`entries` 为空、`children` 为空、`expand` 缺失、`weight` 不是整数、`tag.name` 不是物品标签 ID 或不存在、`dynamic.name` 不是 `contents` 或 `sherds`、`set_count.count` 不是数字提供器、`components` 不是合法的组件补丁、条件 `reference.name` 不是合法命名空间 ID。
- 加载失败（`LootLoadErrorTest`）：多文件出错时异常信息按 ID 排序、`LootManager` 中的集合不变。
- 重载（`LootReloadTest`）：新集合替换旧集合，旧 ID 不再能取到。
- 原版数据（`VanillaLootTest`）：`build.gradle` 中的下载任务从 Mojang 下载 26.2 服务端，按 SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 校验，取出 `META-INF/versions/26.2/server-26.2.jar`，路径经系统属性 `yuanban.vanilla-server` 交给测试。测试用 zip 文件系统打开它，构造 `BuiltInPackProvider`，注册 `item-tag` 和本模块的订阅者，以 `CREATE_WORLD` 模式启动：原版 26.2 数据包的全部 1355 个 `data/minecraft/loot_table/**.json` 解码成功，`LootManager` 中战利品表的数量和这个数相同；对 `blocks/grass_block`、`entities/zombie`、`gameplay/piglin_bartering`、`chests/simple_dungeon` 各做一次解析，检查池数、条目类型和 `type` 字段取值与原版 JSON 相同。
