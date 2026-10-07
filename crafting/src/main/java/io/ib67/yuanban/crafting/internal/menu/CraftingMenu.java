package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.internal.item.ItemStacks;
import io.ib67.yuanban.crafting.internal.item.StackedItemContents;
import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.CraftingRecipe;
import io.ib67.yuanban.crafting.recipe.RecipeManager;
import io.ib67.yuanban.crafting.recipe.RecipeType;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 有合成格的菜单，移植原版 {@code AbstractContainerMenu.doClick} 的各种点击、{@code AbstractCraftingMenu}、
 * {@code ResultSlot} 和 {@code CraftingMenu.slotChangedCraftingGrid}。菜单格子按原版菜单序号排列，0 号是结果格，之后是合成格。
 * 光标物品是玩家 {@link net.minestom.server.inventory.PlayerInventory} 的光标。
 */
abstract class CraftingMenu {
    final Player player;
    final PlayerInventoryOps inventory;
    final RecipeManager recipes;
    final AbstractInventory gridInventory;
    final int resultIndex;
    final int gridStart;
    final int width;
    final int height;
    final List<MenuSlot> slots = new ArrayList<>();
    final ResultSlot resultSlot;
    /**
     * 原版不触发 {@code slotsChanged} 的修改（{@code removeItemNoUpdate}、{@code clearContent}、工作台放置配方的过程中）期间为真。
     */
    private boolean suppressGridUpdates;

    CraftingMenu(Player player, RecipeManager recipes, AbstractInventory gridInventory, int resultIndex, int gridStart, int width, int height) {
        this.player = player;
        this.inventory = new PlayerInventoryOps(player);
        this.recipes = recipes;
        this.gridInventory = gridInventory;
        this.resultIndex = resultIndex;
        this.gridStart = gridStart;
        this.width = width;
        this.height = height;
        this.resultSlot = new ResultSlot();
        slots.add(resultSlot);
        for (int i = 0; i < width * height; i++) {
            slots.add(new MenuSlot(gridInventory, gridStart + i));
        }
    }

    /**
     * 原版 {@code AbstractContainerMenu.addStandardInventorySlots}：背包三行（玩家 9–35 号格）再加快捷栏（玩家 0–8 号格）。
     */
    void addStandardInventorySlots() {
        var playerInventory = inventory.inventory();
        for (int i = 9; i < PlayerInventoryOps.ITEMS_SIZE; i++) {
            slots.add(new MenuSlot(playerInventory, i));
        }
        for (int i = 0; i < 9; i++) {
            slots.add(new MenuSlot(playerInventory, i));
        }
    }

    /**
     * 原版各菜单的 {@code quickMoveStack}：按住 Shift 点击一格时移动一次，没有移动时返回空物品。
     */
    abstract ItemStack quickMoveStack(int index);

    /**
     * 原版 {@code CraftingMenu.canTakeItemForPickAll} 与 {@code InventoryMenu.canTakeItemForPickAll}：双击不从结果格取物。
     */
    boolean canTakeItemForPickAll(MenuSlot target) {
        return target != resultSlot;
    }

    /**
     * 原版各菜单 {@code quickMoveStack} 的末尾：把没有移走的部分写回格子；有物品移走时调用 {@link MenuSlot#onTake}，
     * 结果格剩下的部分丢出。
     *
     * @param remaining 移动之后剩下的物品
     * @param clicked   移动之前格子中的物品
     */
    ItemStack finishQuickMove(int index, ItemStack remaining, ItemStack clicked) {
        var slot = slots.get(index);
        if (remaining.isAir()) {
            slot.setByPlayer(ItemStack.AIR);
        } else {
            slot.set(remaining);
        }
        if (remaining.amount() == clicked.amount()) {
            return ItemStack.AIR;
        }
        var taken = slot.onTake(player, remaining);
        if (index == 0) {
            inventory.drop(taken);
        }
        return clicked;
    }

    /**
     * 原版 {@code AbstractCraftingMenu.beginPlacingRecipe}。
     */
    void beginPlacingRecipe() {
    }

    /**
     * 原版 {@code AbstractCraftingMenu.finishPlacingRecipe}。
     */
    void finishPlacingRecipe(Key recipe) {
    }

    // 光标

    ItemStack getCarried() {
        return inventory.inventory().getCursorItem();
    }

    void setCarried(ItemStack stack) {
        inventory.inventory().setCursorItem(stack);
    }

    // 合成格

    ItemStack gridItem(int index) {
        return gridInventory.getItemStack(gridStart + index);
    }

    void setGridItem(int index, ItemStack stack) {
        gridInventory.setItemStack(gridStart + index, stack);
    }

    List<ItemStack> gridItems() {
        var items = new ArrayList<ItemStack>(width * height);
        for (int i = 0; i < width * height; i++) {
            items.add(gridItem(i));
        }
        return items;
    }

    List<MenuSlot> inputGridSlots() {
        return slots.subList(1, 1 + width * height);
    }

    CraftingInput.Positioned positionedInput() {
        return CraftingInput.ofPositioned(width, height, gridItems());
    }

    /**
     * 原版 {@code TransientCraftingContainer.fillStackedContents}。
     */
    void fillCraftSlotsStackedContents(StackedItemContents contents) {
        for (var item : gridItems()) {
            contents.accountSimpleStack(item);
        }
    }

    boolean recipeMatches(CraftingRecipe recipe) {
        return recipe.matches(positionedInput().input(), recipes.context());
    }

    /**
     * 原版 {@code AbstractCraftingMenu} 中的 {@code clearCraftingContent}：清空结果格和合成格，不重新计算结果。
     */
    void clearCraftingContent() {
        withoutGridUpdates(() -> {
            gridInventory.setItemStack(resultIndex, ItemStack.AIR);
            for (int i = 0; i < width * height; i++) {
                setGridItem(i, ItemStack.AIR);
            }
        });
    }

    void withoutGridUpdates(Runnable action) {
        boolean previous = suppressGridUpdates;
        suppressGridUpdates = true;
        try {
            action.run();
        } finally {
            suppressGridUpdates = previous;
        }
    }

    void setSuppressGridUpdates(boolean suppress) {
        suppressGridUpdates = suppress;
    }

    /**
     * 合成格的一格变化之后调用，对应原版 {@code slotsChanged}。
     */
    void gridChanged() {
        if (!suppressGridUpdates) {
            slotChangedCraftingGrid(null);
        }
    }

    /**
     * 原版 {@code CraftingMenu.slotChangedCraftingGrid}：按合成格的内容查找配方，把产出写入结果格。
     * 全部配方对全部玩家已知，原版 {@code setRecipeUsed} 的检查总是通过。
     */
    void slotChangedCraftingGrid(@Nullable Key hint) {
        var input = positionedInput().input();
        var result = recipes.getRecipeFor(RecipeType.CRAFTING, input, hint)
                .map(holder -> holder.value().assemble(input, recipes.context()))
                .orElse(ItemStack.AIR);
        gridInventory.setItemStack(resultIndex, result);
    }

    // 关闭

    /**
     * 原版 {@code AbstractContainerMenu.removed}：光标物品放回背包，玩家离线时丢出。
     */
    void removeCarried(boolean drop) {
        var carried = getCarried();
        if (!carried.isAir()) {
            setCarried(ItemStack.AIR);
            dropOrPlaceInInventory(carried, drop);
        }
    }

    /**
     * 原版 {@code AbstractContainerMenu.clearContainer}：合成格的物品逐个放回背包，玩家离线时丢出。不重新计算结果。
     */
    void clearGrid(boolean drop) {
        for (int i = 0; i < width * height; i++) {
            int index = i;
            var item = gridItem(index);
            withoutGridUpdates(() -> setGridItem(index, ItemStack.AIR));
            dropOrPlaceInInventory(item, drop);
        }
    }

    private void dropOrPlaceInInventory(ItemStack stack, boolean drop) {
        if (drop) {
            inventory.drop(stack);
        } else {
            inventory.placeItemBackInInventory(stack);
        }
    }

    // 点击

    /**
     * 原版 {@code doClick} 中的 {@code PICKUP}：左键（{@code primary}）或右键单击一格。
     * 原版在这之前调用的 {@code tryItemClickBehaviourOverride}（收纳袋的点击行为）没有移植，与 Minestom 处理其他格子的方式一致。
     */
    void pickup(int index, boolean primary) {
        var slot = slots.get(index);
        var clicked = slot.getItem();
        var carried = getCarried();
        if (clicked.isAir()) {
            if (!carried.isAir()) {
                setCarried(slot.safeInsert(carried, primary ? carried.amount() : 1));
            }
        } else if (slot.mayPickup(player)) {
            if (carried.isAir()) {
                int amount = primary ? clicked.amount() : (clicked.amount() + 1) / 2;
                slot.tryRemove(amount, Integer.MAX_VALUE, player).ifPresent(taken -> {
                    setCarried(taken);
                    setCarried(slot.onTake(player, taken));
                });
            } else if (slot.mayPlace(carried)) {
                if (clicked.isSimilar(carried)) {
                    setCarried(slot.safeInsert(carried, primary ? carried.amount() : 1));
                } else if (carried.amount() <= slot.getMaxStackSize(carried)) {
                    setCarried(clicked);
                    slot.setByPlayer(carried);
                }
            } else if (clicked.isSimilar(carried)) {
                slot.tryRemove(clicked.amount(), carried.maxStackSize() - carried.amount(), player).ifPresent(taken -> {
                    setCarried(carried.withAmount(carried.amount() + taken.amount()));
                    slot.onTake(player, taken);
                });
            }
        }
    }

    /**
     * 原版 {@code doClick} 中的 {@code QUICK_MOVE}：按住 Shift 点击，重复移动直到格子里的物品种类改变。
     */
    void quickMove(int index) {
        var slot = slots.get(index);
        if (!slot.mayPickup(player)) {
            return;
        }
        var clicked = quickMoveStack(index);
        while (!clicked.isAir() && slot.getItem().material() == clicked.material()) {
            clicked = quickMoveStack(index);
        }
    }

    /**
     * 原版 {@code doClick} 中的 {@code SWAP}：与快捷栏或副手交换。{@code inventorySlot} 是 Minestom 玩家背包的格子。
     */
    void swap(int index, int inventorySlot) {
        var source = inventory.get(inventorySlot);
        var target = slots.get(index);
        var targetStack = target.getItem();
        if (source.isAir() && targetStack.isAir()) {
            return;
        }
        if (source.isAir()) {
            if (target.mayPickup(player)) {
                inventory.set(inventorySlot, targetStack);
                target.onSwapCraft(targetStack.amount());
                target.setByPlayer(ItemStack.AIR);
                inventory.set(inventorySlot, target.onTake(player, targetStack));
            }
        } else if (targetStack.isAir()) {
            if (target.mayPlace(source)) {
                int max = target.getMaxStackSize(source);
                if (source.amount() > max) {
                    target.setByPlayer(source.withAmount(max));
                    inventory.set(inventorySlot, source.withAmount(source.amount() - max));
                } else {
                    inventory.set(inventorySlot, ItemStack.AIR);
                    target.setByPlayer(source);
                }
            }
        } else if (target.mayPickup(player) && target.mayPlace(source)) {
            int max = target.getMaxStackSize(source);
            if (source.amount() > max) {
                target.setByPlayer(source.withAmount(max));
                inventory.set(inventorySlot, source.withAmount(source.amount() - max));
                var taken = target.onTake(player, targetStack);
                var added = inventory.add(taken);
                if (!added.added()) {
                    inventory.drop(added.remaining());
                }
            } else {
                inventory.set(inventorySlot, targetStack);
                target.setByPlayer(source);
                inventory.set(inventorySlot, target.onTake(player, targetStack));
            }
        }
    }

    /**
     * 原版 {@code doClick} 中的 {@code CLONE}：创造模式下光标为空时复制一整组。
     */
    void cloneSlot(int index) {
        if (inventory.hasInfiniteMaterials() && getCarried().isAir()) {
            var slot = slots.get(index);
            if (slot.hasItem()) {
                setCarried(slot.safeClone(player));
            }
        }
    }

    /**
     * 原版 {@code doClick} 中的 {@code THROW}：光标为空时丢出一个，{@code all} 为真时按整组反复丢出。
     */
    void throwSlot(int index, boolean all) {
        if (!getCarried().isAir()) {
            return;
        }
        var slot = slots.get(index);
        int amount = all ? slot.getItem().amount() : 1;
        var stack = slot.safeTake(amount, Integer.MAX_VALUE, player);
        inventory.drop(stack);
        if (all) {
            while (!stack.isAir() && slot.getItem().material() == stack.material()) {
                stack = slot.safeTake(amount, Integer.MAX_VALUE, player);
                inventory.drop(stack);
            }
        }
    }

    /**
     * 原版 {@code doClick} 中的 {@code PICKUP_ALL}：双击收集与光标物品相同的物品，第一轮跳过满的格子。
     */
    void pickupAll(int index, boolean forward) {
        var slot = slots.get(index);
        var carried = getCarried();
        if (carried.isAir() || (slot.hasItem() && slot.mayPickup(player))) {
            return;
        }
        int start = forward ? 0 : slots.size() - 1;
        int step = forward ? 1 : -1;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = start; i >= 0 && i < slots.size() && carried.amount() < carried.maxStackSize(); i += step) {
                var target = slots.get(i);
                if (target.hasItem() && canItemQuickReplace(target, carried, true) && target.mayPickup(player)
                        && canTakeItemForPickAll(target)) {
                    var stack = target.getItem();
                    if (pass != 0 || stack.amount() != stack.maxStackSize()) {
                        var removed = target.safeTake(stack.amount(), carried.maxStackSize() - carried.amount(), player);
                        carried = carried.withAmount(carried.amount() + removed.amount());
                        setCarried(carried);
                    }
                }
            }
        }
    }

    /**
     * 原版 {@code doClick} 中的 {@code QUICK_CRAFT}：拖动结束时分配光标物品。{@code type} 为 0（左键平分）、1（右键每格一个）、
     * 2（创造模式中键，每格一整组）。原版在拖动经过每一格时检查并加入集合，这里按 Minestom 记录的经过顺序重放同样的检查。
     */
    void quickcraft(List<Integer> indices, int type) {
        var carried = getCarried();
        if (carried.isAir() || (type == 2 && !inventory.hasInfiniteMaterials())) {
            return;
        }
        var chosen = new LinkedHashSet<MenuSlot>();
        for (int index : indices) {
            var slot = slots.get(index);
            if (canItemQuickReplace(slot, carried, true) && slot.mayPlace(carried) && (type == 2 || carried.amount() > chosen.size())) {
                chosen.add(slot);
            }
        }
        if (chosen.isEmpty()) {
            return;
        }
        if (chosen.size() == 1) {
            // 原版：只经过一格时按单击处理，拖动类型作为按键；中键拖动对应的按键 2 不触发任何操作
            if (type != 2) {
                pickup(slots.indexOf(chosen.getFirst()), type == 0);
            }
            return;
        }
        int remaining = carried.amount();
        for (var slot : chosen) {
            if (canItemQuickReplace(slot, carried, true) && slot.mayPlace(carried) && (type == 2 || carried.amount() >= chosen.size())) {
                int carry = slot.hasItem() ? slot.getItem().amount() : 0;
                int maxSize = Math.min(carried.maxStackSize(), slot.getMaxStackSize(carried));
                int newCount = Math.min(quickCraftPlaceCount(chosen.size(), type, carried) + carry, maxSize);
                remaining -= newCount - carry;
                slot.setByPlayer(carried.withAmount(newCount));
            }
        }
        setCarried(carried.withAmount(remaining));
    }

    private static int quickCraftPlaceCount(int slotCount, int type, ItemStack stack) {
        return switch (type) {
            case 0 -> (int) Math.floor((float) stack.amount() / slotCount);
            case 1 -> 1;
            case 2 -> stack.maxStackSize();
            default -> stack.amount();
        };
    }

    /**
     * 原版 {@code AbstractContainerMenu.canItemQuickReplace}。
     */
    static boolean canItemQuickReplace(MenuSlot slot, ItemStack stack, boolean ignoreSize) {
        boolean slotEmpty = !slot.hasItem();
        if (!slotEmpty && stack.isSimilar(slot.getItem())) {
            return slot.getItem().amount() + (ignoreSize ? 0 : stack.amount()) <= stack.maxStackSize();
        }
        return slotEmpty;
    }

    record MoveResult(boolean changed, ItemStack remaining) {
    }

    /**
     * 原版 {@code AbstractContainerMenu.moveItemStackTo}：先与 {@code [start, end)} 中相同的物品合并，再放入第一个空格。
     */
    MoveResult moveItemStackTo(ItemStack stack, int start, int end, boolean backwards) {
        boolean changed = false;
        int dest = backwards ? end - 1 : start;
        if (ItemStacks.isStackable(stack)) {
            while (!stack.isAir() && (backwards ? dest >= start : dest < end)) {
                var slot = slots.get(dest);
                var target = slot.getItem();
                if (!target.isAir() && stack.isSimilar(target)) {
                    int total = target.amount() + stack.amount();
                    int max = slot.getMaxStackSize(target);
                    if (total <= max) {
                        stack = ItemStack.AIR;
                        slot.set(target.withAmount(total));
                        changed = true;
                    } else if (target.amount() < max) {
                        stack = stack.withAmount(stack.amount() - (max - target.amount()));
                        slot.set(target.withAmount(max));
                        changed = true;
                    }
                }
                dest += backwards ? -1 : 1;
            }
        }
        if (!stack.isAir()) {
            dest = backwards ? end - 1 : start;
            while (backwards ? dest >= start : dest < end) {
                var slot = slots.get(dest);
                if (slot.getItem().isAir() && slot.mayPlace(stack)) {
                    int placed = Math.min(stack.amount(), slot.getMaxStackSize(stack));
                    slot.setByPlayer(stack.withAmount(placed));
                    stack = stack.withAmount(stack.amount() - placed);
                    changed = true;
                    break;
                }
                dest += backwards ? -1 : 1;
            }
        }
        return new MoveResult(changed, stack);
    }

    /**
     * 原版 {@code ResultSlot}：不能放入；取出时整组取走，并按配方消耗合成格中的原料。
     */
    final class ResultSlot extends MenuSlot {
        private int removeCount;

        ResultSlot() {
            super(gridInventory, resultIndex);
        }

        @Override
        boolean mayPlace(ItemStack stack) {
            return false;
        }

        /**
         * 原版 {@code ResultSlot.remove} 与 {@code ResultContainer.removeItem}：按请求的数量计入 {@code removeCount}，但取走整组。
         */
        @Override
        ItemStack remove(int amount) {
            var stack = getItem();
            if (!stack.isAir()) {
                removeCount += Math.min(amount, stack.amount());
            }
            set(ItemStack.AIR);
            return stack;
        }

        @Override
        void onQuickCraft(ItemStack picked, int count) {
            removeCount += count;
            checkTakeAchievements(picked);
        }

        @Override
        void onSwapCraft(int count) {
            removeCount += count;
        }

        /**
         * 原版 {@code ResultSlot.safeClone}：复制之后做物品的后处理。
         */
        @Override
        ItemStack safeClone(Player player) {
            return CraftingStats.postProcess(super.safeClone(player), recipes.context().maps());
        }

        /**
         * 原版 {@code ResultSlot.checkTakeAchievements}：按 {@code removeCount} 增加统计并做后处理。
         * 原版接着调用的 {@code awardUsedRecipes} 用于解锁配方，全部配方已知，不起作用。
         */
        private ItemStack checkTakeAchievements(ItemStack carried) {
            var result = carried;
            if (removeCount > 0) {
                result = CraftingStats.onCraftedBy(player, carried, removeCount, recipes.context().maps());
            }
            removeCount = 0;
            return result;
        }

        @Override
        ItemStack onTake(Player player, ItemStack carried) {
            var result = checkTakeAchievements(carried);
            var positioned = positionedInput();
            var input = positioned.input();
            var remaining = recipes.getRecipeFor(RecipeType.CRAFTING, input, null)
                    .map(holder -> holder.value().remainingItems(input))
                    .orElse(null);
            for (int y = 0; y < input.height(); y++) {
                for (int x = 0; x < input.width(); x++) {
                    int index = x + positioned.left() + (y + positioned.top()) * width;
                    var stack = gridItem(index);
                    if (!stack.isAir()) {
                        setGridItem(index, stack.withAmount(stack.amount() - 1));
                        stack = gridItem(index);
                    }
                    // 原版没有匹配的配方时，剩余物就是格子中的物品对象本身，移除一个之后两者相同
                    var replacement = remaining != null ? remaining.get(x + y * input.width()) : stack;
                    if (replacement.isAir()) {
                        continue;
                    }
                    if (stack.isAir()) {
                        setGridItem(index, replacement);
                    } else if (stack.isSimilar(replacement)) {
                        setGridItem(index, replacement.withAmount(replacement.amount() + stack.amount()));
                    } else {
                        var added = CraftingMenu.this.inventory.add(replacement);
                        if (!added.added()) {
                            CraftingMenu.this.inventory.drop(added.remaining());
                        }
                    }
                }
            }
            return result;
        }
    }
}
