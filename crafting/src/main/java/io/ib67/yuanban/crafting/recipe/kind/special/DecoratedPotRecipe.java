package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.PotDecorations;

import java.util.Objects;

/**
 * {@code crafting_decorated_pot}，原版 {@code DecoratedPotRecipe}：3×3 合成格的上、左、右、下四格决定陶罐四面的图案。
 */
public record DecoratedPotRecipe(ItemIngredient back, ItemIngredient left, ItemIngredient right, ItemIngredient front,
                                 ItemStackTemplate result) implements SpecialCraftingRecipe {
    public DecoratedPotRecipe {
        Objects.requireNonNull(back, "back");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(front, "front");
        Objects.requireNonNull(result, "result");
    }

    private static ItemStack back(CraftingInput input) {
        return input.getItem(1, 0);
    }

    private static ItemStack left(CraftingInput input) {
        return input.getItem(0, 1);
    }

    private static ItemStack right(CraftingInput input) {
        return input.getItem(2, 1);
    }

    private static ItemStack front(CraftingInput input) {
        return input.getItem(1, 2);
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        return input.width() == 3 && input.height() == 3 && input.ingredientCount() == 4
                && back.test(back(input)) && left.test(left(input)) && right.test(right(input)) && front.test(front(input));
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        var decorations = new PotDecorations(back(input).material(), left(input).material(), right(input).material(), front(input).material());
        return result.apply(DataComponentMap.EMPTY.set(DataComponents.POT_DECORATIONS, decorations));
    }
}
