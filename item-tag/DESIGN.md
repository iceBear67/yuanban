# item-tag 设计

## 1. 职责

`item-tag` 是 `datapack-loader` 的订阅者。它读取数据包中的物品标签（`data/<ns>/tags/item/**.json`），按原版规则展开成物品列表，提交阶段写入 Minestom 的物品注册表 `Material.staticRegistry()`，并把新标签发给在线玩家。

其他模块（例如 `item-recipe`）在准备阶段通过 `PrepareContext.dependency(ItemTagSubscriber.KEY)` 读取本次加载展开的标签，在提交阶段读取 Minestom 注册表中已经更新的标签。这和原版的安排相同：原版先把新标签放在 `PendingTags` 中交给配方等加载步骤，全部步骤完成之后才生效。

原版行为对照 26.2 服务端反编译代码：`TagLoader`、`TagEntry`、`DependencySorter`、`ReloadableServerResources`。

## 2. 依赖

```groovy
dependencies {
    api(project(":datapack-loader"))
    api("net.minestom:minestom:2026.10.05-26.2")
    implementation("com.google.guava:guava:33.6.0-jre")
    testFixturesApi(platform("org.junit:junit-bom:6.0.0"))
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
}
```

另外应用 Gradle 自带的 `java-test-fixtures` 插件，测试工具 `ResetItemTags` 放在 `testFixtures` 源码集中，见第 7 节。

Guava 提供 `HashMultimap`，用来以和原版相同的遍历顺序复现 `DependencySorter`，版本取原版 26.2 服务端所用的 33.6.0。

## 3. 公开接口

包 `io.ib67.yuanban.itemtag`：

```java
public record ItemTags(NavigableMap<Key, List<Material>> tags) {
    public Optional<List<Material>> find(Key id);
}

public final class ItemTagSubscriber implements Subscriber<ItemTags> {
    public static final SubscriberKey<ItemTags> KEY = SubscriberKey.of("yuanban:item_tags");
    public static final Topic.Tags TOPIC = Topic.tags("item");

    public ItemTagSubscriber(ServerProcess process);
    public static Subscription<ItemTags> subscription(ServerProcess process);
}

public final class ItemTagException extends DataPackException {}
```

- `subscription` 返回生命周期为 `RELOADABLE`、读取 `TOPIC` 的订阅。
- `ItemTags.tags()` 按 `Key` 的自然顺序排列，每个列表保持原版展开得到的顺序。

## 4. 准备阶段

输入是 `datapack-loader` 合并好的 `TagDeclarations`：每个标签的全部条目，按数据包优先级从低到高排列，`replace` 已经处理过，每个条目带有所属数据包。

### 4.1 展开顺序

标签之间可以互相引用，原版用 `DependencySorter` 决定展开顺序。可选引用和成环的引用会让结果依赖遍历顺序，所以这里复现原版的遍历顺序：

1. 原版 `TagLoader.load` 按文件位置的顺序把标签放入 `HashMap`（文件位置先比较路径 `tags/item/<路径>.json`，再比较命名空间），`build` 再按这个 `HashMap` 的遍历顺序放入 `DependencySorter` 内部的 `HashMap`。这里用同样的步骤构造两个 `HashMap`。键类型 `TagId` 包装 `Key`，`hashCode()` 为 `31 * namespace.hashCode() + value.hashCode()`，和原版 `Identifier` 相同。
2. 依赖关系存放在 Guava `HashMultimap` 中。先加入全部 `required` 的标签引用，再加入全部可选的标签引用；加入一条边 `A → B` 之前检查从 `B` 出发能否沿已有的边回到 `A`，能回到就不加入这条边。
3. 按第二个 `HashMap` 的遍历顺序深度优先访问：先访问依赖，再展开自己；每个标签只访问一次；引用了不存在的标签的时候跳过这个依赖。

### 4.2 展开一个标签

条目依次处理，结果放入 `LinkedHashSet`，重复的物品只保留第一次出现的位置。

| 条目 | 找到 | 找不到 |
|---|---|---|
| 物品 `id` | 用 `Material.fromKey` 查找，加入结果 | `required` 记为缺失；否则跳过 |
| 标签 `#id` | 在已经展开的标签中查找，按顺序加入它的全部物品 | `required` 记为缺失；否则跳过 |

有缺失条目的标签不放入结果。依赖它的标签在查找的时候找不到它，按上表继续处理。

### 4.3 错误

全部标签处理完之后，只要有标签存在缺失条目，就抛出 `ItemTagException`。异常信息每行一个标签，格式和原版日志相同：

```
Couldn't load tag minecraft:foo as it is missing following references: minecraft:bar (from file/pack), #minecraft:baz (from vanilla)
```

原版只记录日志并丢弃这些标签，这里整次加载失败（`datapack-loader` 抛出 `LoadFailedException`，游戏状态不变）。

## 5. 提交阶段

1. 对结果中的每个标签，用 `Material.staticRegistry().getOrCreateTag(TagKey.of(id))` 取得 Minestom 的标签对象。现有条目（按迭代顺序）和新列表不同的时候，移除全部旧条目，再按顺序加入新条目。同一个标签保留同一个对象，其他代码之前取得的引用会看到新内容。
2. 注册表中存在、结果中没有的标签：先移除全部条目，再 `removeTag`。之前取得这个对象的代码看到的是空标签。Minestom 启动时自带的标签同样按这条规则处理，后果见 5.1。
3. 调用一次 `ConnectionManager.invalidateTags()`，让缓存的标签数据包失效，然后对每个在线玩家调用 `ConnectionManager.sendRegistryTags(player)`，和原版 `/reload` 发送 `ClientboundUpdateTagsPacket` 相对应。在线玩家和之后登录的新玩家收到的都是这个缓存的数据包。

   `Backed.add/remove` 每次增删条目都会让缓存失效，但 `getOrCreateTag` 和 `removeTag` 不会。如果一次提交只删除了空标签或者新建了空标签，就没有任何条目增删，缓存不会失效，玩家收到的仍然是旧的标签列表。所以这里在全部修改完成之后显式调用一次。`invalidateTags()` 在 Minestom 中标注为 `@ApiStatus.Internal`：

   ```java
   // FIXME(Minestom M9): Registry.removeTag 和 getOrCreateTag 不会让缓存的标签数据包失效，见 MINESTOM-BUG-REPORT.md
   ```

增删条目使用的 `RegistryTagImpl.Backed.add/remove` 只在包内可见，由放在 `net.minestom.server.registry` 包中的 `TagContentWriter` 调用：

```java
// FIXME(Minestom M5): Minestom 没有修改静态注册表标签内容的公开接口，见 MINESTOM-BUG-REPORT.md
```

这要求 Minestom 位于 classpath 上（未命名模块中允许不同 jar 含有同名包）。

提交期间其他线程读取标签会看到更新到一半的内容：Minestom 的标签对象本身可以修改，框架无法让替换一次完成。宿主应当在不会并发读取标签的时机调用 `start` / `reload`。

### 5.1 删除 Minestom 自带的标签

`Material.staticRegistry()` 是静态字段，同一个 JVM 中只有一份，不属于某个 `ServerProcess`。Minestom 启动时从自带数据填入一份原版物品标签；原版服务端的物品标签只来自数据包。所以提交阶段把数据包中没有的标签全部删除，包括 Minestom 自带的标签。

启用了原版数据包的时候（正常情况），数据包提供的标签包含 Minestom 自带的全部标签，内容和顺序也相同（原版数据测试验证了这一点），提交阶段不会删除任何自带标签，所有标签对象保持不变。

数据包中缺少某个自带标签的时候（例如没有启用 `vanilla` 数据包，或者某个数据包用 `filter` 屏蔽了 `tags/item` 下的文件），这个标签被删除，产生以下后果：

1. Minestom 在创建 `ServerProcess` 时解码的数据直接持有标签对象，例如附魔的 `supported_items` 和 `primary_items`（`RegistryTag` 的编解码器返回注册表中的标签对象本身）。标签被删除之前先清空了条目，这些数据此后看到的是空标签，例如附魔不再适用于任何物品。
2. 之后的重载中数据包重新提供这个标签的时候，`getOrCreateTag` 创建一个新的标签对象。第 1 条中的数据仍然持有被删除的旧对象，看到的仍然是空标签，直到进程重新启动。
3. 同一个 JVM 中之后新建的 `ServerProcess` 解码附魔注册表时找不到被删除的标签，创建失败：

   ```
   Failed to decode registry entry minecraft:vanishing_curse for registry minecraft:enchantment:
   supported_items: Unknown tag minecraft:enchantable/vanishing for registry minecraft:item
   ```

   正式运行的服务器只创建一个 `ServerProcess`，不受这一条影响；测试受影响，处理方法见第 7 节。

## 6. 与原版行为的差异

1. 原版遇到缺失的必需引用，记录日志并丢弃对应的标签；这里抛出异常。
2. 原版 `minecraft:item` 注册表的标签只来自数据包；Minestom 启动时自带一份原版标签。这里在提交阶段删除数据包中没有的标签，提交之后两者一致。原版在创建世界时按数据包解码附魔等注册表，标签缺失会直接报错；Minestom 在加载数据包之前已经解码完这些注册表，标签被删除之后它们看到空标签，见 5.1。

## 7. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程，客户端连接使用它提供的进程内 `TestConnection`，测试任务设置系统属性 `minestom.inside-test=true`。数据包是在 `@TempDir` 中实际写出的目录，经过 `datapack-loader` 的真实加载流程读取。

测试数据包只提供少量标签，提交阶段会删除 Minestom 自带的物品标签，同一个 JVM 中之后新建的 `ServerProcess` 无法创建（5.1 第 3 条），而 `@EnvTest` 为每个测试方法新建一个 `ServerProcess`。所以每个测试类都加上 `@ExtendWith(ResetItemTags.class)`，在每个测试方法开始之前把物品标签恢复成 Minestom 自带的标签：

- `ResetItemTags` 放在本模块的 `testFixtures` 源码集中（Gradle `java-test-fixtures` 插件），`item-recipe` 的测试通过 `testFixtures(project(":item-tag"))` 使用它。
- 它只替换标签，保留物品注册表和其中的物品对象。重新创建整个注册表会得到新的 `MaterialImpl`，它的相等判断依赖没有重写 `equals` 的 `RegistryData.MaterialEntry`，和 `Material.STONE` 等常量不相等。
- 注册表内部保存标签的字段是 `final`，但字段中的 `ConcurrentHashMap` 可以修改。`ResetItemTags` 用反射取得这个 `Map`，清空之后放入 `RegistryData.loadTags`（Minestom 启动时创建自带标签所用的方法，包内可见，同样用反射调用）重新生成的标签。
- `@EnvTest` 在解析测试方法的 `Env` 参数时才新建 `ServerProcess`，这一步在全部 `BeforeEachCallback` 之后，所以恢复一定先于进程创建。

全部测试类在同一个 JVM 中运行。

- 展开：追加和 `replace` 的顺序、重复物品去重、引用其他标签、可选元素缺失被跳过、可选标签引用缺失被跳过。
- 错误：必需元素缺失、必需标签缺失、必需引用成环，异常信息列出全部缺失条目及来源数据包；依赖失败标签的标签也被列出。
- 可选引用成环：结果和按原版遍历顺序计算的结果相同。
- 提交：Minestom 注册表中的标签内容和顺序正确；数据包中没有的标签被删除；已有标签保持同一个对象；重载之后在线玩家收到新的 `TagsPacket`。
- 空标签：缓存的标签数据包已经生成之后，一次只删除空标签的重载、一次只新建空标签的重载，玩家收到的 `TagsPacket` 都反映了变化（`EmptyTagPacketTest`）。
