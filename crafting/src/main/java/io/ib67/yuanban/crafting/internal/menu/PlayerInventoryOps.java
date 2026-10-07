package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.internal.item.ItemStacks;
import io.ib67.yuanban.crafting.internal.item.StackedItemContents;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

/**
 * 原版 {@code player.Inventory} 中合成用到的操作，作用在 Minestom 的 {@link PlayerInventory} 上。
 * 原版背包的 0–35 号格与 Minestom 的编号相同，原版副手 40 号格是 Minestom 的 45 号格。
 */
final class PlayerInventoryOps {
    /**
     * 原版 {@code Inventory.items} 的大小：快捷栏和背包。
     */
    static final int ITEMS_SIZE = 36;
    /**
     * 原版 {@code Container.DEFAULT_MAX_STACK_SIZE}。
     */
    static final int CONTAINER_MAX_STACK_SIZE = 99;

    private final Player player;
    private final PlayerInventory inventory;

    PlayerInventoryOps(Player player) {
        this.player = player;
        this.inventory = player.getInventory();
    }

    PlayerInventory inventory() {
        return inventory;
    }

    ItemStack get(int slot) {
        return inventory.getItemStack(slot);
    }

    void set(int slot, ItemStack stack) {
        inventory.setItemStack(slot, stack);
    }

    /**
     * 原版 {@code Player.hasInfiniteMaterials}。
     */
    boolean hasInfiniteMaterials() {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    /**
     * 原版 {@code Player.drop}。Minestom 不生成掉落物实体，{@link Player#dropItem} 发出 {@code ItemDropEvent}，由宿主处理；
     * 事件被取消时物品没有去处，就此消失。
     */
    void drop(ItemStack stack) {
        player.dropItem(stack);
    }

    private static int maxStackSize(ItemStack stack) {
        return Math.min(CONTAINER_MAX_STACK_SIZE, stack.maxStackSize());
    }

    private static boolean hasRemainingSpaceForItem(ItemStack slotStack, ItemStack newStack) {
        return !slotStack.isAir() && slotStack.isSimilar(newStack) && ItemStacks.isStackable(slotStack)
                && slotStack.amount() < maxStackSize(slotStack);
    }

    /**
     * 原版 {@code Inventory.getFreeSlot}：快捷栏和背包中第一个空格。
     */
    int getFreeSlot() {
        for (int i = 0; i < ITEMS_SIZE; i++) {
            if (get(i).isAir()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 原版 {@code Inventory.getSlotWithRemainingSpace}：依次检查手持格、副手、快捷栏和背包。
     */
    int getSlotWithRemainingSpace(ItemStack stack) {
        int selected = player.getHeldSlot();
        if (hasRemainingSpaceForItem(get(selected), stack)) {
            return selected;
        }
        if (hasRemainingSpaceForItem(get(PlayerInventoryUtils.OFFHAND_SLOT), stack)) {
            return PlayerInventoryUtils.OFFHAND_SLOT;
        }
        for (int i = 0; i < ITEMS_SIZE; i++) {
            if (hasRemainingSpaceForItem(get(i), stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 原版 {@code Inventory.add(int, ItemStack)} 的结果：{@code added} 是原版的返回值，{@code remaining} 是参数物品剩下的部分。
     */
    record AddResult(boolean added, ItemStack remaining) {
    }

    AddResult add(ItemStack stack) {
        return add(-1, stack);
    }

    /**
     * 原版 {@code Inventory.add(int, ItemStack)}。只在最后一轮仍有物品放入时返回成功，
     * 所以只放入一部分的时候返回失败，剩下的部分由调用者处理。
     */
    AddResult add(int slot, ItemStack stack) {
        if (stack.isAir()) {
            return new AddResult(false, stack);
        }
        if (ItemStacks.isDamaged(stack)) {
            if (slot == -1) {
                slot = getFreeSlot();
            }
            if (slot >= 0) {
                set(slot, stack);
                return new AddResult(true, ItemStack.AIR);
            }
            return hasInfiniteMaterials() ? new AddResult(true, ItemStack.AIR) : new AddResult(false, stack);
        }
        int count = stack.amount();
        int last;
        do {
            last = count;
            count = slot == -1 ? addResource(stack.withAmount(count)) : addResource(slot, stack.withAmount(count));
        } while (count > 0 && count < last);
        if (count == last && hasInfiniteMaterials()) {
            return new AddResult(true, ItemStack.AIR);
        }
        return new AddResult(count < last, stack.withAmount(count));
    }

    private int addResource(ItemStack stack) {
        int slot = getSlotWithRemainingSpace(stack);
        if (slot == -1) {
            slot = getFreeSlot();
        }
        return slot == -1 ? stack.amount() : addResource(slot, stack);
    }

    /**
     * 原版 {@code Inventory.addResource(int, ItemStack)}：返回没有放入的数量。
     */
    private int addResource(int slot, ItemStack stack) {
        int count = stack.amount();
        var inSlot = get(slot);
        int current = inSlot.isAir() ? 0 : inSlot.amount();
        int maxToAdd = maxStackSize(inSlot.isAir() ? stack : inSlot) - current;
        int toAdd = Math.min(count, maxToAdd);
        if (toAdd <= 0) {
            return count;
        }
        set(slot, inSlot.isAir() ? stack.withAmount(toAdd) : inSlot.withAmount(current + toAdd));
        return count - toAdd;
    }

    /**
     * 原版 {@code Inventory.placeItemBackInInventory}：逐次放入有剩余空间的格子或空格，放不下的部分丢出。
     */
    void placeItemBackInInventory(ItemStack stack) {
        var remaining = stack;
        while (!remaining.isAir()) {
            int slot = getSlotWithRemainingSpace(remaining);
            if (slot == -1) {
                slot = getFreeSlot();
            }
            if (slot == -1) {
                drop(remaining);
                break;
            }
            int space = remaining.maxStackSize() - get(slot).amount();
            int piece = Math.min(space, remaining.amount());
            add(slot, remaining.withAmount(piece));
            remaining = remaining.withAmount(remaining.amount() - piece);
        }
    }

    /**
     * 原版 {@code Inventory.findSlotMatchingCraftingIngredient}。
     */
    int findSlotMatchingCraftingIngredient(Material material, ItemStack existing) {
        for (int i = 0; i < ITEMS_SIZE; i++) {
            var stack = get(i);
            if (!stack.isAir() && stack.material() == material && ItemStacks.isUsableForCrafting(stack)
                    && (existing.isAir() || existing.isSimilar(stack))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 原版 {@code Inventory.fillStackedContents}：只统计快捷栏和背包。
     */
    void fillStackedContents(StackedItemContents contents) {
        for (int i = 0; i < ITEMS_SIZE; i++) {
            contents.accountSimpleStack(get(i));
        }
    }

    /**
     * 原版 {@code Inventory.removeItem}：从一格中取出最多 {@code count} 个。
     */
    ItemStack removeItem(int slot, int count) {
        var stack = get(slot);
        if (stack.isAir() || count <= 0) {
            return ItemStack.AIR;
        }
        int taken = Math.min(count, stack.amount());
        set(slot, stack.withAmount(stack.amount() - taken));
        return stack.withAmount(taken);
    }

    /**
     * 原版 {@code Inventory.removeItemNoUpdate}：取出一整格。
     */
    ItemStack removeItemNoUpdate(int slot) {
        var stack = get(slot);
        set(slot, ItemStack.AIR);
        return stack;
    }
}
