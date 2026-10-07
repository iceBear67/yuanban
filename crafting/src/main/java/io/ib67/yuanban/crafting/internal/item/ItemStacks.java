package io.ib67.yuanban.crafting.internal.item;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import org.jetbrains.annotations.ApiStatus;

/**
 * 原版 {@code ItemStack} 中合成用到的判断。
 */
@ApiStatus.Internal
public final class ItemStacks {
    private ItemStacks() {
    }

    /**
     * 原版 {@code ItemStack.isDamageableItem}。
     */
    public static boolean isDamageableItem(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE) && !stack.has(DataComponents.UNBREAKABLE) && stack.has(DataComponents.DAMAGE);
    }

    /**
     * 原版 {@code ItemStack.isDamaged}。
     */
    public static boolean isDamaged(ItemStack stack) {
        return isDamageableItem(stack) && damageValue(stack) > 0;
    }

    /**
     * 原版 {@code ItemStack.getDamageValue}：限制在 {@code [0, 最大耐久]} 之内。
     */
    public static int damageValue(ItemStack stack) {
        return Math.clamp(stack.get(DataComponents.DAMAGE, 0), 0, maxDamage(stack));
    }

    public static int maxDamage(ItemStack stack) {
        return stack.get(DataComponents.MAX_DAMAGE, 0);
    }

    /**
     * 原版 {@code ItemStack.isEnchanted}：只看 {@code enchantments}，不看 {@code stored_enchantments}。
     */
    public static boolean isEnchanted(ItemStack stack) {
        return !stack.get(DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY).enchantments().isEmpty();
    }

    /**
     * 原版 {@code ItemStack.isStackable}。
     */
    public static boolean isStackable(ItemStack stack) {
        return stack.maxStackSize() > 1 && (!isDamageableItem(stack) || !isDamaged(stack));
    }

    /**
     * 原版 {@code Inventory.isUsableForCrafting}：配方书只使用没有损坏、没有附魔、没有自定义名称的物品。
     */
    public static boolean isUsableForCrafting(ItemStack stack) {
        return !isDamaged(stack) && !isEnchanted(stack) && !stack.has(DataComponents.CUSTOM_NAME);
    }
}
