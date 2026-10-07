package io.ib67.yuanban.crafting.internal.menu;

import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.item.ItemStack;

import java.util.Optional;

/**
 * 原版 {@code Slot}：菜单中的一格，对应 Minestom 某个背包的一格。原版的物品对象可以修改，这里的物品不可变，
 * 原版修改物品对象的地方改为写回格子或返回新物品。
 */
class MenuSlot {
    final AbstractInventory inventory;
    final int index;

    MenuSlot(AbstractInventory inventory, int index) {
        this.inventory = inventory;
        this.index = index;
    }

    ItemStack getItem() {
        return inventory.getItemStack(index);
    }

    boolean hasItem() {
        return !getItem().isAir();
    }

    void set(ItemStack stack) {
        inventory.setItemStack(index, stack);
    }

    void setByPlayer(ItemStack stack) {
        set(stack);
    }

    boolean mayPlace(ItemStack stack) {
        return true;
    }

    boolean mayPickup(Player player) {
        return true;
    }

    /**
     * 原版 {@code Container.getMaxStackSize()}。
     */
    int getMaxStackSize() {
        return PlayerInventoryOps.CONTAINER_MAX_STACK_SIZE;
    }

    int getMaxStackSize(ItemStack stack) {
        return Math.min(getMaxStackSize(), stack.maxStackSize());
    }

    /**
     * 原版 {@code Container.removeItem}（{@code ContainerHelper.removeItem}）：取出最多 {@code amount} 个。
     */
    ItemStack remove(int amount) {
        var stack = getItem();
        if (stack.isAir() || amount <= 0) {
            return ItemStack.AIR;
        }
        int taken = Math.min(amount, stack.amount());
        set(stack.withAmount(stack.amount() - taken));
        return stack.withAmount(taken);
    }

    /**
     * 原版 {@code Slot.onTake}：物品被玩家取走之后调用，返回处理后的物品。
     */
    ItemStack onTake(Player player, ItemStack carried) {
        return carried;
    }

    /**
     * 原版 {@code Slot.onQuickCraft(ItemStack, ItemStack)}：按住 Shift 移动之后，按移走的数量调用。
     */
    void onQuickCraft(ItemStack picked, ItemStack original) {
        int count = original.amount() - picked.amount();
        if (count > 0) {
            onQuickCraft(original, count);
        }
    }

    void onQuickCraft(ItemStack picked, int count) {
    }

    void onSwapCraft(int count) {
    }

    /**
     * 原版 {@code Slot.safeClone}：创造模式中键点击得到的物品。
     */
    ItemStack safeClone(Player player) {
        var item = getItem();
        return item.withAmount(item.maxStackSize());
    }

    boolean allowModification(Player player) {
        return mayPickup(player) && mayPlace(getItem());
    }

    /**
     * 原版 {@code Slot.tryRemove}。
     */
    Optional<ItemStack> tryRemove(int amount, int maxAmount, Player player) {
        if (!mayPickup(player)) {
            return Optional.empty();
        }
        if (!allowModification(player) && maxAmount < getItem().amount()) {
            return Optional.empty();
        }
        var result = remove(Math.min(amount, maxAmount));
        if (result.isAir()) {
            return Optional.empty();
        }
        if (getItem().isAir()) {
            setByPlayer(ItemStack.AIR);
        }
        return Optional.of(result);
    }

    /**
     * 原版 {@code Slot.safeTake}：返回经过 {@link #onTake} 处理的物品。
     */
    ItemStack safeTake(int amount, int maxAmount, Player player) {
        return tryRemove(amount, maxAmount, player).map(item -> onTake(player, item)).orElse(ItemStack.AIR);
    }

    /**
     * 原版 {@code Slot.safeInsert}：放入最多 {@code amount} 个，返回剩下的物品。
     */
    ItemStack safeInsert(ItemStack stack, int amount) {
        if (stack.isAir() || !mayPlace(stack)) {
            return stack;
        }
        var slotStack = getItem();
        int transferable = Math.min(Math.min(amount, stack.amount()), getMaxStackSize(stack) - (slotStack.isAir() ? 0 : slotStack.amount()));
        if (transferable <= 0) {
            return stack;
        }
        if (slotStack.isAir()) {
            setByPlayer(stack.withAmount(transferable));
        } else if (slotStack.isSimilar(stack)) {
            setByPlayer(slotStack.withAmount(slotStack.amount() + transferable));
        } else {
            return stack;
        }
        return stack.withAmount(stack.amount() - transferable);
    }
}
