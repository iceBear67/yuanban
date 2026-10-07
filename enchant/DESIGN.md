# enchant 设计

## 1. 职责

`enchant` 是 `datapack-loader` 的订阅者。它读取数据包中的附魔定义（`data/<ns>/enchantment/**.json`）、附魔提供器（`data/<ns>/enchantment_provider/**.json`）和附魔标签（`data/<ns>/tags/enchantment/**.json`），把它们组装成本模块的不可变数据类型；提交阶段把附魔注册到 Minestom 的附魔注册表，并把变化同步给在线玩家。

原版行为对照 26.2 服务端反编译代码：`net.minecraft.world.item.enchantment` 包、`net/minecraft/world/item/enchantment/effects` 包、`net/minecraft/world/item/enchantment/providers` 包、以及 `net.minecraft.resources.RegistryDataLoader`、`net.minecraft.server.ReloadableServerResources`、`net.minecraft.server.ReloadableServerRegistries` 中的加载流程。

本模块只负责数据解析和附魔注册表的填充，不实现附魔的实际行为（战斗中的伤害减免、方块替换等）。原版的附魔行为由战斗、方块破坏、钓鱼等游戏系统代码调用 `Enchantment` 上的效果查询方法触发，这些游戏系统在本仓库中还没有对应模块。

以下部分 Minestom 已经完整提供，本模块直接使用：

- 附魔定义接口：`Enchantment`、`Enchantment.Cost`、`Enchantment.Builder`。
- 附魔效果组件：31 个原版效果组件在 `EffectComponent` 中全部注册，和原版的 `EnchantmentEffectComponents` 一一对应。
- 等级相关数值 `LevelBasedValue` 的 7 种子类型：原版附魔数据中用到 5 种（`linear`、`fraction`、`levels_squared`、`clamped`、`lookup`），都在列。
- 价值效果 `ValueEffect` 的 6 种子类，对应原版 `EnchantmentValueEffect`。
- 实体效果 `EntityEffect` 的 14 种子类，对应原版 `EnchantmentEntityEffect`。
- 位置效果 `LocationEffect` 的 1 种实现 `ReplaceDisk`，对应原版 `EnchantmentLocationBasedEffect`。
- 附魔提供器的三个原版类型：`single`、`by_cost`、`by_cost_with_difficulty`（见第 5 节）。

## 2. 依赖

```groovy
dependencies {
    api(project(":datapack-loader"))
    api(project(":item-tag"))
    api("net.minestom:minestom:2026.10.05-26.2")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(testFixtures(project(":item-tag")))
}
```

依赖 `item-tag` 的原因：附魔定义的 `supported_items` 和 `primary_items` 是物品标签引用，需要 `ItemTagSubscriber` 在准备阶段提供的展开结果。

附魔和附魔提供器的 JSON 用 Minestom 自带的编解码器解码，不引入 DataFixerUpper。`Enchantment.REGISTRY_CODEC` 覆盖附魔定义的全部字段；31 个效果组件的值类型由 `EffectComponent` 注册时使用的编解码器解码（`ConditionalEffect.codec(ValueEffect.CODEC)` 等），查表入口是 `EffectComponent.fromKey(Key)`。附魔提供器的三个原版类型（`single`、`by_cost`、`by_cost_with_difficulty`）Minestom 没有现成的结构编解码器，本模块自己按字段解码成 `ProviderEntry`。

## 3. 公开接口

包 `io.ib67.yuanban.enchant`：

```java
public final class EnchantmentSubscriber implements Subscriber<EnchantmentData> {
    public static final SubscriberKey<EnchantmentData> KEY = SubscriberKey.of("yuanban:enchantments");
    public static final Topic.Single TOPIC = Topic.json("enchantment");

    public EnchantmentSubscriber(ServerProcess process);
    public Subscription<EnchantmentData> subscription();
    public EnchantmentData current();
}

public final class EnchantmentTagSubscriber implements Subscriber<EnchantmentTags> {
    public static final SubscriberKey<EnchantmentTags> KEY = SubscriberKey.of("yuanban:enchantment_tags");
    public static final Topic.Tags TOPIC = Topic.tags("enchantment");
}

public final class EnchantmentProviderSubscriber implements Subscriber<EnchantmentProviderData> {
    public static final SubscriberKey<EnchantmentProviderData> KEY = SubscriberKey.of("yuanban:enchantment_providers");
    public static final Topic.Single TOPIC = Topic.json("enchantment_provider");
}

public record EnchantmentData(
    NavigableMap<Key, EnchantmentMut> enchantments
) {}

public record EnchantmentTags(
    NavigableMap<Key, List<Key>> tags
) {}

public record EnchantmentProviderData(
    NavigableMap<Key, ProviderEntry> providers
) {}

public record ProviderEntry(String type, JsonObject definition) {}

public final class EnchantmentException extends DataPackException {}
```

- `EnchantmentSubscriber` 读取附魔定义，依赖 `ItemTagSubscriber.KEY` 和 `EnchantmentTagSubscriber.KEY`。生命周期为 `RELOADABLE`。
- `EnchantmentTagSubscriber` 读取附魔标签。`EnchantmentTags` 是准备好的结果，每条标签列出其中的附魔 ID。生命周期为 `RELOADABLE`。
- `EnchantmentProviderSubscriber` 读取附魔提供器文件，依赖 `EnchantmentSubscriber.KEY`。生命周期为 `RELOADABLE`。

三个订阅者的注册顺序无关，`datapack-loader` 根据 `after` 声明的依赖关系决定执行顺序和 `apply` 顺序。

```java
var enchantments = new EnchantmentSubscriber(process);
var enchantmentTags = new EnchantmentTagSubscriber();
var providers = new EnchantmentProviderSubscriber();

DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(...)
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(enchantmentTags.subscription())
        .subscribe(enchantments.subscription())
        .subscribe(providers.subscription())
        .build();
```

## 4. 附魔定义（EnchantmentData）

原版 26.2 的附魔定义有 43 个文件。字段：

| 字段 | 类型 | 规则 |
|---|---|---|
| `description` | 文本组件 | 必需。储存在 Adventure `Component`。 |
| `supported_items` | 物品标签引用 | 必需。每个原版附魔都引用 `enchantable/*` 标签。在准备阶段解析为 `RegistryTag<Material>`。 |
| `primary_items` | 物品标签引用 | 可选。原版中 `bane_of_arthropods`、`fire_aspect`、`sharpness`、`smite`、`thorns` 有。 |
| `exclusive_set` | 附魔标签引用 | 可选。原版 18 个附魔有，引用了 `exclusive_set/armor`、`boots`、`bow`、`crossbow`、`damage`、`mining`、`riptide` 7 个标签。在准备阶段解析为 `RegistryTag<Enchantment>`。 |
| `weight` | int | 必需，`[1, 1024]`。附魔台选中此附魔的权重。 |
| `max_level` | int | 必需，`[1, 255]`。 |
| `min_cost` / `max_cost` | `Cost` | 必需，`{base, per_level_above_first}`。附魔台经验等级消耗。 |
| `anvil_cost` | int | 必需，非负。铁砧经验等级消耗。 |
| `slots` | `List<EquipmentSlotGroup>` | 必需。原版用到的取值有 `any`、`armor`、`feet`、`hand`、`head`、`legs`、`mainhand`、`offhand` 及组合。 |
| `effects` | 效果组件 JSON | 可选，默认 `DataComponentMap.EMPTY`。原版 43 个附魔的 `effects` 字段用到 30 个效果组件键，除两个单元组件（`prevent_armor_change`、`prevent_equipment_drop`）外都已在 `EffectComponent` 中注册。 |

`EnchantmentMut` 是中间的 record，逐字段保存解码结果，在提交阶段转换为 `Enchantment` 实例。这样做的原因是：`Enchantment` 的构造器对使用者不可见（只能通过 `Enchantment.builder()` 创建，不能从字段集合创建），而 `DataComponentMap` 中的键是 `DataComponent` 实例，Minestom 没有提供从序列化数据构建这个映射的公开接口。`EnchantmentMut.effects` 保存词条列表，提交阶段先检查每个效果组件 ID 是否在 `EffectComponent` 中注册（未知组件抛出异常），然后把结果交给 `Enchantment.Builder`。

物品标签解析的规则：`EnchantmentSubscriber` 在 `prepare` 中通过 `context.dependency(ItemTagSubscriber.KEY)` 取得本次加载的 `ItemTags`，检查每条引用的标签存在。不存在时抛出 `EnchantmentException`。

附魔标签引用的规则：`EnchantmentTagSubscriber` 先把标签名和标签中含有的附魔 ID 列表解码出来（此阶段标签还没有内容，因为标签之间可以互相引用，展开需要全部标签定义之后再做；附魔标签引用另一个附魔标签的情况在本模块中不出现）；`EnchantmentSubscriber` 在 `prepare` 中通过 `context.dependency(EnchantmentTagSubscriber.KEY)` 检查 `exclusive_set` 引用的标签是否存在。原版中 7 个 `exclusive_set` 标签 `armor`、`boots`、`bow`、`crossbow`、`damage`、`mining`、`riptide` 都存在。

### 4.1 等级相关数值

`LevelBasedValue` 是附魔效果的数值计算模型。原版在 `lookup` 中用数值表按等级索引，在 `linear` 中用斜率计算。原版附魔数据用到 5 种：`linear`、`fraction`、`levels_squared`、`clamped`、`lookup`。Minestom 全部提供。

### 4.2 价值效果

`ValueEffect` 修改一个数值（伤害、护甲、弹药消耗等）。原版附魔数据用到 5 种：`add`（22 个附魔）、`all_of`（7 个）、`multiply`（1 个）、`set`（2 个）、`remove_binomial`（`unbreaking` 用它处理精准采集）。Minestom 的 `ValueEffect` 提供全部 6 种子类。

Minestom 的 `ValueEffect.RemoveBinomial.apply` 抛出 `UnsupportedOperationException`。原版数据中 `unbreaking` 使用这个效果，本模块在准备阶段决定这个效果是否存在需要 Minestom 解决之后才能实现，在此之前不阻止 `unbreaking` 加载（效果字段原样保存，行为系统在调用 `ValueEffect.apply` 时会得到 Minestom 的异常）。

### 4.3 实体效果与位置效果

原版附魔数据中用到 14 种实体效果，Minestom 全部提供。原版附魔中实体效果集中在武器类附魔：`thorns` 的反伤、`fire_aspect` 的点燃、`channeling` 的召唤闪电、`wind_burst` 的爆炸击退，以及 `post_attack` 复合效果。原版 `TargetedConditionalEffect` 的 `EnchantmentTarget` 取值有三个：`attacker`、`damaging_entity`、`victim`，Minestom 有同名枚举。`equipment_drops` 中只允许 `attacker` 和 `victim`。

位置效果（`replace_disk`）只有 `frost_walker` 使用，Minestom 的 `LocationEffect` 有对应类型。

### 4.4 战利品表谓词

原版 20 个附魔在 `requirements` 字段中使用战利品表谓词，包括 `protection` 系列的伤害减免条件、`frost_walker` 的落地状态检查、`unbreaking` 的精准采集条件等。Minestom 的 `DataPredicate` 用 NBT 表示谓词，本仓库的战利品表模块尚未实现。

本模块不自行解码战利品表谓词，准备阶段遇到带有 `requirements` 的效果词条时把谓词 JSON 原样保存。在战利品表模块提供谓词到 `DataPredicate` 的转换器之前，`enchant` 提交阶段不负责这部分的解析。原版这 20 个附魔中，伤害减免、反伤、精准采集等效果的行为在 Minestom 中也没有实现，所以谓词只影响数据的保真度而不是行为。

## 5. 附魔提供器

原版 26.2 附魔提供器有 3 种类型，原版数据中有 7 个文件（`raid/` 目录下 4 个，根目录下 3 个）：

| 类型 | 字段 | 原版使用处 |
|---|---|---|
| `single` | `enchantment`（附魔 ID）+ `level`（1-255） | 6 个文件，如 `pillager_spawn_crossbow` 给弩加 `piercing 1` |
| `by_cost` | `enchantments`（附魔标签引用）+ `cost`（整数提供器） | 0 个文件 |
| `by_cost_with_difficulty` | `enchantments`（附魔标签引用）+ `min_cost` + `max_cost_span` | 1 个文件（`mob_spawn_equipment`） |

原版把提供器作为动态注册表加载（`LootDataType` 之一），在怪物生成和村民交易代码中被调用，给一个物品添加上下文相关的附魔组合。Minestom 没有提供器注册表。本模块在准备阶段按类型解码 JSON，提交阶段建立提供器 ID 到 `ProviderEntry` 的映射。提供器的原版行为（按经验等级选取附魔组合）在 Minestom 中的代码和怪物生成、村民交易等系统在一起，这些系统在本仓库中还没有对应模块。

`ProviderEntry` 的 `type` 字段是三选一：`single`、`by_cost`、`by_cost_with_difficulty`。`definition` 保存原始的 JSON 对象。准备阶段检查 `enchantment` 字段引用的附魔在当批次中存在，不存在抛出 `EnchantmentException`。

## 6. 附魔标签

原版 26.2 有 22 个附魔标签文件（`tags/enchantment/` 目录下 15 个，`exclusive_set/` 子目录下 7 个）。

附魔标签在原版中会和附魔注册表条目一起发给客户端：附魔注册表在 `RegistryDataLoader.SYNCHRONIZED_REGISTRIES` 中，26.2 的配置阶段同步里每个注册表条目跟随它的标签一并编码。客户端此时把附魔标签存在自己的注册表中，用于 `Enchantment.getFullname` 的诅咒判断和互斥检查的显示；此外原版的 `ClientboundUpdateTagsPacket` 在注册表数据之后还会把这些标签单独再发一次。本模块中的处理如下。

Minestom 的附魔注册表在启动时通过 `RegistryData.loadTags` 读入原版自带的附魔标签（对象和 `item-tag` 处理物品标签之前看到的一样）。本模块用同一种方法写入新数据：对每个附魔标签，调用 `process.enchantment().getOrCreateTag(TagKey.of(id))` 取得对象，替换它的内容。提交阶段更新全部标签之后这些对象保持同一个实例，之前解码的代码（例如 `Enchantment.getFullname` 读到的 `curse` 标签）看到新的内容。

`EnchantmentTags` 里同时保存标签名到附魔 ID 列表的展开结果，供执行代码直接查询（战利品表等模块读这个不走注册表）。标签之间互相引用的情况在原版附魔标签中不存在。

`curse` 标签决定是否把满级附魔的名称改为红色文本，客户端的展示依赖自己接收到的附魔标签。

## 7. 提交阶段

`EnchantmentSubscriber.apply` 按以下步骤执行：

1. 对每个附魔 ID，用 `Enchantment.Builder` 构造 `Enchantment` 实例。`EnchantmentMut.effects` 中的效果词条逐条编码为 `DataComponent`/`Object` 对之后交给 `Builder.effect`。
2. 调用 `process.enchantment().register(id, enchantment)` 把每个附魔实例放入 Minestom 的附魔注册表。这个注册表是 `DynamicRegistry<Enchantment>`，Minestom 启动时已经从原版数据填入了全部 43 个条目；相同 ID 的条目被替换，不在数据包中的条目保留（和原版用数据包内容覆盖注册表条目的行为一致）。
3. 把附魔注册表的更新通知在线玩家。Minestom 在玩家进入配置阶段时发送附魔注册表的数据包，之后每次重载时如果条目有变化也需要发送。这一点由 Minestom 注册表的内部机制处理，本模块的验证用例如下：玩家在进入游戏之后才发生重载，重载之后捡起带有附魔的物品，物品上显示的附魔名称和等级正确。

## 8. 错误处理

附魔文件解码失败的时候抛出 `EnchantmentException`。异常信息每行一个文件，格式参照原版：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/enchantment/foo.json: <解码错误>
```

| 情况 | 处理 |
|---|---|
| `supported_items` 或 `primary_items` 引用的物品标签不存在 | 抛出 `EnchantmentException` |
| `exclusive_set` 引用的附魔标签不存在 | 抛出 `EnchantmentException` |
| 效果组件 ID 未知 | 抛出 `EnchantmentException` |
| `EnchantmentCodecs` 解码字段失败（数值超范围、缺必需字段、JSON 结构错误） | 抛出 `EnchantmentException` |
| 附魔提供器的 `enchantment` 引用不存在的附魔 | 抛出 `EnchantmentException` |
| 附魔提供器的类型不是 `single`、`by_cost`、`by_cost_with_difficulty` 之一 | 抛出 `EnchantmentException` |
| 任一附魔文件解码失败 | 本模块订阅者失败，`datapack-loader` 整次加载失败，游戏状态不变 |

## 9. 与原版行为的差异

1. Minestom 的附魔注册表在创建 `ServerProcess` 时静态解码原版数据，原版在每次加载世界时从数据包动态解码。本模块在提交阶段把数据包中的条目注册到注册表中，相同 ID 的条目被替换，Minestom 自带的条目和原版数据提供的内容相同，行为一致。
2. 原版把附魔提供器作为一个动态注册表（`LootDataType`）管理，Minestom 没有对应的注册表抽象。本模块在 UPPER 层维护提供器数据，提供器的使用方（战利品表、村民交易）需要通过 `EnchantmentProviderData` 而不是注册表来读取它们。
3. Minestom 的 `ValueEffect.RemoveBinomial.apply` 没有实现，`unbreaking` 附魔的这个效果字段会被加载，但调用时会抛出异常。
4. 附魔效果中的战利品表谓词（`requirements`）在本模块中原样保存 JSON，不做解析。在战利品表模块提供解析器之后才能处理这部分。原版 20 个使用谓词的附魔在本模块中加载成功但谓词字段为空。

## 10. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程。测试数据包是在 `@TempDir` 中实际创建的目录，经过 `datapack-loader` 的真实加载流程读取。测试类都加上 `@ExtendWith(ResetItemTags.class)`，原因同 `item-recipe/DESIGN.md` 第 7 节。

- 附魔定义解码：原版 43 个附魔全部解码成功；`weight` 超出 `[1, 1024]`、`max_level` 超出 `[1, 255]`、`anvil_cost` 为负数、缺少必需字段等各种错误用例；`exclusive_set` 引用错误标签的用例；效果组件 ID 未知的用例。
- 附魔标签：原版 22 个附魔标签全部正确读取，`EnchantmentTags` 中的内容和数据包一致。
- 附魔提供器：原版全部 7 个提供器正确解码；未知类型、未知附魔引用的用例。
- 附魔效果：5 种 `ValueEffect` 子类在原版附魔效果中被正确使用。
- 注册表：提交之后 `process.enchantment().get(id)` 返回的附魔对象的 `weight`、`maxLevel`、`anvilCost` 等字段和数据包一致。
- 重载：重载之后附魔注册表反映新数据，在线玩家持有新附魔的物品行为正确。
- 原版数据：从 Mojang 下载的 26.2 服务端 jar 中提取附魔、附魔标签、附魔提供器文件，经过 `datapack-loader` 加载，解码全部成功，注册表条目和数据包一致。
