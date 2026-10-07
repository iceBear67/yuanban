package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.ItemStack;

import java.util.Objects;

/**
 * 原版 {@code SingleRecipeInput}：烧炼和切石使用的一个物品。
 */
public record SingleRecipeInput(ItemStack item) implements RecipeInput {
    public SingleRecipeInput {
        Objects.requireNonNull(item, "item");
    }

    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No item for index " + index);
        }
        return item;
    }

    @Override
    public int size() {
        return 1;
    }
}
