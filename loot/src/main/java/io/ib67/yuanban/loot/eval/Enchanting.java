package io.ib67.yuanban.loot.eval;

import io.ib67.yuanban.loot.LootException;
import net.minestom.server.ServerProcess;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 原版 {@code EnchantmentHelper} 中战利品修饰器用到的部分：按附魔台的规则挑选附魔、修改物品的附魔组件。
 */
final class Enchanting {
    private Enchanting() {
    }

    /**
     * 原版 {@code EnchantmentInstance}。
     */
    record Instance(RegistryKey<Enchantment> key, Enchantment enchantment, int level) {
    }

    /**
     * 按 ID 取得附魔，找不到时抛出异常：解码时已经检查过附魔存在。
     */
    static Enchantment enchantment(RegistryKey<Enchantment> key, ServerProcess process) {
        var value = process.enchantment().get(key);
        if (value == null) {
            throw new LootException("Unknown enchantment: " + key.key().asString());
        }
        return value;
    }

    /**
     * 原版 {@code EnchantmentHelper.getComponentType}：附魔书使用 {@code stored_enchantments}。
     */
    static DataComponent<EnchantmentList> componentType(ItemStack stack) {
        return stack.material() == Material.ENCHANTED_BOOK ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }

    /**
     * 原版 {@code EnchantmentHelper.updateEnchantments}：物品没有对应的附魔组件时不修改。
     */
    static ItemStack updateEnchantments(ItemStack stack, Consumer<LinkedHashMap<RegistryKey<Enchantment>, Integer>> update) {
        var type = componentType(stack);
        var old = stack.get(type);
        if (old == null) {
            return stack;
        }
        var mutable = new LinkedHashMap<>(old.enchantments());
        update.accept(mutable);
        return stack.with(type, new EnchantmentList(mutable));
    }

    /**
     * 原版 {@code ItemEnchantments$Mutable#upgrade}：等级大于 0 时取原有等级与新等级（不超过 255）中的较大者。
     */
    static void upgrade(LinkedHashMap<RegistryKey<Enchantment>, Integer> enchantments, RegistryKey<Enchantment> key, int level) {
        if (level > 0) {
            enchantments.merge(key, Math.min(level, 255), Integer::max);
        }
    }

    /**
     * 原版 {@code ItemEnchantments$Mutable#set}：等级不大于 0 时移除，否则设为不超过 255 的等级。
     */
    static void set(LinkedHashMap<RegistryKey<Enchantment>, Integer> enchantments, RegistryKey<Enchantment> key, int level) {
        if (level <= 0) {
            enchantments.remove(key);
        } else {
            enchantments.put(key, Math.min(level, 255));
        }
    }

    /**
     * 原版 {@code ItemStack#enchant}。
     */
    static ItemStack enchant(ItemStack stack, RegistryKey<Enchantment> key, int level) {
        return updateEnchantments(stack, enchantments -> upgrade(enchantments, key, level));
    }

    /**
     * 原版 {@code Enchantment#canEnchant}：物品在 {@code supported_items} 中。
     */
    static boolean canEnchant(Enchantment enchantment, ItemStack stack) {
        return enchantment.supportedItems().contains(RegistryKey.of(stack.material().key()));
    }

    /**
     * 原版 {@code Enchantment#isPrimaryItem}：物品可以附魔，并且没有 {@code primary_items} 或在其中。
     */
    private static boolean isPrimaryItem(Enchantment enchantment, ItemStack stack) {
        var primary = enchantment.primaryItems();
        return canEnchant(enchantment, stack) && (primary == null || primary.contains(RegistryKey.of(stack.material().key())));
    }

    /**
     * 原版 {@code Enchantment$Cost#calculate}。
     */
    private static int cost(Enchantment.Cost cost, int level) {
        return cost.base() + cost.perLevelAboveFirst() * (level - 1);
    }

    /**
     * 原版 {@code EnchantmentHelper.enchantItem(RandomSource, ItemStack, int, Stream)}：书变成附魔书（新物品，数量为 1），
     * 再逐个加上挑选出的附魔。
     */
    static ItemStack enchantItem(Random random, ItemStack stack, int enchantmentCost, List<RegistryKey<Enchantment>> source,
                                 ServerProcess process) {
        var enchants = selectEnchantment(random, stack, enchantmentCost, source, process);
        var result = stack.material() == Material.BOOK ? ItemStack.of(Material.ENCHANTED_BOOK) : stack;
        for (var enchant : enchants) {
            result = enchant(result, enchant.key(), enchant.level());
        }
        return result;
    }

    /**
     * 原版 {@code EnchantmentHelper.selectEnchantment}。
     */
    static List<Instance> selectEnchantment(Random random, ItemStack stack, int enchantmentCost,
                                            List<RegistryKey<Enchantment>> source, ServerProcess process) {
        var results = new ArrayList<Instance>();
        Integer enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (enchantable == null) {
            return results;
        }
        enchantmentCost += 1 + random.nextInt(enchantable / 4 + 1) + random.nextInt(enchantable / 4 + 1);
        float randomSpan = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        enchantmentCost = Mth.clamp(Math.round(enchantmentCost + enchantmentCost * randomSpan), 1, Integer.MAX_VALUE);
        var enchantments = availableEnchantmentResults(enchantmentCost, stack, source, process);
        if (!enchantments.isEmpty()) {
            weighted(random, enchantments).ifPresent(results::add);
            while (random.nextInt(50) <= enchantmentCost) {
                if (!results.isEmpty()) {
                    var last = results.getLast();
                    enchantments.removeIf(candidate -> !areCompatible(last, candidate));
                }
                if (enchantments.isEmpty()) {
                    break;
                }
                weighted(random, enchantments).ifPresent(results::add);
                enchantmentCost /= 2;
            }
        }
        return results;
    }

    /**
     * 原版 {@code EnchantmentHelper.getAvailableEnchantmentResults}：每个附魔取费用在范围内的最高等级。
     */
    private static List<Instance> availableEnchantmentResults(int value, ItemStack stack, List<RegistryKey<Enchantment>> source,
                                                              ServerProcess process) {
        var results = new ArrayList<Instance>();
        boolean isBook = stack.material() == Material.BOOK;
        for (var key : source) {
            var enchantment = enchantment(key, process);
            if (!isPrimaryItem(enchantment, stack) && !isBook) {
                continue;
            }
            for (int level = enchantment.maxLevel(); level >= 1; level--) {
                if (value >= cost(enchantment.minCost(), level) && value <= cost(enchantment.maxCost(), level)) {
                    results.add(new Instance(key, enchantment, level));
                    break;
                }
            }
        }
        return results;
    }

    /**
     * 原版 {@code Enchantment.areCompatible}：不是同一个附魔，且互不在对方的互斥集合中。
     */
    private static boolean areCompatible(Instance first, Instance second) {
        return !first.key().equals(second.key())
                && !first.enchantment().exclusiveSet().contains(second.key())
                && !second.enchantment().exclusiveSet().contains(first.key());
    }

    /**
     * 原版 {@code WeightedRandom.getRandomItem}：权重和超过 {@code int} 范围时抛出异常，为 0 时没有结果。
     */
    private static Optional<Instance> weighted(Random random, List<Instance> items) {
        long total = 0;
        for (var item : items) {
            total += item.enchantment().weight();
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        }
        if (total < 0) {
            throw new IllegalArgumentException("Negative total weight in getRandomItem");
        }
        if (total == 0) {
            return Optional.empty();
        }
        int selection = random.nextInt((int) total);
        for (var item : items) {
            selection -= item.enchantment().weight();
            if (selection < 0) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
