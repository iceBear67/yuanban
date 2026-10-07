package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.crafting.ShapedRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.MapPostProcessing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code crafting_special_mapextending}，原版 {@code MapExtendingRecipe}：中心一张已绘制的地图，周围八个材料，结果在取出时放大一级。
 */
public record MapExtendingRecipe(ItemIngredient map, ItemIngredient material, ItemStackTemplate result) implements SpecialCraftingRecipe {
    private static final int MAX_SCALE = 4;

    public MapExtendingRecipe {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(result, "result");
    }

    /**
     * 原版 {@code ShapedRecipePattern.of(Map.of('#', material, 'x', map), "###", "#x#", "###")}。
     */
    private List<Optional<ItemIngredient>> pattern() {
        var m = Optional.of(material);
        return List.of(m, m, m, m, Optional.of(map), m, m, m, m);
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (!ShapedRecipe.matches(3, 3, pattern(), input)) {
            return false;
        }
        var filledMap = findFilledMap(input);
        if (filledMap.isAir()) {
            return false;
        }
        return context.maps().find(filledMap.get(DataComponents.MAP_ID))
                .map(data -> !data.explorationMap() && data.scale() < MAX_SCALE)
                .orElse(false);
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        return result.applyOriginalComponents(findFilledMap(input), 0).with(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
    }

    private static ItemStack findFilledMap(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if (stack.has(DataComponents.MAP_ID)) {
                return stack;
            }
        }
        return ItemStack.AIR;
    }
}
