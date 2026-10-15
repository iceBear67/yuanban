package io.ib67.yuanban.bot.inventory;

import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;

/**
 * 各种窗口独有的槽位数（第一个主物品栏槽位的下标，wiki Inventory），取值与 Minestom {@code InventoryType} 相同。
 */
public final class ContainerSlots {
    private ContainerSlots() {
    }

    public static int unique(ContainerType type) {
        return switch (type) {
            case GENERIC_9X1 -> 9;
            case GENERIC_9X2 -> 18;
            case GENERIC_9X3 -> 27;
            case GENERIC_9X4 -> 36;
            case GENERIC_9X5 -> 45;
            case GENERIC_9X6 -> 54;
            case GENERIC_3X3, CRAFTER_3x3 -> 9;
            case ANVIL, BLAST_FURNACE, FURNACE, GRINDSTONE, MERCHANT, SMOKER, CARTOGRAPHY -> 3;
            case BEACON, LECTERN -> 1;
            case BREWING_STAND, HOPPER -> 5;
            case CRAFTING -> 10;
            case ENCHANTMENT, STONECUTTER -> 2;
            case LOOM, SMITHING -> 4;
            case SHULKER_BOX -> 27;
        };
    }
}
