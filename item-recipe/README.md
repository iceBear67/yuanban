# item-recipe

`item-recipe`（库）是 [datapack-loader](../datapack-loader/DESIGN.md) 的订阅者。它从数据包读取配方（`data/<namespace>/recipe/**.json`），按本次加载的物品标签解码原版 26.2 的全部 21 种配方，然后：

- 把配方注册到 Minestom 的 `RecipeManager`，客户端因此能看到配方书、熔炉等方块的输入物品、切石机配方；
- 以不可变的数据类型提供每个配方的完整内容，给实现合成逻辑的模块使用。

Minestom 只负责把配方信息发给客户端，没有配方的数据类型，也没有判断物品能否合成、合成出什么的逻辑。本模块同样不实现合成逻辑，只提供合成逻辑所需的配方数据。

实现细节和与原版的差异见 [DESIGN.md](DESIGN.md)。

## 注册

本模块依赖 [item-tag](../item-tag/README.md)，两个订阅都需要注册：

```java
RecipeSubscriber recipes = new RecipeSubscriber(process);

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

提交之后，全部配方对全部玩家可见（不处理原版的配方解锁），在线玩家会重新收到配方数据和配方书。

## 在其他模块中使用配方

### 游戏运行期间：`current()`

```java
RecipeSet set = recipes.current();
RecipeDefinition stick = set.recipes().get(Key.key("minecraft:stick"));
```

`current()` 返回最近一次提交的配方，提交之前是 `RecipeSet.EMPTY`。重载成功之后它返回新的 `RecipeSet`；重载失败时不变。返回的对象不可修改，可以在任何线程读取。

### 加载期间：读取准备结果

需要在每次重载时根据配方重建数据的模块（例如按原料建立查找索引），声明依赖 `RecipeSubscriber.KEY`，在 `prepare` 中读取本次加载解码出的配方：

```java
static final SubscriberKey<CraftingIndex> CRAFTING = SubscriberKey.of("example:crafting");

final class CraftingIndexSubscriber implements Subscriber<CraftingIndex> {
    private volatile CraftingIndex index = CraftingIndex.EMPTY;

    @Override
    public CraftingIndex prepare(PrepareContext context) {
        RecipeSet set = context.dependency(RecipeSubscriber.KEY);
        CraftingIndex.Builder builder = CraftingIndex.builder();
        set.recipes().forEach((id, recipe) -> {
            if (recipe instanceof RecipeDefinition.ShapedRecipe shaped) {
                builder.addShaped(id, shaped.width(), shaped.height(), shaped.ingredients(), shaped.result());
            }
        });
        return builder.build();
    }

    @Override
    public void apply(CraftingIndex prepared) {
        index = prepared;
    }
}

loaderBuilder
        .subscribe(ItemTagSubscriber.subscription(process))
        .subscribe(recipes.subscription())
        .subscribe(Subscription.builder(CRAFTING, new CraftingIndexSubscriber())
                .lifecycle(Lifecycle.RELOADABLE)
                .after(RecipeSubscriber.KEY)
                .build());
```

在 `prepare` 中读到的是本次加载的新配方；如果本次加载失败，任何 `apply` 都不会执行，`current()` 和依赖者持有的数据都保持原样。

## 数据类型

`RecipeSet`：

```java
public record RecipeSet(NavigableMap<Key, RecipeDefinition> recipes) {
    public static final RecipeSet EMPTY;
}
```

键是配方 ID（`data/minecraft/recipe/stick.json` 的 ID 是 `minecraft:stick`），按自然顺序排列。

`io.ib67.yuanban.recipe.definition` 包中的类型都是 record，内容不可修改：

| `type` | `RecipeDefinition` 的实现 |
|---|---|
| `crafting_shaped` | `ShapedRecipe(common, book, width, height, ingredients, result)`，`ingredients` 按行排列，空位为 `Optional.empty()` |
| `crafting_shapeless` | `ShapelessRecipe(common, book, result, ingredients)` |
| `crafting_transmute` | `TransmuteRecipe(common, book, input, material, materialCount, result, addMaterialCountToResult)` |
| `crafting_dye` | `DyeRecipe(common, book, target, dye, result)` |
| `crafting_imbue` | `ImbueRecipe(common, book, source, material, result)` |
| `crafting_decorated_pot` | `DecoratedPotRecipe(back, left, right, front, result)` |
| `crafting_special_bookcloning` | `BookCloningRecipe(source, material, allowedGenerations, result)` |
| `crafting_special_mapextending` | `MapExtendingRecipe(map, material, result)` |
| `crafting_special_firework_rocket` | `FireworkRocketRecipe(shell, fuel, star, result)` |
| `crafting_special_firework_star` | `FireworkStarRecipe(shapes, trail, twinkle, fuel, dye, result)` |
| `crafting_special_firework_star_fade` | `FireworkStarFadeRecipe(target, dye, result)` |
| `crafting_special_bannerduplicate` | `BannerDuplicateRecipe(banner, result)` |
| `crafting_special_shielddecoration` | `ShieldDecorationRecipe(banner, target, result)` |
| `crafting_special_repairitem` | `RepairItemRecipe()` |
| `smelting`、`blasting`、`smoking`、`campfire_cooking` | `CookingRecipe(kind, common, book, ingredient, result, experience, cookingTime)`，`kind` 区分四种烧炼方块 |
| `stonecutting` | `StonecutterRecipe(common, ingredient, result)` |
| `smithing_transform` | `SmithingTransformRecipe(common, template, base, addition, result)`，`template`、`addition` 可以为空 |
| `smithing_trim` | `SmithingTrimRecipe(common, template, base, addition, pattern)` |

公共的字段：

- `common`：`CommonInfo(showNotification)`。
- `book`：合成配方为 `CraftingBookInfo(category, group)`，烧炼配方为 `CookingBookInfo(category, group)`。
- `result`：Minestom 的 `ItemStack`，包含数量和组件。
- `materialCount`、`allowedGenerations`：`IntBounds(min, max)`，两端都可以为空。
- `pattern`：Minestom 的 `Holder<TrimPattern>`。

原料是 `ItemIngredient`：

```java
public sealed interface ItemIngredient {
    List<Material> items();
    record OfTag(Key tag, List<Material> items) implements ItemIngredient {}   // "#minecraft:planks"
    record OfItems(List<Material> items) implements ItemIngredient {}          // "minecraft:stone" 或 ["minecraft:stone", ...]
}
```

`OfTag.items()` 是这个标签在本次加载中展开得到的物品，和同一次加载中 `item-tag` 的结果一致，可能为空。原料没有使用 Minestom 的 `Ingredient`，因为准备阶段新标签还没有写进 Minestom 注册表。

判断一个物品能否放进某个原料：

```java
boolean accepts = ingredient.items().contains(itemStack.material());
```

## 错误

配方文件无法解码时，准备阶段抛出 `RecipeException`，整次加载失败（`datapack-loader` 抛出 `LoadFailedException`，它的 cause 就是这个异常），游戏状态不变。异常信息列出全部失败的文件，每行一个：

```
Couldn't parse data file 'example:foo' from [file/pack] data/example/recipe/foo.json: Missing tag: 'minecraft:logs' in 'minecraft:item'
```

原版只记录日志并跳过这些文件。

## 使用限制

以下限制来自 Minestom 的问题，详见根目录 [README.md](../README.md) 和 [MINESTOM-BUG-REPORT.md](../MINESTOM-BUG-REPORT.md)：

- 锻造纹饰配方没有配方书条目（M2）。
- 原料列出多个物品的切石机配方不会出现在客户端的切石机中（M3）。
- 配方书条目没有分组（M4）。
- Minestom 必须位于 classpath 上（Gradle 默认的运行方式），刷新配方数据包用到了反射（M1）。

## 测试

测试会从 Mojang 下载 26.2 服务端 jar（约 60 MB，按 SHA-1 校验，下载一次之后保存在 `build/vanilla/`），用它验证原版全部配方都能解码。测试类使用 item-tag 提供的 `ResetItemTags` 扩展，见 [item-tag/README.md](../item-tag/README.md)。
