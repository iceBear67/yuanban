package io.ib67.yuanban.recipe.definition;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.registry.Holder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一个配方文件解码得到的内容。每种配方对应原版 26.2 的一个配方序列化器，字段和缺省值见 {@code DESIGN.md}。
 */
public sealed interface RecipeDefinition {
    /**
     * 配方文件中 {@code show_notification} 的取值。
     */
    record CommonInfo(boolean showNotification) {
    }

    record CraftingBookInfo(CraftingCategory category, String group) {
    }

    record CookingBookInfo(CookingCategory category, String group) {
    }

    /**
     * {@code crafting_shaped}。{@code ingredients} 按行排列，长度为 {@code width * height}，空位为 {@link Optional#empty()}。
     */
    record ShapedRecipe(CommonInfo common, CraftingBookInfo book, int width, int height,
                        List<Optional<ItemIngredient>> ingredients, ItemStack result) implements RecipeDefinition {
        public ShapedRecipe {
            ingredients = List.copyOf(ingredients);
        }
    }

    /**
     * {@code crafting_shapeless}。
     */
    record ShapelessRecipe(CommonInfo common, CraftingBookInfo book, ItemStack result,
                           List<ItemIngredient> ingredients) implements RecipeDefinition {
        public ShapelessRecipe {
            ingredients = List.copyOf(ingredients);
        }
    }

    /**
     * {@code crafting_transmute}。
     */
    record TransmuteRecipe(CommonInfo common, CraftingBookInfo book, ItemIngredient input, ItemIngredient material,
                           IntBounds materialCount, ItemStack result,
                           boolean addMaterialCountToResult) implements RecipeDefinition {
    }

    /**
     * {@code crafting_dye}。
     */
    record DyeRecipe(CommonInfo common, CraftingBookInfo book, ItemIngredient target, ItemIngredient dye,
                     ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_imbue}。
     */
    record ImbueRecipe(CommonInfo common, CraftingBookInfo book, ItemIngredient source, ItemIngredient material,
                       ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_decorated_pot}。
     */
    record DecoratedPotRecipe(ItemIngredient back, ItemIngredient left, ItemIngredient right, ItemIngredient front,
                              ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_bookcloning}。
     */
    record BookCloningRecipe(ItemIngredient source, ItemIngredient material, IntBounds allowedGenerations,
                             ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_mapextending}。
     */
    record MapExtendingRecipe(ItemIngredient map, ItemIngredient material, ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_firework_rocket}。
     */
    record FireworkRocketRecipe(ItemIngredient shell, ItemIngredient fuel, ItemIngredient star,
                                ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_firework_star}。
     */
    record FireworkStarRecipe(Map<FireworkExplosion.Shape, ItemIngredient> shapes, ItemIngredient trail,
                              ItemIngredient twinkle, ItemIngredient fuel, ItemIngredient dye,
                              ItemStack result) implements RecipeDefinition {
        public FireworkStarRecipe {
            shapes = Map.copyOf(shapes);
        }
    }

    /**
     * {@code crafting_special_firework_star_fade}。
     */
    record FireworkStarFadeRecipe(ItemIngredient target, ItemIngredient dye, ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_bannerduplicate}。
     */
    record BannerDuplicateRecipe(ItemIngredient banner, ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_shielddecoration}。
     */
    record ShieldDecorationRecipe(ItemIngredient banner, ItemIngredient target, ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code crafting_special_repairitem}。
     */
    record RepairItemRecipe() implements RecipeDefinition {
    }

    /**
     * {@code smelting}、{@code blasting}、{@code smoking}、{@code campfire_cooking}。
     */
    record CookingRecipe(CookingKind kind, CommonInfo common, CookingBookInfo book, ItemIngredient ingredient,
                         ItemStack result, float experience, int cookingTime) implements RecipeDefinition {
    }

    /**
     * {@code stonecutting}。
     */
    record StonecutterRecipe(CommonInfo common, ItemIngredient ingredient, ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code smithing_transform}。
     */
    record SmithingTransformRecipe(CommonInfo common, Optional<ItemIngredient> template, ItemIngredient base,
                                   Optional<ItemIngredient> addition, ItemStack result) implements RecipeDefinition {
    }

    /**
     * {@code smithing_trim}。
     */
    record SmithingTrimRecipe(CommonInfo common, ItemIngredient template, ItemIngredient base, ItemIngredient addition,
                              Holder<TrimPattern> pattern) implements RecipeDefinition {
    }
}
