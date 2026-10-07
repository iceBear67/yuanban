package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.internal.item.ItemStacks;
import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * {@code crafting_special_repairitem}，原版 {@code RepairItemRecipe}：两个同种、有耐久的物品合成一个，耐久相加再加上最大耐久的 5%，
 * 只保留诅咒魔咒。
 */
public record RepairItemRecipe() implements SpecialCraftingRecipe {
    private static final Key CURSE_TAG = Key.key("curse");

    private record Pair(ItemStack first, ItemStack second) {
    }

    private static @Nullable Pair itemsToCombine(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }
        ItemStack first = null;
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.isAir()) {
                continue;
            }
            if (first != null) {
                return canCombine(first, stack) ? new Pair(first, stack) : null;
            }
            first = stack;
        }
        return null;
    }

    private static boolean canCombine(ItemStack first, ItemStack second) {
        return second.material() == first.material()
                && first.amount() == 1 && second.amount() == 1
                && first.has(DataComponents.MAX_DAMAGE) && second.has(DataComponents.MAX_DAMAGE)
                && first.has(DataComponents.DAMAGE) && second.has(DataComponents.DAMAGE);
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        return itemsToCombine(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        var pair = itemsToCombine(input);
        if (pair == null) {
            return ItemStack.AIR;
        }
        var first = pair.first();
        var second = pair.second();
        int durability = Math.max(ItemStacks.maxDamage(first), ItemStacks.maxDamage(second));
        int remaining1 = ItemStacks.maxDamage(first) - ItemStacks.damageValue(first);
        int remaining2 = ItemStacks.maxDamage(second) - ItemStacks.damageValue(second);
        int remaining = remaining1 + remaining2 + durability * 5 / 100;
        var repaired = ItemStack.of(first.material())
                .with(DataComponents.MAX_DAMAGE, durability)
                .with(DataComponents.DAMAGE, Math.clamp(Math.max(durability - remaining, 0), 0, durability));

        // 原版 EnchantmentHelper.updateEnchantments：物品原型没有魔咒组件的时候不写入
        var type = enchantmentComponent(repaired.material());
        var old = repaired.get(type);
        if (old == null) {
            return repaired;
        }
        var firstEnchantments = first.get(enchantmentComponent(first.material()), EnchantmentList.EMPTY).enchantments();
        var secondEnchantments = second.get(enchantmentComponent(second.material()), EnchantmentList.EMPTY).enchantments();
        var curses = Objects.requireNonNull(context.registries().enchantment().getTag(CURSE_TAG), "enchantment tag minecraft:curse is not registered");
        var enchantments = new LinkedHashMap<>(old.enchantments());
        var union = new LinkedHashSet<>(firstEnchantments.keySet());
        union.addAll(secondEnchantments.keySet());
        for (var enchantment : union) {
            if (curses.contains(enchantment)) {
                int level = Math.max(firstEnchantments.getOrDefault(enchantment, 0), secondEnchantments.getOrDefault(enchantment, 0));
                if (level <= 0) {
                    enchantments.remove(enchantment);
                } else {
                    enchantments.put(enchantment, Math.min(level, 255));
                }
            }
        }
        return repaired.with(type, new EnchantmentList(enchantments));
    }

    /**
     * 原版 {@code EnchantmentHelper.getComponentType}：附魔书的魔咒在 {@code stored_enchantments} 中。
     */
    private static DataComponent<EnchantmentList> enchantmentComponent(Material material) {
        return material == Material.ENCHANTED_BOOK ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
    }
}
