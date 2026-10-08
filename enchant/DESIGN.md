# enchant 设计

## 1. 职责

`enchant` 是 `datapack-loader` 的订阅者。它读取数据包中的附魔定义（`data/<ns>/enchantment/**.json`）、附魔提供器（`data/<ns>/enchantment_provider/**.json`）和附魔标签（`data/<ns>/tags/enchantment/**.json`），把它们组装成本模块的不可变数据类型；提交阶段把附魔注册到 Minestom 的附魔注册表，并把变化同步给在线玩家。

原版行为对照 26.2 服务端反编译代码：`net.minecraft.world.item.enchantment` 包、`net/minecraft/world/item/enchantment/effects` 包、`net/minecraft/world/item/enchantment/providers` 包、`net.minecraft.util.valueproviders` 包，以及 `net.minecraft.resources.RegistryDataLoader`、`net.minecraft.resources.ResourceManagerRegistryLoadTask`、`net.minecraft.core.MappedRegistry`、`net.minecraft.tags.TagLoader` 中的加载流程。

本模块只负责数据解析和附魔注册表的填充，不实现附魔的实际行为（战斗中的伤害减免、方块替换等）。原版的附魔行为由战斗、方块破坏、钓鱼等游戏系统代码调用 `Enchantment` 上的效果查询方法触发，这些游戏系统在本仓库中还没有对应模块。

以下部分 Minestom 已经完整提供，本模块直接使用：

- 附魔定义接口：`Enchantment`、`Enchantment.Cost`、`Enchantment.Builder`。
- 附魔效果组件：31 个原版效果组件在 `EffectComponent` 中全部注册，和原版的 `EnchantmentEffectComponents` 一一对应。
- 等级相关数值 `LevelBasedValue` 的 7 种子类型：原版附魔数据中用到 5 种（`linear`、`fraction`、`levels_squared`、`clamped`、`lookup`），都在列。
- 价值效果 `ValueEffect` 的 6 种子类，对应原版 `EnchantmentValueEffect`。
- 实体效果 `EntityEffect` 的 14 种子类，对应原版 `EnchantmentEntityEffect`。
- 位置效果 `LocationEffect` 的 1 种实现 `ReplaceDisk`，对应原版 `EnchantmentLocationBasedEffect`。

## 2. 依赖

```groovy
dependencies {
    api(project(":datapack-loader"))
    api(project(":item-tag"))
    api("net.minestom:minestom:2026.10.05-26.2")
    api(project(":dfu-codecs"))
    implementation("com.google.guava:guava:33.6.0-jre")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(testFixtures(project(":item-tag")))
}
```

依赖 `item-tag` 的原因：附魔定义的 `supported_items` 和 `primary_items` 可以引用物品标签，需要 `ItemTagSubscriber` 在准备阶段提供的展开结果。

依赖 `dfu-codecs` 的原因：附魔定义、附魔提供器和整数提供器的编解码器是原版 26.2 编解码器在 DataFixerUpper 上的移植，集中在 `internal/EnchantmentCodecs`。用到 dfu-codecs 的以下部分：

- `RegistryOps`、`RegistryLookup`：解码时检查注册表引用；
- `RegistryCodecs`：`homogeneousList`、`fixed`、`byName`；
- `ExtraCodecs`：`intRange`、`NON_NEGATIVE_INT`；
- `StringRepresentables`：装备槽位组的名字；
- `ComponentSerialization.CODEC`：文本组件。

Minestom 的编解码器只用于效果组件的值。31 个效果组件的值是 Minestom 的 `ConditionalEffect`、`ValueEffect` 等类型，`EffectComponent` 注册每个组件时带有对应的编解码器（例如 `ConditionalEffect.codec(ValueEffect.CODEC).list()`），本模块通过 `MinestomCodecs.adapt` 调用它们。

公开记录 `EnchantmentMut`、`ProviderEntry` 的集合字段是 dfu-codecs 的 `HolderSet`，所以 `dfu-codecs` 以 `api` 声明。

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

public record EnchantmentMut(
    Component description,
    HolderSet<Material> supportedItems,
    @Nullable HolderSet<Material> primaryItems,
    HolderSet<Enchantment> exclusiveSet,
    int weight,
    int maxLevel,
    Enchantment.Cost minCost,
    Enchantment.Cost maxCost,
    int anvilCost,
    List<EquipmentSlotGroup> slots,
    List<Map.Entry<DataComponent<?>, Object>> effects
) {}

public record EnchantmentTags(
    NavigableMap<Key, List<Key>> tags
) {}

public record EnchantmentProviderData(
    NavigableMap<Key, ProviderEntry> providers
) {}

public sealed interface ProviderEntry {
    Set<Key> referencedEnchantments();
    Set<Key> referencedEnchantmentTags();

    record Single(Key enchantment, IntProvider level) implements ProviderEntry {}
    record ByCost(HolderSet<Enchantment> enchantments, IntProvider cost) implements ProviderEntry {}
    record ByCostWithDifficulty(HolderSet<Enchantment> enchantments, int minCost, int maxCostSpan) implements ProviderEntry {}

    sealed interface IntProvider {
        int sample();
        int minInclusive();
        int maxInclusive();

        record ConstantInt(int value) implements IntProvider {}
        record UniformInt(int minInclusive, int maxInclusive) implements IntProvider {}
        record BiasedToBottomInt(int minInclusive, int maxInclusive) implements IntProvider {}
        record ClampedInt(IntProvider source, int minInclusive, int maxInclusive) implements IntProvider {}
        record WeightedListInt(List<Weighted> distribution) implements IntProvider {
            record Weighted(IntProvider data, int weight) {}
        }
        record ClampedNormalInt(float mean, float deviation, int minInclusive, int maxInclusive) implements IntProvider {}
        record TrapezoidInt(int minInclusive, int maxInclusive, int plateau) implements IntProvider {}
    }
}

public final class EnchantmentException extends DataPackException {}
```

- `EnchantmentSubscriber` 读取附魔定义，依赖 `ItemTagSubscriber.KEY` 和 `EnchantmentTagSubscriber.KEY`。生命周期为 `RELOADABLE`。
- `EnchantmentTagSubscriber` 读取附魔标签，同时读取 `EnchantmentSubscriber.TOPIC` 的文件列表，用来判断标签中的附魔是否存在（第 6 节）。`EnchantmentTags` 是准备好的结果，每条标签列出其中的附魔 ID。生命周期为 `RELOADABLE`。
- `EnchantmentProviderSubscriber` 读取附魔提供器文件，依赖 `EnchantmentSubscriber.KEY` 和 `EnchantmentTagSubscriber.KEY`。生命周期为 `RELOADABLE`。

三个订阅者的注册顺序无关，`datapack-loader` 根据 `after` 声明的依赖关系决定执行顺序和 `apply` 顺序。

```java
var enchantments = new EnchantmentSubscriber(process);
var enchantmentTags = new EnchantmentTagSubscriber(process);
var providers = new EnchantmentProviderSubscriber(process);

DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(...)
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(enchantmentTags.subscription())
        .subscribe(enchantments.subscription())
        .subscribe(providers.subscription())
        .build();
```

## 4. 附魔定义（EnchantmentData）

原版 26.2 的附魔定义有 43 个文件。解码用原版 `Enchantment.DIRECT_CODEC` 的移植，`EnchantmentDefinition.CODEC` 的字段平铺在同一个对象中。字段：

| 字段 | 编解码器 | 规则 |
|---|---|---|
| `description` | `ComponentSerialization.CODEC` | 必需。结果是 adventure 的 `Component`。 |
| `supported_items` | `RegistryCodecs.homogeneousList(item)` | 必需。每个原版附魔都引用 `enchantable/*` 标签。 |
| `primary_items` | `RegistryCodecs.homogeneousList(item)` | 可选，缺失时 `EnchantmentMut.primaryItems()` 为 `null`。原版 5 个附魔有：`bane_of_arthropods`、`fire_aspect`、`sharpness`、`smite`、`thorns`。 |
| `weight` | `ExtraCodecs.intRange(1, 1024)` | 必需。附魔台选中此附魔的权重。 |
| `max_level` | `ExtraCodecs.intRange(1, 255)` | 必需。 |
| `min_cost` / `max_cost` | `Enchantment.Cost.CODEC` | 必需，`base` 和 `per_level_above_first` 两个整数字段都必需。附魔台经验等级消耗。 |
| `anvil_cost` | `ExtraCodecs.NON_NEGATIVE_INT` | 必需。铁砧经验等级消耗。 |
| `slots` | `EquipmentSlotGroup` 名字的列表 | 必需。取值有 `any`、`mainhand`、`offhand`、`hand`、`feet`、`legs`、`chest`、`head`、`armor`、`body`、`saddle`，原版数据用到 `any`、`armor`、`feet`、`hand`、`head`、`legs`、`mainhand`、`offhand`。 |
| `exclusive_set` | `RegistryCodecs.homogeneousList(enchantment)` | 可选，缺省为空的元素列表，与原版 `HolderSet.empty()` 相同。原版 18 个附魔有，引用了 `exclusive_set/armor`、`boots`、`bow`、`crossbow`、`damage`、`mining`、`riptide` 7 个标签。 |
| `effects` | `dispatchedMap(enchantment_effect_component_type, ...)` | 可选，缺省为空。原版 43 个附魔用到全部 31 个效果组件。 |

`supported_items`、`primary_items`、`exclusive_set` 接受原版 `HolderSetCodec` 的三种写法：

- `"#命名空间:路径"`：标签，解码为 `HolderSet.Named`；
- `"命名空间:路径"`：单个元素；
- `["命名空间:路径", ...]`：元素列表，可以为空。

后两种解码为 `HolderSet.Direct`，保持顺序和重复项。ID 省略命名空间时为 `minecraft`。

数值字段的错误文本与原版相同，例如 `Value must be within range [1;1024]: 0`、`Value must be non-negative: -1`。`slots` 中的未知名字报 `Unknown element name:<名字>`。原版 `EquipmentSlotGroup` 和 Minestom 的同名枚举取值、名字和顺序都相同，解码直接得到 Minestom 的枚举；不使用 Minestom 的 `EquipmentSlotGroup.CODEC`，它遇到未知名字时抛出 `NullPointerException`。

一个文件中多个字段出错时，`RecordCodecBuilder` 把全部错误合并成一条信息，用 `; ` 分开，与原版相同。

`effects` 按原版 `DataComponentMap.makeCodec` 解码：

- 键按 `enchantment_effect_component_type` 注册表查找，元素是 `EffectComponent` 中的组件；未知的键报 `Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_effect_component_type]: <ID>`。
- 值用该组件在 `EffectComponent` 中注册的 Minestom 编解码器解码，经过 `MinestomCodecs.adapt`。
- DataFixerUpper 的 `dispatchedMap` 逐个条目解码，一个条目失败时其余条目照常解码，全部错误合并成一条，末尾附上 ` missed input: {...}`，列出失败的条目。

`EnchantmentMut` 是中间的 record，逐字段保存解码结果，在提交阶段转换为 `Enchantment` 实例。这样做的原因是：`Enchantment` 的构造器对使用者不可见（只能通过 `Enchantment.builder()` 创建，不能从字段集合创建），而 `DataComponentMap` 中的键是 `DataComponent` 实例，Minestom 没有提供从序列化数据构建这个映射的公开接口。`EnchantmentMut.effects` 按 JSON 中键的顺序保存组件和值，提交阶段逐条交给 `Enchantment.Builder.effect`。

### 4.1 注册表引用的检查

`EnchantmentSubscriber` 在 `prepare` 中构造 `RegistryOps`，以 Minestom 进程的注册表为缺省内容，替换下面两个注册表：

- 物品：元素是 Minestom 的全部物品，标签是 `context.dependency(ItemTagSubscriber.KEY)` 得到的本次加载的物品标签。标签不存在时报 `Missing tag: '<ID>' in 'minecraft:item'`；物品不存在时，`HolderSetCodec` 依次尝试标签、列表和单个元素三种写法，错误中含有 `Failed to get element <ID>`。
- 附魔：注册查找（`RegistryGetter.registration()`），任何附魔和附魔标签在解码时都可以引用。

原版在同一批 worldgen 注册表中加载附魔、附魔标签和附魔提供器（`RegistryDataLoader.WORLDGEN_REGISTRIES`），解码时附魔注册表也是注册查找，冻结注册表时再检查。本模块在全部附魔文件解码之后做同样的检查：

- `exclusive_set` 中的附魔 ID 不在本次加载解码成功的附魔中时，报 `Unbound values in registry ResourceKey[minecraft:root / minecraft:enchantment]: [<ID>, ...]`；
- 没有这样的附魔时，`exclusive_set` 引用的标签不在 `context.dependency(EnchantmentTagSubscriber.KEY)` 中，报 `Unbound tags in registry ResourceKey[minecraft:root / minecraft:enchantment]: [<ID>, ...]`。

ID 去掉重复项，按原版 `Identifier.compareTo` 排序：先比较路径，再比较命名空间。这一行放在各文件的错误之后。

### 4.2 等级相关数值

`LevelBasedValue` 是附魔效果的数值计算模型。原版在 `lookup` 中用数值表按等级索引，在 `linear` 中用斜率计算。原版附魔数据用到 5 种：`linear`、`fraction`、`levels_squared`、`clamped`、`lookup`。Minestom 全部提供。

### 4.3 价值效果

`ValueEffect` 修改一个数值（伤害、护甲、弹药消耗等）。原版附魔数据用到 5 种：`add`（22 个附魔）、`all_of`（7 个）、`multiply`（1 个）、`set`（2 个）、`remove_binomial`（`unbreaking` 用它处理精准采集）。Minestom 的 `ValueEffect` 提供全部 6 种子类。

Minestom 的 `ValueEffect.RemoveBinomial.apply` 抛出 `UnsupportedOperationException`。原版数据中 `unbreaking` 使用这个效果，本模块在准备阶段决定这个效果是否存在需要 Minestom 解决之后才能实现，在此之前不阻止 `unbreaking` 加载（效果字段原样保存，行为系统在调用 `ValueEffect.apply` 时会得到 Minestom 的异常）。

### 4.4 实体效果与位置效果

原版附魔数据中用到 14 种实体效果，Minestom 全部提供。原版附魔中实体效果集中在武器类附魔：`thorns` 的反伤、`fire_aspect` 的点燃、`channeling` 的召唤闪电、`wind_burst` 的爆炸击退，以及 `post_attack` 复合效果。原版 `TargetedConditionalEffect` 的 `EnchantmentTarget` 取值有三个：`attacker`、`damaging_entity`、`victim`，Minestom 有同名枚举。原版 `equipment_drops` 的 `enchanted` 只允许 `attacker` 和 `victim`，没有 `affected` 字段；Minestom 对它使用通用的编解码器，见第 9 节。

位置效果（`replace_disk`）只有 `frost_walker` 使用，Minestom 的 `LocationEffect` 有对应类型。

### 4.5 战利品表谓词

原版 20 个附魔在效果词条的 `requirements` 字段中使用战利品表谓词，包括 `protection` 系列的伤害减免条件、`frost_walker` 的站在地面上的检查、`unbreaking` 的精准采集条件等。

Minestom 的 `ConditionalEffect`、`TargetedConditionalEffect` 把 `requirements` 解码为 `DataPredicate.Noop`：JSON 转换成 NBT 原样保存，不检查谓词的结构。在战利品表模块提供谓词到 `DataPredicate` 的转换器之前，`enchant` 不解析这部分。原版这 20 个附魔中，伤害减免、反伤、精准采集等效果的行为在 Minestom 中也没有实现，所以谓词目前只影响保存的数据。

## 5. 附魔提供器

原版 26.2 附魔提供器有 3 种类型，原版数据中有 7 个文件（`raid/` 目录下 4 个，根目录下 3 个）。原版把提供器作为 worldgen 动态注册表 `enchantment_provider` 加载，与附魔、附魔标签在同一批中，在怪物生成和村民交易代码中被调用，给一个物品添加上下文相关的附魔组合。Minestom 没有提供器注册表。本模块在准备阶段解码，提交阶段保存提供器 ID 到 `ProviderEntry` 的映射。提供器的原版行为（按经验等级选取附魔组合）和怪物生成、村民交易等系统在一起，这些系统在本仓库中还没有对应模块。

解码用原版 `EnchantmentProvider.DIRECT_CODEC` 的移植：按 `type` 字段的完整 ID 在 `enchantment_provider_type` 中分派，省略命名空间时为 `minecraft`。未知类型报 `Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_provider_type]: <ID>`，例如 `example:single`；缺少 `type` 报 `No key type in MapLike[...]`。

| 类型 | 字段 | 原版使用处 |
|---|---|---|
| `single` | `enchantment`（附魔 ID）、`level`（整数提供器） | 6 个文件，如 `pillager_spawn_crossbow` 给弩加 `piercing 1` |
| `by_cost` | `enchantments`（附魔集合）、`cost`（整数提供器） | 0 个文件 |
| `by_cost_with_difficulty` | `enchantments`（附魔集合）、`min_cost`（`intRange(1, 10000)`）、`max_cost_span`（`intRange(0, 10000)`） | 1 个文件（`mob_spawn_equipment`） |

`ProviderEntry` 是 sealed interface，三个实现对应三个原版类型：`Single`、`ByCost`、`ByCostWithDifficulty`。`enchantments` 字段与 `exclusive_set` 相同，接受 `#标签`、单个附魔 ID 和附魔 ID 列表，解码为 `HolderSet<Enchantment>`。`referencedEnchantments()` 返回直接写出的附魔 ID，`referencedEnchantmentTags()` 返回引用的附魔标签。

提供器和附魔在原版中属于同一批，`EnchantmentProviderSubscriber` 解码时附魔注册表同样用注册查找，全部文件解码之后按 `context.dependency(EnchantmentSubscriber.KEY)` 和 `context.dependency(EnchantmentTagSubscriber.KEY)` 检查引用，报告方式和文本与第 4.1 节相同。

### 5.1 整数提供器

`level` 和 `cost` 用原版 `IntProviders.CODEC` 的移植：整数字面量解码为 `ConstantInt`，否则按 `type` 字段在 `int_provider_type` 中分派。两种都失败时错误为 `Failed to parse either. First: <整数的错误>; Second: <对象的错误>`。

| 类型 | 记录 | 字段 | 校验 | 取样 |
|---|---|---|---|---|
| `constant` | `ConstantInt` | `value` | 无 | `value` |
| `uniform` | `UniformInt` | `min_inclusive`、`max_inclusive` | `Max must be at least min, min_inclusive: <a>, max_inclusive: <b>` | 两端之间均匀取样 |
| `biased_to_bottom` | `BiasedToBottomInt` | `min_inclusive`、`max_inclusive` | 同 `uniform` | 先在 `[1, 跨度 + 1]` 中取样一个上限，再在下限和这个上限之间取样，偏向较小的值 |
| `clamped` | `ClampedInt` | `source`（整数提供器）、`min_inclusive`、`max_inclusive` | 同 `uniform`，只检查这两个字段 | `source` 的取样结果限制在两端之间 |
| `weighted_list` | `WeightedListInt` | `distribution`：`{data, weight}` 的列表，`weight` 非负 | 权重之和为 0 时 `Weighted list must contain at least one entry with non-zero weight` | 按权重选一个条目再取样 |
| `clamped_normal` | `ClampedNormalInt` | `mean`、`deviation`、`min_inclusive`、`max_inclusive` | `Max must be larger than min: [<a>, <b>]` | 正态分布取样，限制在两端之间后截断为整数 |
| `trapezoid` | `TrapezoidInt` | `min`、`max`、`plateau` | `Max must be larger than min: [<a>, <b>]`；`plateau` 大于跨度时 `Plateau can at most be the full span: [<a>, <b>]` | 两个均匀分布相加的梯形分布 |

`minInclusive()` 和 `maxInclusive()` 与原版相同：`clamped` 返回和 `source` 的范围求交之后的值，`weighted_list` 返回全部条目（包括权重为 0 的条目）的最小下限和最大上限。`weighted_list` 的权重之和超过 `Integer.MAX_VALUE` 时，与原版 `WeightedList` 相同，构造时抛出 `IllegalArgumentException`。

只提供无参 `sample()`，随机数取自 `ThreadLocalRandom`，嵌套的提供器使用同一个线程的随机数；原版的 `sample(RandomSource)` 在 Minestom 中没有对应物。取样的计算步骤逐个移植原版。

## 6. 附魔标签

原版 26.2 有 22 个附魔标签文件（`tags/enchantment/` 目录下 15 个，`exclusive_set/` 子目录下 7 个）。其中 `double_trade_price`、`in_enchanting_table`、`on_mob_spawn_equipment`、`on_random_loot`、`on_traded_equipment`、`tradeable` 引用了 `#treasure` 或 `#non_treasure`。

附魔标签在原版中会和附魔注册表条目一起发给客户端：附魔注册表在 `RegistryDataLoader.SYNCHRONIZED_REGISTRIES` 中，26.2 的配置阶段同步里每个注册表条目跟随它的标签一并编码。客户端此时把附魔标签存在自己的注册表中，用于 `Enchantment.getFullname` 的诅咒判断和互斥检查的显示；此外原版的 `ClientboundUpdateTagsPacket` 在注册表数据之后还会把这些标签单独再发一次。本模块中的处理如下。

`EnchantmentTagSubscriber` 按原版 `TagLoader.build` 展开标签，展开顺序复现原版 `DependencySorter`。原版在附魔注册之后构建附魔标签（`ResourceManagerRegistryLoadTask`），找不到的引用按下面的规则处理：

- 必需的元素：放入标签。不在本次加载的附魔文件中时，全部标签展开之后报 `Unbound values in registry ResourceKey[minecraft:root / minecraft:enchantment]: [<ID>, ...]`，排序规则同第 4.1 节；
- 可选的元素（`"required": false`）：只在本次加载有这个附魔时放入，否则跳过；
- 可选的标签引用：找不到时跳过，与原版 `TagEntry.build` 相同；
- 必需的标签引用：找不到时（包括循环引用被去掉的情况）报 `Couldn't load tag <ID> as it references missing tag <ID>`。

Minestom 的附魔注册表在启动时通过 `RegistryData.loadTags` 读入原版自带的附魔标签（对象和 `item-tag` 处理物品标签之前看到的一样）。本模块用同一种方法写入新数据：对每个附魔标签，调用 `process.enchantment().getOrCreateTag(TagKey.of(id))` 取得对象，替换它的内容。提交阶段更新全部标签之后这些对象保持同一个实例，之前解码的代码（例如 `Enchantment.getFullname` 读到的 `curse` 标签）看到新的内容。

`EnchantmentTags` 里同时保存标签名到附魔 ID 列表的展开结果，供执行代码直接查询（战利品表等模块读这个不走注册表）。

`curse` 标签决定是否把满级附魔的名称改为红色文本，客户端的展示依赖自己接收到的附魔标签。

## 7. 提交阶段

`EnchantmentSubscriber.apply` 按以下步骤执行：

1. 对每个附魔 ID，用 `Enchantment.Builder` 构造 `Enchantment` 实例。集合字段中的标签用 `Registry.getOrCreateTag` 取得注册表中的标签对象，元素列表按原顺序转换为 `RegistryTag.direct`。`primary_items` 缺失时向 `Builder.primaryItems` 传入 `null`：构造器的缺省值是空标签，Minestom 会把它同步给客户端，使任何物品都不算主要物品；原版在这个字段缺失时把全部支持的物品都当作主要物品。`EnchantmentMut.effects` 中的效果词条逐条交给 `Builder.effect`。
2. 调用 `process.enchantment().register(id, enchantment)` 把每个附魔实例放入 Minestom 的附魔注册表。这个注册表是 `DynamicRegistry<Enchantment>`，Minestom 启动时已经从原版数据填入了全部 43 个条目；相同 ID 的条目被替换，不在数据包中的条目保留（和原版用数据包内容覆盖注册表条目的行为一致）。
3. 把附魔注册表的更新通知在线玩家。Minestom 在玩家进入配置阶段时发送附魔注册表的数据包，之后每次重载时如果条目有变化也需要发送。这一点由 Minestom 注册表的内部机制处理，本模块的验证用例如下：玩家在进入游戏之后才发生重载，重载之后捡起带有附魔的物品，物品上显示的附魔名称和等级正确。

## 8. 错误处理

附魔文件或附魔提供器文件出错的时候，订阅者收集全部错误之后抛出 `EnchantmentException`。异常信息每行一个文件，按资源 ID 排序，格式参照原版：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/enchantment/foo.json: <错误>
```

同一批引用检查的错误（`Unbound values ...` 或 `Unbound tags ...`）单独一行，放在最后。

| 情况 | 错误 |
|---|---|
| JSON 语法错误 | 读取文件时的 `JsonParseException` 的信息，和其他文件的错误一起汇总 |
| 数值超出范围 | `Value must be within range [<最小值>;<最大值>]: <值>`、`Value must be non-negative: <值>` |
| 缺少必需字段 | `No key <字段> in MapLike[...]` |
| `slots` 中的未知名字 | `Unknown element name:<名字>` |
| `supported_items` 或 `primary_items` 引用的物品标签不存在 | `Missing tag: '<ID>' in 'minecraft:item'` |
| `supported_items` 或 `primary_items` 中的物品不存在 | 含有 `Failed to get element <ID>` 的 `Failed to parse either. ...` |
| `exclusive_set` 引用的附魔或附魔标签不存在 | `Unbound values in registry ...` 或 `Unbound tags in registry ...` |
| 效果组件 ID 未知 | `Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_effect_component_type]: <ID>` |
| 效果组件的值无法解码 | Minestom 编解码器的错误文本 |
| 附魔提供器的类型未知 | `Unknown registry key in ResourceKey[minecraft:root / minecraft:enchantment_provider_type]: <ID>` |
| 附魔提供器引用的附魔或附魔标签不存在 | `Unbound values in registry ...` 或 `Unbound tags in registry ...` |
| 整数提供器的校验失败 | 第 5.1 节的原版文本 |
| 附魔标签中必需的附魔不存在 | `EnchantmentTagSubscriber` 报 `Unbound values in registry ...` |
| 附魔标签中必需的标签引用不存在 | `Couldn't load tag <ID> as it references missing tag <ID>` |
| 任一文件出错 | 本模块订阅者失败，`datapack-loader` 整次加载失败，游戏状态不变 |

## 9. 与原版行为的差异

1. Minestom 的附魔注册表在创建 `ServerProcess` 时静态解码原版数据，原版在每次加载世界时从数据包动态解码。本模块在提交阶段把数据包中的条目注册到注册表中，相同 ID 的条目被替换，Minestom 自带的条目和原版数据提供的内容相同，行为一致。
2. 原版把附魔提供器作为 worldgen 动态注册表 `enchantment_provider` 管理，Minestom 没有对应的注册表。本模块在订阅者中保存提供器数据，提供器的使用方（战利品表、村民交易）通过 `EnchantmentProviderData` 读取它们。
3. Minestom 的 `ValueEffect.RemoveBinomial.apply` 没有实现，`unbreaking` 附魔的这个效果字段会被加载，但调用时会抛出异常。
4. 效果组件的值由 Minestom 的编解码器解码，与原版有以下差异：
   - `TargetedConditionalEffect` 的 `affected` 可选，原版必需；
   - `equipment_drops` 使用通用的 `TargetedConditionalEffect` 编解码器，`enchanted` 也接受 `damaging_entity`，并且读取 `affected`；原版只接受 `attacker` 和 `victim`，`affected` 固定为 `victim`；
   - `requirements` 解码为 Minestom 的 `DataPredicate.Noop`，只把 JSON 转换成 NBT 保存，不检查战利品表谓词的结构；
   - 原版按 `LootContextParamSets` 检查效果中使用的战利品上下文参数，本模块不检查；
   - 一个组件的值中只报告第一个错误，错误文本是 Minestom 的。
5. 原版冻结附魔注册表时，一次报出附魔、附魔标签和附魔提供器中全部未绑定的引用。本模块中这三个订阅者在各自的准备阶段分别报告，先失败的订阅者使依赖它的订阅者不再执行。
6. 附魔标签展开时，本模块按本次加载的附魔文件列表判断附魔是否存在，原版按解码成功并注册的附魔判断。两者只在有附魔文件解码失败时不同，这时整次加载已经失败，差别只在错误信息。
7. 原版加载每个文件时捕获全部异常，作为这个文件的错误。本模块只把 JSON 语法错误加入汇总；编解码器内部抛出的异常（例如 `weighted_list` 权重之和溢出）直接使整次加载失败，不与其他文件的错误一起汇总。

## 10. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程。测试数据包是在 `@TempDir` 中实际创建的目录，经过 `datapack-loader` 的真实加载流程读取。这些测试类都加上 `@ExtendWith(ResetItemTags.class)`，原因同 `item-recipe/DESIGN.md` 第 7 节。`IntProviderTest` 直接调用整数提供器的编解码器，不需要 Minestom 进程。

- 附魔定义解码：
  - 标签、单个 ID、ID 列表三种写法，`primary_items` 缺失时注册表中的附魔 `primaryItems()` 为 `null`；
  - `weight`、`max_level`、`anvil_cost` 超出范围，未知槽位名，一个文件中多个字段的错误合并成一行；
  - 物品标签不存在，`exclusive_set` 引用不存在的附魔或标签；
  - 未知效果组件和其他出错的效果条目一起报告；
  - JSON 语法错误和解码错误一起汇总。
- 附魔标签：原版 22 个附魔标签全部正确读取；可选的元素和标签引用找不到时跳过；必需的元素不存在时报 `Unbound values`；必需的标签引用不存在时失败。
- 附魔提供器：原版全部 7 个提供器正确解码；三种类型和三种集合写法；`type` 按完整 ID 匹配；缺少 `type`；未知附魔和未知附魔标签的引用。
- 整数提供器：7 种类型各自接受的输入、原版的校验文本、取样结果在 `minInclusive()` 和 `maxInclusive()` 之间并且覆盖全部可能的值。
- 附魔效果：5 种 `ValueEffect` 子类在原版附魔效果中被正确使用。
- 注册表：提交之后 `process.enchantment().get(id)` 返回的附魔对象的 `weight`、`maxLevel`、`anvilCost` 等字段和数据包一致，元素列表按原顺序成为直接标签。
- 重载：重载之后附魔注册表反映新数据，在线玩家持有新附魔的物品行为正确。
- 原版数据：从根项目的 `extractVanillaServer` 任务准备的 26.2 服务端 jar（全部模块使用同一份，路径通过系统属性 `yuanban.vanilla-server` 传入）中提取附魔、附魔标签、附魔提供器文件，经过 `datapack-loader` 加载，解码全部成功，注册表条目和数据包一致。
