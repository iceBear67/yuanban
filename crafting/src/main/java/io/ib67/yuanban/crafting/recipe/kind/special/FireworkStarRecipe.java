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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code crafting_special_firework_star}，原版 {@code FireworkStarRecipe}：一个燃料、至少一个染料，
 * 可选一个决定爆炸类型的物品、一个拖尾物品、一个闪烁物品。{@code shapes} 保持配方文件中的顺序，查找爆炸类型时按这个顺序。
 */
public record FireworkStarRecipe(Map<FireworkExplosion.Shape, ItemIngredient> shapes, ItemIngredient trail, ItemIngredient twinkle,
                                 ItemIngredient fuel, ItemIngredient dye, ItemStackTemplate result) implements SpecialCraftingRecipe {
    public FireworkStarRecipe {
        shapes = Collections.unmodifiableMap(new LinkedHashMap<>(shapes));
        Objects.requireNonNull(trail, "trail");
        Objects.requireNonNull(twinkle, "twinkle");
        Objects.requireNonNull(fuel, "fuel");
        Objects.requireNonNull(dye, "dye");
        Objects.requireNonNull(result, "result");
    }

    private FireworkExplosion.@Nullable Shape findShape(ItemStack stack) {
        for (var entry : shapes.entrySet()) {
            if (entry.getValue().test(stack)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() < 2) {
            return false;
        }
        boolean hasFuel = false;
        boolean hasDye = false;
        boolean hasShape = false;
        boolean hasTrail = false;
        boolean hasTwinkle = false;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (twinkle.test(stack)) {
                if (hasTwinkle) {
                    return false;
                }
                hasTwinkle = true;
            } else if (trail.test(stack)) {
                if (hasTrail) {
                    return false;
                }
                hasTrail = true;
            } else if (fuel.test(stack)) {
                if (hasFuel) {
                    return false;
                }
                hasFuel = true;
            } else if (dye.test(stack) && stack.has(DataComponents.DYE)) {
                hasDye = true;
            } else {
                if (findShape(stack) == null || hasShape) {
                    return false;
                }
                hasShape = true;
            }
        }
        return hasFuel && hasDye;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        var shape = FireworkExplosion.Shape.SMALL_BALL;
        boolean hasTwinkle = false;
        boolean hasTrail = false;
        List<RGBLike> colors = new ArrayList<>();
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            var maybeShape = findShape(stack);
            if (maybeShape != null) {
                shape = maybeShape;
            } else if (twinkle.test(stack)) {
                hasTwinkle = true;
            } else if (trail.test(stack)) {
                hasTrail = true;
            } else if (dye.test(stack)) {
                colors.add(stack.get(DataComponents.DYE, DyeColor.WHITE).fireworkColor());
            }
        }
        return result.create().with(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(shape, colors, List.of(), hasTrail, hasTwinkle));
    }
}
