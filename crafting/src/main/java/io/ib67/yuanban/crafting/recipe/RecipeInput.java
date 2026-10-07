package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.ItemStack;

/**
 * 原版 {@code RecipeInput}：配方匹配时读取的物品。
 */
public sealed interface RecipeInput permits CraftingInput, SingleRecipeInput, SmithingRecipeInput {
    ItemStack getItem(int index);

    int size();

    /**
     * 原版 {@code RecipeInput.isEmpty}：全部物品为空。为空的输入不匹配任何配方。
     */
    default boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (!getItem(i).isAir()) {
                return false;
            }
        }
        return true;
    }
}
