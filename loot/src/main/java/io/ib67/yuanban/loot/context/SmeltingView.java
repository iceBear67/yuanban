package io.ib67.yuanban.loot.context;

import net.minestom.server.item.ItemStack;

import java.util.Optional;

/**
 * 熔炼配方的只读视图：{@code furnace_smelt} 修饰器读取的最小面，对应原版
 * {@code level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)} 之后的 {@code assemble(input)}。
 * 宿主可以用 {@code crafting} 模块的熔炼配方实现它。
 */
public interface SmeltingView {
    /**
     * 按原版 {@code SingleRecipeInput} 查找与 {@code input} 匹配的熔炼配方，返回配方组装出的结果；
     * 没有匹配的配方时为空。结果的数量是配方本身的产出数量，与输入的数量无关。
     */
    Optional<ItemStack> smelt(ItemStack input);
}
