package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.Material;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.Optional;

/**
 * 原版 {@code SmithingRecipe}：在锻造台中使用的配方。
 */
public interface SmithingRecipe extends Recipe<SmithingRecipeInput> {
    SlotDisplay SMITHING_TABLE = new SlotDisplay.Item(Material.SMITHING_TABLE);

    @Override
    default RecipeType<SmithingRecipe> type() {
        return RecipeType.SMITHING;
    }

    @Override
    default boolean matches(SmithingRecipeInput input, RecipeContext context) {
        return ItemIngredient.testOptional(templateIngredient(), input.template())
                && baseIngredient().test(input.base())
                && ItemIngredient.testOptional(additionIngredient(), input.addition());
    }

    Optional<ItemIngredient> templateIngredient();

    ItemIngredient baseIngredient();

    Optional<ItemIngredient> additionIngredient();

    @Override
    default String group() {
        return "";
    }

    @Override
    default RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategory.SMITHING;
    }
}
