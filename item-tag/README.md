# item-tag

`item-tag`（库）是 [datapack-loader](../datapack-loader/DESIGN.md) 的订阅者。它从数据包读取物品标签（`data/<namespace>/tags/item/**.json`），按原版 26.2 的规则展开成物品列表，写入 Minestom 的物品注册表 `Material.staticRegistry()`，并把新标签发给在线玩家。每次 `/reload` 都会重新执行。

实现细节和与原版的差异见 [DESIGN.md](DESIGN.md)。

## 注册

```java
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
        .provider(new FolderPackProvider(worldDir.resolve("datapacks"), PackOrigin.WORLD, symlinkAllowList))
        .subscribe(ItemTagSubscriber.subscription(process))
        .executor(ForkJoinPool.commonPool())
        .build();
DataPackSession session = loader.start(selectionRequest);
```

`process` 是 `MinecraftServer.process()` 返回的 `ServerProcess`。`subscription` 返回的订阅：

| 项目 | 取值 |
|---|---|
| 订阅者名字 | `ItemTagSubscriber.KEY`（`yuanban:item_tags`） |
| 生命周期 | `RELOADABLE`，启动和每次重载都执行 |
| 读取的 topic | `ItemTagSubscriber.TOPIC`，即 `Topic.tags("item")` |

## 在其他模块中使用物品标签

物品标签有两个读取位置，取决于读取发生在什么时候。

### 加载期间：读取准备结果

其他订阅者在 `after` 中声明依赖 `ItemTagSubscriber.KEY`，就能在自己的 `prepare` 中读到本次加载展开的标签。这时新标签还没有写进 Minestom，Minestom 注册表中仍然是上一次的标签，所以加载期间要从准备结果读取。

```java
static final SubscriberKey<FuelTable> FUELS = SubscriberKey.of("example:fuels");

final class FuelSubscriber implements Subscriber<FuelTable> {
    @Override
    public FuelTable prepare(PrepareContext context) {
        ItemTags tags = context.dependency(ItemTagSubscriber.KEY);
        List<Material> logs = tags.find(Key.key("minecraft:logs")).orElseThrow();
        return FuelTable.of(logs);
    }

    @Override
    public void apply(FuelTable prepared) {
        // 替换本模块持有的引用
    }
}

loaderBuilder
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(Subscription.builder(FUELS, new FuelSubscriber())
                .lifecycle(Lifecycle.RELOADABLE)
                .after(ItemTagSubscriber.KEY)
                .build());
```

`ItemTags`：

```java
public record ItemTags(NavigableMap<Key, List<Material>> tags) {
    public Optional<List<Material>> find(Key id);
}
```

- `tags()` 按标签 ID 的自然顺序排列，内容不可修改。
- 每个列表已经展开了对其他标签的引用，顺序和原版相同，不含重复的物品。

`after` 同时保证 `item-tag` 的 `apply` 先于依赖者的 `apply` 执行。所以在依赖者的 `apply` 中，Minestom 注册表里已经是新标签。

### 游戏运行期间：读取 Minestom 注册表

提交之后，标签已经写入 Minestom，直接使用 Minestom 的接口：

```java
RegistryTag<Material> logs = Material.staticRegistry().getTag(TagKey.of("minecraft:logs"));
boolean isLog = logs != null && logs.contains(Material.OAK_LOG);
```

数据包中仍然存在的标签，重载前后是同一个 `RegistryTag` 对象，持有它的代码会看到新内容。数据包中已经不存在的标签会被清空并从注册表中删除；如果之后的重载中它又出现，注册表中放的是一个新对象。所以需要跨越重载使用标签时，每次按标签 ID 重新查找，不要长期持有 `RegistryTag` 对象（详见 [DESIGN.md](DESIGN.md) 5.1）。

## 错误

标签引用了不存在的物品或标签（`required` 为默认的 `true`）时，准备阶段抛出 `ItemTagException`，整次加载失败（`datapack-loader` 抛出 `LoadFailedException`，它的 cause 就是这个异常），游戏状态不变。异常信息每行一个标签，格式和原版日志相同：

```
Couldn't load tag minecraft:foo as it is missing following references: minecraft:bar (from file/pack)
```

原版只记录日志并丢弃这些标签。

## 使用限制

- **数据包中没有的标签会被删除，包括 Minestom 启动时自带的标签。** 启用了 `vanilla` 数据包时，自带标签都在数据包中，不会被删除。没有启用 `vanilla` 数据包，或者某个数据包用 `filter` 屏蔽了原版物品标签时，附魔等在进程启动时解码的数据会看到空标签。详见 [DESIGN.md](DESIGN.md) 5.1。
- **Minestom 必须位于 classpath 上**（Gradle 默认的运行方式）。写入标签用到了 Minestom 的包内方法，见根目录 [README.md](../README.md) 中的 M5、M9。
- **提交时标签逐个修改。** 提交期间其他线程读取标签会看到更新到一半的内容，宿主应当在不会并发读取标签的时机调用 `start` / `reload`。

## 测试工具

物品注册表是 JVM 内全局的状态，提交阶段会删除 Minestom 自带的标签，之后同一个 JVM 中新建的 `ServerProcess` 无法创建。本模块的 `testFixtures` 源码集提供 JUnit 扩展 `io.ib67.yuanban.itemtag.testing.ResetItemTags`，它在每个测试方法开始之前把物品标签恢复成 Minestom 自带的标签。

```groovy
dependencies {
    testImplementation(testFixtures(project(":item-tag")))
}
```

```java
@EnvTest
@ExtendWith(ResetItemTags.class)
class MyTest { ... }
```
