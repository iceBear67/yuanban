package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.recipe.kind.misc.PlacementInfo;
import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.display.RecipeDisplay;

import java.util.List;

/**
 * 原版 {@code Recipe}：一个配方的匹配规则、产出和配方书数据。实现都是不可变的 record，对应原版 26.2 的配方序列化器。
 *
 * @param <I> 匹配时读取的输入
 */
public interface Recipe<I extends RecipeInput> {
    RecipeType<?> type();

    boolean matches(I input, RecipeContext context);

    /**
     * 按输入产出结果。结果经过原版 {@code ItemStack.validateStrict}，不合法时为 {@link ItemStack#AIR}。
     */
    ItemStack assemble(I input, RecipeContext context);

    /**
     * 产出取决于输入物品组件的配方，原版 {@code CustomRecipe}。它们没有配方书显示，不能放置。
     */
    default boolean isSpecial() {
        return false;
    }

    String group();

    /**
     * 原版各配方类的 {@code createPlacementInfo()}；特殊配方为 {@link PlacementInfo#NOT_PLACEABLE}。
     */
    PlacementInfo placementInfo();

    /**
     * 配方书中的显示数据，使用 Minestom 的显示数据类型做网络编码。
     */
    List<RecipeDisplay> display();

    RecipeBookCategory recipeBookCategory();
}
