package io.ib67.yuanban.loot.eval;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.animal.ChestedHorseMeta;
import net.minestom.server.entity.metadata.animal.LlamaMeta;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.item.FireballMeta;
import net.minestom.server.entity.metadata.item.SmallFireballMeta;
import net.minestom.server.entity.metadata.minecart.AbstractMinecartContainerMeta;
import net.minestom.server.entity.metadata.monster.PiglinMeta;
import net.minestom.server.entity.metadata.monster.raider.PillagerMeta;
import net.minestom.server.entity.metadata.other.BoatMeta;
import net.minestom.server.entity.metadata.other.ItemFrameMeta;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.entity.metadata.villager.AbstractVillagerMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.Nullable;

/**
 * 原版 {@code Entity#getSlot(int)} 的槽位映射：按实体的类型读取槽位中的物品，实体没有这个槽位时为 {@code null}。
 * <ul>
 *   <li>玩家：光标 499、合成格 500–503、背包 0–35、末影箱 200–226；</li>
 *   <li>生物：主手 98、副手 99、脚 100、腿 101、胸 102、头 103、身体 105、鞍 106；</li>
 *   <li>掉落物、物品展示框、物品展示实体、火球：0；</li>
 *   <li>带箱子的驴、骡、羊驼：箱子 499，背包从 500 起；</li>
 *   <li>村民、猪灵、掠夺者：背包从 300 起；箭：0；运输矿车与运输船：容器的槽位。</li>
 * </ul>
 */
final class EntitySlots {
    private EntitySlots() {
    }

    static @Nullable ItemStack get(Entity entity, int slot) {
        var meta = entity.getEntityMeta();
        if (entity instanceof Player player) {
            return playerSlot(player, slot);
        }
        if (entity instanceof ItemEntity item) {
            return slot == 0 ? item.getItemStack() : null;
        }
        if (meta instanceof ItemFrameMeta frame) {
            return slot == 0 ? frame.getItem() : null;
        }
        if (meta instanceof ItemDisplayMeta display) {
            return slot == 0 ? display.getItemStack() : null;
        }
        if (meta instanceof FireballMeta fireball) {
            return slot == 0 ? fireball.getItem() : null;
        }
        if (meta instanceof SmallFireballMeta fireball) {
            return slot == 0 ? fireball.getItem() : null;
        }
        if (meta instanceof AbstractArrowMeta) {
            if (slot == 0) {
                // TODO(minestom): Minestom 的箭没有原版拾取时得到的物品（AbstractArrow#getPickupItemStackOrigin）
                throw LootEvaluation.unsupported("pickup item of the arrow " + entity.getEntityType().key().asString());
            }
            return null;
        }
        if (meta instanceof AbstractMinecartContainerMeta || isChestBoat(entity, meta)) {
            int size = containerSize(entity);
            if (slot >= 0 && slot < size) {
                // TODO(minestom): Minestom 的运输矿车与运输船没有容器内容
                throw LootEvaluation.unsupported("container slot " + slot + " of " + entity.getEntityType().key().asString());
            }
            return null;
        }
        if (entity instanceof LivingEntity living) {
            if (meta instanceof ChestedHorseMeta chested) {
                if (slot == 499) {
                    return chested.isHasChest() ? ItemStack.of(Material.CHEST) : ItemStack.AIR;
                }
                int columns = !chested.isHasChest() ? 0 : meta instanceof LlamaMeta llama ? llama.getStrength() : 5;
                int inventorySlot = slot - 500;
                if (inventorySlot >= 0 && inventorySlot < columns * 3) {
                    // TODO(minestom): Minestom 的驴、骡、羊驼没有箱子中的物品
                    throw LootEvaluation.unsupported("chest slot " + slot + " of " + entity.getEntityType().key().asString());
                }
            }
            int inventorySize = meta instanceof AbstractVillagerMeta || meta instanceof PiglinMeta ? 8
                    : meta instanceof PillagerMeta ? 5 : 0;
            int inventorySlot = slot - 300;
            if (inventorySlot >= 0 && inventorySlot < inventorySize) {
                // TODO(minestom): Minestom 的村民、猪灵、掠夺者没有背包
                throw LootEvaluation.unsupported("inventory slot " + slot + " of " + entity.getEntityType().key().asString());
            }
            return equipmentSlot(living, slot);
        }
        return null;
    }

    private static @Nullable ItemStack playerSlot(Player player, int slot) {
        var inventory = player.getInventory();
        if (slot == 499) {
            return inventory.getCursorItem();
        }
        int craftSlot = slot - 500;
        if (craftSlot >= 0 && craftSlot < 4) {
            return inventory.getItemStack(PlayerInventoryUtils.CRAFT_SLOT_1 + craftSlot);
        }
        if (slot >= 0 && slot < 36) {
            return inventory.getItemStack(slot);
        }
        int enderSlot = slot - 200;
        if (enderSlot >= 0 && enderSlot < 27) {
            // TODO(minestom): Minestom 的玩家没有末影箱物品栏
            throw LootEvaluation.unsupported("ender chest slot " + slot + " of a player");
        }
        return equipmentSlot(player, slot);
    }

    /**
     * 原版 {@code LivingEntity#getEquipmentSlot(int)}。
     */
    private static @Nullable ItemStack equipmentSlot(LivingEntity entity, int slot) {
        EquipmentSlot equipment = switch (slot) {
            case 98 -> EquipmentSlot.MAIN_HAND;
            case 99 -> EquipmentSlot.OFF_HAND;
            case 100 -> EquipmentSlot.BOOTS;
            case 101 -> EquipmentSlot.LEGGINGS;
            case 102 -> EquipmentSlot.CHESTPLATE;
            case 103 -> EquipmentSlot.HELMET;
            case 105 -> EquipmentSlot.BODY;
            case 106 -> EquipmentSlot.SADDLE;
            default -> null;
        };
        return equipment == null ? null : entity.getEquipment(equipment);
    }

    /**
     * 原版 {@code AbstractChestBoat}：运输船与运输竹筏。
     */
    private static boolean isChestBoat(Entity entity, Object meta) {
        if (!(meta instanceof BoatMeta)) {
            return false;
        }
        var path = entity.getEntityType().key().value();
        return path.endsWith("_chest_boat") || path.endsWith("_chest_raft");
    }

    /**
     * 原版 {@code ContainerEntity#getContainerSize}：运输漏斗矿车 5 格，其余 27 格。
     */
    private static int containerSize(Entity entity) {
        return entity.getEntityType().key().value().equals("hopper_minecart") ? 5 : 27;
    }
}
