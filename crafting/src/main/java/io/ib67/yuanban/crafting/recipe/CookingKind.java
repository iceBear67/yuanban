package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.item.Material;

/**
 * 四种烧炼配方，{@code defaultCookingTime} 是 {@code cookingtime} 缺省时的取值。
 */
public enum CookingKind {
    SMELTING("smelting", 200, Material.FURNACE),
    BLASTING("blasting", 100, Material.BLAST_FURNACE),
    SMOKING("smoking", 100, Material.SMOKER),
    CAMPFIRE_COOKING("campfire_cooking", 100, Material.CAMPFIRE);

    private final String type;
    private final int defaultCookingTime;
    private final Material furnaceIcon;

    CookingKind(String type, int defaultCookingTime, Material furnaceIcon) {
        this.type = type;
        this.defaultCookingTime = defaultCookingTime;
        this.furnaceIcon = furnaceIcon;
    }

    /**
     * 配方文件中 {@code type} 的路径部分。
     */
    public String type() {
        return type;
    }

    public int defaultCookingTime() {
        return defaultCookingTime;
    }

    /**
     * 原版各烧炼配方类的 {@code furnaceIcon()}，用作显示数据中的工作方块。
     */
    public Material furnaceIcon() {
        return furnaceIcon;
    }

    public RecipeType<CookingRecipe> recipeType() {
        return switch (this) {
            case SMELTING -> RecipeType.SMELTING;
            case BLASTING -> RecipeType.BLASTING;
            case SMOKING -> RecipeType.SMOKING;
            case CAMPFIRE_COOKING -> RecipeType.CAMPFIRE_COOKING;
        };
    }
}
