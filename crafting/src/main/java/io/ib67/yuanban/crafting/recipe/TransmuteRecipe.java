package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_transmute}，原版 {@code TransmuteRecipe}：一个输入物品加若干材料，结果保留输入物品的组件。
 */
public record TransmuteRecipe(CraftingBookInfo book, ItemIngredient input, ItemIngredient material, IntBounds materialCount,
                              ItemStackTemplate result, boolean addMaterialCountToResult) implements CraftingRecipe {
    private static final int MIN_MATERIAL_COUNT = 1;
    private static final int MAX_MATERIAL_COUNT = 8;

    public TransmuteRecipe {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(materialCount, "materialCount");
        Objects.requireNonNull(result, "result");
    }

    private int minMaterialCount() {
        return materialCount.min().orElse(MIN_MATERIAL_COUNT);
    }

    private int maxMaterialCount() {
        return materialCount.max().orElse(MAX_MATERIAL_COUNT);
    }

    private int computeResultSize(int materialCount) {
        return addMaterialCountToResult ? materialCount + result.count() : result.count();
    }

    private ItemStack computeResult(ItemStack inputIngredient, int materialCount) {
        return result.applyOriginalComponents(inputIngredient, materialCount);
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        int min = minMaterialCount();
        int max = maxMaterialCount();
        if (input.ingredientCount() < min + 1 || input.ingredientCount() > max + 1) {
            return false;
        }
        ItemStack foundInput = null;
        int materials = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (this.input.test(stack)) {
                if (foundInput != null) {
                    return false;
                }
                foundInput = stack;
            } else {
                if (!material.test(stack)) {
                    return false;
                }
                if (++materials > max) {
                    return false;
                }
            }
        }
        if (foundInput == null || !materialCount.matches(materials)) {
            return false;
        }
        if (computeResultSize(materials) != 1) {
            return true;
        }
        // 结果与输入物品完全相同的时候不匹配，例如把物品转换成它自己
        var produced = computeResult(foundInput, 0);
        return !produced.isAir() && !foundInput.isSimilar(produced);
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        if (addMaterialCountToResult) {
            int materials = 0;
            var inputIngredient = ItemStack.AIR;
            for (int slot = 0; slot < input.size(); slot++) {
                var stack = input.getItem(slot);
                if (stack.isAir()) {
                    continue;
                }
                if (this.input.test(stack)) {
                    inputIngredient = stack;
                } else if (material.test(stack)) {
                    materials++;
                }
            }
            return computeResult(inputIngredient, materials);
        }
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (!stack.isAir() && this.input.test(stack)) {
                return computeResult(stack, 0);
            }
        }
        return ItemStack.AIR;
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
        var ingredients = new ArrayList<ItemIngredient>(1 + maxMaterialCount());
        ingredients.add(input);
        ingredients.addAll(Collections.nCopies(maxMaterialCount(), material));
        return PlacementInfo.create(ingredients);
    }

    /**
     * 材料数量从最小值到最大值各一个显示。
     */
    @Override
    public List<RecipeDisplay> display() {
        var displays = new ArrayList<RecipeDisplay>();
        var slots = new ArrayList<SlotDisplay>();
        slots.add(input.display());
        var materialDisplay = material.display();
        for (int count = minMaterialCount(); count <= maxMaterialCount(); count++) {
            slots.add(materialDisplay);
            displays.add(new RecipeDisplay.CraftingShapeless(List.copyOf(slots),
                    new SlotDisplay.ItemStack(result.withCount(computeResultSize(count)).toDisplayStack()), CRAFTING_TABLE));
        }
        return displays;
    }
}
