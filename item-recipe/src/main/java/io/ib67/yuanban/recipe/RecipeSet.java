package io.ib67.yuanban.recipe;

import io.ib67.yuanban.recipe.definition.RecipeDefinition;
import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一次加载解码得到的全部配方，按配方 ID 的自然顺序排列。
 */
public record RecipeSet(NavigableMap<Key, RecipeDefinition> recipes) {
    public static final RecipeSet EMPTY = new RecipeSet(new TreeMap<>());

    public RecipeSet {
        Objects.requireNonNull(recipes, "recipes");
        recipes = Collections.unmodifiableNavigableMap(new TreeMap<>(recipes));
    }
}
