package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.List;
import java.util.Objects;

/**
 * {@code smelting}、{@code blasting}、{@code smoking}、{@code campfire_cooking}，原版 {@code AbstractCookingRecipe} 的四个子类，
 * {@code kind} 区分烧炼方块。
 */
public record CookingRecipe(CookingKind kind, CookingBookInfo book, ItemIngredient ingredient, ItemStackTemplate result,
                            float experience, int cookingTime) implements Recipe<SingleRecipeInput> {
    public CookingRecipe {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public RecipeType<CookingRecipe> type() {
        return kind.recipeType();
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
        return book.group();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredient);
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new RecipeDisplay.Furnace(ingredient.display(), SlotDisplay.AnyFuel.INSTANCE,
                new SlotDisplay.ItemStack(result.toDisplayStack()), new SlotDisplay.Item(kind.furnaceIcon()), cookingTime, experience));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return switch (kind) {
            case SMELTING -> switch (book.category()) {
                case BLOCKS -> RecipeBookCategory.FURNACE_BLOCKS;
                case FOOD -> RecipeBookCategory.FURNACE_FOOD;
                case MISC -> RecipeBookCategory.FURNACE_MISC;
            };
            case BLASTING -> book.category() == CookingCategory.BLOCKS ? RecipeBookCategory.BLAST_FURNACE_BLOCKS : RecipeBookCategory.BLAST_FURNACE_MISC;
            case SMOKING -> RecipeBookCategory.SMOKER_FOOD;
            case CAMPFIRE_COOKING -> RecipeBookCategory.CAMPFIRE;
        };
    }
}
