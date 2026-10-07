package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.recipe.RecipeManager;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

/**
 * 原版 {@code CraftingMenu}：工作台的菜单。菜单序号 0 是结果格，1–9 是 3×3 合成格，10–36 是背包，37–45 是快捷栏。
 */
final class TableMenu extends CraftingMenu {
    static final int GRID_END = 10;
    static final int INVENTORY_END = 37;
    static final int SLOT_COUNT = 46;

    TableMenu(Player player, RecipeManager recipes, CraftingTableInventory table) {
        super(player, recipes, table, 0, 1, 3, 3);
        addStandardInventorySlots();
    }

    /**
     * 原版 {@code CraftingMenu.beginPlacingRecipe}：放置配方的过程中不重新计算结果。
     */
    @Override
    void beginPlacingRecipe() {
        setSuppressGridUpdates(true);
    }

    /**
     * 原版 {@code CraftingMenu.finishPlacingRecipe}：放置结束后带上配方提示计算一次结果。
     */
    @Override
    void finishPlacingRecipe(Key recipe) {
        setSuppressGridUpdates(false);
        slotChangedCraftingGrid(recipe);
    }

    /**
     * 原版 {@code CraftingMenu.quickMoveStack}：结果格移入背包（从快捷栏末尾向前）；背包和快捷栏先移入合成格，
     * 再在两者之间移动；合成格移入背包。
     */
    @Override
    ItemStack quickMoveStack(int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.AIR;
        }
        var stack = slot.getItem();
        var clicked = stack;
        if (index == 0) {
            stack = CraftingStats.postProcess(stack, recipes.context().maps());
            var moved = moveItemStackTo(stack, GRID_END, SLOT_COUNT, true);
            if (!moved.changed()) {
                return ItemStack.AIR;
            }
            stack = moved.remaining();
            slot.onQuickCraft(stack, clicked);
        } else if (index >= GRID_END) {
            var moved = moveItemStackTo(stack, 1, GRID_END, false);
            if (!moved.changed()) {
                moved = index < INVENTORY_END
                        ? moveItemStackTo(stack, INVENTORY_END, SLOT_COUNT, false)
                        : moveItemStackTo(stack, GRID_END, INVENTORY_END, false);
                if (!moved.changed()) {
                    return ItemStack.AIR;
                }
            }
            stack = moved.remaining();
        } else {
            var moved = moveItemStackTo(stack, GRID_END, SLOT_COUNT, false);
            if (!moved.changed()) {
                return ItemStack.AIR;
            }
            stack = moved.remaining();
        }
        return finishQuickMove(index, stack, clicked);
    }
}
