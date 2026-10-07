package io.ib67.yuanban.crafting.internal.item;

import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.PlacementInfo;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 原版 {@code StackedItemContents}：以物品种类为单位统计 {@link StackedContents}。
 */
@ApiStatus.Internal
public final class StackedItemContents {
    private final StackedContents<Material> raw = new StackedContents<>();

    /**
     * 只统计配方书可以使用的物品，见 {@link ItemStacks#isUsableForCrafting}。
     */
    public void accountSimpleStack(ItemStack stack) {
        if (ItemStacks.isUsableForCrafting(stack)) {
            accountStack(stack);
        }
    }

    public void accountStack(ItemStack stack) {
        accountStack(stack, stack.maxStackSize());
    }

    public void accountStack(ItemStack stack, int maxCount) {
        if (!stack.isAir()) {
            raw.account(stack.material(), Math.min(maxCount, stack.amount()));
        }
    }

    public boolean canCraft(PlacementInfo placement, StackedContents.@Nullable Output<Material> output) {
        return canCraft(placement, 1, output);
    }

    public boolean canCraft(PlacementInfo placement, int amount, StackedContents.@Nullable Output<Material> output) {
        return !placement.isImpossibleToPlace() && raw.tryPick(infos(placement.ingredients()), amount, output);
    }

    public int getBiggestCraftableStack(PlacementInfo placement, StackedContents.@Nullable Output<Material> output) {
        return getBiggestCraftableStack(placement, Integer.MAX_VALUE, output);
    }

    public int getBiggestCraftableStack(PlacementInfo placement, int maxSize, StackedContents.@Nullable Output<Material> output) {
        return raw.tryPickAll(infos(placement.ingredients()), maxSize, output);
    }

    private static List<StackedContents.IngredientInfo<Material>> infos(List<ItemIngredient> ingredients) {
        return ingredients.stream().<StackedContents.IngredientInfo<Material>>map(ingredient -> ingredient::acceptsItem).toList();
    }
}
