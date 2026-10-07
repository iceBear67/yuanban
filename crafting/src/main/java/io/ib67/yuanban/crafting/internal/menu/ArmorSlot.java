package io.ib67.yuanban.crafting.internal.menu;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.registry.Registries;

import java.util.Objects;

/**
 * 原版 {@code ArmorSlot}：背包菜单中的盔甲格，最多放一个，只接受这个部位的可装备物品，带绑定诅咒的物品在非创造模式下不能取下。
 */
final class ArmorSlot extends MenuSlot {
    private final EquipmentSlot slot;
    private final Registries registries;

    ArmorSlot(AbstractInventory inventory, int index, EquipmentSlot slot, Registries registries) {
        super(inventory, index);
        this.slot = slot;
        this.registries = registries;
    }

    @Override
    int getMaxStackSize() {
        return 1;
    }

    /**
     * 原版 {@code LivingEntity.isEquippableInSlot}。
     */
    @Override
    boolean mayPlace(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return slot == EquipmentSlot.MAIN_HAND;
        }
        var allowed = equippable.allowedEntities();
        return slot == equippable.slot() && (allowed == null || allowed.contains(EntityType.PLAYER.registryKey()));
    }

    @Override
    boolean mayPickup(Player player) {
        var stack = getItem();
        if (!stack.isAir() && player.getGameMode() != GameMode.CREATIVE && preventsArmorChange(stack)) {
            return false;
        }
        return super.mayPickup(player);
    }

    /**
     * 原版 {@code EnchantmentHelper.has(stack, PREVENT_ARMOR_CHANGE)}：物品的魔咒中有一个带这个效果。
     */
    private boolean preventsArmorChange(ItemStack stack) {
        var enchantments = stack.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY).enchantments();
        for (var key : enchantments.keySet()) {
            var enchantment = Objects.requireNonNull(registries.enchantment().get(key), () -> "unknown enchantment " + key);
            if (enchantment.effects().has(EffectComponent.PREVENT_ARMOR_CHANGE)) {
                return true;
            }
        }
        return false;
    }
}
