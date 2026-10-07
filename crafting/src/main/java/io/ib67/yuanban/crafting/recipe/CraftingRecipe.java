package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingCategory;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingRemainders;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 {@code CraftingRecipe}：在工作台和背包合成格中使用的配方。
 */
public interface CraftingRecipe extends Recipe<CraftingInput> {
    SlotDisplay CRAFTING_TABLE = new SlotDisplay.Item(Material.CRAFTING_TABLE);

    @Override
    default RecipeType<CraftingRecipe> type() {
        return RecipeType.CRAFTING;
    }

    CraftingCategory category();

    /**
     * 原版 {@code getRemainingItems}：取走结果之后留在每一格的物品，与 {@link CraftingInput#items()} 一一对应。
     */
    default List<ItemStack> remainingItems(CraftingInput input) {
        return defaultCraftingRemainder(input);
    }

    @Override
    default RecipeBookCategory recipeBookCategory() {
        return switch (category()) {
            case BUILDING -> RecipeBookCategory.CRAFTING_BUILDING_BLOCKS;
            case EQUIPMENT -> RecipeBookCategory.CRAFTING_EQUIPMENT;
            case REDSTONE -> RecipeBookCategory.CRAFTING_REDSTONE;
            case MISC -> RecipeBookCategory.CRAFTING_MISC;
        };
    }

    /**
     * 原版 {@code CraftingRecipe.defaultCraftingReminder}：每一格留下物品的合成剩余物。
     */
    static List<ItemStack> defaultCraftingRemainder(CraftingInput input) {
        var result = new ArrayList<ItemStack>(input.size());
        for (int slot = 0; slot < input.size(); slot++) {
            var remainder = CraftingRemainders.of(input.getItem(slot).material());
            result.add(remainder != null ? ItemStack.of(remainder) : ItemStack.AIR);
        }
        return result;
    }
}
