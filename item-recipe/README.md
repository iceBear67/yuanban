# item-recipe

`item-recipe`（库）是 [datapack-loader](../datapack-loader/DESIGN.md) 的订阅者。它从数据包读取配方（`data/<namespace>/recipe/**.json`），按本次加载的物品标签解码原版 26.2 的全部 21 种配方，提交阶段交给 [crafting](../crafting/README.md) 模块的 `RecipeManager`。

配方的类型、匹配与产出、配方书、发给客户端的配方数据、工作台和背包合成格都由 `crafting` 提供。本模块只负责从数据包得到配方。

实现细节和与原版的差异见 [DESIGN.md](DESIGN.md)。

## 注册

本模块依赖 [item-tag](../item-tag/README.md) 和 [crafting](../crafting/README.md)：

```java
Crafting crafting = new Crafting(process, maps);
process.eventHandler().addChild(crafting.eventNode());
RecipeSubscriber recipes = new RecipeSubscriber(process, crafting.recipes());

DataPackLoader loader = DataPackLoader.builder(LoaderSettings.MINECRAFT_26_2)
        .provider(new BuiltInPackProvider(vanillaRoot, "26.2"))
        .provider(new FolderPackProvider(worldDir.resolve("datapacks"), PackOrigin.WORLD, symlinkAllowList))
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(recipes.subscription())
        .executor(ForkJoinPool.commonPool())
        .build();
DataPackSession session = loader.start(selectionRequest);
```

`recipes.subscription()` 以 `recipes` 这个对象作为订阅者：

| 项目 | 取值 |
|---|---|
| 订阅者名字 | `RecipeSubscriber.KEY`（`yuanban:recipes`） |
| 生命周期 | `RELOADABLE`，启动和每次重载都执行 |
| 读取的 topic | `RecipeSubscriber.TOPIC`，即 `Topic.json("recipe")` |
| 依赖 | `ItemTagSubscriber.KEY` |

提交之后，`crafting` 的 `RecipeManager` 持有新配方，在线玩家会重新收到配方数据和配方书。

## 在其他模块中使用配方

### 游戏运行期间

匹配配方、查询配方书条目使用 `crafting` 的 `RecipeManager`，见 [crafting/README.md](../crafting/README.md)。`current()` 返回最近一次提交的解码结果，提交之前是 `RecipeSet.EMPTY`；重载失败时不变。返回的对象不可修改，可以在任何线程读取。

```java
RecipeSet set = recipes.current();
Recipe<?> stick = set.recipes().get(Key.key("minecraft:stick"));
```

### 加载期间：读取准备结果

需要在每次重载时根据配方重建数据的模块，声明依赖 `RecipeSubscriber.KEY`，在 `prepare` 中读取本次加载解码出的配方：

```java
static final SubscriberKey<CookingIndex> COOKING = SubscriberKey.of("example:cooking");

final class CookingIndexSubscriber implements Subscriber<CookingIndex> {
    private volatile CookingIndex index = CookingIndex.EMPTY;

    @Override
    public CookingIndex prepare(PrepareContext context) {
        RecipeSet set = context.dependency(RecipeSubscriber.KEY);
        CookingIndex.Builder builder = CookingIndex.builder();
        set.recipes().forEach((id, recipe) -> {
            if (recipe instanceof CookingRecipe cooking) {
                builder.add(id, cooking.kind(), cooking.ingredient(), cooking.result());
            }
        });
        return builder.build();
    }

    @Override
    public void apply(CookingIndex prepared) {
        index = prepared;
    }
}

loaderBuilder
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(recipes.subscription())
        .subscribe(Subscription.builder(COOKING, new CookingIndexSubscriber())
                .lifecycle(Lifecycle.RELOADABLE)
                .after(RecipeSubscriber.KEY)
                .build());
```

在 `prepare` 中读到的是本次加载的新配方；如果本次加载失败，任何 `apply` 都不会执行，`current()` 和依赖者持有的数据都保持原样。

## 数据类型

`RecipeSet`：

```java
public record RecipeSet(NavigableMap<Key, Recipe<?>> recipes) {
    public static final RecipeSet EMPTY;
}
```

键是配方 ID（`data/minecraft/recipe/stick.json` 的 ID 是 `minecraft:stick`），按自然顺序排列。配方的类型在 `crafting` 模块的 `io.ib67.yuanban.crafting.recipe` 包中，都是不可变的 record：

| `type` | `Recipe` 的实现 |
|---|---|
| `crafting_shaped` | `ShapedRecipe(book, width, height, ingredients, result)`，`ingredients` 按行排列，空位为 `Optional.empty()` |
| `crafting_shapeless` | `ShapelessRecipe(book, result, ingredients)` |
| `crafting_transmute` | `TransmuteRecipe(book, input, material, materialCount, result, addMaterialCountToResult)` |
| `crafting_dye` | `DyeRecipe(book, target, dye, result)` |
| `crafting_imbue` | `ImbueRecipe(book, source, material, result)` |
| `crafting_decorated_pot` | `DecoratedPotRecipe(back, left, right, front, result)` |
| `crafting_special_bookcloning` | `BookCloningRecipe(source, material, allowedGenerations, result)` |
| `crafting_special_mapextending` | `MapExtendingRecipe(map, material, result)` |
| `crafting_special_firework_rocket` | `FireworkRocketRecipe(shell, fuel, star, result)` |
| `crafting_special_firework_star` | `FireworkStarRecipe(shapes, trail, twinkle, fuel, dye, result)` |
| `crafting_special_firework_star_fade` | `FireworkStarFadeRecipe(target, dye, result)` |
| `crafting_special_bannerduplicate` | `BannerDuplicateRecipe(banner, result)` |
| `crafting_special_shielddecoration` | `ShieldDecorationRecipe(banner, target, result)` |
| `crafting_special_repairitem` | `RepairItemRecipe()` |
| `smelting`、`blasting`、`smoking`、`campfire_cooking` | `CookingRecipe(kind, book, ingredient, result, experience, cookingTime)`，`kind` 区分四种烧炼方块 |
| `stonecutting` | `StonecutterRecipe(ingredient, result)` |
| `smithing_transform` | `SmithingTransformRecipe(template, base, addition, result)`，`template`、`addition` 可以为空 |
| `smithing_trim` | `SmithingTrimRecipe(template, base, addition, pattern)` |

公共的字段：

- `book`：合成配方为 `CraftingBookInfo(category, group)`，烧炼配方为 `CookingBookInfo(category, group)`。
- `result`：`ItemStackTemplate(material, count, components)`，`components` 是配方文件中的组件补丁。
- `materialCount`、`allowedGenerations`：`IntBounds(min, max)`，两端都可以为空。
- `pattern`：Minestom 的 `Holder<TrimPattern>`。

配方文件中的 `show_notification` 按原版检查类型，但不保存：它只用于配方解锁时的提示，`crafting` 让全部玩家已知全部配方。

原料是 `ItemIngredient`：

```java
public sealed interface ItemIngredient {
    List<Material> items();
    record OfTag(Key tag, List<Material> items) implements ItemIngredient {}   // "#minecraft:planks"
    record OfItems(List<Material> items) implements ItemIngredient {}          // "minecraft:stone" 或 ["minecraft:stone", ...]
}
```

`OfTag.items()` 是这个标签在本次加载中展开得到的物品，和同一次加载中 `item-tag` 的结果一致，可能为空。判断一个物品能否放进某个原料使用 `ingredient.test(itemStack)`。

## 错误

配方文件无法解码时，准备阶段抛出 `RecipeException`，整次加载失败（`datapack-loader` 抛出 `LoadFailedException`，它的 cause 就是这个异常），游戏状态不变。异常信息列出全部失败的文件，每行一个：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/recipe/foo.json: Missing tag: 'minecraft:logs' in 'minecraft:item'
```

原版只记录日志并跳过这些文件。

## 测试

测试会从 Mojang 下载 26.2 服务端 jar（约 60 MB，按 SHA-1 校验，下载一次之后保存在 `build/vanilla/`），用它验证原版全部配方都能解码。测试类使用 item-tag 提供的 `ResetItemTags` 扩展，见 [item-tag/README.md](../item-tag/README.md)；地图数据使用 crafting 的 testFixtures 中的 `InMemoryMaps`。
