# crafting

`crafting`（库）按原版 26.2 实现配方系统和合成：

- 配方类型：原版 26.2 全部 21 种配方序列化器对应的配方，包括匹配规则、产出、剩余物和配方书显示；
- 配方注册表 `RecipeManager`：按类型查找配方，给客户端发送配方书、物品属性（熔炉输入物品等）和切石机列表；
- 工作台的 3×3 合成格和玩家背包的 2×2 合成格，包括各种点击、按住 Shift 合成全部、关闭界面时物品放回背包；
- 配方书的放置请求；
- 合成统计（`minecraft:crafted`、`minecraft:interact_with_crafting_table`）。

配方通常由 [item-recipe](../item-recipe/README.md) 从数据包读取后交给本模块。本模块不使用 Minestom 的 `RecipeManager`。

实现细节和与原版的差异见 [DESIGN.md](DESIGN.md)。

## 接入

```java
Crafting crafting = new Crafting(process, maps);
process.eventHandler().addChild(crafting.eventNode());

RecipeSubscriber recipes = new RecipeSubscriber(process, crafting.recipes());
DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(recipes.subscription())
        .executor(ForkJoinPool.commonPool())
        .build();
```

`eventNode()` 中的监听：

| 事件 | 作用 |
|---|---|
| `PlayerSpawnEvent` | 玩家首次出生时发送配方数据和配方书 |
| `PlayerBlockInteractEvent` | 使用工作台时打开合成界面 |
| `PlayerTickEvent` | 工作台被移除或玩家走远时关闭界面 |
| `InventoryPreClickEvent` | 背包 2×2 合成格的点击 |
| `InventoryItemChangeEvent` | 背包合成格变化后重新计算结果 |
| `InventoryCloseEvent` | 关闭背包时把合成格和光标中的物品放回背包 |
| `PlayerDisconnectEvent` | 离线时丢出合成格和光标中的物品 |
| `PlayerPacketEvent` | 配方书的放置请求 |

## 地图数据

地图放大配方（`crafting_special_mapextending`）需要按地图 ID 读取缩放等级和是否为探险家地图，取出结果时需要复制一份放大一级的地图数据。宿主实现 `MapDataAccess` 提供这些数据：

```java
public interface MapDataAccess {
    Optional<MapInfo> find(int mapId);
    OptionalInt createScaledCopy(int mapId);
    record MapInfo(int scale, boolean explorationMap) {}
}
```

`createScaledCopy` 按原版 `MapItemSavedData.scaled()` 复制地图：缩放等级加一（最大为 4），按新的缩放等级重新计算中心坐标，分配并返回新的地图 ID。地图不存在时两个方法都返回空。方法在玩家所在实例的线程上调用。

## 使用配方

```java
RecipeManager recipes = crafting.recipes();

CraftingInput input = CraftingInput.of(3, 3, items);   // 按行排列的 9 个物品，空格为 ItemStack.AIR
recipes.getRecipeFor(RecipeType.CRAFTING, input, null).ifPresent(holder -> {
    ItemStack result = holder.value().assemble(input, recipes.context());
    List<ItemStack> remaining = holder.value().remainingItems(input);
});

SingleRecipeInput ore = new SingleRecipeInput(ItemStack.of(Material.IRON_ORE));
recipes.getRecipeFor(RecipeType.SMELTING, ore, null);
```

- `getRecipeFor` 返回配方 ID 最小的匹配配方；第三个参数是配方提示，提示的配方匹配时优先返回它。
- `assemble` 的结果经过原版的合法性检查，不合法时为 `ItemStack.AIR`。
- `replaceRecipes` 替换全部配方，并给在线玩家发送新的配方数据和配方书。查询方法可以在任何线程调用。

配方类型都在 `io.ib67.yuanban.crafting.recipe` 包中，是不可变的 record，表格见 [item-recipe/README.md](../item-recipe/README.md#数据类型)。

## 与原版的主要差异

- 全部玩家看到全部配方，不记录解锁和高亮，不保存配方书状态。
- 掉落物由 Minestom 的 `ItemDropEvent` 交给宿主处理；事件被取消时物品消失。
- 背包 2×2 合成格只拦截涉及合成格的点击，其余点击保持 Minestom 的行为。
- 不实现熔炉、切石机、锻造台的菜单。

完整列表见 [DESIGN.md](DESIGN.md) 第 14 节，涉及的 Minestom 问题见根目录 [README.md](../README.md)。

## 测试

测试使用 item-recipe 下载的原版 26.2 服务端 jar 中的数据，通过真实的客户端数据包操作界面。测试类使用 item-tag 提供的 `ResetItemTags` 扩展；地图数据使用本模块 testFixtures 中的 `InMemoryMaps`。
