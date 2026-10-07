package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code smithing_transform}，原版 {@code SmithingTransformRecipe}：结果保留基础物品的组件。{@code template}、{@code addition} 可以为空。
 */
public record SmithingTransformRecipe(Optional<ItemIngredient> template, ItemIngredient base, Optional<ItemIngredient> addition,
                                      ItemStackTemplate result) implements SmithingRecipe {
    public SmithingTransformRecipe {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(addition, "addition");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, RecipeContext context) {
        return result.applyOriginalComponents(input.base(), 0);
    }

    @Override
    public Optional<ItemIngredient> templateIngredient() {
        return template;
    }

    @Override
    public ItemIngredient baseIngredient() {
        return base;
    }

    @Override
    public Optional<ItemIngredient> additionIngredient() {
        return addition;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.createFromOptionals(List.of(template, Optional.of(base), addition));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new RecipeDisplay.Smithing(ItemIngredient.displayOptional(template), base.display(),
                ItemIngredient.displayOptional(addition), new SlotDisplay.ItemStack(result.toDisplayStack()), SMITHING_TABLE));
    }
}
