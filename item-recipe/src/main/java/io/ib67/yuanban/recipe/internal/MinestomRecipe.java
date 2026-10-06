package io.ib67.yuanban.recipe.internal;

import io.ib67.yuanban.recipe.definition.CookingKind;
import io.ib67.yuanban.recipe.definition.ItemIngredient;
import io.ib67.yuanban.recipe.definition.RecipeDefinition;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.BannerDuplicateRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.BookCloningRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.CookingRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.DecoratedPotRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.DyeRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.FireworkRocketRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.FireworkStarFadeRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.FireworkStarRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.ImbueRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.MapExtendingRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.RepairItemRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.ShapedRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.ShapelessRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.ShieldDecorationRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.SmithingTransformRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.SmithingTrimRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.StonecutterRecipe;
import io.ib67.yuanban.recipe.definition.RecipeDefinition.TransmuteRecipe;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.Recipe;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.RecipeProperty;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import net.minestom.server.registry.TagKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 一个配方在 Minestom 中的表示，提供客户端需要的配方书显示、类别、分组、物品属性和原料需求，规则见 {@code DESIGN.md} 第 5 节。
 * {@link net.minestom.server.recipe.RecipeManager} 以配方对象为键，这里使用对象自身的相等判断。
 */
// FIXME(Minestom M7): Material 没有所需特性开关数据，显示数据和物品属性不按特性开关过滤；26.2 中过滤不改变结果，
//  见 MINESTOM-BUG-REPORT.md
@ApiStatus.Internal
public final class MinestomRecipe implements Recipe {
    private static final SlotDisplay CRAFTING_TABLE = new SlotDisplay.Item(Material.CRAFTING_TABLE);
    private static final SlotDisplay STONECUTTER = new SlotDisplay.Item(Material.STONECUTTER);
    private static final SlotDisplay SMITHING_TABLE = new SlotDisplay.Item(Material.SMITHING_TABLE);

    private final Key id;
    private final RecipeDefinition definition;
    private final List<RecipeDisplay> displays;
    private final RecipeBookCategory category;
    private final @Nullable String group;
    private final Map<RecipeProperty, List<Material>> itemProperties;
    private final @Nullable List<ItemIngredient> placement;

    private MinestomRecipe(Key id, RecipeDefinition definition) {
        this.id = id;
        this.definition = definition;
        this.displays = List.copyOf(displays(definition));
        this.category = category(definition);
        var group = group(definition);
        this.group = group.isEmpty() ? null : group;
        var placement = placement(definition);
        // 原版：非特殊配方中有原料是空标签的时候不能放置，原料需求为空，不加入物品属性集合
        boolean impossibleToPlace = placement != null && placement.stream().anyMatch(ingredient -> ingredient.items().isEmpty());
        this.placement = placement == null ? null : impossibleToPlace ? List.of() : placement;
        this.itemProperties = impossibleToPlace ? Map.of() : itemProperties(definition);
    }

    public static MinestomRecipe of(Key id, RecipeDefinition definition) {
        return new MinestomRecipe(Objects.requireNonNull(id, "id"), Objects.requireNonNull(definition, "definition"));
    }

    public Key id() {
        return id;
    }

    public RecipeDefinition definition() {
        return definition;
    }

    @Override
    public List<RecipeDisplay> createRecipeDisplays() {
        return displays;
    }

    @Override
    public Map<RecipeProperty, List<Material>> itemProperties() {
        return itemProperties;
    }

    // FIXME(Minestom M4): RecipeManager.addRecipe 不读取 recipeBookGroup()，配方书条目没有分组，见 MINESTOM-BUG-REPORT.md
    @Override
    public @Nullable String recipeBookGroup() {
        return group;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return category;
    }

    /**
     * 原版 {@code PlacementInfo.ingredients()}。标签原料使用 Minestom 注册表中的标签，由 {@code item-tag} 在本模块之前提交。
     */
    @Override
    public @Nullable List<Ingredient> craftingRequirements() {
        if (placement == null) {
            return null;
        }
        var result = new ArrayList<Ingredient>(placement.size());
        for (var ingredient : placement) {
            result.add(switch (ingredient) {
                case ItemIngredient.OfTag tag -> new Ingredient(Objects.requireNonNull(
                        Material.staticRegistry().getTag(TagKey.of(tag.tag())), () -> "item tag " + tag.tag().asString() + " is not registered"));
                case ItemIngredient.OfItems items -> new Ingredient(items.items());
            });
        }
        return result;
    }

    @Override
    public String toString() {
        return "MinestomRecipe[" + id.asString() + "]";
    }

    // 显示数据：原版各配方类的 display()

    private static List<RecipeDisplay> displays(RecipeDefinition definition) {
        return switch (definition) {
            case ShapedRecipe shaped -> List.of(new RecipeDisplay.CraftingShaped(
                    shaped.width(), shaped.height(),
                    shaped.ingredients().stream().map(slot -> slot.map(MinestomRecipe::display).orElse(SlotDisplay.Empty.INSTANCE)).toList(),
                    new SlotDisplay.ItemStack(shaped.result()), CRAFTING_TABLE));
            case ShapelessRecipe shapeless -> List.of(new RecipeDisplay.CraftingShapeless(
                    shapeless.ingredients().stream().map(MinestomRecipe::display).toList(),
                    new SlotDisplay.ItemStack(shapeless.result()), CRAFTING_TABLE));
            case TransmuteRecipe transmute -> transmuteDisplays(transmute);
            case DyeRecipe dye -> {
                var dyes = new SlotDisplay.OnlyWithComponent(display(dye.dye()), DataComponents.DYE);
                var target = display(dye.target());
                yield List.of(new RecipeDisplay.CraftingShapeless(List.of(target, dyes), new SlotDisplay.Dyed(dyes, target), CRAFTING_TABLE));
            }
            case ImbueRecipe imbue -> {
                var material = display(imbue.material());
                var source = new SlotDisplay.WithAnyPotion(display(imbue.source()));
                yield List.of(new RecipeDisplay.CraftingShaped(3, 3,
                        List.of(material, material, material, material, source, material, material, material, material),
                        new SlotDisplay.WithAnyPotion(new SlotDisplay.ItemStack(imbue.result())), CRAFTING_TABLE));
            }
            case CookingRecipe cooking -> List.of(new RecipeDisplay.Furnace(
                    display(cooking.ingredient()), SlotDisplay.AnyFuel.INSTANCE, new SlotDisplay.ItemStack(cooking.result()),
                    new SlotDisplay.Item(furnaceIcon(cooking.kind())), cooking.cookingTime(), cooking.experience()));
            case StonecutterRecipe stonecutter -> List.of(new RecipeDisplay.Stonecutter(
                    stonecutterDisplay(stonecutter.ingredient()), new SlotDisplay.ItemStack(stonecutter.result()), STONECUTTER));
            case SmithingTransformRecipe transform -> List.of(new RecipeDisplay.Smithing(
                    transform.template().map(MinestomRecipe::display).orElse(SlotDisplay.Empty.INSTANCE),
                    display(transform.base()),
                    transform.addition().map(MinestomRecipe::display).orElse(SlotDisplay.Empty.INSTANCE),
                    new SlotDisplay.ItemStack(transform.result()), SMITHING_TABLE));
            // FIXME(Minestom M2): SlotDisplay.SmithingTrim 把纹饰样式编码成 SlotDisplay，发出去会破坏数据包，
            //  所以锻造纹饰配方不生成显示，见 MINESTOM-BUG-REPORT.md
            case SmithingTrimRecipe _ -> List.of();
            case DecoratedPotRecipe _, BookCloningRecipe _, MapExtendingRecipe _, FireworkRocketRecipe _,
                 FireworkStarRecipe _, FireworkStarFadeRecipe _, BannerDuplicateRecipe _, ShieldDecorationRecipe _,
                 RepairItemRecipe _ -> List.of();
        };
    }

    private static List<RecipeDisplay> transmuteDisplays(TransmuteRecipe transmute) {
        var displays = new ArrayList<RecipeDisplay>();
        var slots = new ArrayList<SlotDisplay>();
        slots.add(display(transmute.input()));
        var material = display(transmute.material());
        int min = transmute.materialCount().min().orElse(1);
        int max = transmute.materialCount().max().orElse(8);
        for (int count = min; count <= max; count++) {
            slots.add(material);
            int resultCount = transmute.addMaterialCountToResult() ? count + transmute.result().amount() : transmute.result().amount();
            displays.add(new RecipeDisplay.CraftingShapeless(List.copyOf(slots),
                    new SlotDisplay.ItemStack(transmute.result().withAmount(resultCount)), CRAFTING_TABLE));
        }
        return displays;
    }

    /**
     * 原版 {@code Ingredient.display()}。
     */
    private static SlotDisplay display(ItemIngredient ingredient) {
        return switch (ingredient) {
            case ItemIngredient.OfTag tag -> new SlotDisplay.Tag(TagKey.of(tag.tag()));
            case ItemIngredient.OfItems items -> new SlotDisplay.Composite(items.items().stream().map(MinestomRecipe::displayForSingleItem).toList());
        };
    }

    private static SlotDisplay displayForSingleItem(Material material) {
        var input = new SlotDisplay.Item(material);
        var remainder = MinestomWorkarounds.craftingRemainder(material);
        return remainder == null ? input : new SlotDisplay.WithRemainder(input, new SlotDisplay.ItemStack(ItemStack.of(remainder)));
    }

    // FIXME(Minestom M3): DeclareRecipesPacket 从显示数据反推切石机原料，只接受 Item 和 Tag，丢弃原版生成的 Composite。
    //  原料只有一个没有合成剩余物的物品时改用 Item；列出多个物品的原料仍然会丢失，见 MINESTOM-BUG-REPORT.md
    private static SlotDisplay stonecutterDisplay(ItemIngredient ingredient) {
        if (ingredient instanceof ItemIngredient.OfItems(var items) && items.size() == 1
                && MinestomWorkarounds.craftingRemainder(items.getFirst()) == null) {
            return new SlotDisplay.Item(items.getFirst());
        }
        return display(ingredient);
    }

    private static Material furnaceIcon(CookingKind kind) {
        return switch (kind) {
            case SMELTING -> Material.FURNACE;
            case BLASTING -> Material.BLAST_FURNACE;
            case SMOKING -> Material.SMOKER;
            case CAMPFIRE_COOKING -> Material.CAMPFIRE;
        };
    }

    // 配方书类别与分组：原版各配方类的 recipeBookCategory() 和 group()

    private static RecipeBookCategory category(RecipeDefinition definition) {
        return switch (definition) {
            case ShapedRecipe shaped -> crafting(shaped.book());
            case ShapelessRecipe shapeless -> crafting(shapeless.book());
            case TransmuteRecipe transmute -> crafting(transmute.book());
            case DyeRecipe dye -> crafting(dye.book());
            case ImbueRecipe imbue -> crafting(imbue.book());
            case DecoratedPotRecipe _, BookCloningRecipe _, MapExtendingRecipe _, FireworkRocketRecipe _,
                 FireworkStarRecipe _, FireworkStarFadeRecipe _, BannerDuplicateRecipe _, ShieldDecorationRecipe _,
                 RepairItemRecipe _ -> RecipeBookCategory.CRAFTING_MISC;
            case CookingRecipe cooking -> switch (cooking.kind()) {
                case SMELTING -> switch (cooking.book().category()) {
                    case BLOCKS -> RecipeBookCategory.FURNACE_BLOCKS;
                    case FOOD -> RecipeBookCategory.FURNACE_FOOD;
                    case MISC -> RecipeBookCategory.FURNACE_MISC;
                };
                case BLASTING -> switch (cooking.book().category()) {
                    case BLOCKS -> RecipeBookCategory.BLAST_FURNACE_BLOCKS;
                    case FOOD, MISC -> RecipeBookCategory.BLAST_FURNACE_MISC;
                };
                case SMOKING -> RecipeBookCategory.SMOKER_FOOD;
                case CAMPFIRE_COOKING -> RecipeBookCategory.CAMPFIRE;
            };
            case StonecutterRecipe _ -> RecipeBookCategory.STONECUTTER;
            case SmithingTransformRecipe _, SmithingTrimRecipe _ -> RecipeBookCategory.SMITHING;
        };
    }

    private static RecipeBookCategory crafting(RecipeDefinition.CraftingBookInfo book) {
        return switch (book.category()) {
            case BUILDING -> RecipeBookCategory.CRAFTING_BUILDING_BLOCKS;
            case REDSTONE -> RecipeBookCategory.CRAFTING_REDSTONE;
            case EQUIPMENT -> RecipeBookCategory.CRAFTING_EQUIPMENT;
            case MISC -> RecipeBookCategory.CRAFTING_MISC;
        };
    }

    private static String group(RecipeDefinition definition) {
        return switch (definition) {
            case ShapedRecipe shaped -> shaped.book().group();
            case ShapelessRecipe shapeless -> shapeless.book().group();
            case TransmuteRecipe transmute -> transmute.book().group();
            case DyeRecipe dye -> dye.book().group();
            case ImbueRecipe imbue -> imbue.book().group();
            case CookingRecipe cooking -> cooking.book().group();
            case DecoratedPotRecipe _, BookCloningRecipe _, MapExtendingRecipe _, FireworkRocketRecipe _,
                 FireworkStarRecipe _, FireworkStarFadeRecipe _, BannerDuplicateRecipe _, ShieldDecorationRecipe _,
                 RepairItemRecipe _, StonecutterRecipe _, SmithingTransformRecipe _, SmithingTrimRecipe _ -> "";
        };
    }

    // 放置信息与物品属性：原版 PlacementInfo 和 RecipeManager.RECIPE_PROPERTY_SETS

    /**
     * 原版各配方类的 {@code createPlacementInfo()} 使用的原料；特殊配方返回 {@code null}。
     */
    private static @Nullable List<ItemIngredient> placement(RecipeDefinition definition) {
        return switch (definition) {
            case ShapedRecipe shaped -> shaped.ingredients().stream().flatMap(Optional::stream).toList();
            case ShapelessRecipe shapeless -> shapeless.ingredients();
            case TransmuteRecipe transmute -> {
                var ingredients = new ArrayList<ItemIngredient>();
                ingredients.add(transmute.input());
                ingredients.addAll(Collections.nCopies(transmute.materialCount().max().orElse(8), transmute.material()));
                yield ingredients;
            }
            case DyeRecipe dye -> List.of(dye.target(), dye.dye());
            case ImbueRecipe imbue -> {
                var m = imbue.material();
                yield List.of(m, m, m, m, imbue.source(), m, m, m, m);
            }
            case CookingRecipe cooking -> List.of(cooking.ingredient());
            case StonecutterRecipe stonecutter -> List.of(stonecutter.ingredient());
            case SmithingTransformRecipe transform -> {
                var ingredients = new ArrayList<ItemIngredient>();
                transform.template().ifPresent(ingredients::add);
                ingredients.add(transform.base());
                transform.addition().ifPresent(ingredients::add);
                yield ingredients;
            }
            case SmithingTrimRecipe trim -> List.of(trim.template(), trim.base(), trim.addition());
            case DecoratedPotRecipe _, BookCloningRecipe _, MapExtendingRecipe _, FireworkRocketRecipe _,
                 FireworkStarRecipe _, FireworkStarFadeRecipe _, BannerDuplicateRecipe _, ShieldDecorationRecipe _,
                 RepairItemRecipe _ -> null;
        };
    }

    private static Map<RecipeProperty, List<Material>> itemProperties(RecipeDefinition definition) {
        var properties = new EnumMap<RecipeProperty, List<Material>>(RecipeProperty.class);
        switch (definition) {
            case CookingRecipe cooking -> properties.put(switch (cooking.kind()) {
                case SMELTING -> RecipeProperty.FURNACE_INPUT;
                case BLASTING -> RecipeProperty.BLAST_FURNACE_INPUT;
                case SMOKING -> RecipeProperty.SMOKER_INPUT;
                case CAMPFIRE_COOKING -> RecipeProperty.CAMPFIRE_INPUT;
            }, cooking.ingredient().items());
            case SmithingTransformRecipe transform -> {
                transform.template().ifPresent(template -> properties.put(RecipeProperty.SMITHING_TEMPLATE, template.items()));
                properties.put(RecipeProperty.SMITHING_BASE, transform.base().items());
                transform.addition().ifPresent(addition -> properties.put(RecipeProperty.SMITHING_ADDITION, addition.items()));
            }
            case SmithingTrimRecipe trim -> {
                properties.put(RecipeProperty.SMITHING_TEMPLATE, trim.template().items());
                properties.put(RecipeProperty.SMITHING_BASE, trim.base().items());
                properties.put(RecipeProperty.SMITHING_ADDITION, trim.addition().items());
            }
            default -> {
            }
        }
        return Collections.unmodifiableMap(properties);
    }
}
