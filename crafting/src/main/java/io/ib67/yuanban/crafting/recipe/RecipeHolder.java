package io.ib67.yuanban.crafting.recipe;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/**
 * 原版 {@code RecipeHolder}：配方与它的 ID。
 */
public record RecipeHolder<R extends Recipe<?>>(Key id, R value) {
    public RecipeHolder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
    }
}
