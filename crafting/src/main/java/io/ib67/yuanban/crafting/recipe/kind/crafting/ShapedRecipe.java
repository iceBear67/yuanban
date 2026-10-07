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
import java.util.Optional;

/**
 * {@code crafting_shaped}，原版 {@code ShapedRecipe}。{@code ingredients} 按行排列，长度为 {@code width * height}，空位为 {@link Optional#empty()}。
 */
public record ShapedRecipe(CraftingBookInfo book, int width, int height, List<Optional<ItemIngredient>> ingredients,
                           ItemStackTemplate result) implements CraftingRecipe {
    public ShapedRecipe {
        ingredients = List.copyOf(ingredients);
        if (ingredients.size() != width * height) {
            throw new IllegalArgumentException("pattern has " + ingredients.size() + " slots, expected " + width * height);
        }
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        return matches(width, height, ingredients, input);
    }

    /**
     * 原版 {@code ShapedRecipePattern.matches}：原料数和尺寸都相同；图案不对称时先试左右翻转。
     */
    public static boolean matches(int width, int height, List<Optional<ItemIngredient>> ingredients, CraftingInput input) {
        long ingredientCount = ingredients.stream().filter(Optional::isPresent).count();
        if (input.ingredientCount() != ingredientCount || input.width() != width || input.height() != height) {
            return false;
        }
        if (!isSymmetrical(width, height, ingredients) && matches(width, height, ingredients, input, true)) {
            return true;
        }
        return matches(width, height, ingredients, input, false);
    }

    private static boolean matches(int width, int height, List<Optional<ItemIngredient>> ingredients, CraftingInput input, boolean xFlip) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var expected = xFlip ? ingredients.get(width - x - 1 + y * width) : ingredients.get(x + y * width);
                if (!ItemIngredient.testOptional(expected, input.getItem(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 原版 {@code Util.isSymmetrical}：每一行左右对称。
     */
    private static boolean isSymmetrical(int width, int height, List<Optional<ItemIngredient>> ingredients) {
        if (width == 1) {
            return true;
        }
        int centerX = width / 2;
        for (int y = 0; y < height; y++) {
            for (int leftX = 0; leftX < centerX; leftX++) {
                int rightX = width - 1 - leftX;
                if (!ingredients.get(leftX + y * width).equals(ingredients.get(rightX + y * width))) {
                    return false;
                }
            }
        }
        return true;
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
        return PlacementInfo.createFromOptionals(ingredients);
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new RecipeDisplay.CraftingShaped(width, height,
                ingredients.stream().map(ItemIngredient::displayOptional).toList(),
                new SlotDisplay.ItemStack(result.toDisplayStack()), CRAFTING_TABLE));
    }
}
