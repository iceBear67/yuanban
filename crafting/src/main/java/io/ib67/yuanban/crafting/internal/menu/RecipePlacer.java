package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.internal.item.StackedItemContents;
import io.ib67.yuanban.crafting.recipe.CraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.PlacementInfo;
import io.ib67.yuanban.crafting.recipe.RecipeHolder;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapedRecipe;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 {@code ServerPlaceRecipe} 与 {@code PlaceRecipeHelper}：处理配方书的放置请求，从背包中取出材料摆进合成格。
 */
final class RecipePlacer {
    enum PostPlaceAction {
        NOTHING,
        PLACE_GHOST_RECIPE
    }

    private final CraftingMenu menu;
    private final PlayerInventoryOps inventory;
    private final boolean useMaxItems;
    private final List<MenuSlot> inputGridSlots;

    private RecipePlacer(CraftingMenu menu, boolean useMaxItems) {
        this.menu = menu;
        this.inventory = menu.inventory;
        this.useMaxItems = useMaxItems;
        this.inputGridSlots = menu.inputGridSlots();
    }

    /**
     * 原版 {@code AbstractCraftingMenu.handlePlacement}。{@code allowDroppingItemsToClear} 为创造模式：清空合成格放不下的物品可以丢出。
     */
    static PostPlaceAction placeRecipe(CraftingMenu menu, RecipeHolder<CraftingRecipe> recipe, boolean useMaxItems, boolean allowDroppingItemsToClear) {
        menu.beginPlacingRecipe();
        try {
            var placer = new RecipePlacer(menu, useMaxItems);
            if (!allowDroppingItemsToClear && !placer.testClearGrid()) {
                return PostPlaceAction.NOTHING;
            }
            var available = new StackedItemContents();
            menu.inventory.fillStackedContents(available);
            menu.fillCraftSlotsStackedContents(available);
            return placer.tryPlaceRecipe(recipe, available);
        } finally {
            menu.finishPlacingRecipe(recipe.id());
        }
    }

    private PostPlaceAction tryPlaceRecipe(RecipeHolder<CraftingRecipe> recipe, StackedItemContents available) {
        if (available.canCraft(recipe.value().placementInfo(), null)) {
            placeRecipe(recipe.value(), available);
            return PostPlaceAction.NOTHING;
        }
        clearGrid();
        return PostPlaceAction.PLACE_GHOST_RECIPE;
    }

    /**
     * 合成格的物品放回背包，然后清空合成格和结果格。
     */
    private void clearGrid() {
        for (var slot : inputGridSlots) {
            var item = slot.getItem();
            inventory.placeItemBackInInventory(item);
            slot.set(ItemStack.AIR);
        }
        menu.clearCraftingContent();
    }

    private void placeRecipe(CraftingRecipe recipe, StackedItemContents available) {
        var placement = recipe.placementInfo();
        boolean recipeMatchesPlaced = menu.recipeMatches(recipe);
        int biggestCraftableStack = available.getBiggestCraftableStack(placement, null);
        if (recipeMatchesPlaced) {
            // 已经摆好这个配方，并且某一格无法再多放一份时不处理
            for (var slot : inputGridSlots) {
                var stack = slot.getItem();
                if (!stack.isAir() && Math.min(biggestCraftableStack, stack.maxStackSize()) < stack.amount() + 1) {
                    return;
                }
            }
        }
        int amountToCraft = calculateAmountToCraft(biggestCraftableStack, recipeMatchesPlaced);
        var itemsUsedPerIngredient = new ArrayList<Material>();
        if (!available.canCraft(placement, amountToCraft, itemsUsedPerIngredient::add)) {
            return;
        }
        int adjustedAmountToCraft = clampToMaxStackSize(amountToCraft, itemsUsedPerIngredient);
        if (adjustedAmountToCraft != amountToCraft) {
            itemsUsedPerIngredient.clear();
            if (!available.canCraft(placement, adjustedAmountToCraft, itemsUsedPerIngredient::add)) {
                return;
            }
        }
        clearGrid();
        int recipeWidth = recipe instanceof ShapedRecipe shaped ? shaped.width() : menu.width;
        int recipeHeight = recipe instanceof ShapedRecipe shaped ? shaped.height() : menu.height;
        placeRecipe(menu.width, menu.height, recipeWidth, recipeHeight, placement.slotsToIngredientIndex(), (ingredientIndex, gridIndex) -> {
            if (ingredientIndex == PlacementInfo.EMPTY_SLOT) {
                return;
            }
            var targetSlot = inputGridSlots.get(gridIndex);
            var itemUsed = itemsUsedPerIngredient.get(ingredientIndex);
            int remainingCount = adjustedAmountToCraft;
            while (remainingCount > 0) {
                remainingCount = moveItemToGrid(targetSlot, itemUsed, remainingCount);
                if (remainingCount == -1) {
                    return;
                }
            }
        });
    }

    /**
     * 原版用物品原型的最大堆叠数（缺省为 1）限制份数。
     */
    private static int clampToMaxStackSize(int value, List<Material> items) {
        for (var item : items) {
            value = Math.min(value, item.prototype().get(DataComponents.MAX_STACK_SIZE, 1));
        }
        return value;
    }

    /**
     * 全部放置时放最多的份数；已经摆好这个配方时在最少的一格基础上多放一份；否则放一份。
     */
    private int calculateAmountToCraft(int biggestCraftableStack, boolean recipeMatchesPlaced) {
        if (useMaxItems) {
            return biggestCraftableStack;
        }
        if (recipeMatchesPlaced) {
            int smallestStackSize = Integer.MAX_VALUE;
            for (var slot : inputGridSlots) {
                var stack = slot.getItem();
                if (!stack.isAir() && smallestStackSize > stack.amount()) {
                    smallestStackSize = stack.amount();
                }
            }
            if (smallestStackSize != Integer.MAX_VALUE) {
                smallestStackSize++;
            }
            return smallestStackSize;
        }
        return 1;
    }

    /**
     * 从背包中取出一组可以用于合成的物品放进合成格，返回还需要的数量；背包中没有这种物品时返回 -1。
     */
    private int moveItemToGrid(MenuSlot targetSlot, Material item, int count) {
        var itemInTargetSlot = targetSlot.getItem();
        int inventorySlot = inventory.findSlotMatchingCraftingIngredient(item, itemInTargetSlot);
        if (inventorySlot == -1) {
            return -1;
        }
        var inventoryItem = inventory.get(inventorySlot);
        var taken = count < inventoryItem.amount() ? inventory.removeItem(inventorySlot, count) : inventory.removeItemNoUpdate(inventorySlot);
        int takenCount = taken.amount();
        if (itemInTargetSlot.isAir()) {
            targetSlot.set(taken);
        } else {
            targetSlot.set(itemInTargetSlot.withAmount(itemInTargetSlot.amount() + takenCount));
        }
        return count - takenCount;
    }

    /**
     * 原版 {@code ServerPlaceRecipe.testClearGrid}：估计合成格的物品能否全部放回背包。
     * 有某一格还有剩余空间的物品直接视为可以放回。
     */
    private boolean testClearGrid() {
        var freeSlots = new ArrayList<ItemStack>();
        int freeSlotsInInventory = 0;
        for (int i = 0; i < PlayerInventoryOps.ITEMS_SIZE; i++) {
            if (inventory.get(i).isAir()) {
                freeSlotsInInventory++;
            }
        }
        for (var inputSlot : inputGridSlots) {
            var stack = inputSlot.getItem();
            if (stack.isAir()) {
                continue;
            }
            int slotId = inventory.getSlotWithRemainingSpace(stack);
            if (slotId == -1 && freeSlots.size() <= freeSlotsInInventory) {
                for (int i = 0; i < freeSlots.size(); i++) {
                    var inList = freeSlots.get(i);
                    if (inList.material() == stack.material() && inList.amount() != inList.maxStackSize()
                            && inList.amount() + stack.amount() <= inList.maxStackSize()) {
                        freeSlots.set(i, inList.withAmount(inList.amount() + stack.amount()));
                        stack = ItemStack.AIR;
                        break;
                    }
                }
                if (!stack.isAir()) {
                    if (freeSlots.size() >= freeSlotsInInventory) {
                        return false;
                    }
                    freeSlots.add(stack);
                }
            } else if (slotId == -1) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    interface Output {
        void addItemToSlot(int ingredientIndex, int gridIndex);
    }

    /**
     * 原版 {@code PlaceRecipeHelper.placeRecipe}：比合成格小一半以上的配方居中放置。
     */
    static void placeRecipe(int gridWidth, int gridHeight, int recipeWidth, int recipeHeight, List<Integer> entries, Output output) {
        var iterator = entries.iterator();
        int gridIndex = 0;
        for (int gridYPos = 0; gridYPos < gridHeight; gridYPos++) {
            boolean shouldCenterRecipe = recipeHeight < gridHeight / 2.0F;
            int startPosCenterRecipe = (int) Math.floor(gridHeight / 2.0F - recipeHeight / 2.0F);
            if (shouldCenterRecipe && startPosCenterRecipe > gridYPos) {
                gridIndex += gridWidth;
                gridYPos++;
            }
            for (int gridXPos = 0; gridXPos < gridWidth; gridXPos++) {
                if (!iterator.hasNext()) {
                    return;
                }
                shouldCenterRecipe = recipeWidth < gridWidth / 2.0F;
                startPosCenterRecipe = (int) Math.floor(gridWidth / 2.0F - recipeWidth / 2.0F);
                int totalRecipeWidthInGrid = recipeWidth;
                boolean addIngredientToSlot = gridXPos < recipeWidth;
                if (shouldCenterRecipe) {
                    totalRecipeWidthInGrid = startPosCenterRecipe + recipeWidth;
                    addIngredientToSlot = startPosCenterRecipe <= gridXPos && gridXPos < startPosCenterRecipe + recipeWidth;
                }
                if (addIngredientToSlot) {
                    output.addItemToSlot(iterator.next(), gridIndex);
                } else if (totalRecipeWidthInGrid == gridXPos) {
                    gridIndex += gridWidth - gridXPos;
                    break;
                }
                gridIndex++;
            }
        }
    }
}
