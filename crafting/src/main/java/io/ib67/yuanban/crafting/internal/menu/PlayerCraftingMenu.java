package io.ib67.yuanban.crafting.internal.menu;

import io.ib67.yuanban.crafting.recipe.RecipeManager;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

/**
 * 原版 {@code InventoryMenu}：玩家背包的菜单（窗口 0）。菜单序号 0 是结果格（玩家 36 号格），1–4 是 2×2 合成格（37–40），
 * 5–8 是头、胸、腿、脚（41–44），9–35 是背包，36–44 是快捷栏（0–8），45 是副手。
 */
final class PlayerCraftingMenu extends CraftingMenu {
    private static final int INVENTORY_START = 9;
    private static final int HOTBAR_START = 36;
    private static final int OFFHAND = 45;

    PlayerCraftingMenu(Player player, RecipeManager recipes) {
        super(player, recipes, player.getInventory(), PlayerInventoryUtils.CRAFT_RESULT, PlayerInventoryUtils.CRAFT_SLOT_1, 2, 2);
        var playerInventory = player.getInventory();
        var registries = recipes.context().registries();
        slots.add(new ArmorSlot(playerInventory, PlayerInventoryUtils.HELMET_SLOT, EquipmentSlot.HELMET, registries));
        slots.add(new ArmorSlot(playerInventory, PlayerInventoryUtils.CHESTPLATE_SLOT, EquipmentSlot.CHESTPLATE, registries));
        slots.add(new ArmorSlot(playerInventory, PlayerInventoryUtils.LEGGINGS_SLOT, EquipmentSlot.LEGGINGS, registries));
        slots.add(new ArmorSlot(playerInventory, PlayerInventoryUtils.BOOTS_SLOT, EquipmentSlot.BOOTS, registries));
        addStandardInventorySlots();
        slots.add(new MenuSlot(playerInventory, PlayerInventoryUtils.OFFHAND_SLOT));
    }

    /**
     * Minestom 玩家背包格子编号对应的菜单序号。
     */
    static int menuIndex(int minestomSlot) {
        if (minestomSlot >= PlayerInventoryUtils.CRAFT_RESULT && minestomSlot <= PlayerInventoryUtils.BOOTS_SLOT) {
            return minestomSlot - PlayerInventoryUtils.CRAFT_RESULT;
        }
        if (minestomSlot == PlayerInventoryUtils.OFFHAND_SLOT) {
            return OFFHAND;
        }
        if (minestomSlot < 9) {
            return HOTBAR_START + minestomSlot;
        }
        return minestomSlot;
    }

    /**
     * 原版 {@code LivingEntity.getEquipmentSlotForItem}。
     */
    private static EquipmentSlot equipmentSlotForItem(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null ? equippable.slot() : EquipmentSlot.MAIN_HAND;
    }

    /**
     * 原版 {@code InventoryMenu.quickMoveStack}。
     */
    @Override
    ItemStack quickMoveStack(int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.AIR;
        }
        var stack = slot.getItem();
        var equipmentSlot = equipmentSlotForItem(stack);
        MoveResult moved;
        if (index == 0) {
            moved = moveItemStackTo(stack, INVENTORY_START, OFFHAND, true);
            if (!moved.changed()) {
                return ItemStack.AIR;
            }
            slot.onQuickCraft(moved.remaining(), stack);
        } else if (index < INVENTORY_START) {
            // 合成格和盔甲格移入背包和快捷栏
            moved = moveItemStackTo(stack, INVENTORY_START, OFFHAND, false);
        } else if (equipmentSlot.isArmor() && !slots.get(equipmentSlot.armorSlot() - PlayerInventoryUtils.CRAFT_RESULT).hasItem()) {
            int position = equipmentSlot.armorSlot() - PlayerInventoryUtils.CRAFT_RESULT;
            moved = moveItemStackTo(stack, position, position + 1, false);
        } else if (equipmentSlot == EquipmentSlot.OFF_HAND && !slots.get(OFFHAND).hasItem()) {
            moved = moveItemStackTo(stack, OFFHAND, OFFHAND + 1, false);
        } else if (index < HOTBAR_START) {
            moved = moveItemStackTo(stack, HOTBAR_START, OFFHAND, false);
        } else if (index < OFFHAND) {
            moved = moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false);
        } else {
            moved = moveItemStackTo(stack, INVENTORY_START, OFFHAND, false);
        }
        if (!moved.changed()) {
            return ItemStack.AIR;
        }
        return finishQuickMove(index, moved.remaining(), stack);
    }
}
