package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.predicate.ItemPredicate;
import net.minestom.server.ServerProcess;
import net.minestom.server.item.ItemStack;

/**
 * 原版 {@code ItemPredicate#test} 的求值：物品、数量、组件依次检查。
 */
final class ItemPredicates {
    private ItemPredicates() {
    }

    static boolean test(ItemPredicate predicate, ItemStack stack, ServerProcess process) {
        if (predicate.items().isPresent() && !Holders.contains(predicate.items().get(), stack.material().key(), process.material())) {
            return false;
        }
        // 原版空物品堆的数量是 0
        int count = stack.isAir() ? 0 : stack.amount();
        if (!predicate.count().inRange(count)) {
            return false;
        }
        return ComponentPredicates.test(predicate.components(), stack, process);
    }
}
