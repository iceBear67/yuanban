package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.internal.item.BannerColors;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.BannerPatterns;

import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_special_shielddecoration}，原版 {@code ShieldDecorationRecipe}：一面旗帜和一个没有图案的盾牌，盾牌带上旗帜的图案和底色。
 */
public record ShieldDecorationRecipe(ItemIngredient banner, ItemIngredient target, ItemStackTemplate result) implements SpecialCraftingRecipe {
    private static final BannerPatterns NO_PATTERNS = new BannerPatterns(List.of());

    public ShieldDecorationRecipe {
        Objects.requireNonNull(banner, "banner");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() != 2) {
            return false;
        }
        boolean hasClearTarget = false;
        boolean hasPatternBanner = false;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (banner.test(stack) && BannerColors.of(stack.material()) != null) {
                if (hasPatternBanner) {
                    return false;
                }
                hasPatternBanner = true;
            } else {
                if (!target.test(stack) || hasClearTarget) {
                    return false;
                }
                if (!stack.get(DataComponents.BANNER_PATTERNS, NO_PATTERNS).layers().isEmpty()) {
                    return false;
                }
                hasClearTarget = true;
            }
        }
        return hasClearTarget && hasPatternBanner;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        BannerPatterns patterns = null;
        var baseColor = DyeColor.WHITE;
        var targetStack = ItemStack.AIR;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            var bannerColor = BannerColors.of(stack.material());
            if (banner.test(stack) && bannerColor != null) {
                patterns = stack.get(DataComponents.BANNER_PATTERNS);
                baseColor = bannerColor;
            } else if (target.test(stack)) {
                targetStack = stack;
            }
        }
        var produced = result.applyOriginalComponents(targetStack, 0);
        // 原版 set 传入 null 的时候移除这个组件
        produced = patterns == null ? produced.without(DataComponents.BANNER_PATTERNS) : produced.with(DataComponents.BANNER_PATTERNS, patterns);
        return produced.with(DataComponents.BASE_COLOR, baseColor);
    }
}
