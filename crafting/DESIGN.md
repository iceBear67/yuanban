# crafting 设计

## 1. 职责

`crafting` 包含两部分：

- 配方系统：原版 26.2 全部 21 种配方序列化器对应的配方类型（匹配规则、产出、剩余物、配方书显示），配方注册表 `RecipeManager`（按类型查找配方、配方书条目、物品属性、切石机列表），以及发给客户端的配方数据和配方书。
- 合成逻辑：工作台的 3×3 合成格、玩家背包的 2×2 合成格、配方书的放置请求、合成统计。

配方来自 `item-recipe`：它从数据包解码配方，提交阶段调用 `RecipeManager.replaceRecipes`。本模块本身不依赖 `datapack-loader`，任何来源的配方都可以交给 `RecipeManager`。

本模块不使用 Minestom 的 `RecipeManager` 和 `net.minestom.server.recipe.Recipe`。Minestom 的数据包类型、显示数据类型（`RecipeDisplay`、`SlotDisplay`）和 `net.minestom.server.recipe.Ingredient` 只用于网络编码。界面使用 Minestom `2026.10.05-26.2` 实际提供的 Inventory 接口：`Inventory` 子类重写点击处理方法、`InventoryPreClickEvent`、`InventoryItemChangeEvent`、`InventoryCloseEvent`。Minestom 文档页面 *Inventories* 中的 `ContainerInventory`、`Click.Info`、`InventoryPostClickEvent` 在这个版本中不存在。

## 2. 依据

原版行为对照 26.2 服务端（`server-26.2.jar`，SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` 的 bundle 中取出）的反编译代码：

- 配方：`net.minecraft.world.item.crafting` 中的全部配方类、`CraftingInput`、`PlacementInfo`、`Ingredient`、`RecipeManager`、`RecipeMap`、`RecipePropertySet`、`SelectableRecipe`、`ItemStackTemplate`、`ItemStack.validateStrict`、`BundleContents`、`DyedItemColor`、`EnchantmentHelper`。
- 菜单：`AbstractContainerMenu`、`AbstractCraftingMenu`、`CraftingMenu`、`InventoryMenu`、`Slot`、`ResultSlot`、`ArmorSlot`、`TransientCraftingContainer`、`player.Inventory`、`StackedContents`、`StackedItemContents`、`ServerPlaceRecipe`、`PlaceRecipeHelper`。
- 其他：`CraftingTableBlock`、`ServerPlayerGameMode.useItemOn`、`ServerGamePacketListenerImpl.handlePlaceRecipe`、`ServerPlayer.tick`、`Player.remove`、`PlayerList.reloadResources`、`ServerRecipeBook.sendInitialRecipeBook`、`MapItem`、`MapItemSavedData`、`ServerStatsCounter`。

已确定的决策：

| 方面 | 决策 |
|---|---|
| 地图数据 | 地图放大配方需要的地图数据由宿主通过 `MapDataAccess` 提供，构造模块时必须传入 |
| 背包 2×2 合成格 | Minestom 的 `Player` 在构造时固定创建 `PlayerInventory`，本模块用事件拦截涉及合成格的点击，其余点击由 Minestom 处理 |
| 配方书 | 全部玩家看到全部配方，不记录解锁和高亮，不保存配方书状态 |

## 3. 依赖

```groovy
plugins {
    id("java-test-fixtures")
}

dependencies {
    api("net.minestom:minestom:2026.10.05-26.2")
    implementation("it.unimi.dsi:fastutil:8.5.19")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    testImplementation("net.minestom:testing:2026.10.05-26.2")
    testImplementation(project(":item-recipe"))
    testImplementation("com.mojang:datafixerupper:10.0.21")
    testImplementation(testFixtures(project(":item-tag")))
}
```

- fastutil 与 Minestom 运行时使用的版本相同，用于移植原版 `StackedContents`。
- commons-lang3 的版本取自原版 26.2 的 `META-INF/libraries.list`，用于原版计算收纳袋重量的 `Fraction`。
- 测试依赖 `item-recipe`，配方经过真实的解码流程得到；测试直接调用 `item-recipe` 的解码器，所以同时依赖它使用的 DataFixerUpper。Gradle 中 `crafting:compileTestJava` 依赖 `item-recipe:compileJava`，后者依赖 `crafting:compileJava`，任务之间没有环。

## 4. 公开接口

包 `io.ib67.yuanban.crafting`：

```java
public final class Crafting {
    public Crafting(ServerProcess process, MapDataAccess maps);
    public RecipeManager recipes();
    public EventNode<Event> eventNode(); // 宿主加入 process.eventHandler()
}

public interface MapDataAccess {
    Optional<MapInfo> find(int mapId);       // 原版 MapItem.getSavedData，地图不存在时为空
    OptionalInt createScaledCopy(int mapId); // 原版 MapItem.scaleMap，地图不存在时为空
    record MapInfo(int scale, boolean explorationMap) {}
}
```

`createScaledCopy` 按原版 `MapItemSavedData.scaled()` 复制地图数据：缩放等级加一（最大为 4），按新的缩放等级以 `createFresh` 重新计算中心坐标，保留追踪设置和维度，分配新的地图 ID。`MapDataAccess` 的方法在玩家所在实例的线程上调用。

包 `io.ib67.yuanban.crafting.recipe`：

```java
public sealed interface Recipe<I extends RecipeInput> permits CraftingRecipe, CookingRecipe, StonecutterRecipe, SmithingRecipe {
    RecipeType<?> type();
    boolean matches(I input, RecipeContext context);
    ItemStack assemble(I input, RecipeContext context);
    default boolean isSpecial();
    String group();
    PlacementInfo placementInfo();
    List<RecipeDisplay> display();
    RecipeBookCategory recipeBookCategory();
}

public sealed interface CraftingRecipe extends Recipe<CraftingInput> {
    CraftingCategory category();
    default List<ItemStack> remainingItems(CraftingInput input);
}
public sealed interface SpecialCraftingRecipe extends CraftingRecipe {}  // 原版 CustomRecipe
public sealed interface SmithingRecipe extends Recipe<SmithingRecipeInput> {}

public interface RecipeContext { Registries registries(); MapDataAccess maps(); }

public final class RecipeManager {
    public RecipeManager(ServerProcess process, MapDataAccess maps);
    public RecipeContext context();
    public void replaceRecipes(NavigableMap<Key, Recipe<?>> recipes);
    public void sendRecipes(Player player);
    public NavigableMap<Key, Recipe<?>> recipes();
    public Optional<RecipeHolder<?>> byId(Key id);
    public <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> getRecipeFor(RecipeType<R> type, I input, @Nullable Key hint);
    public Optional<RecipeDisplayInfo> displayInfo(int displayId);
}
```

其余公开类型：18 个配方 record（第 5 节）、`RecipeInput` 与 `CraftingInput`、`SingleRecipeInput`、`SmithingRecipeInput`、`RecipeType`、`RecipeHolder`、`RecipeDisplayInfo`、`PlacementInfo`、`ItemIngredient`、`ItemStackTemplate`、`IntBounds`、`CraftingBookInfo`、`CookingBookInfo`、`CraftingCategory`、`CookingCategory`、`CookingKind`、`CraftingRemainders`。

## 5. 配方类型

配方是带行为的不可变 record，每个 record 逐行移植原版对应类的 `matches`、`assemble`、`getRemainingItems`、`display`、`recipeBookCategory`、`group` 和 `createPlacementInfo`。原版传给配方的 `Level` 对应 `RecipeContext`：修复物品用它的注册表查找诅咒魔咒标签，地图放大用它的地图数据。

| 序列化器 | record | 匹配与产出 |
|---|---|---|
| `crafting_shaped` | `ShapedRecipe` | 原料数和尺寸相同；图案左右不对称时先试左右翻转 |
| `crafting_shapeless` | `ShapelessRecipe` | 原料数相同；只有一格时直接测试，否则用 `StackedContents` 为每个原料分配一个格子 |
| `crafting_transmute` | `TransmuteRecipe` | 一个输入物品加材料，材料数在范围内；结果保留输入物品的组件，可以把材料数加到结果数量上；结果数量为 1 并且与输入物品完全相同时不匹配 |
| `crafting_dye` | `DyeRecipe` | 一个目标加带 `dye` 组件的染料；`DyedItemColor.applyDyes` 混合颜色 |
| `crafting_imbue` | `ImbueRecipe` | 3×3 全满，中心为 `source`；结果带上 `source` 的 `potion_contents` |
| `crafting_decorated_pot` | `DecoratedPotRecipe` | 3×3 的上、左、右、下四格写入 `pot_decorations` |
| `crafting_special_bookcloning` | `BookCloningRecipe` | 一本代数在范围内的成书加若干材料；代数加一，数量为材料数；原书留在合成格中 |
| `crafting_special_mapextending` | `MapExtendingRecipe` | 中心一张地图、周围八个材料；地图存在、缩放等级小于 4、不是探险家地图；结果带上 `map_post_processing = SCALE` |
| `crafting_special_firework_rocket` | `FireworkRocketRecipe` | 一个外壳、一到三个燃料、任意个烟火之星 |
| `crafting_special_firework_star` | `FireworkStarRecipe` | 一个燃料、至少一个带 `dye` 组件的染料、至多一个爆炸类型物品、拖尾和闪烁各至多一个 |
| `crafting_special_firework_star_fade` | `FireworkStarFadeRecipe` | 一个目标加带 `dye` 组件的染料，染料颜色成为淡化颜色 |
| `crafting_special_bannerduplicate` | `BannerDuplicateRecipe` | 两面同色旗帜，一面有 1 到 6 层图案，一面空白；有图案的旗帜留在合成格中 |
| `crafting_special_shielddecoration` | `ShieldDecorationRecipe` | 一面旗帜和一个没有图案的盾牌；盾牌带上图案和底色 |
| `crafting_special_repairitem` | `RepairItemRecipe` | 两个同种、数量为 1、有耐久的物品；耐久相加再加最大耐久的 5%，只保留诅咒魔咒 |
| `smelting` 等四种 | `CookingRecipe` | 原料测试；`kind` 区分方块 |
| `stonecutting` | `StonecutterRecipe` | 原料测试 |
| `smithing_transform` | `SmithingTransformRecipe` | 三格按可选原料测试；结果保留基础物品的组件 |
| `smithing_trim` | `SmithingTrimRecipe` | 附加物品提供纹饰材料；已经有同样纹饰时没有结果 |

细节：

- `ItemStackTemplate(material, count, components)` 对应原版 `ItemStackTemplate`。`components` 原样保存配方文件中的补丁；`apply(count, patch)` 以 `patch`（通常是输入物品的 `componentPatch()`）为起点，按模板补丁逐项设置或移除组件，再建立物品。
- 产出经过原版 `ItemStack.validateStrict`：数量不超过最大堆叠数；同时有 `max_damage` 和大于 1 的最大堆叠数时不合法；`container`、`bundle_contents`、`charged_projectiles` 中每个物品的数量不超过其最大堆叠数；收纳袋重量（嵌套收纳袋加 1/16，带蜜蜂的蜂巢为 1）计算溢出时不合法。不合法的结果为 `ItemStack.AIR`。
- `ItemIngredient.display()` 是原版 `Ingredient.display()`：标签原料显示为 `SlotDisplay.Tag`，物品列表显示为 `Composite`，有合成剩余物的物品包装成 `WithRemainder`。`toNetwork()` 中标签原料引用 Minestom 注册表中的标签，找不到时抛出异常。
- 原版依赖物品类的判断：`BannerItem` 与它的颜色取自原版 26.2 `Items` 中的 16 种旗帜（`internal.item.BannerColors`）；只有已绘制的地图有合成后处理（原版 `MapItem.onCraftedPostProcess`）。
- 合成剩余物（`CraftingRemainders`）是原版 26.2 `Items` 中的取值，`FIXME(Minestom M6)`。
- 锻造纹饰配方没有显示数据，`FIXME(Minestom M2)`。

## 6. 配方注册表与客户端同步

`RecipeManager.replaceRecipes` 按原版 `RecipeMap.create` 与 `RecipeManager.finalizeRecipeLoading` 一次算好下列数据，整体替换 volatile 快照，然后给全部在线玩家发送 `DeclareRecipesPacket` 和替换整个配方书的 `RecipeBookAddPacket`：

- 按类型分组、保持配方 ID 顺序的配方。adventure 的 `Key` 先比较值再比较命名空间，与原版 `Identifier.compareTo` 相同。
- `getRecipeFor(type, input, hint)`：配方提示属于这个类型并且匹配时返回它；否则输入为空时没有结果；再否则返回第一个匹配的配方。
- 物品属性：原版 `RECIPE_PROPERTY_SETS` 的七种，全部发送（集合为空也发送）。不能放置的非特殊配方不参与。
- 切石机列表：每个可以放置的切石机配方一项，原料是配方原料本身，显示为 `SlotDisplay.ItemStack(结果)`，按配方 ID 排列。
- 配方书条目：全部配方的显示数据按配方顺序编号，条目 ID 即序号；分组 ID 按非空分组字符串第一次出现的顺序分配；原料需求为特殊配方空、其余为 `placementInfo().ingredients()`（不能放置时为空列表）。全部条目不提示、不高亮。
- 不按特性开关过滤显示数据、物品属性和切石机列表，`FIXME(Minestom M7)`。

`replaceRecipes` 在调用者的线程上执行（`item-recipe` 的提交阶段），查询方法读取快照，可以在任何线程调用。

玩家首次出生（`PlayerSpawnEvent.isFirstSpawn()`）时发送同样的两个数据包。Minestom 在 `Player.UNSAFE_init` 中调用 `refreshRecipes()` 发送它自己（空的）`RecipeManager` 的数据，早于出生事件，会被这两个数据包覆盖。

## 7. 菜单

两个菜单共用一套移植自原版 `AbstractContainerMenu.doClick` 的点击逻辑（`internal.menu.CraftingMenu`），作用在按原版菜单序号排列的“菜单格子”上。每个菜单格子对应 Minestom 某个背包的一格，并带有原版格子类型的规则：

| 格子类型 | 规则 |
|---|---|
| `MenuSlot` | 原版 `Slot`：最大堆叠数为 `min(99, 物品最大堆叠数)` |
| `ResultSlot` | 原版 `ResultSlot`：不能放入；`remove` 按请求的数量计入 `removeCount`，但整组取走；取走后消耗原料 |
| `ArmorSlot` | 原版 `ArmorSlot`：最多一个；只接受这个部位的可装备物品；物品带有 `prevent_armor_change` 效果的魔咒时，非创造模式不能取下 |

| 菜单 | 菜单序号 → Minestom 格子 |
|---|---|
| 工作台（原版 `CraftingMenu`） | 0 结果、1–9 合成格 → 工作台 `Inventory` 的 0–9；10–36 → 玩家 9–35；37–45 → 玩家 0–8 |
| 背包（原版 `InventoryMenu`） | 0 结果 → 玩家 36；1–4 合成格 → 37–40；5–8 头、胸、腿、脚 → 41–44；9–35 → 9–35；36–44 → 0–8；45 副手 → 45 |

光标物品是玩家 `PlayerInventory` 的光标。原版的物品对象可以修改，Minestom 的物品不可变：原版修改物品对象的地方改为写回格子，或者由方法返回新物品（例如 `onTake` 返回经过后处理的物品，调用者写回光标或快捷栏）。

原版 `player.Inventory` 的 `add`、`placeItemBackInInventory`、`getSlotWithRemainingSpace`（手持格、副手、快捷栏和背包）、`getFreeSlot`、`findSlotMatchingCraftingIngredient`、`fillStackedContents` 移植在 `PlayerInventoryOps` 中。原版背包的 0–35 号格与 Minestom 的编号相同，原版副手 40 号格是 Minestom 的 45 号格。`add` 只在最后一轮仍有物品放入时返回成功，与原版相同。

原版的丢出物品调用 `Player.dropItem`。Minestom 不生成掉落物实体，它发出 `ItemDropEvent`，由宿主处理；事件被取消时物品没有去处，就此消失。

### 7.1 结果格与合成格

- 合成格的一格变化之后按原版 `CraftingMenu.slotChangedCraftingGrid` 重新计算结果：`getRecipeFor(CRAFTING, 合成格内容, 提示)`，把 `assemble` 的结果写入结果格。原版 `setRecipeUsed` 在 `doLimitedCrafting` 开启时检查配方是否已解锁；全部配方已知，这个检查总是通过。
- 原版不触发 `slotsChanged` 的修改期间不重新计算：关闭菜单时清空合成格（`removeItemNoUpdate`）、`clearCraftingContent`、工作台放置配方的过程中（原版 `CraftingMenu.beginPlacingRecipe`，结束后带上配方提示计算一次）。背包菜单放置配方时每次修改都重新计算，没有配方提示，与原版 `InventoryMenu` 相同。
- 取走结果（原版 `ResultSlot.onTake`）：先按 `removeCount` 增加统计并做后处理（`checkTakeAchievements`），再按 `CraftingInput.ofPositioned` 的偏移逐格移除一个原料；剩余物放回原格、与原格中相同的物品合并，或者放入背包，放不下时丢出。剩余物取自匹配的配方；没有匹配的配方时，原版的剩余物就是格子中的物品对象本身，移除一个之后两者相同，合并后的数量为 `2c - 2`（`c` 为原有数量），这里按这个结果移植。
- 原版 `awardUsedRecipes` 解锁所用的配方并触发进度条件；全部配方已知，yuanban 没有进度系统，这一步不做任何事。
- 和原版 `/reload` 相同，重载不重新计算已经打开的合成格。

### 7.2 点击

各种点击按原版 `doClick` 移植：`PICKUP`（单击）、`QUICK_MOVE`（按住 Shift 重复调用 `quickMoveStack` 直到格子中的物品种类改变）、`SWAP`、`CLONE`（创造模式）、`THROW`（光标为空时；按住 Ctrl 时反复丢出）、`PICKUP_ALL`（两轮，第一轮跳过满的格子；不从结果格取物）、`QUICK_CRAFT`（拖动结束时分配）。

原版在拖动经过每一格时检查并把格子加入集合，Minestom 的 `ClickPreprocessor` 在拖动结束时一次给出经过的格子；这里按经过的顺序重放同样的检查（物品可以放入、格子可以放入、左键拖动时光标数量大于已选格数）。只选中一格时按原版改为单击，拖动类型作为按键。

原版单击前调用的 `tryItemClickBehaviourOverride`（收纳袋的点击行为）没有移植，与 Minestom 处理其他格子的方式一致。

由本模块处理的点击按 Minestom 自己处理点击的方式发出 `InventoryClickEvent`：单击类操作为被点击的一格发出一个事件，物品和光标是操作之前的；按住 Shift 的点击、双击和拖动为内容变化的每一格发出一个事件，物品是操作之后的，光标是操作之前的。

原版用 `stateId` 和客户端上报的格子散列值只修正与客户端不一致的格子；Minestom 只校正光标物品。所以在本模块处理的点击之后重新发送背包的全部内容。

## 8. 工作台

- 打开：监听 `PlayerBlockInteractEvent`。方块为 `crafting_table` 时按原版 `ServerPlayerGameMode.useItemOn`：旁观模式直接打开；否则潜行并且任意一只手有物品时不处理，只有主手交互时打开并增加统计 `minecraft:interact_with_crafting_table`。打开时 `setBlockingItemUse(true)`。每次打开新建一个 `CraftingTableInventory`（`Inventory(InventoryType.CRAFTING, Component.translatable("container.crafting"))` 的子类），记录实例和方块位置。
- 有效性（原版 `CraftingMenu.stillValid`）：该位置仍是工作台（区块没有加载时视为不是），并且方块的包围盒到眼睛位置的距离平方小于 `(block_interaction_range + 4)²`。监听 `PlayerTickEvent`，打开工作台的玩家每刻检查一次，失效时 `player.closeInventory()`；点击和放置配方之前也检查，失效时不处理。
- 点击（重写 `Inventory` 的处理方法）。原版 `handleContainerClick` 不处理旁观模式和失效菜单的点击，这时返回 `false`，Minestom 重新发送背包：

  | 方法 | 结果格 0 | 其他格子 |
  |---|---|---|
  | `leftClick`、`rightClick`、`changeHeld`、`drop`、`middleClick` | 本模块 | `super` |
  | `shiftClick` | 本模块（原版 `CraftingMenu.quickMoveStack`） | 本模块：背包和快捷栏先移入合成格，再在两者之间移动；合成格移入背包。Minestom 的实现会移入结果格 |
  | `dragging` | 本模块（结果格因不能放入而排除） | 不经过结果格时 `super` |
  | `doubleClick` | 本模块 | 本模块。Minestom 的实现会从结果格取物 |

- 合成格变化：重写 `setItemStack(int, ItemStack, boolean)`，格子 1–9 的内容改变后重新计算结果。
- 关闭：重写 `removeViewer`，按原版 `CraftingMenu.removed` 先把光标物品放回背包（`FIXME(Minestom M15)`），再调用 `super`，最后把合成格的物品逐个放回背包。

## 9. 背包 2×2 合成格

`InventoryPreClickEvent` 中背包是玩家自己的 `PlayerInventory` 并且玩家没有打开其他背包时（窗口 0），下列点击取消事件后按原版处理，Minestom 随后重新发送玩家背包：

| 点击 | 原因 |
|---|---|
| 格子 36（结果格）上的任何点击 | 结果格规则；按住 Shift 时按原版 `InventoryMenu.quickMoveStack` 移入菜单 9–45 |
| 合成格 37–40 上按住 Shift 的点击 | 原版在背包和快捷栏中先合并再找空格，Minestom 分成背包、快捷栏两段处理 |
| 经过格子 36 的拖动 | 结果格不能放入；其余格子按原版规则，包括盔甲格 |
| 合成格中有能与光标物品合并的物品时的双击 | 原版按菜单顺序收集，合成格在背包之前；Minestom 不从合成格收集 |

其余点击由 Minestom 处理。`InventoryItemChangeEvent` 中玩家格子 37–40 变化后重新计算结果（原版 `InventoryMenu.slotsChanged`）。每个玩家的背包菜单状态（`removeCount`、暂停重新计算的标记）保存在玩家的 `Tag.Transient` 中。

关闭窗口 0（`InventoryCloseEvent`）时按原版 `InventoryMenu.removed`：光标物品放回背包（`FIXME(Minestom M16)`），清空结果格，合成格物品逐个放回。

## 10. 离线

监听 `PlayerDisconnectEvent`，按原版 `Player.remove` 的顺序丢出物品：先处理背包菜单（没有打开其他背包时包括光标物品；清空结果格；丢出合成格 37–40），再处理打开的工作台（光标物品和合成格）。光标物品属于当前打开的菜单。Minestom 之后调用打开的背包的 `removeViewer`，那时格子已经为空。

## 11. 配方书放置

监听 `PlayerPacketEvent` 中的 `ClientPlaceRecipePacket`，全部取消事件（Minestom 的 `RecipeListener` 查询的是它自己的 `RecipeManager`），按原版 `handlePlaceRecipe` 处理：

- 窗口 ID 是玩家打开的 `CraftingTableInventory`，或者玩家没有打开背包并且窗口 ID 为 0 时处理；其他窗口不处理。
- 旁观模式、菜单失效、显示 ID 找不到、配方不是合成配方或者不能放置时不处理。原版客户端只对合成配方发送放置请求；原版服务端收到非合成配方时会抛出 `ClassCastException`。
- 移植原版 `ServerPlaceRecipe`：非创造模式下先用 `testClearGrid` 估计合成格的物品能否放回背包，不能时不处理；统计背包和合成格中可以用于合成的物品（没有损坏、没有附魔、没有自定义名称）；材料足够时按 `getBiggestCraftableStack`、`calculateAmountToCraft`（全部放置时为最多份数；已经摆好这个配方时在最少的一格上多放一份；否则一份）和 `clampToMaxStackSize` 决定份数，清空合成格后按原版 `PlaceRecipeHelper` 放置（比合成格窄或矮一半以上的配方居中）；材料不足时清空合成格并回复 `PlaceGhostRecipePacket(窗口 ID, 显示数据)`。
- 放置之后重新发送背包的全部内容。

## 12. 统计与地图

- 原版 `ResultSlot.checkTakeAchievements`：`removeCount` 大于 0 时增加 `minecraft:crafted` 统计（物品 ID），数量为 `removeCount`。原版 `THROW` 丢出结果格时请求 1 个，所以统计只增加 1，虽然整组被取走。
- 打开工作台时增加 `minecraft:interact_with_crafting_table`。
- 统计按原版 `ServerStatsCounter.increment` 累加，上限为 `Integer.MAX_VALUE`。Minestom 的统计映射无法按键查找，`FIXME(Minestom M14)`。
- 后处理（原版 `MapItem.onCraftedPostProcess`）：已绘制的地图带有 `map_post_processing = SCALE` 时移除这个组件，调用 `MapDataAccess.createScaledCopy`，有结果时换成新的 `map_id`。原版按住 Shift 取结果时对移动的物品和返回值的副本各做一次后处理，这里同样调用两次 `createScaledCopy`。合成不会产生 `LOCK`（只来自制图台），遇到时抛出 `IllegalStateException`。

## 13. Minestom 问题

| Id | 位置 | 问题 | 绕过方法 |
|---|---|---|---|
| M2 | `SmithingTrimRecipe.display` | `SlotDisplay.SmithingTrim` 把纹饰样式编码成 `SlotDisplay` | 锻造纹饰配方没有显示数据 |
| M6 | `CraftingRemainders` | `Material` 没有合成剩余物数据 | 内置原版 26.2 的取值 |
| M7 | `RecipeManager` | `Material` 没有所需特性开关数据 | 不过滤；26.2 中没有影响 |
| M14 | `internal.menu.CraftingStats` | `PlayerStatistic` 没有 `equals` / `hashCode` | 按类别和 ID 逐项比较 |
| M15 | `internal.menu.CraftingTableInventory.removeViewer` | `AbstractInventory.removeViewer` 丢出光标物品 | 调用 `super` 之前按原版把光标物品放回背包 |
| M16 | `internal.menu.CraftingEvents` | 关闭窗口 0 时 Minestom 不处理光标物品 | 关闭事件中按原版放回背包 |

详细说明见根目录 [MINESTOM-BUG-REPORT.md](../MINESTOM-BUG-REPORT.md)。

## 14. 与原版的差异

1. 全部玩家已知全部配方：不解锁配方、不触发进度条件、不记录高亮、不保存配方书状态，`doLimitedCrafting` 不起作用。
2. 统计不更新记分板准则（Minestom 没有统计类准则）。
3. 不按特性开关过滤显示数据、物品属性和合成结果（M7）。
4. 原版只修正与客户端不一致的格子；这里在本模块处理的点击和放置之后重新发送全部内容。
5. 背包（窗口 0）中没有被拦截的点击保持 Minestom 的行为。
6. Minestom 的 `Click.Double` 不带按键，按原版客户端发送的按键 0（正向收集）处理。
7. 锻造纹饰配方没有配方书显示（M2）。
8. 原版单击前的 `tryItemClickBehaviourOverride`（收纳袋的点击行为）没有移植。
9. 本模块不实现熔炉、切石机、锻造台的菜单；这些配方类型提供匹配、产出和客户端同步所需的数据。

## 15. 测试

测试使用 `net.minestom:testing` 的 `@EnvTest` 启动真实的 Minestom 进程。配方经过 `datapack-loader`、`item-tag`、`item-recipe` 的真实加载流程注册进 `Crafting`；多数测试使用原版 26.2 服务端 jar 中的数据（由 `item-recipe` 的下载任务准备，路径通过系统属性 `yuanban.vanilla-server` 传入）。玩家由 `TestConnection` 连接，通过 `player.addPacketToQueue` 发送真实的客户端数据包。每个测试类加 `@ExtendWith(ResetItemTags.class)`。测试代码作为宿主，用 testFixtures 中的 `InMemoryMaps`（`HashMap` 保存地图数据，`createScaledCopy` 按原版 `createFresh` 计算中心）提供地图数据。预期值按原版代码推算，测试注释写明对应的原版方法。

- `RecipeManagerTest`：显示数据、配方书类别、分组、放置信息；`replaceRecipes` 之后的显示 ID、分组 ID、原料需求、物品属性、切石机列表，在线玩家收到的两个数据包能按协议编码；`getRecipeFor` 的顺序与配方提示。
- `RecipeMatchingTest`：有序配方的平移与左右翻转，无序配方的原料分配，嬗变保留组件、结果与输入相同时不匹配、地图复制，结果模板中与原型相同的组件覆盖输入物品，结果超过最大堆叠数时为空，染色的混色公式，浸染、饰纹陶罐、成书复制与剩余物、地图放大与地图数据，烟花火箭、烟火之星、淡化，旗帜复制与剩余物、盾牌装饰，修复物品的耐久公式与诅咒魔咒，牛奶桶的剩余物，烧炼、切石、锻造升级与纹饰。
- `CraftingTableTest`：方块交互打开工作台与统计（潜行、副手、旁观模式）；放入原料后出现结果，结果格不能放入，单击取走结果消耗原料与统计；按住 Shift 合成全部；牛奶桶留下桶；拖动和双击跳过结果格；丢出结果格整组与统计；数字键交换；背包物品先移入合成格；关闭时物品放回背包；方块被移除或走远后下一刻关闭；离线时丢出物品。
- `InventoryCraftingTest`：Minestom 处理的合成格点击之后更新结果；取走结果；按住 Shift 合成全部；合成格按住 Shift 先合并到快捷栏中的同种物品；拖动跳过结果格；双击从合成格收集；结果格不能放入；关闭背包和离线。
- `RecipePlacementTest`：有序配方居中放置、重复放置每次多一份、全部放置、材料不足时回复虚影配方并清空合成格、背包放不下时不处理、背包 2×2 合成格放置。
- `VanillaCraftingTest`：全部原版有序、无序、嬗变配方用每个原料的第一个物品摆出合成格后匹配并产出结果；全部可以放置的原版合成配方在背包中放入所需物品后，经过放置请求摆进工作台，合成格被这个配方匹配，结果格是它的产出；原版中不能放置的配方都是特殊配方。
