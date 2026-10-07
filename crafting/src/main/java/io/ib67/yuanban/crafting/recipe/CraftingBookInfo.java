package io.ib67.yuanban.crafting.recipe;

import java.util.Objects;

/**
 * 原版 {@code CraftingRecipe.CraftingBookInfo}：合成配方在配方书中的类别和分组。
 */
public record CraftingBookInfo(CraftingCategory category, String group) {
    public CraftingBookInfo {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(group, "group");
    }
}
