package io.ib67.yuanban.crafting.recipe.kind.cooking;

import java.util.Objects;

/**
 * 原版 {@code AbstractCookingRecipe.CookingBookInfo}：烧炼配方在配方书中的类别和分组。
 */
public record CookingBookInfo(CookingCategory category, String group) {
    public CookingBookInfo {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(group, "group");
    }
}
