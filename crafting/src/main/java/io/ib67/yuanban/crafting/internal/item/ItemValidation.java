package io.ib67.yuanban.crafting.internal.item;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * 原版 {@code ItemStack.validateStrict}，配方产出物品之前用它检查结果。
 */
@ApiStatus.Internal
public final class ItemValidation {
    private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);

    private ItemValidation() {
    }

    /**
     * 原版 {@code ItemStackTemplate.validate}：结果不合法时返回 {@link ItemStack#AIR}。
     */
    public static ItemStack validateStrict(ItemStack stack) {
        if (stack.isAir()) {
            return stack;
        }
        return componentsValid(stack) && stack.amount() <= stack.maxStackSize() ? stack : ItemStack.AIR;
    }

    /**
     * 原版 {@code ItemStack.validateComponents}。
     */
    private static boolean componentsValid(ItemStack stack) {
        if (stack.has(DataComponents.MAX_DAMAGE) && stack.get(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return false;
        }
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null && !containedSizesValid(container)) {
            return false;
        }
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && (!containedSizesValid(bundle) || bundleWeight(bundle) == null)) {
            return false;
        }
        var projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        return projectiles == null || containedSizesValid(projectiles);
    }

    /**
     * 原版 {@code ItemStack.validateContainedItemSizes}，空格子不参与检查。
     */
    private static boolean containedSizesValid(List<ItemStack> items) {
        for (var item : items) {
            if (!item.isAir() && item.amount() > item.maxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 原版 {@code BundleContents.computeContentWeight}；原版在 {@link Fraction} 运算溢出时给出错误，这里返回 {@code null}。
     */
    private static Fraction bundleWeight(List<ItemStack> items) {
        var weight = Fraction.ZERO;
        for (var item : items) {
            var itemWeight = weight(item);
            if (itemWeight == null) {
                return null;
            }
            try {
                weight = weight.add(itemWeight.multiplyBy(Fraction.getFraction(item.amount(), 1)));
            } catch (ArithmeticException e) {
                // 原版捕获同一个异常，报告 "Excessive total bundle weight"，物品因此不合法
                return null;
            }
        }
        return weight;
    }

    /**
     * 原版 {@code BundleContents.getWeight}。
     */
    private static Fraction weight(ItemStack item) {
        var nested = item.get(DataComponents.BUNDLE_CONTENTS);
        if (nested != null) {
            var nestedWeight = bundleWeight(nested);
            if (nestedWeight == null) {
                return null;
            }
            try {
                return nestedWeight.add(BUNDLE_IN_BUNDLE_WEIGHT);
            } catch (ArithmeticException e) {
                return null;
            }
        }
        var bees = item.get(DataComponents.BEES);
        return bees != null && !bees.isEmpty() ? Fraction.ONE : Fraction.getFraction(1, item.maxStackSize());
    }
}
