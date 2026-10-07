package io.ib67.yuanban.crafting.recipe.kind.stonecutter;

import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.Recipe;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.RecipeType;
import io.ib67.yuanban.crafting.recipe.SingleRecipeInput;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import io.ib67.yuanban.crafting.recipe.kind.misc.PlacementInfo;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.List;
import java.util.Objects;

/**
 * {@code stonecutting}，原版 {@code StonecutterRecipe}。
 */
public record StonecutterRecipe(ItemIngredient ingredient, ItemStackTemplate result) implements Recipe<SingleRecipeInput> {
    private static final SlotDisplay STONECUTTER = new SlotDisplay.Item(Material.STONECUTTER);

    public StonecutterRecipe {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public RecipeType<StonecutterRecipe> type() {
        return RecipeType.STONECUTTING;
    }

    @Override
    public boolean matches(SingleRecipeInput input, RecipeContext context) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, RecipeContext context) {
        return result.create();
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredient);
    }

    /**
     * 原版 {@code StonecutterRecipe.resultDisplay}：切石机选项列表中显示的结果。
     */
    public SlotDisplay resultDisplay() {
        return new SlotDisplay.ItemStack(result.toDisplayStack());
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new RecipeDisplay.Stonecutter(ingredient.display(), resultDisplay(), STONECUTTER));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategory.STONECUTTER;
    }
}
