package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.FireworkExplosion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_special_firework_star_fade}，原版 {@code FireworkStarFadeRecipe}：一个烟火之星加若干染料，染料颜色成为淡化颜色。
 */
public record FireworkStarFadeRecipe(ItemIngredient target, ItemIngredient dye, ItemStackTemplate result) implements SpecialCraftingRecipe {
    /**
     * 原版 {@code FireworkExplosion.DEFAULT}。
     */
    private static final FireworkExplosion DEFAULT_EXPLOSION = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, List.of(), List.of(), false, false);

    public FireworkStarFadeRecipe {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(dye, "dye");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() < 2) {
            return false;
        }
        boolean hasDye = false;
        boolean hasTarget = false;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (dye.test(stack) && stack.has(DataComponents.DYE)) {
                hasDye = true;
            } else {
                if (!target.test(stack) || hasTarget) {
                    return false;
                }
                hasTarget = true;
            }
        }
        return hasTarget && hasDye;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        List<RGBLike> colors = new ArrayList<>();
        ItemStack targetStack = null;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (dye.test(stack)) {
                colors.add(stack.get(DataComponents.DYE, DyeColor.WHITE).fireworkColor());
            } else if (target.test(stack)) {
                targetStack = stack;
            }
        }
        if (targetStack == null || colors.isEmpty()) {
            return ItemStack.AIR;
        }
        var produced = result.applyOriginalComponents(targetStack, 0);
        var explosion = produced.get(DataComponents.FIREWORK_EXPLOSION, DEFAULT_EXPLOSION);
        return produced.with(DataComponents.FIREWORK_EXPLOSION,
                new FireworkExplosion(explosion.shape(), explosion.colors(), colors, explosion.hasTrail(), explosion.hasTwinkle()));
    }
}
