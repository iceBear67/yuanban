package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.recipe.kind.cooking.CookingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.stonecutter.StonecutterRecipe;
import net.kyori.adventure.key.Key;

/**
 * 原版 {@code RecipeType}：同一类型的配方使用同一种输入，在同一种方块中使用。
 *
 * @param <R> 这个类型的配方
 */
public final class RecipeType<R extends Recipe<?>> {
    public static final RecipeType<CraftingRecipe> CRAFTING = new RecipeType<>(Key.key("crafting"));
    public static final RecipeType<CookingRecipe> SMELTING = new RecipeType<>(Key.key("smelting"));
    public static final RecipeType<CookingRecipe> BLASTING = new RecipeType<>(Key.key("blasting"));
    public static final RecipeType<CookingRecipe> SMOKING = new RecipeType<>(Key.key("smoking"));
    public static final RecipeType<CookingRecipe> CAMPFIRE_COOKING = new RecipeType<>(Key.key("campfire_cooking"));
    public static final RecipeType<StonecutterRecipe> STONECUTTING = new RecipeType<>(Key.key("stonecutting"));
    public static final RecipeType<SmithingRecipe> SMITHING = new RecipeType<>(Key.key("smithing"));

    private final Key id;

    private RecipeType(Key id) {
        this.id = id;
    }

    public Key id() {
        return id;
    }

    @Override
    public String toString() {
        return "RecipeType[" + id.asString() + "]";
    }
}
