package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.recipe.display.RecipeDisplay;

import java.util.List;

/**
 * 原版 {@code CustomRecipe}：产出取决于输入物品组件的合成配方。没有配方书显示，不能放置，类别固定为 {@code misc}。
 */
public sealed interface SpecialCraftingRecipe extends CraftingRecipe
        permits DecoratedPotRecipe, BookCloningRecipe, MapExtendingRecipe, FireworkRocketRecipe, FireworkStarRecipe,
        FireworkStarFadeRecipe, BannerDuplicateRecipe, ShieldDecorationRecipe, RepairItemRecipe {
    @Override
    default boolean isSpecial() {
        return true;
    }

    @Override
    default String group() {
        return "";
    }

    @Override
    default CraftingCategory category() {
        return CraftingCategory.MISC;
    }

    @Override
    default PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    default List<RecipeDisplay> display() {
        return List.of();
    }
}
