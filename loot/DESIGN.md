# loot 设计

## 1. 职责

`loot` 提供战利品表系统：

- 数据类型：原版 26.2 注册的 9 种条目、20 种条件、43 种物品修饰器、8 种数字提供器（共 80 种）对应的不可变记录，由它们组成的战利品池（`LootPool`）和战利品表（`LootTable`），以及条件和修饰器用到的谓词（物品、实体、位置、伤害来源、方块状态）和槽位来源。
- 编解码器：`io.ib67.yuanban.loot.codec` 包按原版各类型的 `MapCodec` 写出 DataFixerUpper 编解码器；`LootCodecs` 组装按类型字段分派的编解码器，类型 ID 在 `LootTypeRegistry` 中查找。
- 类型注册表：`LootTypeRegistry` 保管条目、条件、修饰器、数字提供器四组类型，每个类型带有自己的编解码器，宿主可以注册自己的类型。
- 存储与查询：`LootManager` 保管当前生效的战利品表集合，按 ID 查询。
- 内容提供器：`LootContents` 给出一张表引用到的全部物品标签、战利品表、嵌套的内联战利品表；存入 `LootManager` 时执行引用完整、嵌套不递归、复合条目展开有界的校验。
- 求值：`LootSelector.roll(...)` 接收取用环境和随机数源，产出最终生成的一组物品堆。`LootContexts` 提供原版 26 种上下文类型的快捷构造。

战利品表文件从数据包读取、解码后注册进本系统的工作由 `loot-table-parser` 完成，见 [loot-table-parser/DESIGN.md](../loot-table-parser/DESIGN.md)。本模块不依赖 `datapack-loader`，任何来源的战利品表都可以交给 `LootManager`。

原版行为对照 26.2 服务端（`server-26.2.jar`，SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 的 bundle 中取出）反编译代码：`net.minecraft.world.level.storage.loot` 包中的 `LootTable.getRandomItemsRaw` / `createStackSplitter`、`LootPool.addRandomItems`、`LootContext`、`LootParams`、`LootContext.getLuck`、`LootPoolEntryContainer.expand`、`LootItemFunction.run`、`LootDataType`、`ValidationContext`、`entries` 包全部 9 种条目、`predicates` 包全部 20 种条件、`functions` 包全部 43 种修饰器、`providers.number` 包全部 8 种数字提供器、`parameters.LootContextParamSets`、`BuiltInLootTables`，`net.minecraft.advancements.predicates` 包中的各种谓词，`SlotSources` 与 `SlotRanges`，以及 `ReloadableServerResources` 的加载次序。minecraft.wiki 的 *Loot table* 页面记载了文件布局和 `type` / `pools` / `rolls` / `bonus_rolls` / `weight` / `quality` 等字段的含义；类型 ID 和字段名以服务端 jar 注册的为准。

## 2. 术语

| 术语 | 含义 |
|---|---|
| 战利品表（loot table） | 一段战利品配置。原版存放在 `data/<ns>/loot_table/**.json`，代码中构造的表也同样表示。 |
| 战利品池（pool） | 战利品表中的一个列表项，每次取用先按 `rolls` 确定抽取次数，每次抽取从池内加权随机选出一个条目。 |
| 条目（entry） | 池中的元素：单例条目（`item`、`loot_table`、`dynamic`、`empty`、`slots`、`tag`）、复合条目（`group`、`alternatives`、`sequence`）。`tag` 的 `expand` 为 `true` 时在抽取中按标签的物品展开。 |
| 条件（condition） | 谓词，原版字段名 `conditions`。pool、entry、function 上的 `conditions` 是条件列表，全部通过才算通过。 |
| 物品修饰器（item modifier，原版 JSON 字段名 `functions`） | 对取出的物品堆执行的处理：设置数量、设置组件、按附魔加成等。 |
| 数字提供器（number provider） | 数值的来源：`constant`、`uniform`、`binomial`、`score`、`storage`、`sum`、`enchantment_level`、`environment_attribute`。 |
| 谓词（predicate） | 条件引用的匹配规则：物品谓词、实体谓词、位置谓词、伤害来源谓词、方块状态谓词，对应原版 `net.minecraft.advancements.predicates` 包。 |
| 槽位来源（slot source） | `slots` 条目读取物品的来源：实体或方块实体的槽位、物品的容器组件等，对应原版 `SlotSources`。 |
| 编解码器（codec） | DataFixerUpper 的 `Codec` / `MapCodec`，把 JSON 解码成记录。 |
| 上下文（loot context） | 一次取用的运行环境：必需和可选的上下文参数、运气值、随机数源、所在的世界，对应原版 `LootContext`。 |
| 上下文参数（context parameter） | 上下文中的一项取值，原版 `LootContextParams` 中的 `THIS_ENTITY`、`BLOCK_STATE`、`TOOL`、`ORIGIN` 等。 |
| 求值（evaluation） | 按战利品表和上下文产出物品的过程。 |
| 取用（roll） | 一次调用战利品表产生物品的完整过程，每个池按 `rolls` 抽取多次。 |

## 3. 依赖

```groovy
plugins {
    id("java-test-fixtures")
}

dependencies {
    api("net.kyori:adventure-key:5.2.0")
    api("net.minestom:minestom:2026.10.05-26.2")
    api(project(":dfu-codecs"))
    implementation("org.apache.commons:commons-lang3:3.20.0")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
}

tasks.test {
    systemProperty("minestom.inside-test", "true")
}
```

- 全部命名空间 ID 使用 `net.kyori.adventure.key.Key`，和 `datapack-loader`、Minestom 注册表共用同一类型。
- Minestom 直接出现在 `api` 中：`LootSelector.roll` 的返回值是 `ItemStack` 列表，记录的字段使用 Minestom 的类型（`Material`、`Block`、`PotionType`、`DataComponentMap`、`EnvironmentAttribute` 等），`LootEnvironment` 提供 `ServerProcess`，这些类型都出现在公开 API 的签名里。
- `dfu-codecs` 直接出现在 `api` 中：`codec` 包的公开方法返回 DataFixerUpper 的 `Codec` / `MapCodec`；记录使用这个模块的 `HolderSet`（注册表元素的列表或标签）和 `NbtPath`；解码时传入这个模块的 `RegistryOps`。DataFixerUpper 10.0.21 经 `dfu-codecs` 传递进来，版本和原版 26.2 相同。
- `commons-lang3` 提供 `Fraction`：收纳袋的重量按原版 `BundleContents` 使用分数计算，版本取自原版 26.2 的 `META-INF/libraries.list`。
- 测试的系统属性 `minestom.inside-test`：`net.minestom:testing` 在平台线程上创建玩家，需要关闭 Minestom 的虚拟线程断言。
- `java-test-fixtures` 向 `loot-table-parser` 提供测试用的求值环境 `InMemoryLootEnvironment`（见 `loot-table-parser` 的测试一节）。

## 4. 类型注册表

```java
public final class LootTypeRegistry {
    public static LootTypeRegistry vanilla();
    public void registerEntry(Key id, LootEntryType<?> type);
    public void registerCondition(Key id, LootConditionType<?> type);
    public void registerFunction(Key id, LootFunctionType<?> type);
    public void registerNumberProvider(Key id, NumberProviderType<?> type);
    public Optional<LootEntryType<? extends LootEntry>> entry(Key id);
    public Optional<LootConditionType<? extends LootCondition>> condition(Key id);
    public Optional<LootFunctionType<? extends LootFunction>> function(Key id);
    public Optional<NumberProviderType<? extends NumberProvider>> numberProvider(Key id);
}

public record LootEntryType<T extends LootEntry>(Key id, Function<LootCodecs, MapCodec<T>> codec) {}
public record LootConditionType<T extends LootCondition>(Key id, Function<LootCodecs, MapCodec<T>> codec) {}
public record LootFunctionType<T extends LootFunction>(Key id, Function<LootCodecs, MapCodec<T>> codec) {}
public record NumberProviderType<T extends NumberProvider>(Key id, Function<LootCodecs, MapCodec<T>> codec) {}
```

- `vanilla()` 按原版的 ID 和注册次序登记 `LootPoolEntries` 的 9 种条目、`LootItemConditions` 的 20 种条件、`LootItemFunctions` 的 43 种修饰器、`NumberProviders` 的 8 种数字提供器。每个类型的编解码器是 `codec` 包中对应的工厂方法，例如 `new LootEntryType<>(Key.key("item"), EntryCodecs::item)`。
- 类型记录的 `id` 和 `codec` 都不能为 `null`。`codec` 是从 `LootCodecs` 到 `MapCodec` 的函数：很多类型的字段又是条件、修饰器、条目或数字提供器（`alternatives` 的 `children`、`filtered` 的 `on_pass` 等），它们的编解码器是 `LootCodecs` 中按本注册表分派的编解码器。
- `register*` 在 ID 已被占用、或者 ID 使用 `minecraft` 命名空间时抛出 `IllegalArgumentException`。原版的注册表在启动之后不再接受注册，本模块用禁止 `minecraft` 命名空间代替这个约束。

### 4.1 LootCodecs

```java
public final class LootCodecs {
    public LootCodecs(LootTypeRegistry types);
    public LootTypeRegistry types();
    public Codec<NumberProvider> number();      // 原版 NumberProviders.CODEC
    public Codec<IntRange> intRange();          // 原版 IntRange.CODEC
    public Codec<LootCondition> condition();    // 原版 LootItemCondition.DIRECT_CODEC
    public Codec<LootFunction> typedFunction(); // 原版 LootItemFunctions.TYPED_CODEC
    public Codec<LootFunction> function();      // 原版 LootItemFunctions.ROOT_CODEC
    public Codec<LootEntry> entry();            // 原版 LootPoolEntries.CODEC
    public Codec<LootPool> pool();              // 原版 LootPool.CODEC
    public Codec<LootTable> table();            // 原版 LootTable.DIRECT_CODEC
}
```

- 条目按 `type`、条件按 `condition`、修饰器按 `function`、数字提供器按 `type` 分派。类型 ID 在解码时到注册表中查找，宿主在构造 `LootCodecs` 之后注册的类型同样能解码。找不到类型时的错误和原版相同：`Unknown registry key in ResourceKey[minecraft:root / minecraft:loot_pool_entry_type]: <id>`，另外三种分别是 `loot_condition_type`、`loot_function_type`、`loot_number_provider_type`。
- 每个类型的 `MapCodec` 在第一次解码到这个类型时由类型对象的 `codec` 函数构造，之后缓存在这个 `LootCodecs` 中。
- 这四种分派编解码器只能解码：类型对象只保存 ID 和构造函数，从记录得不到它的类型 ID，编码时返回错误结果。
- 数字提供器：裸数字是 `constant`；对象先按 `type` 分派，没有 `type` 字段时按 `uniform` 解码。
- 整数范围：裸整数表示两端都是这个常数，对象形式是可选的 `min`、`max`。
- 条件位置可以直接写条件列表，解码成 `all_of`；修饰器位置可以直接写修饰器列表，解码成 `sequence`。
- 表的 `type` 按原版 `lenientOptionalFieldOf` 解码：取值不在原版 `LootContextParamSets` 注册的 26 个名字里时（错误为 `No parameter set exists with id: '<id>'`）按缺省的 `minecraft:generic` 处理，整张表照常解码。
- 注册表引用（物品、附魔、结构、标签等）经 `dfu-codecs` 的 `RegistryOps` 检查，解码时传入带注册表的 ops，例如 `codecs.table().parse(ops, json)`。没有给出的注册表使用 Minestom 的注册表。

## 5. 数据模型

全部类型都在 `io.ib67.yuanban.loot` 包及其子包下，是不可变记录，字段沿用原版 26.2 注册的 `MapCodec` 的字段名和缺省值。表中“必填”以外的字段都可以省略。

### 5.1 战利品表与战利品池

```java
public record LootTable(Key type,                          // 缺省 minecraft:generic
                        List<LootFunction> functions,
                        List<LootPool> pools,
                        Optional<Key> randomSequence) {}

public record LootPool(List<LootCondition> conditions,
                       List<LootFunction> functions,
                       NumberProvider rolls,
                       NumberProvider bonusRolls,   // 缺省 constant(0)
                       List<LootEntry> entries) {}
```

- `LootTable` 的紧凑构造器在 `type` 为 `null` 时填入 `minecraft:generic`，在 `randomSequence` 为 `null` 时填入 `Optional.empty()`；`LootPool` 另有不带 `bonusRolls` 的构造器，填入 `constant(0)`。
- JSON 字段：表的 `type`、`random_sequence`、`pools`、`functions` 都可以省略；池的 `entries` 和 `rolls` 必填，`entries` 可以是空列表，`conditions`、`functions`、`bonus_rolls` 可以省略。
- `LootTable.isKnownType` 列出原版 `LootContextParamSets` 注册的 26 种上下文类型：`empty`、`chest`、`command`、`selector`、`villager_trade`、`fishing`、`entity`、`equipment`、`archaeology`、`gift`、`barter`、`vault`、`advancement_reward`、`advancement_entity`、`advancement_location`、`block_use`、`generic`、`block`、`shearing`、`entity_interact`、`block_interact`、`enchanted_damage`、`enchanted_item`、`enchanted_location`、`enchanted_entity`、`hit_block`。

### 5.2 条目

```java
public sealed interface LootEntry permits SingletonEntry, CompositeEntry {
    List<LootCondition> conditions();
}

public sealed interface SingletonEntry extends LootEntry {
    int weight();                    // 缺省 1
    int quality();                   // 缺省 0
    List<LootFunction> functions();
}

public sealed interface CompositeEntry extends LootEntry {
    List<LootEntry> children();      // 缺省空列表
}
```

原版 26.2 注册的 9 种条目。单例条目都有 `weight`、`quality`、`conditions`、`functions`，复合条目都有 `children`、`conditions`：

| `type` | 记录 | 独有字段 |
|---|---|---|
| `empty` | `EmptyEntry` | 无 |
| `item` | `ItemEntry` | `name`：物品，不能是 `minecraft:air` |
| `loot_table` | `NestedTableEntry` | `value`：表 ID（解码时不检查是否存在），或内联的战利品表；记录中是 `EitherId<LootTable>` |
| `dynamic` | `DynamicEntry` | `name`：任意命名空间 ID（原版方块使用 `contents`、`sherds`） |
| `tag` | `TagEntry` | `name`：物品标签 ID，不带 `#`，解码时不检查是否存在；`expand`：必填 |
| `slots` | `SlotsEntry` | `slot_source`：槽位来源（5.7） |
| `alternatives` | `AlternativesEntry` | 无 |
| `sequence` | `SequenceEntry` | 无 |
| `group` | `GroupEntry` | 无 |

`TagEntry` 实现 `SingletonEntry`，`expand` 为 `true` 时求值器在抽取中按标签的物品展开（8.3）。

### 5.3 条件

```java
public sealed interface LootCondition { }
```

原版 26.2 注册的 20 种：

| `condition` | 记录 | 字段 |
|---|---|---|
| `inverted` | `InvertedCondition` | `term` |
| `any_of` | `AnyOfCondition` | `terms` |
| `all_of` | `AllOfCondition` | `terms` |
| `random_chance` | `RandomChanceCondition` | `chance`：数字提供器 |
| `random_chance_with_enchanted_bonus` | `RandomChanceWithEnchantedBonusCondition` | `unenchanted_chance`：0 到 1 的浮点数；`enchanted_chance`：等级数值（`LevelBasedValue`）；`enchantment` |
| `entity_properties` | `EntityPropertiesCondition` | `predicate`：实体谓词；`entity`：必填 |
| `killed_by_player` | `KilledByPlayerCondition` | 无 |
| `entity_scores` | `EntityScoresCondition` | `scores`：计分项到整数范围；`entity`：必填 |
| `block_state_property` | `BlockStatePropertyCondition` | `block`；`properties`：方块状态谓词，属性名必须属于这个方块 |
| `match_tool` | `MatchToolCondition` | `predicate`：物品谓词 |
| `table_bonus` | `TableBonusCondition` | `enchantment`；`chances`：非空的浮点数列表 |
| `survives_explosion` | `SurvivesExplosionCondition` | 无 |
| `damage_source_properties` | `DamageSourcePropertiesCondition` | `predicate`：伤害来源谓词 |
| `location_check` | `LocationCheckCondition` | `predicate`：位置谓词；`offsetX`、`offsetY`、`offsetZ`：缺省 0 |
| `weather_check` | `WeatherCheckCondition` | `raining`、`thundering` |
| `reference` | `ReferenceCondition` | `name`：谓词 ID，解码时不检查是否存在 |
| `time_check` | `TimeCheckCondition` | `clock`：必填，世界时钟；`period`；`value`：整数范围 |
| `value_check` | `ValueCheckCondition` | `value`：数字提供器；`range`：整数范围 |
| `enchantment_active_check` | `EnchantmentActiveCheckCondition` | `active` |
| `environment_attribute_check` | `EnvironmentAttributeCheckCondition<V>` | `attribute`；`value`：按这个属性的取值编解码器解码 |

### 5.4 物品修饰器

```java
public sealed interface LootFunction {
    List<LootCondition> conditions();
}
```

原版 26.2 注册的 43 种，按注册次序列出。除 `sequence` 外每种都有 `conditions`：

| `function` | 记录 | 字段 |
|---|---|---|
| `set_count` | `SetCountFunction` | `count`；`add`：缺省 `false` |
| `set_item` | `SetItemFunction` | `item`：不能是 `minecraft:air` |
| `enchant_with_levels` | `EnchantWithLevelsFunction` | `levels`；`options`：附魔集合；`include_additional_cost_component`：缺省 `false` |
| `enchant_randomly` | `EnchantRandomlyFunction` | `options`：附魔集合；`only_compatible`：缺省 `true`；`include_additional_cost_component`：缺省 `false` |
| `set_enchantments` | `SetEnchantmentsFunction` | `enchantments`：附魔到数字提供器；`add`：缺省 `false` |
| `set_custom_data` | `SetCustomDataFunction` | `tag`：SNBT 字符串或 NBT 对象 |
| `set_components` | `SetComponentsFunction` | `components`：组件补丁，`!` 前缀表示移除 |
| `furnace_smelt` | `SmeltFunction` | `use_input_count`：缺省 `true` |
| `enchanted_count_increase` | `EnchantedCountIncreaseFunction` | `enchantment`；`count`；`limit`：缺省 0，表示不限制 |
| `set_damage` | `SetDamageFunction` | `damage`；`add`：缺省 `false` |
| `set_attributes` | `SetAttributesFunction` | `modifiers`：`{id, attribute, operation, amount, slot}` 列表，`slot` 是单个槽位组或非空的槽位组列表；`replace`：缺省 `true` |
| `set_name` | `SetNameFunction` | `name`：文本组件；`entity`；`target`：`custom_name` 或 `item_name`，缺省 `custom_name` |
| `exploration_map` | `ExplorationMapFunction` | `destination`：结构标签，缺省 `on_treasure_maps`；`decoration`：地图装饰类型，缺省 `mansion`；`zoom`：缺省 2；`search_radius`：缺省 50；`skip_existing_chunks`：缺省 `true` |
| `set_stew_effect` | `SetStewEffectFunction` | `effects`：`{type, duration}` 列表，同一个效果不能出现两次 |
| `copy_name` | `CopyNameFunction` | `source`：实体目标名或 `block_entity` |
| `set_contents` | `SetContainerContentsFunction` | `component`：`container`、`bundle_contents` 或 `charged_projectiles`；`entries`：必填，条目列表 |
| `modify_contents` | `ModifyContentsFunction` | `component`；`modifier`：修饰器或修饰器列表 |
| `filtered` | `FilteredFunction` | `item_filter`：物品谓词；`on_pass`、`on_fail`：修饰器 |
| `limit_count` | `LimitCountFunction` | `limit`：必填，整数范围 |
| `apply_bonus` | `ApplyBonusFunction` | `enchantment`；`formula`：`binomial_with_bonus_count`、`uniform_bonus_count` 或 `ore_drops`；`parameters`：公式的参数 |
| `set_loot_table` | `SetContainerLootTableFunction` | `name`：表 ID；`seed`：缺省 0；`type`：方块实体类型 |
| `explosion_decay` | `ExplosionDecayFunction` | 无 |
| `set_lore` | `SetLoreFunction` | `lore`：最多 256 行；`mode`：必填，列表操作；`entity` |
| `fill_player_head` | `FillPlayerHeadFunction` | `entity`：必填 |
| `copy_custom_data` | `CopyCustomDataFunction` | `source`：NBT 来源；`ops`：`{source, target, op}` 列表，`op` 必填，取值 `replace`、`append`、`merge` |
| `copy_state` | `CopyBlockStateFunction` | `block`；`properties`：必填，属性名列表 |
| `set_banner_pattern` | `SetBannerPatternFunction` | `patterns`：`{pattern, color}` 列表，图案是注册表 ID 或内联定义；`append`：必填 |
| `set_potion` | `SetPotionFunction` | `id`：药水 |
| `set_random_dyes` | `SetRandomDyesFunction` | `number_of_dyes` |
| `set_random_potion` | `SetRandomPotionFunction` | `options`：药水集合 |
| `set_instrument` | `SetInstrumentFunction` | `options`：必填，乐器集合 |
| `reference` | `FunctionReference` | `name`：物品修饰器 ID，解码时不检查是否存在 |
| `sequence` | `SequenceFunction` | `functions`：带 `function` 字段的修饰器对象列表；没有 `conditions` |
| `copy_components` | `CopyComponentsFunction` | `source`：实体目标名、`block_entity` 或 `tool`；`include`、`exclude`：组件类型列表 |
| `set_fireworks` | `SetFireworksFunction` | `explosions`：最多 256 个爆炸效果加列表操作；`flight_duration`：0 到 255 |
| `set_firework_explosion` | `SetFireworkExplosionFunction` | `shape`、`colors`、`fade_colors`、`trail`、`twinkle` |
| `set_book_cover` | `SetBookCoverFunction` | `title`：可过滤文本，最长 32；`author`；`generation`：0 到 3 |
| `set_written_book_pages` | `SetWrittenBookPagesFunction` | `pages`：可过滤文本组件列表；`mode`：必填，列表操作 |
| `set_writable_book_pages` | `SetWritableBookPagesFunction` | `pages`：最多 100 页，每页最长 1024；`mode`：必填，列表操作，上限 100 |
| `toggle_tooltips` | `ToggleTooltipsFunction` | `toggles`：组件类型到布尔值 |
| `set_ominous_bottle_amplifier` | `SetOminousBottleAmplifierFunction` | `amplifier` |
| `set_custom_model_data` | `SetCustomModelDataFunction` | `floats`、`flags`、`strings`、`colors`：各自是值列表加列表操作；颜色可以写数字提供器或 RGB 颜色 |
| `discard` | `DiscardFunction` | 无 |

- 列表操作（`ListOperation`）按原版内联在修饰器对象中：`mode` 取 `append`、`insert`（`offset`）、`replace_all`、`replace_section`（`offset`、`size`）。`StandAloneOperation<T>` 是 `{values, mode}` 形式的独立列表操作。
- NBT 来源（`NbtSource`）是实体目标名或 `block_entity`，或者按 `type` 分派的 `context`（`target`）和 `storage`（`source`）。
- 实体、方块实体、工具这三种上下文来源用 `context.LootContextArg` 表示，各字段接受哪几种由它的编解码器决定。
- 引用 Minestom 静态注册表的字段解码成 Minestom 对象（`Material`、`Block`、`PotionType`、`PotionEffect`、`Attribute`、`BlockEntityType`、`DataComponent`）；引用动态注册表的字段解码成 `RegistryKey` 或 `HolderSet`（附魔、乐器、药水集合、结构标签等）。

### 5.5 数字提供器

```java
public sealed interface NumberProvider { }
```

原版 26.2 注册的 8 种：

| `type` | 记录 | 字段 |
|---|---|---|
| `constant` | `ConstantValue` | `value` |
| `uniform` | `UniformValue` | `min`、`max`：数字提供器 |
| `binomial` | `BinomialValue` | `n`、`p` |
| `score` | `ScoreboardValue` | `target`：固定名（`fixed`）或上下文实体（`context`）；`score`；`scale`：缺省 1 |
| `storage` | `StorageValue` | `storage`：命名空间 ID；`path`：NBT 路径 |
| `sum` | `SumValue` | `summands`：数字提供器列表 |
| `enchantment_level` | `EnchantmentLevelValue` | `amount`：等级数值 |
| `environment_attribute` | `EnvironmentAttributeValue` | `attribute`：取值能转换成数值的环境属性 |

- 等级数值（`LevelBasedValue`）是裸数字（`Constant`），或按 `type` 分派的 `linear`、`clamped`、`fraction`、`levels_squared`、`lookup`、`exponent`，字段和原版 `LevelBasedValue` 相同。
- 整数范围（`IntRange`）的 `min`、`max` 是可空的数字提供器。

### 5.6 谓词

包 `loot.predicate` 中的记录对应原版 `net.minecraft.advancements.predicates`，字段名、缺省值、解码时的检查与原版相同，编解码器在 `PredicateCodecs`：

- `ItemPredicate`：`items`（物品集合）、`count`（整数范围）、`components`（`ComponentMatchers`：精确匹配的组件和 `predicates` 中按类型分派的组件谓词）。组件谓词在 `predicate.component` 子包，与原版 `data_component_predicate_type` 注册的 15 种一一对应（`damage`、`enchantments`、`stored_enchantments`、`potion_contents`、`custom_data`、`container`、`bundle_contents`、`firework_explosion`、`fireworks`、`writable_book_content`、`written_book_content`、`attribute_modifiers`、`trim`、`jukebox_playable`、`villager/variant`）；`predicates` 的键也可以是任意组件类型 ID，解码成只检查组件存在的 `AnyValue`。
- `EntityPredicate`：实体子谓词的列表，子谓词在 `predicate.entity` 子包，与原版 `EntitySubPredicates` 注册的 25 种类型一一对应（实体类型、位置、距离、移动、效果、NBT、标志、装备、乘客、目标、队伍、槽位、组件、玩家、闪电、浮漂、史莱姆大小、袭击者、羊等）。
- `LocationPredicate`：`position`、`biomes`、`structures`、`dimension`、`smokey`、`light`、`block`、`fluid`、`can_see_sky`。
- `DamageSourcePredicate`：伤害类型标签、直接实体、来源实体、`is_direct`。
- `StatePropertiesPredicate`：属性名到精确值或范围的列表。

### 5.7 槽位来源

包 `loot.slot`：

- `SlotSource`：按 `type` 分派的 `group`（`terms`）、`filtered`（`slot_source`、`item_filter`）、`limit_slots`（`slot_source`、`limit`）、`slot_range`（`source`、`slots`）、`contents`（`slot_source`、`component`）、`empty`；直接写出的槽位来源列表解码成 `group`。编解码器是 `SlotSourceCodecs.CODEC`。
- `SlotRange`：原版 `SlotRanges` 登记的槽位名字（`contents`、`container.*`、`hotbar.*`、`armor.head` 等）和编号。
- `ContainerComponent`：`container`、`bundle_contents`、`charged_projectiles` 三种装有物品的组件。

### 5.8 每个类型的 referencedParams()

`LootCondition`、`LootFunction`、`NumberProvider`、`SlotSource`、`NbtSource` 约定 `referencedParams(): Set<ContextKey<?>>`，默认返回 `Set.of()`。语义沿用原版 `LootContextUser#getReferencedContextParams`：返回该记录自身直接引用的上下文参数集合，不合并嵌套的子条件、子修饰器、子数字提供器。每个记录按原版同名类的方法的返回值实现：

- 不引用任何参数的记录使用接口的默认实现；原版复合类（`AllOfCondition`、`SequenceFunction`、`SumValue` 等）自己不引用参数，同样返回空集。
- 自身引用参数的记录（例如 `KilledByPlayerCondition` 返回 `Set.of(LAST_DAMAGE_PLAYER)`）覆盖这个方法。

嵌套结构的参数覆盖检查由外层逐个节点完成，对应原版 `Validatable#validate(ValidationContext)` 由外向里的递归：`LootPool.validate` 对 conditions、functions、entries 各自调用 `Validatable.validate(...)`，复合条件（原版 `CompositeLootItemCondition#validate`）先对当前节点执行 `ValidationContext#validateContextUsage(this)`，再对它的 `terms` 逐个 `validate`。原版 `ValidationContext#validateContextUsage` 只读取当前节点的 `getReferencedContextParams()` 与参数集合求差集。本模块沿用同一约定：每个节点只按自身的 `referencedParams()` 校验，外层调用方负责递归到子节点（见 8.4）。

`LootEntry` 接口本身没有 `referencedParams()`；调用方在池、表、嵌套容器里拿到具体条目之后，从它的 `conditions()`、`functions()`、槽位来源，以及池的 `rolls()`、`bonusRolls()` 合并。

## 6. LootManager：注册与查询

```java
public final class LootManager {
    public LootManager(LootTypeRegistry types);
    public void replaceAll(NavigableMap<Key, LootTable> tables);
    public Optional<LootTable> get(Key id);
    public NavigableMap<Key, LootTable> tables();
    public LootTypeRegistry types();
}
```

- `replaceAll` 整体替换当前生效的集合：调用前由订阅者准备好新集合；调用后旧 ID 全部失效，快照整体切换，`get` / `tables()` 读取当前快照。方法和 `crafting` 的 `RecipeManager.replaceRecipes` 含义相同。
- `replaceAll` 在替换之前先校验（第 7 节），校验失败抛出 `LootException`，当前集合保持不变。
- 查询方法读取快照，可在任何线程调用。求值器经 `LootManager.get(id)` 取得当前 ID 对应的 `LootTable`。
- 本模块不向客户端发送任何数据包：战利品表是服务端数据，26.2 的注册表同步不包含它。

## 7. 校验与 LootContents

原版把战利品表、谓词、物品修饰器作为同一种 loot 资源在 `ReloadableServerResources` 中加载，之后在 `ValidationContext` 上校验。本模块把检查分成两步：单个文件内的取值在解码时检查（`loot-table-parser` 用 `LootCodecs` 解码），跨文件的引用在 `LootManager.replaceAll` 中校验。

```java
public record LootContents(Set<Key> itemTags, Set<Key> tables, List<LootTable> nested) {
    public static LootContents inspect(LootTable table);
}
```

`LootContents.inspect` 沿条目和修饰器深度优先遍历，只读取记录的字段：`tag` 条目的 `name` 和 `match_tool` 物品谓词中以标签给出的 `items` 进入 `itemTags`；`loot_table` 条目的 ID 形式和 `set_loot_table` 修饰器的 `name` 进入 `tables`；`loot_table` 条目的内联表按出现顺序进入 `nested`，内联表自身按同一规则继续遍历。

`replaceAll` 的校验规则：

1. `loot_table` 条目的 ID 形式、以及 `set_loot_table` 修饰器的 `name`，必须在被加载的集合中存在，否则报错 `Loot table <id> references missing loot table: <ref>`。
2. 不允许递归：任何表经引用链直接或间接指回自己时报错 `Detected infinite loop in loot tables: <id> -> <ref>`；内联表参与同一条检查。
3. 复合条目完全展开后的总权重不能超过原版 `WeightedRandom` 的上限 `Integer.MAX_VALUE`，本模块在注册阶段提前拦截。`tag(expand=true)` 的成员数量在注册阶段未知，按条目自身的 `weight` 计算一次。

解码阶段检查的内容不在这里重复：注册表引用存在、物品谓词等位置的标签存在、枚举取值合法、数值在范围内。`tag` 条目的标签与原版相同，在解码和注册时都不检查是否存在，求值时找不到按空标签处理。

任何一条不满足就抛出 `LootException`，异常信息写明表 ID；本次 `replaceAll` 不做任何修改。

## 8. 求值

求值让使用者在拿到上下文之后直接得到 `ItemStack` 列表，对应原版 `LootTable.getRandomItems(LootParams, long)` 的用法。

```java
public final class LootSelector {
    public LootSelector(ServerProcess process, LootManager manager, LootTypeRegistry types,
                        LootEnvironment environment);

    public List<ItemStack> roll(LootTable table, LootContext context, Random random);
    public List<ItemStack> roll(Key tableId, LootContext context, Random random);  // 表必须存在
}
```

- `process` 给求值提供注册表（物品、附魔、方块、伤害类型等）的运行时查找入口。
- `manager` 是当前生效的战利品表集合；`roll(Key, ...)` 在这里查找表，找不到时报错 `Missing loot table: <id>`；`roll(LootTable, ...)` 用于内联表和宿主直接构造的表。
- `environment` 提供求值期的服务端环境，见 8.2。

`LootEvaluation` 公开求值用到的辅助方法（数字提供器取值、整数范围、上下文参数按类型读取、附魔等级），供宿主自己的类型复用。

### 8.1 LootContext

```java
public sealed interface LootContext permits LootContextImpl {
    Set<ContextKey<?>> params();
    <T> T require(ContextKey<T> key);
    <T> @Nullable T find(ContextKey<T> key);
    float luck();
    Key contextType();
    ServerProcess process();
    @Nullable Instance level();
    Random random();
}

public record ContextKey<T>(Key id) {}
```

- `ContextKey` 常量沿用原版 `LootContextParams` 的名字：`THIS_ENTITY`、`INTERACTING_ENTITY`、`TARGET_ENTITY`、`LAST_DAMAGE_PLAYER`、`DAMAGE_SOURCE`、`ATTACKING_ENTITY`、`DIRECT_ATTACKING_ENTITY`、`ORIGIN`、`BLOCK_STATE`、`BLOCK_ENTITY`、`TOOL`、`EXPLOSION_RADIUS`、`ENCHANTMENT_LEVEL`、`ENCHANTMENT_ACTIVE`、`ADDITIONAL_COST_COMPONENT_ALLOWED`。wiki *Loot table* 里的 `attacker`、`direct_attacker`、`attacking_player` 等名字对应这里的 `ATTACKING_ENTITY`、`DIRECT_ATTACKING_ENTITY`、`LAST_DAMAGE_PLAYER`。
- 参数值的类型：实体参数是 `Entity`，`DAMAGE_SOURCE` 是 `Damage`，`ORIGIN` 是 `Point`，`BLOCK_STATE` 和 `BLOCK_ENTITY` 是 `Block`（方块实体参数是带有方块实体类型与 NBT 的方块），`TOOL` 是 `ItemStack`，`EXPLOSION_RADIUS` 是 `Float`，`ENCHANTMENT_LEVEL` 是 `Integer`，`ENCHANTMENT_ACTIVE` 和 `ADDITIONAL_COST_COMPONENT_ALLOWED` 是 `Boolean`。求值读取到类型不符的值时抛出 `LootException`：`<参数 ID> must be a <类型>, but was <实际类型>`。
- 求值读取可选参数使用 `find`，对应原版 `getOptionalParameter`。
- `level()` 是取用所在的世界，对应原版 `LootContext#getLevel`。原版的上下文总有世界；本模块允许宿主不给出世界，这时需要世界的求值（位置、天气、时间条件等）抛出 `LootException`：`The loot context has no level, but the evaluation needs one`。
- 原版 `LootContext.getLuck` 把玩家运气属性和钓竿的 `fishing_luck_bonus` 相加，在钓鱼以外只使用属性。`LootContextImpl` 在构造时经 `LootEnvironment.luckOf` 按原版规则计算，宿主只需要在构造时给出玩家和工具（见 `LootContexts`）。

`LootContexts` 提供原版全部 26 种上下文类型的快捷构造，最后两个参数固定是世界和 `LootEnvironment`，可选参数传入 `null`，例如：

```java
LootContext block = LootContexts.block(blockState, origin, tool,
        thisEntityOrNull, blockEntityOrNull, explosionRadiusOrNull, instance, environment);   // type=block
LootContext entity = LootContexts.entity(thisEntity, origin, damageSource,
        attackerOrNull, directAttackerOrNull, lastDamagePlayerOrNull, instance, environment); // type=entity
LootContext fishing = LootContexts.fishing(tool, origin, thisEntityOrNull, instance, environment);
LootContext empty = LootContexts.empty(instance, environment);
```

每个构造的必需和可选参数沿用原版 `LootContextParamSets` 对该类型的划分；宿主需要其他组合时可以直接构造 `LootContextImpl`。

### 8.2 LootEnvironment

```java
public interface LootEnvironment {
    ServerProcess process();
    ScoreboardView scoreboards();                   // score 数字提供器、entity_scores 条件
    StorageView storage();                          // storage 数字提供器、copy_custom_data 的 storage 来源
    SmeltingView smelting();                        // furnace_smelt
    float luckOf(@Nullable Player player, @Nullable ItemStack tool, Key contextType);
    Stream<ItemStack> dynamicLoot(Key name);        // dynamic 条目
    Optional<LootFunction> findFunction(Key id);    // 修饰器的 reference
    Optional<LootCondition> findCondition(Key id);  // 条件的 reference
    long worldSeed();                               // random_sequence
}
```

宿主按自己的运行环境给出实现：

- `ScoreboardView` 读取记录名在计分项上的分值，并给出实体的记录名（玩家是玩家名，其他实体是 UUID 字符串）。
- `StorageView.root(storage)` 返回整个命令存储的根标签，存储不存在时返回空的复合标签。
- `SmeltingView.smelt(input)` 返回与输入匹配的熔炼配方的结果，宿主可以用 `crafting` 的熔炼配方实现。
- `worldSeed()` 是世界种子：Minestom 的 `Instance` 只保存散列后的种子，无法取回原始种子，因此由宿主给出。

### 8.3 roll 的流程

按原版 `LootTable.getRandomItemsRaw` 相同的步骤：

1. 上下文参数覆盖检查，见 8.4。
2. 随机数源：表声明了 `random_sequence` 时，按 `LootEnvironment.worldSeed()` 和序列 ID 派生稳定序列（原版 `RandomSequence`：序列 ID 的 MD5 散列与世界种子混合后装入 Xoroshiro128++），否则使用传入的 `random`。上下文的随机数源替换成选定的随机数源。
3. 遍历每个池：池的 `conditions` 全部通过才使用。
4. 每个池的抽取次数 = `rolls.getInt(context) + floor(bonusRolls.getFloat(context) * context.luck())`。`getInt` 按各数字提供器自己的规则计算：`uniform` 在两端取整之后的整数中均匀选取，`binomial` 按次数抽样，`sum` 是浮点和向下取整，`storage` 读取整数，其余按 `getFloat` 四舍五入。
5. 每次抽取按原版 `LootPoolEntryContainer.expand` 展开全部条目：
   - 条目自身的条件不满足时展开失败；
   - 单例条目展开成一个候选项；`tag(expand=true)` 为标签的每个物品产出一个候选项，标签为空也算展开成功；
   - `group` 展开全部子条目，总是成功；`alternatives` 选取第一个展开成功的子条目，没有子条目时失败；`sequence` 依次展开到第一个失败的子条目为止，没有子条目时成功。

   候选项的权重是 `max(floor(weight + quality * luck), 0)`，只保留权重大于 0 的候选项。没有候选项时不产出；只有一个候选项时直接产出，不消耗随机数；否则用 `nextInt(总权重)` 依次减去权重，小于 0 时选中。
6. 被选中的候选项产出物品：
   - `item`：一件该物品；
   - `tag(expand=false)`：标签中每个物品各一件；`tag(expand=true)` 展开出的候选项：一件对应物品，不经过条目的修饰器；
   - `empty`：不产出；
   - `loot_table`：按 ID 或内联表递归取用，嵌套超过 64 层时报错；
   - `dynamic`：`environment.dynamicLoot(name)`；
   - `slots`：槽位来源提供的全部非空物品（原版 `SlotLoot`）。
7. 修饰器按条目、池、表的顺序依次应用（原版 `LootItemFunction.decorate` 层层包装）。每种修饰器按原版同名类的 `run` 移植；修饰器自身的条件不满足时物品不变；`discard` 产出空物品。
8. 表的产出经过原版 `LootTable.createStackSplitter`：数量不小于最大堆叠数的物品按最大堆叠数切分。最后把完全相同的物品按最大堆叠数合并，空物品不进入结果。

### 8.4 上下文参数覆盖检查

取用之前，求值器从表开始递归遍历池、条目、条件、修饰器、数字提供器、槽位来源、NBT 来源、`set_contents` 的条目、能找到的 ID 形式嵌套表，以及经 `LootEnvironment` 能找到的 `reference` 目标，收集每个节点 `referencedParams()` 中不在 `LootContext` 里的参数。有缺失时抛出 `LootException`，文案与原版 `ValidationContext$ParametersNotProvidedProblem` 相同：`<参数 ID> is not provided in this context`，多个参数按 ID 排序、以逗号连接，写作 `are not provided in this context`。

### 8.5 reference 的求值

修饰器 `function: "minecraft:reference"` 的 `name` 指向物品修饰器，条件 `condition: "minecraft:reference"` 的 `name` 指向谓词。求值时经 `LootEnvironment.findFunction` / `findCondition` 查找；找不到时抛出 `LootException`：`Unknown function: <id>` / `Unknown condition: <id>`。

## 9. 与原版行为的差异

### 9.1 加载与求值规则

1. 原版三种 loot 资源（`loot_table` / `predicate` / `item_modifier`）通过 `LootDataType` 统一加载，`/reload` 一并重载并允许 `reference` 互相引用。本模块的 `LootManager` 只保管战利品表本身；谓词和物品修饰器由宿主经 `LootEnvironment` 提供。原版找不到 `reference` 的目标时记录日志，条件按不通过处理、修饰器按原样返回；本模块抛出异常。
2. 原版在加载阶段按表的 `type` 检查上下文参数，不匹配时只记录日志；取用时缺少的可选参数按不存在处理（例如没有玩家时 `killed_by_player` 不通过）。本模块在每次取用之前检查表引用的全部参数都在本次的上下文中（8.4），缺少时抛出异常。
3. 原版的递归检查在验证阶段记录日志 "Detected infinite loop in loot tables"，取用时才抛出 `IllegalStateException`；本模块在注册阶段检测，任何递归都视为注册失败。
4. 原版的 `random_sequence` 状态保存在世界中，同一个序列的连续取用得到不同结果；本模块每次取用都从序列起点重新派生。稳定序列的 `nextLong`、`nextFloat` 与原版 `XoroshiroRandomSource` 相同，`nextInt`、`nextBoolean`、`nextDouble` 使用 `java.util.Random` 的算法，结果与原版不同。
5. 原版 `getRandomItems` 返回拆分之后的物品列表；本模块再把完全相同的物品合并（8.3 第 8 步）。原版拆分时跳过未启用的物品（`isItemEnabled`），Minestom 没有功能开关，本模块不执行这一步。
6. Minestom 的 `ItemStack` 数量不大于 0 时变成空气，无法保存数量为 0 的物品。原版中数量减到 0 的物品仍保留物品类型，之后的 `set_count`（`add=true`）能让它恢复；本模块中它保持为空气。
7. 原版的上下文总有世界和 `RandomSource`；本模块的世界可以为空（8.1），随机数源是 `java.util.Random`。

### 9.2 Minestom 缺少数据时的处理

求值用到 Minestom 不提供的数据时，抛出 `LootEvaluation.unsupported(...)` 创建的 `LootException`（`Not supported on Minestom: <内容>`），代码中每个调用处带有写明缺少哪些数据的待办注释，`UnsupportedEvaluationTest` 中每处至少有一个测试：

| 位置 | 缺少的数据 | 测试 |
|---|---|---|
| 生物群系（`BiomeZoom`） | 未加载区块的生物群系来源 | `biomeOfUnloadedCorner` |
| 实体槽位（`EntitySlots`） | 箭拾取时得到的物品 | `arrowPickupItem` |
| 实体槽位 | 运输矿车、运输船的容器内容 | `chestMinecartContents` |
| 实体槽位 | 驴、骡、羊驼箱子中的物品 | `chestedHorseInventory` |
| 实体槽位 | 村民、猪灵、掠夺者的背包 | `villagerInventory` |
| 实体槽位 | 玩家的末影箱 | `playerEnderChest` |
| 位置谓词 `structures` | 结构 | `structures` |
| 位置谓词 `can_see_sky` | 天空光照高度图 | `canSeeSky` |
| 位置谓词 `light` 带范围时 | 环境属性计算的天空变暗值 | `boundedLight` |
| 方块谓词 `components` | 方块实体的组件 | `blockEntityComponents` |
| 槽位来源 `slot_range` 读取方块实体 | 方块实体的容器内容 | `blockEntityAsSlotSource` |
| 方块状态谓词对枚举属性使用范围 | 枚举属性取值的声明顺序 | `rangeOnEnumProperty` |
| 实体子谓词 `stepping_on` | 实体脚下支撑它的方块 | `steppingOnWhileOnGround` |
| 实体子谓词 `movement_affected_by` | 影响实体移动的方块 | `movementAffectedBy` |
| 实体子谓词 `nbt` | 实体保存时写出的 NBT | `entityNbt` |
| 实体子谓词 `entity_tags` | 实体的命令标签 | `entityTags` |
| 实体子谓词 `lightning` 带条件时 | 闪电点燃的方块数和击中的实体 | `lightningWithConditions` |
| 实体子谓词 `fishing_hook` | 浮漂是否在开阔水域 | `fishingHookInOpenWater` |
| 实体子谓词 `raider` | 袭击与队长 | `raider` |
| 实体子谓词 `distance` 中的下落距离 | 实体的下落距离 | `fallDistance` |
| 实体标志 `is_in_water` | 实体是否接触水 | `isInWater` |
| 玩家子谓词 `recipes` | 玩家的配方书 | `playerRecipes` |
| 玩家子谓词 `advancements` | 玩家的进度 | `playerAdvancements` |
| 数字提供器 `environment_attribute` | 环境属性在某个位置的取值 | `environmentAttributeNumber` |
| 没有 Minestom 注册表的元素集合写成标签（属性、村民类型） | 注册表与标签 | `attributeTag`、`villagerTypeTag` |
| 条件 `environment_attribute_check` | 环境属性在某个位置的取值 | `environmentAttributeCheck` |
| 修饰器 `exploration_map` | 结构与地图数据 | `explorationMap` |
| 修饰器 `copy_name` 读取方块实体 | 方块实体的自定义名称 | `copyNameFromBlockEntity` |
| 修饰器 `set_name`、`set_lore` 带 `entity` | 按实体解析文本组件 | `resolveTextForEntity` |
| 修饰器 `copy_custom_data` 读取实体 | 实体保存时写出的 NBT | `copyCustomDataFromEntity` |
| 修饰器 `copy_components` 读取方块实体 | 方块实体的组件 | `copyComponentsFromBlockEntity` |

### 9.3 其余可见的差异

1. `entity_properties` 的 `looking_at`：原版按方块的碰撞箱逐格追踪两眼之间的连线；本模块按原版检查世界和 128 格的距离，连线是否被挡住由 Minestom 的 `Entity#hasLineOfSight` 判断。
2. 实体子谓词 `movement`：原版玩家的速度来自客户端上报的移动；本模块使用玩家最近一次位置更新的位移，其他实体使用 Minestom 的速度。
3. `weather_check` 读取 `Instance#getWeather`，它是天气过渡的目标值，过渡中的取值 Minestom 不对外提供；原版读取过渡中的当前雨量和雷暴强度，天气过渡期间两者可能不同。
4. 位置谓词的“已加载”检查：原版还要求坐标在世界生成的水平范围之内，这个范围远大于 Minestom 能使用的坐标，本模块不检查。
5. `copy_custom_data` 读取方块实体：上下文中的方块参数没有位置，结果只有方块实体的数据加上 `id`，没有原版写入的 `x`、`y`、`z`。
6. `copy_custom_data` 的某个操作合并失败时，原版就地修改的标签可能保留这一步的部分修改；本模块保留这一步之前的结果。
7. `set_stew_effect` 中同一个效果出现两次时，原版的错误信息打印效果对象（它没有自己的 `toString`）；本模块在该位置打印效果 ID。
8. `environment_attribute_check` 的 `value` 使用 Minestom 的取值编解码器，原版额外检查的取值范围不在其中。经 `dfu-codecs` 适配的 Minestom 编解码器只报告第一个错误，错误文本是 Minestom 的。
9. Minestom 的 `Color(int)` 丢弃最高 8 位：烟花颜色和 `set_custom_model_data` 的颜色经 `Color` 保存，原版保存完整的整数。

## 10. 包结构

根包 `io.ib67.yuanban.loot`：

| 包 | 内容 |
|---|---|
| `loot` | `LootTable`、`LootPool`、`LootManager`、`LootTypeRegistry`、`LootContents`、`LootException`、`EitherId` |
| `loot.type` | `LootEntryType`、`LootConditionType`、`LootFunctionType`、`NumberProviderType` |
| `loot.codec` | `LootCodecs`；各类型编解码器的工厂方法 `EntryCodecs`、`ConditionCodecs`、`FunctionCodecs`、`NumberCodecs`；谓词与槽位来源的编解码器 `PredicateCodecs`、`SlotSourceCodecs`；包内共用的 `CommonCodecs` |
| `loot.context` | `LootContext`、`LootContextImpl`、`ContextKey`、`LootContexts`、`LootContextArg`、`LootEnvironment`、`ScoreboardView`、`StorageView`、`SmeltingView` |
| `loot.entry` | `LootEntry`、`SingletonEntry`、`CompositeEntry`，9 个条目记录 |
| `loot.condition` | `LootCondition`，20 个条件记录 |
| `loot.function` | `LootFunction`，43 个修饰器记录，以及列表操作、`StandAloneOperation`、`ApplyBonusFormula`、`NbtSource`、`CopyOperation`、`MergeStrategy`、`AttributeModifierRecord`、`StewEffectEntry`、`SetNameTarget` |
| `loot.number` | `NumberProvider`，8 个数字提供器记录，以及 `IntRange`、`LevelBasedValue`、`EntityTarget`、`ScoreboardName` |
| `loot.predicate` | `ItemPredicate`、`ComponentMatchers`、`CollectionPredicate`、`LocationPredicate`、`DamageSourcePredicate`、`StatePropertiesPredicate`；子包 `component`（组件谓词）、`entity`（实体谓词与子谓词） |
| `loot.slot` | `SlotSource`、`SlotRange`、`ContainerComponent` |
| `loot.eval` | `LootSelector`、`LootEvaluation`；包内移植原版求值的辅助类：`Mth`、`SequenceRandom`、`ItemPredicates`、`ComponentPredicates`、`EntityPredicates`、`LocationPredicates`、`StatePredicates`、`FluidStates`、`BiomeZoom`、`Holders`、`EntitySlots`、`SlotSources`、`NbtPaths`、`ListOperations`、`Enchanting`、`ContainerContents` |

## 11. 测试

`LootManager`、`LootContents`、`LootTypeRegistry` 和各记录的测试不启动 Minestom 进程，直接构造记录：

- 注册与查询（`LootManagerTest`）：`replaceAll` 之后 `get` / `tables()` 返回新内容；再次替换后旧 ID 不再存在；快照在两次替换之间不被修改。校验：引用缺失、直接递归、间接递归（A → B → A）、内联表递归、复合条目超长各一个用例，异常信息能定位到表 ID；`tag` 条目进入 `itemTags`、内联表进入 `nested`。
- 类型注册表（`LootTypeRegistryTest`、`PoolAndTableTest`）：`vanilla()` 登记全部 80 种类型，没有 `function` 修饰器；类型记录的编解码器不能为 `null`；重复注册同一 ID 和使用 `minecraft` 命名空间注册时抛出异常；`isKnownType` 接受 26 种上下文类型。
- 记录（`EntryRecordsTest`、`ConditionRecordsTest`、`FunctionRecordsTest`、`NumberRecordsTest`）：构造器的缺省值和不可变副本。
- `referencedParams()`（`referenced` 包）：每个记录的返回值与原版 `getReferencedContextParams` 相同。

编解码器和求值的测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程，`LootEnvironment` 使用本模块 testFixtures 里的 `InMemoryLootEnvironment`：

- 编解码器（`LootCodecsTest`）：数字提供器、整数范围、条件列表、修饰器列表的各种写法；每种类型的必填字段、缺省值和取值检查，错误用例断言原版的错误文本（未知类型、`Item must not be minecraft:air`、`No key <字段>`、`Missing tag`、`Block ... has no property<名字>`、`No formula type with id`、列表长度上限等）。
- 取用（`LootSelectorRollsTest`）：同一随机数种子重复取用产出相同；声明 `random_sequence` 的表不受传入随机数源的影响；`blocks/grass_block` 的等价结构按工具产出泥土或草方块；`random_chance` 取 0 和 1 的情况；权重为 0 的候选项不参与抽取，只剩一个候选项时不消耗随机数；品质按运气改变权重；三种复合条目没有子条目时的展开结果；展开的标签物品不经过条目的修饰器；不存在的标签按空标签处理。
- 条件（`LootSelectorConditionsTest`）和修饰器（`LootSelectorModifiersTest`）：20 种条件和 43 种修饰器各自按原版规则的结果。
- 谓词（`PredicateEvaluationTest`）和槽位来源（`SlotSourceTest`）：物品、实体、位置、伤害来源、方块状态谓词，以及各种槽位来源。
- 求值辅助方法（`LootEvaluationTest`）：数字提供器的取整、等级数值、范围、上下文参数的类型检查。
- 覆盖检查（`LootSelectorCoverageTest`）：嵌套在条件、修饰器、数字提供器、槽位来源、NBT 来源中的参数缺失时按原版文案报错。
- Minestom 缺少数据的位置（`UnsupportedEvaluationTest`）：9.2 表中每处一个测试，断言抛出 `LootException`。
