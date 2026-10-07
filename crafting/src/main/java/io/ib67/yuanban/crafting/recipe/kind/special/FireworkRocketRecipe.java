package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import net.minestom.server.component.DataComponentMap;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;

import java.util.ArrayList;
import java.util.Objects;

/**
 * {@code crafting_special_firework_rocket}，原版 {@code FireworkRocketRecipe}：一个外壳、一到三个燃料、任意个烟火之星。
 */
public record FireworkRocketRecipe(ItemIngredient shell, ItemIngredient fuel, ItemIngredient star,
                                   ItemStackTemplate result) implements SpecialCraftingRecipe {
    private static final int MAX_FUEL = 3;

    public FireworkRocketRecipe {
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(star, "star");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() < 2) {
            return false;
        }
        boolean hasShell = false;
        int fuelCount = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (shell.test(stack)) {
                if (hasShell) {
                    return false;
                }
                hasShell = true;
            } else if (fuel.test(stack)) {
                if (++fuelCount > MAX_FUEL) {
                    return false;
                }
            } else if (!star.test(stack)) {
                return false;
            }
        }
        return hasShell && fuelCount >= 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        var explosions = new ArrayList<FireworkExplosion>();
        int fuelCount = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (fuel.test(stack)) {
                fuelCount++;
            } else if (star.test(stack)) {
                var explosion = stack.get(DataComponents.FIREWORK_EXPLOSION);
                if (explosion != null) {
                    explosions.add(explosion);
                }
            }
        }
        return result.apply(DataComponentMap.EMPTY.set(DataComponents.FIREWORKS, new FireworkList(fuelCount, explosions)));
    }
}
