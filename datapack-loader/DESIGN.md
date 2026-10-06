# datapack-loader 设计

## 1. 职责

`datapack-loader` 把磁盘上的 Minecraft 数据包整理成按优先级叠加好的文件集合，再把文件交给订阅了相应 topic 的模块。框架负责的工作：

- 发现数据包：原版数据、原版自带的特性数据包、世界 `datapacks/` 目录下的目录数据包和 zip 数据包，以及宿主额外注册的数据包提供者给出的数据包。
- 解析 `pack.mcmeta`：格式版本范围、特性开关、`filter`、`overlays`。
- 计算启用列表和叠加次序，语义和原版 `level.dat` 中的 `DataPacks.Enabled` / `DataPacks.Disabled` 相同，并处理 `/reload`、`/datapack enable|disable` 引起的次序变化。
- 叠加数据包内容：overlay 目录、`filter` 屏蔽、同名文件覆盖、标签（tag）文件合并。
- 调度订阅者：并行执行解析，按依赖顺序提交结果；重载失败的时候保留旧数据。

框架不解释游戏内容。配方、战利品表、世界生成等文件的含义由订阅者决定，订阅者也负责把结果填入 Minestom。框架自己只解析 `pack.mcmeta` 和标签文件这两类文件，因为叠加规则直接取决于它们的内容。

本文描述的原版行为都对照了 26.2 服务端的反编译代码（26.1 起官方发布的 jar 不再混淆），涉及的类有 `PackRepository`、`Pack`、`FolderRepositorySource`、`ServerPacksSource`、`PackFormat`、`OverlayMetadataSection`、`CompositePackResources`、`MultiPackResourceManager`、`FallbackResourceManager`、`IdentifierPattern`、`TagLoader`、`MinecraftServer.configurePackRepository`、`ReloadCommand`、`DataPackCommand`。

## 2. 支持的版本

支持 Java 版 26.2 及以上，26.2 的数据包格式是 107.1。格式版本只按 `min_format` / `max_format` 读取（25w31a 引入的规则）。为旧版游戏准备的 `pack_format`、`supported_formats` 以及 overlay 条目里的 `formats` 不读取。

当前游戏的数据包格式和已知特性开关由宿主通过 `LoaderSettings` 传入。`LoaderSettings` 构造的时候检查格式不低于 107.1，低于这个值抛出异常。`LoaderSettings.MINECRAFT_26_2` 给出 26.2 的取值：

| 字段 | 26.2 的取值 |
|---|---|
| `dataFormat` | `107.1` |
| `featureFlags` | `minecraft:vanilla`、`minecraft:trade_rebalance`、`minecraft:redstone_experiments`、`minecraft:minecart_improvements` |
| `defaultPackId` | `vanilla` |

## 3. 术语

| 术语 | 含义 |
|---|---|
| 数据包（pack） | 根目录含有 `pack.mcmeta` 的目录或 zip 文件，内容位于 `data/<namespace>/` 之下。 |
| 数据包 ID | 数据包在仓库中的唯一名字，例如 `vanilla`、`trade_rebalance`、`file/foo.zip`。 |
| overlay 目录 | `pack.mcmeta` 的 `overlays.entries` 列出的子目录，内部结构和数据包根目录相同，格式范围包含当前格式的时候叠加在根目录内容之上。 |
| 文件位置 | 命名空间加上命名空间目录下的相对路径，例如 `minecraft:recipe/stick.json`。`filter` 匹配的对象就是文件位置。 |
| topic | 命名空间目录下的一个子目录加一个扩展名，例如 `recipe` + `.json`、`worldgen/biome` + `.json`、`function` + `.mcfunction`。目录可以有多级。 |
| 资源 ID | 文件在某个 topic 中的 ID：去掉 topic 目录前缀和扩展名之后的路径。`data/minecraft/recipe/stick.json` 在 topic `recipe` 中的资源 ID 是 `minecraft:stick`。 |
| 订阅者（subscriber） | 模块注册到框架的处理对象。它声明要读取的 topic、依赖的其他订阅者、生命周期，并实现准备和提交两个阶段。 |
| 批次 | 一次成功加载所打开的全部数据包文件系统以及它们的索引。重载成功之后，新批次替换旧批次。 |

## 4. 模块结构与依赖

`datapack-loader/build.gradle`：

```groovy
dependencies {
    api("net.kyori:adventure-key:5.2.0")
    api("com.google.code.gson:gson:2.14.0")
}
```

- `adventure-key` 提供 `net.kyori.adventure.key.Key`，作为全部命名空间 ID 的类型。它的字符规则（命名空间 `[a-z0-9_.-]`，路径 `[a-z0-9_.-/]`）和排序规则（先比较路径，再比较命名空间）都和原版 `Identifier` 相同。Minestom 的注册表也使用这个类型，订阅者拿到的 ID 可以直接交给 Minestom。版本取 Minestom `2026.10.05-26.2` 所用的 Adventure 5.2.0。
- Gson 解析 JSON，版本取 Minestom 所用的 2.14.0。
- zip 数据包用 JDK 自带的 zip 文件系统（`jdk.zipfs`）打开。目录数据包、zip 数据包、jar 内部的目录在框架内部都表示为 `java.nio.file.Path`，读取逻辑只有一套。
- JetBrains annotations 和 Lombok 由根项目提供。公开 API 用 `@NotNull` / `@Nullable` 标注；内部实现放在 `internal` 包，标注 `@ApiStatus.Internal`。数据类型使用 record。
- 框架不输出日志。需要告知宿主的信息（例如自动启用了哪些数据包）放在返回值中，由宿主决定如何记录。

包划分，根包是 `io.ib67.yuanban.datapack`：

| 包 | 内容 |
|---|---|
| `datapack` | `DataPackLoader`、`DataPackSession`、`LoaderSettings`、`ReloadRequest`、异常类型 |
| `datapack.pack` | `PackFormat`、`FormatRange`、`PackCompatibility`、`PackMetadata`、`OverlayEntry`、`ResourceFilter`、`FilterRule`、`Pack`、`PackInfo`、`PackOrigin`、`KnownPack`、`PackSelectionConfig`、`PackPosition`、`PackRoot` |
| `datapack.provider` | `PackProvider`、`BuiltInPackProvider`、`FolderPackProvider` |
| `datapack.selection` | `DataPackConfig`、`SelectionRequest`、`SelectionMode`、`Selection`、`SelectionReport`、`SelectionEditor`、`Placement` |
| `datapack.topic` | `Topic`、`TopicFiles`、`TopicLayers`、`Resource` |
| `datapack.tag` | `TagEntry`、`TagValue`、`TagDeclaration`、`TagDeclarations` |
| `datapack.subscribe` | `Subscriber`、`SubscriberKey`、`Subscription`、`Lifecycle`、`PrepareContext`、`LoadKind` |
| `datapack.internal` | `pack.mcmeta` 解析、索引、视图构建、选择算法、调度器 |

## 5. 数据包的发现

### 5.1 PackProvider

```java
public interface PackProvider {
    void discover(LoaderSettings settings, Consumer<Pack> output);
}
```

`DataPackLoader` 持有一组提供者。需要刷新可用数据包的时候（启动、`/reload`），框架依次调用全部提供者，把结果放入按数据包 ID 排序的 `TreeMap`。两个数据包 ID 相同就抛出异常。

发现阶段只读取 `pack.mcmeta`，读完立即关闭 zip 文件系统；数据包内容在加载阶段重新打开。

### 5.2 Pack

```java
public final class Pack {
    public String id();
    public PackOrigin origin();
    public Optional<KnownPack> knownPack();
    public PackSelectionConfig selectionConfig();
    public PackMetadata metadata();
    public PackCompatibility compatibility();   // 由格式范围和当前格式计算，见第 6 节
    public List<String> activeOverlays();       // 当前格式下生效的 overlay 目录，保持 pack.mcmeta 中的顺序
    public PackRoot root();
    public PackInfo info();                     // id、origin、knownPack 三项
}

public record PackOrigin(String name, boolean autoEnable) {
    public static final PackOrigin BUILT_IN = new PackOrigin("built_in", true);
    public static final PackOrigin FEATURE = new PackOrigin("feature", false);
    public static final PackOrigin WORLD = new PackOrigin("world", true);
}

public record PackSelectionConfig(boolean required, PackPosition defaultPosition, boolean fixedPosition) {}

public enum PackPosition { TOP, BOTTOM }

public record KnownPack(String namespace, String id, String version) {}

public record PackInfo(String id, PackOrigin origin, Optional<KnownPack> knownPack) {}

public sealed interface PackRoot {
    record Directory(Path path) implements PackRoot {}   // 默认文件系统中的目录
    record Zip(Path file) implements PackRoot {}         // 默认文件系统中的 zip 文件
    record Embedded(Path path) implements PackRoot {}    // 宿主已经打开的文件系统中的目录，例如原版 jar 内部
}
```

- `PackOrigin.autoEnable` 对应原版 `PackSource.shouldAddAutomatically`：这类数据包第一次被发现的时候自动加入启用列表。`FEATURE` 另外用于两条规则：世界已启用的特性缺少提供它的数据包的时候，框架从 `FEATURE` 类别中挑选数据包强制启用；声明了特性的 `FEATURE` 数据包不能被禁用。宿主可以用 record 构造器定义自己的类别。
- `PackSelectionConfig` 对应原版同名类型。`required` 的数据包总会出现在启用列表中；`fixedPosition` 的数据包在插入其他数据包的时候保持在自己默认位置那一端。原版 26.2 中没有 `required` 或 `fixedPosition` 的数据包，这两项留给宿主自己的数据包使用。
- `KnownPack` 对应原版同名类型。客户端在配置阶段告诉服务端自己拥有哪些已知数据包，服务端同步注册表的时候，如果某个条目所属的数据包是客户端已有的已知数据包，就可以省略条目内容。订阅者通过 `Resource.pack().knownPack()` 拿到条目所属数据包的这项信息；宿主在配置阶段发送的已知数据包列表取自 `Selection.packs()` 中各数据包的 `knownPack()`。
- 打开 `PackRoot.Zip` 会新建 zip 文件系统，由所在批次负责关闭。`Directory` 和 `Embedded` 不需要关闭，`Embedded` 所在的文件系统归宿主所有。

### 5.3 BuiltInPackProvider：原版数据

构造参数：原版数据根目录（`Path`，通常是宿主用 zip 文件系统打开的 26.2 服务端 jar 的根目录，其中包含 `data/minecraft/...`）和版本名（`"26.2"`）。

给出的数据包：

1. `vanilla`：根目录就是原版数据根目录。原版 jar 中没有 `pack.mcmeta`，元数据由代码构造，内容和原版 `ServerPacksSource` 相同：描述 `{"translate":"dataPack.vanilla.description"}`，格式范围 `[当前格式, 当前主版本.任意次版本]`，特性 `minecraft:vanilla`，没有 filter 和 overlay。类别 `BUILT_IN`，选择配置 `(required=false, BOTTOM, fixedPosition=false)`，已知数据包 `minecraft:core:<版本名>`。
2. 内置特性数据包：扫描 `<根目录>/data/minecraft/datapacks/` 下的每个条目，判断规则和 5.4 相同。数据包 ID 是条目名，zip 去掉 `.zip` 后缀。26.2 中有 `minecart_improvements`、`redstone_experiments`、`trade_rebalance` 三个。类别 `FEATURE`，选择配置 `(false, TOP, false)`，已知数据包 `minecraft:<数据包 ID>:<版本名>`。根目录位于非默认文件系统中，这里遇到 zip 条目没有办法打开，直接抛出异常。

### 5.4 FolderPackProvider：世界数据包目录

构造参数：目录路径（通常是 `<世界目录>/datapacks`）、类别（`PackOrigin.WORLD`）、符号链接白名单（`PathMatcher`，宿主按原版 `allowed_symlinks.txt` 的格式读取之后传入；不允许任何符号链接的时候传入总是返回 `false` 的匹配器）。

目录不存在的时候创建它（和原版相同）。对目录下的每个条目：

- 条目本身是符号链接：链接目标必须匹配白名单，否则抛出异常；之后按链接目标继续判断。
- 目录：含有 `pack.mcmeta` 普通文件就是数据包，并且目录内部的每个符号链接（递归检查）都必须匹配白名单，否则抛出异常。目录中没有 `pack.mcmeta` 的时候跳过这个目录（原版同样跳过）。
- 文件名以 `.zip` 结尾的普通文件是数据包，zip 里面必须有 `pack.mcmeta`，没有就抛出异常。
- 其他文件跳过。

数据包 ID 是 `file/<条目名>`（zip 保留 `.zip` 后缀），选择配置 `(false, TOP, false)`，没有已知数据包信息。

## 6. pack.mcmeta

框架中所有 JSON（`pack.mcmeta`、标签文件、`Resource.readJson()`）都用 Gson 的 `Strictness.STRICT` 解析，并且要求读完整个文档，规则和原版 `StrictJsonParser` 相同。框架读取下表中的字段，其他字段和其他顶层段落忽略。

| 字段 | 规则 |
|---|---|
| `pack` | 必需，对象。 |
| `pack.description` | 必需，文本组件（字符串、对象或数组）。以 `JsonElement` 原样保存，由宿主转换，例如交给 Adventure 的 Gson 序列化器。 |
| `pack.min_format`、`pack.max_format` | 两个都必需。取值是非负整数 `N`，或者长度 1 到 256 的非负整数数组（只使用前两个元素，和原版编解码器相同）。`min_format` 中 `N` 和 `[N]` 表示 `N.0`；`max_format` 中 `N` 和 `[N]` 表示主版本 `N` 下的任意次版本；`[N, M]` 表示 `N.M`。`min` 大于 `max` 的时候抛出异常。 |
| `features.enabled` | `features` 存在的时候必需，命名空间 ID 列表。每一项都必须是 `LoaderSettings.featureFlags` 中登记过的特性开关，否则抛出异常。 |
| `filter.block` | `filter` 存在的时候必需，列表。每一项是对象，可以有 `namespace`、`path` 两个字段，值是 Java 正则表达式；编译失败抛出异常。 |
| `overlays.entries` | `overlays` 存在的时候必需，列表。每一项必需 `directory`（匹配 `[-_a-zA-Z0-9.]+`，和原版代码相同）、`min_format`、`max_format`（规则同上）。 |

```java
public record PackFormat(int major, int minor) implements Comparable<PackFormat> {}
public record FormatRange(PackFormat min, PackFormat max) {}
public enum PackCompatibility { TOO_OLD, TOO_NEW, COMPATIBLE }
public record PackMetadata(JsonElement description, FormatRange formats, Set<Key> features,
                           ResourceFilter filter, List<OverlayEntry> overlays) {}
public record OverlayEntry(String directory, FormatRange formats) {}
public record FilterRule(@Nullable Pattern namespace, @Nullable Pattern path) {}
public record ResourceFilter(List<FilterRule> block) {}
```

兼容性：`max` 小于当前格式是 `TOO_OLD`，当前格式小于 `min` 是 `TOO_NEW`，其余是 `COMPATIBLE`。和原版相同，兼容性只作为信息提供，不兼容的数据包仍然可以启用和加载。

生效的 overlay：格式范围包含当前格式的条目，保持 `entries` 中的顺序。列表中越靠后的 overlay 优先级越高，所有 overlay 的优先级都高于数据包根目录。overlay 只能替换文件，没有办法删除根目录中的文件。`entries` 列出的目录在数据包中不存在的时候，这个 overlay 没有任何文件（zip 不一定为目录保存条目，所以 zip 中的目录只由文件路径的前缀确定）。overlay 目录中的 `pack.mcmeta` 和 `pack.png` 没有作用。

## 7. 启用与加载次序

### 7.1 次序的含义

启用列表是有序列表，越靠前的数据包优先级越低，越靠后的优先级越高，顺序和原版 `/datapack list` 以及 `level.dat` 中 `Enabled` 的顺序相同。同一个文件位置出现在多个数据包中的时候，以优先级最高的数据包为准；标签文件按第 9 节的规则合并。

### 7.2 输入

```java
public record DataPackConfig(List<String> enabled, List<String> disabled) {}

public enum SelectionMode { CREATE_WORLD, LOAD_WORLD }

public record SelectionRequest(DataPackConfig config,
                               Set<Key> worldFeatures,    // LOAD_WORLD 使用；CREATE_WORLD 忽略
                               SelectionMode mode,
                               boolean safeMode) {}
```

`DataPackConfig` 和世界启用的特性由宿主持久化，原版存放在 `level.dat`。新建世界的时候，宿主用 `server.properties` 中的 `initial-enabled-packs`（默认 `vanilla`）和 `initial-disabled-packs`（默认为空）构造 `DataPackConfig`，模式是 `CREATE_WORLD`。打开已有世界的时候，宿主从存档读出 `DataPackConfig` 和特性，模式是 `LOAD_WORLD`。`safeMode` 对应原版的 `--safeMode` 启动参数。

### 7.3 选择算法

和原版 `MinecraftServer.configurePackRepository` 相同。`available` 是按数据包 ID 排序的全部可用数据包，下文的"按 ID 顺序遍历"都指这个顺序。

1. 计算两个特性集合：
   - `forced`：`CREATE_WORLD` 下是空集，`LOAD_WORLD` 下是 `worldFeatures`。
   - `allowed`：`CREATE_WORLD` 下是 `LoaderSettings.featureFlags` 的全部特性，`LOAD_WORLD` 下是 `worldFeatures`。
2. 安全模式：`ids = [defaultPackId]`，直接执行第 6 步，并且第 8 步的禁用列表为空。
3. `ids` 是有序集合。按 `config.enabled` 的顺序加入存在于 `available` 中的 ID；不存在的 ID 记入报告的 `missing`。
4. 按 ID 顺序遍历 `available` 中不在 `config.disabled` 里的数据包：
   - 不在 `ids` 中并且类别可以自动启用：所需特性是 `allowed` 的子集，就追加到 `ids` 末尾并记入 `autoEnabled`；否则记入 `skippedForFeatures`。
   - 在 `ids` 中并且所需特性不是 `allowed` 的子集：从 `ids` 中移除，记入 `removedForFeatures`。
5. `ids` 为空的时候加入 `defaultPackId`。默认数据包不可用就抛出异常。
6. 重建列表：把 `ids` 依次换成数据包；再按 ID 顺序检查 `available` 中 `required` 为真并且不在列表里的数据包，按它的默认位置插入：
   - `BOTTOM`：插在列表开头连续的"固定在底部"的数据包之后；
   - `TOP`：插在列表末尾连续的"固定在顶部"的数据包之前。
7. 强制启用特性数据包：`missing = forced − 列表中全部数据包所需特性的并集`。`missing` 不为空的时候，按 ID 顺序遍历 `available` 中类别是 `FEATURE` 的数据包，直到 `missing` 为空：所需特性不为空、和 `missing` 有交集、并且是 `forced` 的子集，就追加到列表末尾，记入 `forcedFeaturePacks`，`missing` 减去它的特性。之后再执行一次第 6 步。
8. 结果：

```java
public record Selection(List<Pack> packs,           // 最终列表，优先级从低到高
                        Set<Key> enabledFeatures,   // 列表中全部数据包所需特性的并集，再并上 forced
                        DataPackConfig config,      // enabled：最终列表的 ID；disabled：available 中不在列表里的 ID
                        SelectionReport report) {}

public record SelectionReport(List<String> missing, List<String> autoEnabled, List<String> skippedForFeatures,
                              List<String> removedForFeatures, List<String> forcedFeaturePacks) {}
```

`missing`、特性不满足这几种情况来自世界配置记录的数据包和磁盘上现有的数据包之间的差异，原版按上面的规则调整列表之后继续运行，这里保持相同的行为，每一项调整都记入 `SelectionReport`，由宿主输出。删除一个数据包目录之后，世界仍然可以正常打开。

### 7.4 重载期间的次序

会话期间世界的特性保持不变。重载请求有两种：

```java
public sealed interface ReloadRequest {
    record Rescan() implements ReloadRequest {}                     // 对应 /reload
    record Select(List<String> ids) implements ReloadRequest {}     // 对应 /datapack enable|disable
}
```

- `Rescan`：重新运行全部提供者得到新的 `available`，然后以 `DataPackConfig(当前启用列表, 当前禁用列表)`、`LOAD_WORLD`、当前特性执行 7.3 的算法。效果是：新出现的、可以自动启用的、特性满足的、并且没有被禁用的数据包按 ID 顺序追加到最高优先级；已经从磁盘消失的数据包从列表中去掉。
- `Select`：不重新发现数据包，按给定顺序加载。每个 ID 都必须在 `available` 中、没有重复、所需特性都已启用、`required` 数据包都在列表中，任何一项不满足就抛出 `SelectionException`。成功之后 `config.disabled` 是 `available` 中不在列表里的 ID。

### 7.5 SelectionEditor

`SelectionEditor` 为 `/datapack enable|disable` 计算新的列表，校验规则来自原版 `DataPackCommand`：

```java
public final class SelectionEditor {
    public List<String> enable(String id, Placement placement);
    public List<String> disable(String id);
}

public sealed interface Placement {
    record DefaultPosition() implements Placement {}        // 按数据包的默认位置插入，规则同 7.3 第 6 步
    record First() implements Placement {}                  // 优先级最低
    record Last() implements Placement {}                   // 优先级最高
    record Before(String existing) implements Placement {}
    record After(String existing) implements Placement {}
}
```

- `enable`：数据包必须可用、还没有启用、所需特性都已启用；`Before` / `After` 引用的数据包必须已经启用。
- `disable`：数据包必须已经启用；类别是 `FEATURE` 并且声明了特性的数据包不能禁用；`required` 数据包不能禁用。
- 违反任意一条抛出 `SelectionException`，宿主把异常信息转成命令的错误提示。

返回的列表交给 `session.reload(new ReloadRequest.Select(...))`。

## 8. 资源索引与 topic 视图

### 8.1 打开与索引

加载开始之后，框架按选择结果打开每个数据包，并为每个数据包建立一份索引：

1. 先遍历根目录下的 `data/`，再按顺序遍历每个生效 overlay 的 `<overlay>/data/`，把文件逐个放入 `Map<命名空间, NavigableMap<相对路径, 索引项>>`。后遍历的覆盖先遍历的，所以同一个路径保留优先级最高的那一份。索引项记录文件的 `Path` 和所在的 overlay 目录（根目录记为 `null`）。
2. `data/` 下的每个直接子项都必须是目录，并且目录名是合法的命名空间，否则抛出异常。
3. 相对路径由 `Path` 的各级名字用 `/` 拼接得到，和操作系统的路径分隔符无关。
4. 遍历不跟随符号链接，符号链接本身也不进入索引，和原版列出资源的行为相同。

每个数据包只遍历一次文件系统。之后所有 topic 的查询都是对内存中的 `NavigableMap` 做前缀范围查询：`subMap(dir + "/", true, dir + "0", false)`，`'0'` 在 ASCII 中紧跟在 `'/'` 之后。各数据包的索引在执行器上并行建立。

### 8.2 filter

数据包的 `filter` 只作用于优先级比它低的数据包，不影响它自己的文件。判断规则和原版 `MultiPackResourceManager`、`ResourceFilterSection`、`IdentifierPattern` 的实际行为相同：

- 文件位置 `ns:path` 被屏蔽，当且仅当：有一条规则的 `namespace` 匹配 `ns`，并且有一条规则的 `path` 匹配 `path`。这两个条件可以由不同的规则分别满足。
- 正则用 `Pattern.asPredicate()` 判断：字符串中能找到匹配的片段就算匹配，不要求整个字符串匹配。`"path": "recipe"` 会屏蔽所有路径中含有 `recipe` 的文件。
- 规则缺少 `namespace` 或 `path` 的时候，缺少的那一项匹配任何字符串。
- `path` 匹配的是命名空间目录下的完整相对路径，包括 topic 目录和扩展名，例如 `recipe/stick.json`。

前两条和直觉不同，这里仍然照原版行为实现，保证同一个数据包在原版和这里屏蔽的文件相同。

### 8.3 两种视图

topic 由目录和扩展名确定。构建视图的时候只考虑这个目录下、文件名以这个扩展名结尾的文件，其他文件不属于这个 topic，直接忽略（和原版 `FileToIdConverter` 相同）。属于 topic 的文件，资源 ID 的路径部分必须合法，否则抛出异常。

视图按数据包优先级从低到高处理每个数据包：先用这个数据包的 `filter` 从已经收集的结果中删除被屏蔽的文件位置，再加入它自己在这个 topic 下的文件。

- 覆盖模式（`Topic.Single`）：同一个资源 ID，后加入的替换先加入的。结果是 `TopicFiles`。原版除标签以外的全部数据（配方、战利品表、进度、函数、结构模板、各个注册表的内容）都按这种方式读取。
- 分层模式（`Topic.Layered`）：同一个资源 ID 的全部版本按优先级从低到高排成列表；`filter` 删除的是这个位置在低优先级数据包中的全部版本，删除之后列表为空的资源 ID 不出现在结果中。结果是 `TopicLayers`。

```java
public interface TopicFiles {
    NavigableMap<Key, Resource> files();
    Optional<Resource> find(Key id);
}

public interface TopicLayers {
    NavigableMap<Key, List<Resource>> layers();   // 每个列表按优先级从低到高
}
```

结果按 `Key` 的自然顺序排列，相同的输入每次得到相同的遍历顺序。

### 8.4 Resource

```java
public final class Resource {
    public PackInfo pack();                 // 所属数据包
    public @Nullable String overlay();      // 所在 overlay 目录，根目录为 null
    public Key location();                  // 文件位置，例如 minecraft:recipe/stick.json
    public Key id();                        // 资源 ID，例如 minecraft:stick
    public InputStream open() throws IOException;
    public byte[] readAllBytes() throws IOException;
    public BufferedReader openReader() throws IOException;   // UTF-8
    public JsonElement readJson() throws IOException;        // Gson STRICT，必须读完整个文档
}
```

`toString()` 输出数据包 ID、overlay 目录和完整路径。订阅者抛出的异常信息里拼接它，就能定位到出错的文件。

`Resource` 在所属批次关闭之前都可以读取。游戏运行期间需要按需读取文件的模块（例如原版按需读取结构模板）可以保留 `TopicFiles`，规则见 10.4。

## 9. 标签合并

原版 26.2 的服务端数据中，只有标签文件会跨数据包合并：原版代码里读取同一文件多个版本的地方只有 `TagLoader`。框架为标签提供内置的 topic 类型：

```java
Topic.Tags blockTags = Topic.tags("block");             // data/<ns>/tags/block/**.json
Topic.Tags biomeTags = Topic.tags("worldgen/biome");    // data/<ns>/tags/worldgen/biome/**.json
Topic.Tags functionTags = Topic.tags("function");       // data/<ns>/tags/function/**.json
```

目录是 `tags/<注册表路径>`。注册表路径是注册表 ID 的路径部分，命名空间不参与，和原版 `Registries.tagsDirPath` 相同。

标签文件格式：

```json
{
  "replace": false,
  "values": ["minecraft:stone", "#minecraft:logs", {"id": "example:ruby_block", "required": false}]
}
```

- `values` 必需。每一项是字符串或对象。字符串以 `#` 开头表示引用同一注册表的另一个标签，`#` 之后的部分按命名空间 ID 解析，省略命名空间的时候是 `minecraft`。对象有必需的 `id`（字符串，规则同上）和可选的 `required`（默认 `true`）。字符串写法的 `required` 是 `true`。
- `replace` 可选，默认 `false`。
- 未知字段忽略，和原版编解码器相同。格式错误抛出 `TagFileException`，异常信息包含所属数据包和文件位置。

合并在 8.3 分层视图的基础上进行。对每个标签 ID，按优先级从低到高处理它的每个版本：`replace` 为 `true` 的时候先清空已经累积的条目，然后把这个版本的条目依次追加到末尾，每个条目记录所属的数据包。同一个数据包内部，overlay 中的标签文件替换根目录中的同名文件，两者不合并（这是 8.1 索引规则的直接结果，和原版 `CompositePackResources` 相同）。

```java
public record TagEntry(Key id, boolean tag, boolean required) {}
public record TagValue(TagEntry entry, PackInfo pack) {}
public record TagDeclaration(Key id, List<TagValue> values) {}

public interface TagDeclarations {
    NavigableMap<Key, TagDeclaration> tags();
}
```

框架的工作到这里为止。条目指向的元素是否存在、`#` 引用的展开、循环引用的检查、缺少必需条目的时候怎么处理，这些都需要注册表内容才能判断，由标签模块完成。每个条目都带有所属数据包，标签模块可以据此给出和原版相同的错误信息。

## 10. 订阅接口与调度

### 10.1 接口

```java
public interface Subscriber<R> {
    R prepare(PrepareContext context) throws Exception;
    void apply(R prepared);
}

public interface PrepareContext {
    <C> C contents(Topic<C> topic);            // 只能读取订阅中声明过的 topic，否则抛出异常
    <D> D dependency(SubscriberKey<D> key);    // 只能读取 after 中声明过的订阅者的准备结果，否则抛出异常
    Set<Key> enabledFeatures();
    LoadKind kind();                           // INITIAL 或 RELOAD
    ForkJoinPool executor();                   // 订阅者内部需要拆分并行任务的时候使用
}

public record SubscriberKey<R>(Key name) {
    public static <R> SubscriberKey<R> of(String name);
}

public enum Lifecycle { STARTUP, RELOADABLE }

public enum LoadKind { INITIAL, RELOAD }

public sealed interface Topic<C> permits Topic.Single, Topic.Layered, Topic.Tags {
    String directory();
    String extension();

    record Single(String directory, String extension) implements Topic<TopicFiles> {}
    record Layered(String directory, String extension) implements Topic<TopicLayers> {}
    record Tags(String registry) implements Topic<TagDeclarations> {
        public String directory() { return "tags/" + registry; }
        public String extension() { return ".json"; }
    }

    static Single single(String directory, String extension);
    static Single json(String directory);       // single(directory, ".json")
    static Layered layered(String directory, String extension);
    static Tags tags(String registry);
}

public final class Subscription<R> {
    public static <R> Builder<R> builder(SubscriberKey<R> key, Subscriber<R> subscriber);
    // Builder: lifecycle(Lifecycle) 必须调用；topic(Topic<?>) 与 after(SubscriberKey<?>) 可以调用多次
}
```

- 准备阶段（`prepare`）在执行器上运行，读取文件、解析内容，返回不可变的准备结果。准备阶段不允许修改游戏状态：任何一个订阅者失败，整次加载都要放弃。
- 提交阶段（`apply`）在调用 `start` / `reload` 的线程上依次运行，把准备结果填入游戏，例如替换 Minestom 注册表中的内容。提交阶段只做替换引用这类不会失败的操作；解析、校验等可能失败的工作全部放在准备阶段。
- 依赖（`after`）有两层含义：被依赖的订阅者的准备结果可以在 `prepare` 中通过 `dependency` 读取；被依赖的订阅者的 `apply` 先执行。依赖者在准备阶段就能使用新数据，例如配方模块直接使用新解析出的物品标签，不需要等新标签提交到游戏中。原版的加载流程也这样安排：新标签先存放在 `PendingTags` 中交给后续的加载步骤，所有步骤完成之后才统一生效。
- 生命周期：
  - `STARTUP`：只在会话启动的时候执行一次。对应原版只在打开世界的时候加载、`/reload` 不会改变的内容：世界生成、维度、维度类型、生物群系、伤害类型等动态注册表。
  - `RELOADABLE`：启动和每次重载都执行。对应原版 `/reload` 会重新加载的内容：标签、战利品表、谓词、物品修饰器、配方、进度、函数、结构模板。

  两种生命周期之间可以互相依赖。`RELOADABLE` 订阅者依赖 `STARTUP` 订阅者的时候，重载期间读到的是启动的时候得到的准备结果。框架在构建阶段找出所有被 `RELOADABLE` 订阅者依赖的 `STARTUP` 订阅者，在整个会话期间保留它们的准备结果；其他订阅者的准备结果在提交之后不再引用。
- 执行器使用 `ForkJoinPool`：订阅者在 `prepare` 中提交子任务并等待结果的时候，`ForkJoinPool` 会补充工作线程，执行器中的线程不会全部处于等待状态。

注册：

```java
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
        .provider(new FolderPackProvider(worldDir.resolve("datapacks"), PackOrigin.WORLD, symlinkAllowList))
        .subscribe(Subscription.builder(ITEM_TAGS, itemTagSubscriber)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(Topic.tags("item"))
                .build())
        .subscribe(Subscription.builder(RECIPES, recipeSubscriber)
                .lifecycle(Lifecycle.RELOADABLE)
                .topic(RECIPE_TOPIC)
                .after(ITEM_TAGS)
                .build())
        .executor(ForkJoinPool.commonPool())
        .build();
```

`build()` 检查下列各项，任何一项不满足就抛出 `SubscriptionException`：

- 订阅者 key 不重复；
- `after` 引用的 key 都已经注册；
- 依赖关系没有环，有环的时候异常信息列出环上的全部 key；
- topic 目录不为空、不以 `/` 开头或结尾、没有空段和 `.`、`..` 段，字符都在 `[a-z0-9_.-/]` 之内；扩展名以 `.` 开头，后面至少跟一个字符，字符都在 `[a-z0-9_.-]` 之内。

之后订阅关系不再变化。提交顺序也在 `build()` 中确定：对依赖图做拓扑排序，没有依赖关系的订阅者之间按注册顺序排列。

某个配方模块的订阅者可以这样写（`ItemTags`、`RecipeSet`、`RecipeDecoder`、`RecipeManager` 是这个模块自己的类型）：

```java
static final Topic.Single RECIPE_TOPIC = Topic.json("recipe");
static final SubscriberKey<ItemTags> ITEM_TAGS = SubscriberKey.of("yuanban:item_tags");
static final SubscriberKey<RecipeSet> RECIPES = SubscriberKey.of("yuanban:recipes");

final class RecipeSubscriber implements Subscriber<RecipeSet> {
    private final RecipeManager recipeManager;

    RecipeSubscriber(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    @Override
    public RecipeSet prepare(PrepareContext context) throws IOException {
        ItemTags tags = context.dependency(ITEM_TAGS);
        RecipeSet.Builder builder = RecipeSet.builder();
        for (Map.Entry<Key, Resource> entry : context.contents(RECIPE_TOPIC).files().entrySet()) {
            builder.add(entry.getKey(), RecipeDecoder.decode(entry.getValue().readJson(), tags, context.enabledFeatures()));
        }
        return builder.build();
    }

    @Override
    public void apply(RecipeSet prepared) {
        recipeManager.replaceAll(prepared);
    }
}
```

### 10.2 一次加载的步骤

`loader.start(SelectionRequest)` 和 `session.reload(ReloadRequest)` 都按下面的步骤执行。参与的订阅者在启动的时候是全部订阅者，在重载的时候是 `RELOADABLE` 订阅者。

1. 发现和选择（第 5、7 节）。启动和 `Rescan` 运行全部提供者；`Select` 使用上一次发现的结果。这一步抛出的异常直接向外传播，会话保持原状。
2. 打开选中的数据包，组成新批次。
3. 建立索引：每个数据包一个任务。
4. 构建视图：参与的订阅者声明过的每个不同的 topic 一个任务，前置任务是全部索引任务。多个订阅者声明同一个 topic 的时候共用同一个视图。
5. 准备：每个参与的订阅者一个任务，前置任务是它声明的 topic 的视图任务，以及它依赖的订阅者的准备任务。重载期间，被依赖的 `STARTUP` 订阅者直接使用保留的准备结果。

   第 3 到第 5 步组成一张 `CompletableFuture` 依赖图，全部在执行器上运行。任务在前置任务全部完成之后才提交，执行器中的线程不会为了等待其他任务而阻塞。

6. 等待全部任务结束。只要有一个任务失败：已经开始的任务继续运行到结束；还没开始的任务在开始的时候检查到失败标记，立即放弃。全部结束之后关闭新批次，抛出 `LoadFailedException`：第一个失败作为 cause，其余失败作为 suppressed，异常信息包含失败的订阅者 key 或数据包 ID。这时没有调用过任何 `apply`，游戏状态和当前批次都没有变化。
7. 提交：在调用线程上按 `build()` 确定的顺序调用参与的订阅者的 `apply`。
8. 新批次成为当前批次，关闭旧批次，更新会话中的 `Selection`。

`apply` 抛出的异常原样向外传播。这时一部分订阅者已经提交，游戏处于新旧数据混合的状态。这属于订阅者的实现错误（违反了 10.1 中提交阶段只做不会失败的操作这条约定），框架不尝试恢复。

### 10.3 会话

```java
public final class DataPackSession implements AutoCloseable {
    public Selection selection();                  // 当前启用的数据包、启用的特性、待持久化的 DataPackConfig、选择报告
    public SortedMap<String, Pack> available();    // 最近一次发现的全部数据包
    public SelectionEditor editor();
    public Selection reload(ReloadRequest request);
    public void close();                           // 关闭当前批次
}
```

`start` 和 `reload` 都是阻塞调用，结束的时候订阅者的 `apply` 已经全部执行完。同一个会话同一时刻只能有一个 `reload` 在执行，并发调用抛出 `IllegalStateException`。`reload` 成功之后，宿主把新的 `selection().config()` 写回存档。

### 10.4 批次与按需读取

批次关闭之后，它打开的 zip 文件系统也随之关闭，再读取其中的 `Resource` 会抛出 `ClosedFileSystemException`。需要在运行期间按需读取文件的模块，在 `apply` 中把持有的 `TopicFiles` 换成新的，并且自己保证切换之后不再使用旧的 `TopicFiles`，例如用读写锁保护读取和切换。框架在全部 `apply` 完成之后才关闭旧批次。

## 11. 错误处理

数据包内容不合法的时候，框架在发现问题的位置抛出异常，异常信息指明数据包和文件。全部异常类型继承 `DataPackException`（非受检异常）。

| 情况 | 处理 |
|---|---|
| 两个数据包 ID 相同 | 抛出 `PackStructureException` |
| 符号链接不在白名单中 | 抛出 `PackStructureException` |
| zip 数据包缺少 `pack.mcmeta` | 抛出 `PackStructureException` |
| 内置特性数据包是 zip 文件 | 抛出 `PackStructureException` |
| `pack.mcmeta` 语法错误、缺少必需字段、取值不合法、特性开关未登记、正则无法编译 | 抛出 `PackMetadataException`，包含数据包 ID 和字段路径 |
| `data/` 下出现普通文件或者不合法的命名空间目录名 | 抛出 `PackStructureException` |
| 属于某个 topic 的文件不能构成合法的资源 ID | 抛出 `PackStructureException` |
| 标签文件格式错误 | 抛出 `TagFileException` |
| 订阅关系错误：key 重复、依赖未注册、依赖有环、topic 不合法 | `build()` 抛出 `SubscriptionException` |
| 默认数据包不可用 | 抛出 `SelectionException` |
| `SelectionEditor` 或 `ReloadRequest.Select` 的规则不满足 | 抛出 `SelectionException` |
| 订阅者 `prepare` 抛出异常，或者索引、视图任务抛出异常 | 本次加载失败，抛出 `LoadFailedException`，游戏状态不变 |
| 订阅者 `apply` 抛出异常 | 原样传播 |
| 启用列表中的数据包已经不存在、数据包所需特性未启用 | 按原版规则调整列表，记入 `SelectionReport` |

## 12. 与原版行为的差异

第 1 到 4 条来自"数据有错误就抛出异常"和"不读取旧版本字段"这两项决定。第 5、6 条是原版命令实现中的缺陷，这里按启动流程的规则处理。

1. 原版遇到格式错误的 `pack.mcmeta`、不合法的命名空间目录、不合法的文件名、格式错误的标签文件、`data/` 下的普通文件、不在白名单中的符号链接，都会记录日志并跳过对应的数据包或文件；这里抛出异常。原版解析 `pack.mcmeta` 不检查文档末尾的多余内容；这里要求读完整个文档。
2. 原版接受只写了 `pack_format` 的旧数据包（标记为不兼容，仍然可以加载），也接受 `min_format` / `max_format` 解析失败但有描述的数据包（兼容性记为未知）；这里要求 `min_format` 和 `max_format`，缺少就抛出异常。
3. 原版对缺少格式范围的 overlay 条目记录日志并跳过；这里抛出异常。
4. 原版遇到重复的数据包 ID，以后发现的数据包为准；这里抛出异常。
5. 原版 `/reload` 把新出现的、没有被禁用的数据包全部追加到启用列表，不检查类别和特性，可能加载特性没有启用的数据包；这里使用和启动相同的选择算法，只追加可以自动启用并且特性满足的数据包。
6. 原版 `/datapack enable <name> before|after <existing>` 对 `existing` 套用了禁用检查，以声明了特性的特性数据包作为参照位置的时候会报错；这里只要求 `existing` 已经启用。原版 `/datapack disable` 允许禁用 `required` 数据包，重载之后它又被加回列表，加载的内容和列表不符；这里不允许禁用。原版 26.2 中没有 `required` 数据包，所以这一点对原版内容没有影响。

## 13. 测试

测试使用根项目配置好的 JUnit。测试数据是在 `@TempDir` 中实际创建的目录和 zip 文件，经过真实的加载流程读取，不使用 mock。

- 格式版本：整数和数组写法、`min` / `max` 的次版本缺省值、三种兼容性结果、overlay 的选择。
- `pack.mcmeta`：第 6 节每一条错误规则各一个用例，检查异常中的数据包 ID 和字段路径。
- 发现：目录数据包、zip 数据包、没有 `pack.mcmeta` 的目录被跳过、符号链接白名单、ID 重复、内置特性数据包。
- 选择算法：7.3 的每一步至少一个用例，覆盖新建世界、安全模式、缺失的数据包、特性不满足、`required` 数据包插入到固定位置的数据包旁边、强制启用特性数据包、`Rescan` 和 `Select`，以及 `SelectionEditor` 的每条校验规则。
- 叠加：同名文件覆盖、overlay 的顺序、overlay 不影响其他数据包、`filter` 只作用于低优先级数据包、`filter` 的两条原版规则（不同规则分别满足命名空间和路径、部分匹配）、分层视图被 `filter` 清空。同一组用例用目录数据包和 zip 数据包各运行一次，结果必须相同。
- 标签：`replace` 和追加的顺序、overlay 中的标签文件替换根目录中的同名文件、各种条目写法、格式错误。
- 调度：提交顺序符合依赖关系、多个订阅者共用 topic 视图、准备失败的时候没有调用任何 `apply` 并且新批次已关闭、依赖环检测、`RELOADABLE` 订阅者在重载期间读到保留的 `STARTUP` 准备结果、重载失败之后旧批次仍然可以读取。
- 原版数据：测试源集使用 `de.undercouch.download` 插件（5.7.0）从 Mojang 下载 26.2 服务端，按 SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 校验，取出其中的 `META-INF/versions/26.2/server-26.2.jar`。测试用 zip 文件系统打开它，构造 `BuiltInPackProvider`，以 `CREATE_WORLD` 模式启动一次，检查发现了 `vanilla` 和三个特性数据包；`recipe`、`loot_table`、`tags/block`、`worldgen/biome` 这几个 topic 的资源数量和直接用 `java.util.zip` 列出 jar 条目得到的数量相同。
