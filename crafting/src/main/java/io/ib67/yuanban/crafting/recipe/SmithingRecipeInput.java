package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.ItemStack;

import java.util.Objects;

/**
 * 原版 {@code SmithingRecipeInput}：锻造台的模板、基础和附加物品。
 */
public record SmithingRecipeInput(ItemStack template, ItemStack base, ItemStack addition) implements RecipeInput {
    public SmithingRecipeInput {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(addition, "addition");
    }

    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> template;
            case 1 -> base;
            case 2 -> addition;
            default -> throw new IllegalArgumentException("Recipe does not contain slot " + index);
        };
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public boolean isEmpty() {
        return template.isAir() && base.isAir() && addition.isAir();
    }
}
