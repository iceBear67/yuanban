# loot 设计

## 1. 职责

`loot` 提供战利品表系统：

- 数据类型：原版 26.2 全部战利品条目类型 9 种（`item`/`loot_table`/`dynamic`/`empty`/`slots`/`tag`/`group`/`alternatives`/`sequence`）、条件类型 20 种、物品修饰器类型 44 种、数字提供器类型 8 种（共 81 种）对应的不可变 record，以及由它们组成的战利品池（`LootPool`）和战利品表（`LootTable`）。
- 类型注册表：`LootTypeRegistry` 保管条目、条件、修饰器、数字提供器四种类型，解析器按 JSON 的 `type` / `condition` / `function` 字段从注册表查类型，宿主可以注册自己的类型。
- 存储与查询：`LootManager` 保管当前生效的战利品表集合，按 ID 查询。
- 内容提供器：`LootContents` 给出一张表引用到的全部物品标签、战利品表、嵌套的内联战利品表；存入 `LootManager` 时做引用完整、嵌套不递归、复合条目展开有界的校验。
- 求值：`LootSelector.roll(...)` 接收取用环境和随机数源，产出最终生成的一组物品堆。`LootContexts` 提供原版 20 种上下文类型的快捷构造。

战利品表文件从数据包解码、注册进本系统的工作由 `loot-table-parser` 完成，见 [loot-table-parser/DESIGN.md](../loot-table-parser/DESIGN.md)。本模块不依赖 `datapack-loader`，任何来源的战利品表都可以交给 `LootManager`。

原版行为对照 26.2 服务端（`server-26.2.jar`，SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 的 bundle 中取出）反编译代码：`net.minecraft.world.level.storage.loot` 包中的 `LootTable.getRandomItemsRaw` / `createStackSplitter`、`LootPool.addRandomItems`、`LootContext`、`LootParams`、`LootContext.getLuck`、`LootPoolEntryContainer.expand`、`LootItemFunction.run`、`LootDataType`、`ValidationContext`、`entries` 包全部 10 种条目、`predicates` 包全部 20 种条件、`functions` 包全部 44 种修饰器、`providers.number` 包全部 8 种数字提供器、`parameters.LootContextParamSets`、`BuiltInLootTables`，以及 `ReloadableServerResources` 的加载次序。minecraft.wiki 的 *Loot table* 页面记载了文件布局和 `type` / `pools` / `rolls` / `bonus_rolls` / `weight` / `quality` 等字段的含义；类型 ID 和字段名以服务端 jar 注册的为准。

## 2. 术语

| 术语 | 含义 |
|---|---|
| 战利品表（loot table） | 一段战利品配置。原版存放在 `data/<ns>/loot_table/**.json`，代码中构造的表也同样表示。 |
| 战利品池（pool） | 战利品表中的一个列表项，每次取用先按 `rolls` 确定抽取次数，每次抽取从池内加权随机选一个条目。 |
| 条目（entry） | 池中的元素：单例条目（`item`、`loot_table`、`dynamic`、`empty`、`slots`）、标签条目（`tag`，`expand` 决定视为单例还是复合条目）、复合条目（`group`、`alternatives`、`sequence`）。 |
| 条件（condition） | 谓词，原版字段名 `conditions`。pool、entry、function 上的 `conditions` 是条件列表，全部通过才算通过。 |
| 物品修饰器（item modifier，原版 JSON 字段名 `functions`） | 对取出的物品堆做的处理：设置数量、设置组件、按魔咒加成等。 |
| 数字提供器（number provider） | 数值的来源：`constant`、`uniform`、`binomial`、`score`、`storage`、`sum`、`enchantment_level`、`environment_attribute`。 |
| 上下文（loot context） | 一次取用的运行环境：必需和可选的上下文参数、运气值、随机数源，对应原版 `LootContext`。 |
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
    testImplementation("net.minestom:testing:2026.10.05-26.2")
}
```

- 全部命名空间 ID 使用 `net.kyori.adventure.key.Key`，和 `datapack-loader`、Minestom 注册表共用同一类型。
- Minestom 直接出现在 `api` 中：`LootSelector.roll` 的返回值是 `ItemStack` 列表，`LootEnvironment` 暴露 `ServerProcess`，都必须在公开 API 签名里出现。
- `java-test-fixtures` 用来向 `loot-table-parser` 提供复读用测试环境（见 `loot-table-parser` 的测试一节）。

## 4. 类型注册表

`LootTypeRegistry` 保管四组类型对象。原版 26.2 注册的 10 + 20 + 44 + 8 个类型在 `LootTypeRegistry.vanilla()` 里全部注册：

```java
public final class LootTypeRegistry {
    public static LootTypeRegistry vanilla();
    public void registerEntry(Key id, LootEntryType<?> type);
    public void registerCondition(Key id, LootConditionType<?> type);
    public void registerFunction(Key id, LootFunctionType<?> type);
    public void registerNumberProvider(Key id, NumberProviderType<?> type);
    public Optional<LootEntryType<?>> entry(Key id);
    public Optional<LootConditionType<?>> condition(Key id);
    public Optional<LootFunctionType<?>> function(Key id);
    public Optional<NumberProviderType<?>> numberProvider(Key id);
}
```

类型对象（`LootEntryType` 等）是带 `MapCodec`-式解码描述的记录，类型 ID 例如 `minecraft:item`、`minecraft:random_chance`、`minecraft:set_count`、`minecraft:uniform`。重复注册同一 ID、或用 `minecraft` 命名空间注册自定义类型时抛出 `IllegalArgumentException`；原版没注册的 ID 在解析器里按原版文案报错【loot-table-parser 第 4 节给出原文】。

宿主注册自己的类型时，类型 ID 必须使用自己的命名空间。每个类型对象提供 `decode(JsonObject, LootReading)` 把 JSON 转成不可变实例，以及 `referencedParams()` 返回求值所需的上下文参数集合（见第 7.3 节）。

## 5. 数据模型

全部类型都在 `io.ib67.yuanban.loot` 包及其子包下，是不可变 record，字段沿用原版 26.2 注册的 `MapCodec` 的字段名和缺省值。

### 5.1 战利品表与战利品池

```java
public record LootTable(Key type,                          // 默认 minecraft:generic
                        List<LootFunction> functions,
                        List<LootPool> pools,
                        Optional<Key> randomSequence) {}

public record LootPool(List<LootCondition> conditions,
                       List<LootFunction> functions,
                       NumberProvider rolls,
                       NumberProvider bonusRolls,   // 默认 constant(0)
                       List<LootEntry> entries) {}
```

`LootTable`、`LootPool` 是 record：`LootTable` 的紧凑构造器在 `type` 缺省的时候填入 `minecraft:generic`、`randomSequence` 缺省的时候填入 `Optional.empty()`；`LootPool` 的紧凑构造器在 `bonusRolls` 缺省的时候填入 `constant(0)`。

`LootTable` 的 `type` 取值来自原版 `LootContextParamSets` 注册的 20 种：`generic`（代码中叫 `EMPTY`）、`chest`、`command`、`selector`、`villager_trade`、`fishing`、`entity`、`equipment`、`archaeology`、`gift`、`barter`、`vault`、`advancement_reward`、`advancement_entity`、`advancement_location`、`block_use`、`block`、`shearing`、`entity_interact`、`block_interact`。未知取值在解析阶段报错。

### 5.2 条目

```java
public sealed interface LootEntry permits SingletonEntry, TagEntry, CompositeEntry {}

public sealed interface SingletonEntry extends LootEntry {
    int weight();     // 默认 1
    int quality();    // 默认 0
}

public sealed interface CompositeEntry extends LootEntry {
    List<LootEntry> children();
}
```

原版 26.2 注册的 9 种条目类型（minecraft:tag 同时承担单例和复合两种形态，按 `expand` 字段分派；表格里拆开写只是为了列出两种语义对应的 record 状态）：

| `type` | record | 独有字段 |
|---|---|---|
| `item` | `ItemEntry` | `name`（物品 ID） |
| `loot_table` | `NestedTableEntry` | `value`：表 ID，或内联 `LootTable` |
| `dynamic` | `DynamicEntry` | `name`（`contents` 或 `sherds`） |
| `empty` | `EmptyEntry` | 无 |
| `slots` | `SlotsEntry` | `slot_source`（槽位来源内联对象） |
| `tag`（`expand=false`） | `TagEntry` | `name`（物品标签 ID）、`expand` |
| `tag`（`expand=true`） | `TagEntry` | 同上 |
| `group` | `GroupEntry` | `children` |
| `alternatives` | `AlternativesEntry` | `children` |
| `sequence` | `SequenceEntry` | `children` |

`slots` 的 `slot_source` 在原版数据里没使用，字段按原版保留；求值器遇到它直接抛出"当前版本不支持 slots 条目"。

### 5.3 条件

```java
public sealed interface LootCondition { }
```

原版 26.2 注册的 20 种：`all_of`、`any_of`、`block_state_property`、`damage_source_properties`、`enchantment_active_check`、`entity_properties`、`entity_scores`、`environment_attribute_check`、`inverted`、`killed_by_player`、`location_check`、`match_tool`、`random_chance`、`random_chance_with_enchanted_bonus`、`reference`、`survives_explosion`、`table_bonus`、`time_check`、`value_check`、`weather_check`。

- `reference`（原版 `ConditionReference`）只保存 `name`，求值时经 `LootConditionResolver` 查找，解析不展开。
- `random_chance` 的 `chance` 是浮点数字提供器（裸数字按 `constant` 解析）；原版允许负数，按不通过处理。
- 其余条件的字段按原版 `LootItemConditions` 注册的 `MapCodec` 保留。

### 5.4 物品修饰器

```java
public sealed interface LootFunction { }
```

原版 26.2 注册的 44 种修饰器按 `LootItemFunctions` 注册，字段照原版 `MapCodec` 保留：

| `function` | record | 字段 |
|---|---|---|
| `set_count` | `SetCountFunction` | `count`（数字提供器）、`add`（默认 `false`） |
| `set_damage` | `SetDamageFunction` | `damage`（`[0,1]` 的数字提供器）、`add` |
| `set_components` | `SetComponentsFunction` | `components`（组件补丁对象，原样保存） |
| `set_item` | `SetItemFunction` | `id`（物品 ID） |
| `set_name`、`set_lore` | `SetNameFunction`、`SetLoreFunction` | `name`/`lore`、`target` |
| `apply_bonus` | `ApplyBonusFunction` | `enchantment`、`formula`（`binomial_with_bonus_count`、`uniform_bonus_count`、`ore_drops`）、`parameters` |
| `enchanted_count_increase` | `EnchantedCountIncreaseFunction` | `enchantment`、`count`、`limit`（默认 `0` 表不限制） |
| `enchant_randomly` | `EnchantRandomlyFunction` | `options`（魔咒 ID 列表或标签）、`include_compatible`（默认 `true`） |
| `enchant_with_levels` | `EnchantWithLevelsFunction` | `levels`、`options`、`include_treasure`（默认 `false`） |
| `limit_count` | `LimitCountFunction` | `limit`（`{min?, max?}`） |
| `furnace_smelt` | `SmeltFunction` | 无 |
| `explosion_decay` | `ExplosionDecayFunction` | 无 |
| `discard` | `DiscardFunction` | 无 |
| `copy_components` | `CopyComponentsFunction` | `source`、`include` / `exclude` |
| `copy_name` | `CopyNameFunction` | `source` |
| `copy_state` | `CopyStateFunction` | `block`、`properties` |
| `set_enchantments` | `SetEnchantmentsFunction` | `enchantments`、`add` |
| `set_potion` | `SetPotionFunction` | `id`（药水 ID） |
| `set_stew_effect` | `SetStewEffectFunction` | `effects` |
| `sequence` | `SequenceFunction` | `functions`（按顺序应用） |
| `reference` | `FunctionReference` | `name`（`item_modifier` 中的 ID） |
| `filtered` | `FilteredFunction` | `item_filter`、`on_match` |
| `toggle_tooltips` | `ToggleTooltipsFunction` | `toggles` |

其余修饰器（`modify_contents`、`set_contents`、`set_container_loot_table`、`copy_custom_data`、`set_custom_data`、`set_custom_model_data`、`set_attributes`、`set_banner_pattern`、`set_book_cover`、`exploration_map`、`fill_player_head`、`set_firework_explosion`、`set_fireworks`、`set_instrument`、`set_ominous_bottle_amplifier`、`set_random_dyes`、`set_random_potion`、`set_writable_book_pages`、`set_written_book_pages`）按原版注册项一一保留 record。条件和函数里的引用取值（物品 ID、魔咒 ID、标签 ID、伤害类型、属性 ID 等）在 `LootContents` 中收集，解码的时候不解析成 Minestom 对象。

### 5.5 数字提供器

```java
public sealed interface NumberProvider { }
```

原版 26.2 注册的 8 种。字段沿用原版：

| `type` | record | 字段 |
|---|---|---|
| `constant` | `ConstantValue` | `value` |
| `uniform` | `UniformValue` | `min`、`max`（都是数字提供器） |
| `binomial` | `BinomialValue` | `n`、`p` |
| `score` | `ScoreboardValue` | `target`（固定名或上下文实体）、`score`、`scale`（默认 `1.0`） |
| `storage` | `StorageValue` | `storage`（命名空间 ID）、`path`（SNBT 路径） |
| `sum` | `SumValue` | `terms`（数字提供器列表） |
| `enchantment_level` | `EnchantmentLevelValue` | `amount`（原版 `LevelBasedValue` 取值；本模块的值类型按原版的 `constant` / `linear` / `clamped` / `fraction` / `levels_squared` / 倒数变体的 JSON 结构对应） |
| `environment_attribute` | `EnvironmentAttributeValue` | `attribute`（原版 `EnvironmentAttribute` 的命名空间 ID） |

文件里数字提供器位置出现的裸数字读作 `constant`（例如 `"rolls": 3`），布尔等非法类型按原版 `MapCodec` 的规则报错。

### 5.6 每个类型的 referencedParams()

`LootCondition`、`LootFunction`、`NumberProvider` 接口约定 `referencedParams(): Set<ContextKey<?>>`，默认返回 `Set.of()`。语义沿用原版 `LootContextUser#getReferencedContextParams`：返回该 record 自身直接引用的上下文参数集合，**不**把嵌套的子条件、子修饰器、子数字提供器一路合并起来。每个 record 按原版同名类的方法的返回值实现：

- 不引用任何参数的 record 走接口默认实现；原版复合类（`AllOfCondition`、`SequenceFunction`、`Sum` 等）自己不引用参数，同样返回空集。
- 自身引用参数的 record（例如 `KilledByPlayerCondition` 返回 `Set.of(LAST_DAMAGE_PLAYER)`）覆盖这个方法。

嵌套结构的参数覆盖检查由外层逐节点完成，对应原版 `Validatable#validate(ValidationContext)` 的自外向里递归：`LootPool.validate` 对 conditions、functions、entries 各自调用 `Validatable.validate(...)`，复合条件（原版 `CompositeLootItemCondition#validate`）先对当前节点执行 `ValidationContext#validateContextUsage(this)`，再对它的 `terms` 逐个 `validate`。原版 `ValidationContext#validateContextUsage` 只读取当前节点的 `getReferencedContextParams()` 与参数集合做差集。求值期本模块沿用同一约定：每个节点只按自身的 `referencedParams()` 校验，外层调用方负责递归到子节点（见 8.4）。

`LootEntry` 接口本身不强制 `referencedParams()`；调用方在池/表/嵌套容器里拿到具体条目之后，从它的 `conditions()`、`functions()`、（如果是数字提供器）`rolls()`/`bonusRolls()` 这些下游去合并。

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

- `replaceAll` 整体替换当前生效的集合：调用前由订阅者算好新集合；调用后旧 ID 全部失效，快照整体切换，`get` / `tables()` 读取当前快照。方法和 `crafting` 的 `RecipeManager.replaceRecipes` 含义相同。
- `replaceAll` 在替换之前先校验（第 7 节），校验失败抛出 `LootException`，当前集合保持不变。
- 查询方法读快照，可在任何线程调用。求值器经 `LootManager.get(id)` 取当前 ID 对应的 `LootTable`。
- 本模块不向客户端发送任何数据包：战利品表是服务端数据，26.2 的注册表同步不包含它。

## 7. 校验与 LootContents

原版把战利品表、谓词、物品修饰器作为同一种 loot 资源在 `ReloadableServerResources` 中加载，之后在 `ValidationContext` 上校验；本模块把"解析成 record"和"跨文件校验"分开：解析在 `loot-table-parser`，校验在 `LootManager.replaceAll`。

```java
public record LootContents(Set<Key> itemTags, Set<Key> tables, List<LootTable> nested) {
    public static LootContents inspect(LootTable table);
}
```

校验规则：

1. `loot_table` 条目的 `value` 是 ID 时、以及 `set_container_loot_table` 修饰器的 `name`，必须在被加载的集合中存在。
2. 不允许递归：任何表经引用链直接或间接指回自己时报错 "Detected infinite loop in loot tables"；内联表参与同一条检查。
3. 复合条目展开的尺寸在每次池允许的规模之内；超过原版 `WeightedRandom` 求值时的总权重上限（`Integer.MAX_VALUE`）时报错，本模块在注册阶段提前拦截。
4. `slots` 的 `slot_source`、`dynamic` 的 `name` 等枚举取值已经在解析阶段验证过。

物品标签引用是否存在不在本模块校验；`loot-table-parser` 在准备阶段按本次加载的 `ItemTags` 核对。

任何一条不满足就抛 `LootException`，异常信息写明表 ID 和（适用时）字段路径；本次 `replaceAll` 不做任何修改。

## 8. 求值

求值让使用者在拿到上下文之后直接得到 `ItemStack` 列表，对应原版 `LootTable.getRandomItems(LootParams, long)` 的用法。

```java
public final class LootSelector {
    public LootSelector(ServerProcess process, LootManager manager, LootTypeRegistry types,
                        LootEnvironment environment);

    public List<ItemStack> roll(LootTable table, LootContext context, RandomSource random);
    public List<ItemStack> roll(Key tableId, LootContext context, RandomSource random);  // 表必须存在
}
```

- `process` 给求值提供注册表（物品、魔咒、方块状态属性、伤害类型等）的运行时查找入口。
- `manager` 是当前生效的战利品表集合；`roll(Key, ...)` 在这里查表，`roll(LootTable, ...)` 用于内联表和宿主直接构造的表。
- `environment` 提供求值期的服务端环境，见 8.2。

### 8.1 LootContext

```java
public sealed interface LootContext permits LootContextImpl {
    Set<ContextKey<?>> params();
    <T> T require(ContextKey<T> key);
    <T> @Nullable T find(ContextKey<T> key);
    float luck();
    ServerProcess process();
    RandomSource random();
}

public record ContextKey<T>(Key id) {}
```

`ContextKey` 常量沿用原版 `LootContextParams` 的名字：`THIS_ENTITY`、`INTERACTING_ENTITY`、`TARGET_ENTITY`、`LAST_DAMAGE_PLAYER`、`DAMAGE_SOURCE`、`ATTACKING_ENTITY`、`DIRECT_ATTACKING_ENTITY`、`ORIGIN`、`BLOCK_STATE`、`BLOCK_ENTITY`、`TOOL`、`EXPLOSION_RADIUS`、`ENCHANTMENT_LEVEL`、`ENCHANTMENT_ACTIVE`。wiki *Loot table* 里的 `attacker`、`direct_attacker`、`attacking_player` 等名字对应这里的 `ATTACKING_ENTITY`、`DIRECT_ATTACKING_ENTITY`、`LAST_DAMAGE_PLAYER`。

原版 `LootContext.getLuck` 把玩家运气属性和钓竿的 `fishing_luck_bonus` 相加，在钓鱼以外只取属性。这个计算由 `LootContext` 的实现按原版规则完成，宿主只需要在构造时给出玩家和工具（见 `LootContexts`）。

`LootContexts` 提供原版全部上下文类型的快捷构造，例如：

```java
LootContext block = LootContexts.block(blockState, blockEntity, tool, origin);      // loot_table type=block
LootContext entity = LootContexts.entity(thisEntity, attacker, directAttacker,
        attackingPlayer, damageSource, origin);                                     // loot_table type=entity
LootContext fishing = LootContexts.fishing(tool, origin);                            // type=fishing
LootContext chest = LootContexts.chest(origin);                                      // type=chest
```

每个构造的必需/可选参数沿用原版 `LootContextParamSets` 对该类型的划分；宿主需要其他组合时可以直接 new `LootContextImpl`。

### 8.2 LootEnvironment

```java
public interface LootEnvironment {
    ServerProcess process();
    Scoreboard scoreboards();                       // 只读；score 数字提供器用
    CommandStorage storage();                       // 只读；storage 数字提供器用
    float luckOf(@Nullable Player player, @Nullable ItemStack tool, Key contextType);
    Stream<ItemStack> dynamicLoot(Key name);        // contents / sherds
    Optional<LootFunction> findFunction(Key id);    // 处理 function 的 reference
    Optional<LootCondition> findCondition(Key id);  // 处理 condition 的 reference
}
```

宿主按自己的运行环境给出实现。`LootSelector` 不直接调用 `LootEnvironment.findFunction` / `findCondition`；它们由 `reference` 类型的修饰器/条件各自的求值逻辑调用。

### 8.3 roll 的流程

按原版 `LootTable.getRandomItemsRaw` 相同的步骤：

1. 随机数源：优先使用 `table.randomSequence()` 对应的稳定序列（按世界种子派生），否则用传入的 `random`。原版 `LootParams.withRandomSequence` 在序列缺失时回退到世界随机；本模块简化：宿主传什么用什么，`randomSequence` 只对记名册自身持久序列启用。
2. 遍历每个池：池的 `conditions` 全部通过才使用。
3. 每个池的抽取次数 = `rolls.getInt(context) + floor(bonusRolls.getFloat(context) * context.luck())`；`bonusRolls` 默认 `constant(0)`。
4. 每次抽取：按原版 `LootPoolEntryContainer.expand` 把复合条目展开成单例条目（`group` 全展开、`alternatives` 取第一个条件满足、`sequence` 逐个展开到首个不满足为止），`tag(expand=true)` 按物品展开成单例；条件不满足的单例剔除。剩下按 `weight + floor(quality * luck)` 加权抽取；总权重为 0 或负时不产出。
5. 被选中的单例条目：
   - `item`：`ItemStack(Material.fromKey(name), 1)`。
   - `tag(expand=false)`：标签中每个物品各一件。
   - `empty`：不产出。
   - `loot_table`：递归 `roll`；递归环已在 `LootManager.replaceAll` 排除。
   - `dynamic`：`environment.dynamicLoot(name)`。
   - `slots`：抛出"当前版本不支持 slots 条目"（原版 26.2 的原版数据没有使用）。
6. 每层 `functions`（条目自己的、池的、表的）按原版顺序依次应用。修饰器的运行规则按原版 `LootItemFunction.run` 的实现移植；`discard` 直接丢弃。
7. 整张表的产出按原版 `LootTable.createStackSplitter` 的堆叠合并规则处理后再返回。`/loot` 命令对应的 `getRandomItems` 路径不受容器容量限制；`LootTable.fill` 的容量约束（27 组）属于宿主模拟容器的场景，本模块不提供。

### 8.4 上下文参数覆盖检查

求值时某个条件、修饰器、数字提供器需要的参数不在 `LootContext` 中时，抛原版同文案：`<参数名> is not present in this loot context`。每个 record 实现 `referencedParams()` 列出它用到的参数。解析阶段已经拦截未知类型，这一步覆盖数据包作者在多态表（`type=entity` 的表在方块场景被使用）里混用条件的情况。

### 8.5 reference 的求值

修饰器 `function: "minecraft:reference"` 的 `name` 指向 `item_modifier`，条件 `condition: "minecraft:reference"` 的 `name` 指向 `predicate`。求值的时候经 `LootEnvironment.findFunction` / `findCondition` 查找；找不到时报原版同文案：`Tried to use unknown condition table called '<id>'` / `Unknown function: <id>`。

## 9. 与原版行为的对照

- 原版三种 loot 资源（`loot_table` / `predicate` / `item_modifier`）通过 `LootDataType` 统一加载，原版 `/reload` 一并重载并允许 `reference` 互相引用。本模块的 `LootManager` 只保管战利品表本身；谓词和物品修饰器作为独立资源未来的订阅者加载，本模块在 `reference` 处只记 ID。
- 原版遇到解码失败的战利品表记录日志并跳过整个文件；`loot-table-parser` 沿用项目其他订阅者的做法，任何文件解码失败就抛出异常，整次加载失败，游戏状态不变。
- 原版 `LootTable.fill`、`getRandomItems`、`createStackSplitter`、`LootContext.getLuck`、`LootPool.addRandomItems` 的实现移植到 `LootSelector.roll` 和 `LootContext`，语义保持一致。原版 `LootTable.KEY_CODEC`、`LootTable.DEFAULT_PARAM_SET`、`BuiltInLootTables` 的常量列表对本模块不需要（原版用于方块实体 NBT 和代码引用）。

## 10. 包结构

根包 `io.ib67.yuanban.loot`：

| 包 | 内容 |
|---|---|
| `loot` | `LootTable`、`LootPool`、`LootManager`、`LootTypeRegistry`、`LootContents`、`LootException` |
| `loot.context` | `LootContext`、`ContextKey`、`LootContexts`、`LootEnvironment` |
| `loot.entry` | `LootEntry`、`SingletonEntry`、`CompositeEntry`，10 个条目 record |
| `loot.condition` | `LootCondition`，20 个条件 record |
| `loot.function` | `LootFunction`，44 个修饰器 record |
| `loot.number` | `NumberProvider`，8 个数字提供器 record |
| `loot.eval` | `LootSelector`、求值中间类型 |

各类型对象（解码描述）放在 `loot.type` 子包，或随记录类作为嵌套常量公开。

## 11. 测试

`LootManager`、`LootContents`、`LootTypeRegistry` 的测试不启动 Minestom 进程，直接构造 record：

- 注册与查询：`replaceAll` 之后 `get` / `tables()` 返回新内容；再次替换后旧 ID 不再存在；快照在两次替换之间不被修改。
- 校验：引用缺失、直接递归、间接递归（A → B → A）、内联表递归、复合条目超长这五类各一个用例，异常信息能定位到表 ID；`tag` 条目进入 `itemTags`、内联表进入 `nested`。
- 类型注册表：`vanilla()` 覆盖第 7、8、9 节列出的全部类型；重复注册同一 ID 抛异常；`minecraft` 命名空间之外的新类型能注册并被解析使用（`loot-table-parser` 的测试覆盖）。

`LootSelector` 的测试用 `net.minestom:testing` 的 `@EnvTest` 启动真实 Minestom 进程（`minestom.inside-test=true`）。`LootEnvironment` 用本模块 testFixtures 里的内存实现：

- `roll`：同一固定随机数种子重复取用产出相同（对照原版 `random_sequence`）；`LootContexts.block(...)` 直接跑 `blocks/grass_block` 的等价结构产出 `dirt` 或 `grass_block`（取决于工具）。
- 修饰器：`set_count` 的数量、`add` 的叠加、`discard` 的剔除、`limit_count` 的截断、`sequence` 的顺序、`set_components` 的组件替换。
- 条件：`random_chance` 的边界（0、1）、`killed_by_player` 缺 `LAST_DAMAGE_PLAYER` 报错、`table_bonus` 按魔咒表查数。
- 错误：缺必需上下文参数按原版文案报错；`slots` 条目报"当前版本不支持 slots 条目"。

原版 `LootTable.fill` 的容器容量上限由宿主自己模拟容器场景，不放进 `LootSelector`。
