package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.internal.item.BannerColors;
import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingRemainders;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.BannerPatterns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_special_bannerduplicate}，原版 {@code BannerDuplicateRecipe}：同色的一面有图案的旗帜和一面空白旗帜，
 * 复制出有图案的旗帜，原旗帜留在合成格中。
 */
public record BannerDuplicateRecipe(ItemIngredient banner, ItemStackTemplate result) implements SpecialCraftingRecipe {
    private static final int MAX_PATTERNS = 6;
    private static final BannerPatterns NO_PATTERNS = new BannerPatterns(List.of());

    public BannerDuplicateRecipe {
        Objects.requireNonNull(banner, "banner");
        Objects.requireNonNull(result, "result");
    }

    private static int patternCount(ItemStack stack) {
        return stack.get(DataComponents.BANNER_PATTERNS, NO_PATTERNS).layers().size();
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() != 2) {
            return false;
        }
        DyeColor color = null;
        boolean hasTarget = false;
        boolean hasSource = false;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            var bannerColor = BannerColors.of(stack.material());
            if (!banner.test(stack) || bannerColor == null) {
                return false;
            }
            if (color == null) {
                color = bannerColor;
            } else if (color != bannerColor) {
                return false;
            }
            int patterns = patternCount(stack);
            if (patterns > MAX_PATTERNS) {
                return false;
            }
            if (patterns > 0) {
                if (hasSource) {
                    return false;
                }
                hasSource = true;
            } else {
                if (hasTarget) {
                    return false;
                }
                hasTarget = true;
            }
        }
        return hasSource && hasTarget;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            int patterns = patternCount(stack);
            if (patterns > 0 && patterns <= MAX_PATTERNS) {
                return result.applyOriginalComponents(stack, 0);
            }
        }
        return ItemStack.AIR;
    }

    /**
     * 有合成剩余物的格子留下剩余物；有图案的旗帜留下一面。
     */
    @Override
    public List<ItemStack> remainingItems(CraftingInput input) {
        var result = new ArrayList<>(Collections.nCopies(input.size(), ItemStack.AIR));
        for (int slot = 0; slot < result.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            var remainder = CraftingRemainders.of(stack.material());
            if (remainder != null) {
                result.set(slot, ItemStack.of(remainder));
            } else if (patternCount(stack) > 0) {
                result.set(slot, stack.withAmount(1));
            }
        }
        return result;
    }
}
