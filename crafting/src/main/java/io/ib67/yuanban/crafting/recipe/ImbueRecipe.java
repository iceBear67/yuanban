package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_imbue}，原版 {@code ImbueRecipe}：中心一个 {@code source}，周围八个 {@code material}，结果带上 {@code source} 的药水效果。
 */
public record ImbueRecipe(CraftingBookInfo book, ItemIngredient source, ItemIngredient material, ItemStackTemplate result) implements CraftingRecipe {
    public ImbueRecipe {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) {
            return false;
        }
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                var stack = input.getItem(x, y);
                if (stack.isAir()) {
                    return false;
                }
                var ingredient = x == 1 && y == 1 ? source : material;
                if (!ingredient.test(stack)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        var potion = input.getItem(1, 1).get(DataComponents.POTION_CONTENTS);
        var produced = result.create();
        // 原版 set 传入 null 的时候移除这个组件
        return potion == null ? produced.without(DataComponents.POTION_CONTENTS) : produced.with(DataComponents.POTION_CONTENTS, potion);
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
        return PlacementInfo.create(List.of(material, material, material, material, source, material, material, material, material));
    }

    @Override
    public List<RecipeDisplay> display() {
        var materialDisplay = material.display();
        var sourceDisplay = new SlotDisplay.WithAnyPotion(source.display());
        return List.of(new RecipeDisplay.CraftingShaped(3, 3,
                List.of(materialDisplay, materialDisplay, materialDisplay, materialDisplay, sourceDisplay,
                        materialDisplay, materialDisplay, materialDisplay, materialDisplay),
                new SlotDisplay.WithAnyPotion(new SlotDisplay.ItemStack(result.toDisplayStack())), CRAFTING_TABLE));
    }
}
