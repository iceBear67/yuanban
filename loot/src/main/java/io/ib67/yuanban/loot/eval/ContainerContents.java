package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.slot.ContainerComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 原版 {@code ContainerComponentManipulators} 中三种容器组件的写入规则，以及收纳袋的重量规则（原版 {@code BundleContents}）。
 */
final class ContainerContents {
    /**
     * 原版 {@code BundleContents.BUNDLE_IN_BUNDLE_WEIGHT}。
     */
    private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);
    /**
     * 原版 {@code BundleContents.BEEHIVE_WEIGHT}。
     */
    private static final Fraction BEEHIVE_WEIGHT = Fraction.getFraction(1, 1);
    /**
     * 原版 {@code ItemContainerContents.MAX_SIZE}。
     */
    private static final int CONTAINER_MAX_SIZE = 256;

    private ContainerContents() {
    }

    /**
     * 原版 {@code ContainerComponentManipulator#setContents(ItemStack, Stream)}：以空的组件为基础写入新的内容。
     */
    static ItemStack setContents(ItemStack stack, ContainerComponent component, List<ItemStack> contents) {
        return switch (component) {
            case CONTAINER -> stack.with(DataComponents.CONTAINER, containerFromItems(contents));
            case BUNDLE_CONTENTS -> stack.with(DataComponents.BUNDLE_CONTENTS, bundleFromItems(contents));
            case CHARGED_PROJECTILES -> stack.with(DataComponents.CHARGED_PROJECTILES,
                    contents.stream().filter(item -> !item.isAir()).toList());
        };
    }

    /**
     * 原版 {@code ContainerComponentManipulator#modifyItems}：组件不存在时不修改；空槽位保持为空，其余物品经修饰之后
     * 数量限制在最大堆叠数以内，再按 {@link #setContents} 写回。
     */
    static ItemStack modifyItems(ItemStack stack, ContainerComponent component, UnaryOperator<ItemStack> modifier) {
        var contents = stack.get(component.component());
        if (contents == null) {
            return stack;
        }
        var modified = new ArrayList<ItemStack>(contents.size());
        for (var item : contents) {
            if (item.isAir()) {
                modified.add(item);
                continue;
            }
            var result = modifier.apply(item);
            if (!result.isAir() && result.amount() > result.maxStackSize()) {
                result = result.withAmount(result.maxStackSize());
            }
            modified.add(result);
        }
        return setContents(stack, component, modified);
    }

    /**
     * 原版 {@code ItemContainerContents.fromItems}：去掉末尾的空槽位，超过 256 个槽位时抛出异常。
     */
    private static List<ItemStack> containerFromItems(List<ItemStack> items) {
        int last = items.size() - 1;
        while (last >= 0 && items.get(last).isAir()) {
            last--;
        }
        if (last + 1 > CONTAINER_MAX_SIZE) {
            throw new IllegalArgumentException("Got " + (last + 1) + " items, but maximum is " + CONTAINER_MAX_SIZE);
        }
        return List.copyOf(items.subList(0, last + 1));
    }

    /**
     * 原版 {@code BundleContents$Mutable}：从空的收纳袋开始逐个 {@code tryInsert}。
     */
    private static List<ItemStack> bundleFromItems(List<ItemStack> items) {
        var contents = new ArrayList<ItemStack>();
        Fraction weight = Fraction.ZERO;
        for (var item : items) {
            weight = tryInsert(contents, weight, item);
        }
        return List.copyOf(contents);
    }

    /**
     * 原版 {@code BundleContents$Mutable#tryInsert}：按剩余重量能放下的数量放入；可以堆叠的物品与已有的相同物品合并，
     * 放入的物品移到最前面。
     *
     * @return 放入之后的总重量
     */
    private static Fraction tryInsert(List<ItemStack> contents, Fraction weight, ItemStack item) {
        if (!canItemBeInBundle(item)) {
            return weight;
        }
        var itemWeight = weight(item);
        if (itemWeight.isEmpty()) {
            return weight;
        }
        int maxAmount = Math.max(Fraction.ONE.subtract(weight).divideBy(itemWeight.get()).intValue(), 0);
        int amountToAdd = Math.min(item.amount(), maxAmount);
        if (amountToAdd == 0) {
            return weight;
        }
        var newWeight = weight.add(itemWeight.get().multiplyBy(Fraction.getFraction(amountToAdd, 1)));
        int index = isStackable(item) ? indexOfSimilar(contents, item) : -1;
        if (index != -1) {
            var removed = contents.remove(index);
            contents.addFirst(removed.withAmount(removed.amount() + amountToAdd));
        } else {
            contents.addFirst(item.withAmount(amountToAdd));
        }
        return newWeight;
    }

    private static int indexOfSimilar(List<ItemStack> contents, ItemStack item) {
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i).isSimilar(item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 原版 {@code ItemStack#isStackable}：最大堆叠数大于 1，并且不是已经损坏的可损坏物品。
     */
    private static boolean isStackable(ItemStack item) {
        if (item.maxStackSize() <= 1) {
            return false;
        }
        return !isDamageable(item) || item.get(DataComponents.DAMAGE, 0) <= 0;
    }

    /**
     * 原版 {@code ItemStack#isDamageableItem}。
     */
    static boolean isDamageable(ItemStack item) {
        return item.has(DataComponents.MAX_DAMAGE) && !item.has(DataComponents.UNBREAKABLE) && item.has(DataComponents.DAMAGE);
    }

    /**
     * 原版 {@code BundleContents.canItemBeInBundle}：不为空，且物品可以放进容器物品（原版只有潜影盒不行）。
     */
    private static boolean canItemBeInBundle(ItemStack item) {
        if (item.isAir()) {
            return false;
        }
        var block = item.material().block();
        return block == null || !block.key().value().endsWith("shulker_box");
    }

    /**
     * 原版 {@code BundleContents.getWeight}：收纳袋是内容的重量加 1/16，有蜜蜂的蜂巢是 1，其他物品是最大堆叠数的倒数。
     * 内容的重量溢出时没有结果。
     */
    private static Optional<Fraction> weight(ItemStack item) {
        var bundle = item.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            return contentWeight(bundle).map(weight -> weight.add(BUNDLE_IN_BUNDLE_WEIGHT));
        }
        var bees = item.get(DataComponents.BEES, List.of());
        return Optional.of(!bees.isEmpty() ? BEEHIVE_WEIGHT : Fraction.getFraction(1, item.maxStackSize()));
    }

    /**
     * 原版 {@code BundleContents#weight} 不是错误结果：收纳袋内容的重量能够计算。
     */
    static boolean isBundleWeightValid(List<ItemStack> items) {
        return contentWeight(items).isPresent();
    }

    /**
     * 原版 {@code BundleContents.computeContentWeight}：算术溢出时为 {@code Excessive total bundle weight} 错误，这里没有结果。
     */
    private static Optional<Fraction> contentWeight(List<ItemStack> items) {
        try {
            Fraction weight = Fraction.ZERO;
            for (var item : items) {
                var itemWeight = weight(item);
                if (itemWeight.isEmpty()) {
                    return Optional.empty();
                }
                weight = weight.add(itemWeight.get().multiplyBy(Fraction.getFraction(item.amount(), 1)));
            }
            return Optional.of(weight);
        } catch (ArithmeticException overflow) {
            // 原版 computeContentWeight 捕获这个异常，得到错误结果
            return Optional.empty();
        }
    }
}
