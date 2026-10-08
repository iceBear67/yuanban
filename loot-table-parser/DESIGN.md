# loot-table-parser 设计

## 1. 职责

`loot-table-parser` 是 `datapack-loader` 的订阅者。它读取数据包中的战利品表（`data/<ns>/loot_table/**.json`），用 `loot` 模块的 `LootCodecs` 把每个 JSON 文件解码成 `LootTable`；解码中的注册表引用按同一次加载的内容检查：物品标签取自 `item-tag`，附魔和附魔标签取自 `enchant`，结构和结构标签取自本次加载的 `worldgen/structure` 文件；提交阶段把战利品表集合交给 `loot` 的 `LootManager`。

数据类型、编解码器、`LootManager`、引用和递归校验、求值都在 `loot` 模块，见 [loot/DESIGN.md](../loot/DESIGN.md)。本模块只负责数据包这一侧：topic 声明、注册表查找、错误聚合、提交。

原版行为对照 26.2 服务端反编译代码：`SimpleJsonResourceReloadListener`（按 topic 扫描 JSON 文件）、`LootDataType`（loot 资源类型）、`ReloadableServerResources`（加载次序：标签和注册表先于战利品表）、`RegistryOps`（解码时检查注册表引用）、`net.minecraft.world.level.storage.loot` 包中各类型的 `MapCodec`（字段名、缺省值），以及 minecraft.wiki 的 *Loot table* 页面；字段名和取值以服务端 jar 注册的为准。

## 2. 依赖

```groovy
dependencies {
    api(project(":loot"))
    api(project(":item-tag"))
    implementation(project(":dfu-codecs"))
    implementation(project(":enchant"))
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(testFixtures(project(":item-tag")))
    testImplementation(testFixtures(project(":loot")))
}
```

- 编解码器在 `loot` 模块（`io.ib67.yuanban.loot.codec`），本模块不另写编解码器。`dfu-codecs` 提供 `RegistryOps`、`RegistryLookup`、`RegistryGetter`：本模块用它们组装解码时查找注册表的 ops。
- `item-tag` 和 `enchant` 提供本次加载准备好的物品标签、附魔、附魔标签，本模块的订阅声明依赖它们（第 3 节）。
- 构造参数中的 `ServerProcess` 提供 Minestom 的注册表：物品注册表的元素、以及没有在 ops 中单独给出的注册表（方块、药水、效果、伤害类型等）都从这里查找。
- 测试读取的原版服务端由根项目下载，见第 7 节。

## 3. 公开接口

包 `io.ib67.yuanban.lootparser`：

```java
public final class LootTableSubscriber implements Subscriber<LootTableSet> {
    public static final SubscriberKey<LootTableSet> KEY = SubscriberKey.of("yuanban:loot_tables");
    public static final Topic.Single TOPIC = Topic.json("loot_table");
    public static final Topic.Single STRUCTURE_TOPIC = Topic.json("worldgen/structure");
    public static final Topic.Tags STRUCTURE_TAG_TOPIC = Topic.tags("worldgen/structure");

    public LootTableSubscriber(ServerProcess process, LootManager manager);
    public Subscription<LootTableSet> subscription();
    public LootTableSet current();
}

public record LootTableSet(NavigableMap<Key, LootTable> tables) {
    public static final LootTableSet EMPTY;
}

public final class LootException extends DataPackException {}
```

- `subscription()` 返回以这个对象为订阅者、生命周期为 `RELOADABLE` 的订阅：
  - 读取 `TOPIC`、`STRUCTURE_TOPIC`、`STRUCTURE_TAG_TOPIC`；
  - `after(ItemTagSubscriber.KEY)`、`after(EnchantmentSubscriber.KEY)`、`after(EnchantmentTagSubscriber.KEY)`。原版在解码战利品表时检查物品标签、附魔和附魔标签，它们来自同一次加载，和原版先加载标签与注册表、再加载战利品表的次序相同。宿主需要同时注册这三个订阅。
- 构造时用 `manager.types()` 创建 `LootCodecs`，宿主在 `LootTypeRegistry` 中注册的类型同样能从数据包解码。`manager` 也是提交阶段接收新集合的对象。
- `current()` 是最近一次提交的战利品表集合，提交之前是 `LootTableSet.EMPTY`。

用法：

```java
var loot = new LootManager(LootTypeRegistry.vanilla());
var lootTables = new LootTableSubscriber(process, loot);
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(...)
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(new EnchantmentTagSubscriber(process).subscription())
        .subscribe(new EnchantmentSubscriber(process).subscription())
        .subscribe(lootTables.subscription())
        .build();
```

## 4. 解码

战利品表是 `data/<ns>/loot_table/**.json` 中的 JSON 文件，资源 ID 是路径去掉 `loot_table/` 前缀和 `.json` 后缀。每个文件用 `LootCodecs.table()` 解码，各类型的字段、缺省值和取值检查见 [loot/DESIGN.md](../loot/DESIGN.md) 第 4、5 节。

准备阶段为本次加载组装一个 `RegistryOps<JsonElement>`，解码中的注册表引用按下面的来源检查：

| 注册表 | 元素 | 标签 |
|---|---|---|
| `minecraft:item` | Minestom 的全部物品 | `ItemTagSubscriber` 本次展开的物品标签 |
| `minecraft:enchantment` | `EnchantmentSubscriber` 本次加载的附魔 | `EnchantmentTagSubscriber` 本次展开的附魔标签 |
| `minecraft:worldgen/structure` | 本次加载的结构文件的 ID | 本次加载的结构标签声明，内容取声明中直接写出的元素 |
| 其余注册表 | Minestom 的注册表 | Minestom 的标签 |

- 引用的元素不存在时错误和原版相同，例如 `Failed to get element <id>`、`Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: <id>`；引用的标签不存在时为 `Missing tag: '<id>' in '<注册表>'`。
- `tag` 条目的 `name`、`loot_table` 条目和 `set_loot_table` 的表 ID、`reference` 的 `name` 与原版相同，解码时不检查是否存在。表 ID 的存在性在提交阶段由 `LootManager` 校验；`tag` 条目的标签在求值时找不到按空标签处理。
- 结构只用于检查 `exploration_map` 的 `destination` 和位置谓词的 `structures`，本模块不解析结构文件的内容。

## 5. 错误处理与提交

准备阶段解码全部战利品表文件。读取 JSON 失败或解码失败的文件都被收集，全部文件处理完后，如果有失败，按文本排序（等同于按资源 ID 排序）、每行一个，抛出 `LootException`：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/loot_table/foo.json: <错误信息>
```

其中 `[file/pack] data/example/loot_table/foo.json` 是 `Resource.toString()`，和 `item-recipe`、`item-tag` 的格式相同；错误信息是 JSON 解析异常的信息，或者 DataFixerUpper 解码结果的错误信息。整次加载失败（`datapack-loader` 抛出 `LoadFailedException`），`LootManager` 中的集合不变。

提交阶段按顺序执行（在 `datapack-loader` 的提交线程上）：

1. `LootManager.replaceAll(prepared.tables())`：`loot` 第 7 节的校验（引用存在、无递归、复合条目总权重有限）在这里执行，失败抛出 `LootException`，本次 `apply` 失败。
2. `current()` 指向新的 `LootTableSet`。

物品标签、附魔和附魔标签先于本模块提交。

## 6. 与原版行为的差异

1. 原版遇到解码失败的战利品表记录日志并跳过这个文件；本模块抛出异常，整次加载失败。
2. 原版把 `predicate` 和 `item_modifier` 作为独立资源加载并允许 `reference` 互相引用。本模块不加载这两种文件；`reference` 类型只记录 ID，求值时经 `LootEnvironment.findFunction` / `findCondition` 查找。原版 26.2 的原版数据包本身不含这两种文件，对原版数据没有影响。
3. 原版加载阶段为每个 `LootContextUser` 检查上下文参数是否被表的类型覆盖，不匹配的只记录日志；本模块在加载时不做这项检查，取用时按 `loot` 8.4 检查。
4. 原版的递归检查在验证阶段记录日志 "Detected infinite loop in loot tables"（加载仍然继续，取用的时候才抛出 `IllegalStateException`）；本模块在提交阶段检测，任何递归都视为加载失败。
5. 结构标签只读取声明中直接写出的结构，引用其他结构标签的条目不展开；标签是否存在的检查不受影响。
6. 经 `dfu-codecs` 适配的 Minestom 编解码器（文本组件、组件补丁、环境属性取值等）只报告第一个错误，错误文本是 Minestom 的。

## 7. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程（解码需要 `ServerProcess` 的注册表）；`minestom.inside-test=true`。数据包是在 `@TempDir` 中实际写出的目录，经过 `datapack-loader` 的真实加载流程读取，物品标签、附魔和附魔标签由 `ItemTagSubscriber`、`EnchantmentSubscriber`、`EnchantmentTagSubscriber` 在同一次加载中准备（`LootTestWorld.loader`）。每个测试类加 `@ExtendWith(ResetItemTags.class)`，原因和 `item-recipe` 相同。

- 解码（`LootDecodingTest`）：经过真实的加载流程解码，原版 9 种条目、20 种条件、43 种修饰器、8 种数字提供器各至少一个用例，检查字段和缺省值；错误用例断言原版的错误文本：
  - 未知的条目、条件、修饰器、数字提供器类型，包括已经删除的 `minecraft:function` 修饰器；
  - 必填字段缺失（`entries`、`expand`、`entity`、`clock`、`limit`、`append`、`mode`、`op`、`id`、`summands` 等）；
  - `item` 条目使用 `minecraft:air` 或不存在的物品；
  - 物品谓词引用不存在的物品标签；引用本次加载中没有的附魔；引用不存在的世界时钟；`table_bonus` 的 `chances` 为空；
  - `block_state_property` 的属性名不属于该方块；
  - `exploration_map` 的地图装饰类型不存在；
  - `storage` 数字提供器的 NBT 路径无法解析。

  接受用例覆盖：表的未知 `type` 按 `generic` 处理、池的 `entries` 可以为空、`tag` 条目不检查标签、条件列表解码成 `all_of`、修饰器列表解码成 `sequence`、`set_custom_model_data` 的颜色接受 RGB 写法、物品谓词解码成完整的记录。
- 加载失败（`LootLoadErrorTest`）：多个文件出错时异常信息按 ID 排序、每行带有文件位置；`LootManager` 中的集合不变。
- 重载（`LootReloadTest`）：新集合替换旧集合，旧 ID 不再能取到。
- 原版数据（`VanillaLootTest`）：根项目的 `extractVanillaServer` 任务从 Mojang 下载 26.2 服务端，按 SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 校验，取出 `META-INF/versions/26.2/server-26.2.jar`，全部模块使用这同一份；本模块的 `test` 任务依赖它，路径经系统属性 `yuanban.vanilla-server` 交给测试。测试用 zip 文件系统打开它，构造 `BuiltInPackProvider`，注册 `item-tag`、`enchant` 和本模块的订阅者，以 `CREATE_WORLD` 模式启动：
  - jar 中全部 `data/minecraft/loot_table/**.json` 解码成功，`LootManager` 中战利品表的数量和文件数相同；
  - 对 `blocks/grass_block`、`entities/zombie`、`gameplay/piglin_bartering`、`chests/simple_dungeon` 检查池数、条目类型和 `type` 字段取值与原版 JSON 相同；
  - 用 `InMemoryLootEnvironment` 实际取用：`blocks/diamond_ore`（精准采集、普通工具、时运）、`entities/cow`（着火时经熔炼得到熟牛肉）、`chests/simple_dungeon`（`random_sequence` 使两次取用结果相同，物品都来自表中的条目）、`blocks/tall_grass`（`location_check` 读取世界中的方块）、`entities/turtle`（被闪电击中时多掉落碗）。
