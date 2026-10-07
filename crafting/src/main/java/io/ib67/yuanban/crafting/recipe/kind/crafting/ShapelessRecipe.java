package io.ib67.yuanban.crafting.recipe.kind.crafting;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.CraftingRecipe;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import io.ib67.yuanban.crafting.recipe.kind.misc.PlacementInfo;
import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.List;

/**
 * {@code crafting_shapeless}，原版 {@code ShapelessRecipe}。
 */
public record ShapelessRecipe(CraftingBookInfo book, ItemStackTemplate result, List<ItemIngredient> ingredients) implements CraftingRecipe {
    public ShapelessRecipe {
        ingredients = List.copyOf(ingredients);
    }

    /**
     * 原料数相同；只有一格时直接测试，否则用 {@code StackedContents} 为每个原料分配一个格子。
     */
    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() != ingredients.size()) {
            return false;
        }
        return input.size() == 1 && ingredients.size() == 1
                ? ingredients.getFirst().test(input.getItem(0))
                : input.stackedContents().canCraft(placementInfo(), null);
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        return result.create();
    }

    @Override
    public String group() {
        return book.group();
    }

    @Override
    public CraftingCategory category() {
        return book.category();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients);
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new RecipeDisplay.CraftingShapeless(ingredients.stream().map(ItemIngredient::display).toList(),
                new SlotDisplay.ItemStack(result.toDisplayStack()), CRAFTING_TABLE));
    }
}
