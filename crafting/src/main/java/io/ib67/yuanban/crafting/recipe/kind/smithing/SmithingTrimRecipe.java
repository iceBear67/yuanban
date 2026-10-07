package io.ib67.yuanban.crafting.recipe.kind.smithing;

import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SmithingRecipe;
import io.ib67.yuanban.crafting.recipe.SmithingRecipeInput;
import io.ib67.yuanban.crafting.recipe.kind.misc.PlacementInfo;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.component.ArmorTrim;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.registry.Holder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code smithing_trim}，原版 {@code SmithingTrimRecipe}：给基础物品加上纹饰，纹饰材料由附加物品的 {@code provides_trim_material} 决定。
 */
public record SmithingTrimRecipe(ItemIngredient template, ItemIngredient base, ItemIngredient addition,
                                 Holder<TrimPattern> pattern) implements SmithingRecipe {
    public SmithingTrimRecipe {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(addition, "addition");
        Objects.requireNonNull(pattern, "pattern");
    }

    /**
     * 原版 {@code SmithingTrimRecipe.applyTrim}：附加物品不提供纹饰材料，或者基础物品已经有同样的纹饰时没有结果。
     */
    @Override
    public ItemStack assemble(SmithingRecipeInput input, RecipeContext context) {
        var material = input.addition().get(DataComponents.PROVIDES_TRIM_MATERIAL);
        if (material == null) {
            return ItemStack.AIR;
        }
        var newTrim = new ArmorTrim(material, pattern);
        if (Objects.equals(input.base().get(DataComponents.TRIM), newTrim)) {
            return ItemStack.AIR;
        }
        return input.base().withAmount(1).with(DataComponents.TRIM, newTrim);
    }

    @Override
    public Optional<ItemIngredient> templateIngredient() {
        return Optional.of(template);
    }

    @Override
    public ItemIngredient baseIngredient() {
        return base;
    }

    @Override
    public Optional<ItemIngredient> additionIngredient() {
        return Optional.of(addition);
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(List.of(template, base, addition));
    }

    // FIXME(Minestom M2): SlotDisplay.SmithingTrim 把纹饰样式编码成 SlotDisplay，发出去会破坏数据包，
    //  所以锻造纹饰配方没有显示数据，见 MINESTOM-BUG-REPORT.md
    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }
}
