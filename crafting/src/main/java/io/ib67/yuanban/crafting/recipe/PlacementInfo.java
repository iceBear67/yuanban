package io.ib67.yuanban.crafting.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 原版 {@code PlacementInfo}：配方书放置配方时需要的原料，以及配方的每一格对应第几个原料（空格为 -1）。
 * 有原料是空标签的配方不能放置，两个列表都为空。
 */
public record PlacementInfo(List<ItemIngredient> ingredients, List<Integer> slotsToIngredientIndex) {
    public static final int EMPTY_SLOT = -1;
    public static final PlacementInfo NOT_PLACEABLE = new PlacementInfo(List.of(), List.of());

    public PlacementInfo {
        ingredients = List.copyOf(ingredients);
        slotsToIngredientIndex = List.copyOf(slotsToIngredientIndex);
    }

    public static PlacementInfo create(ItemIngredient ingredient) {
        return ingredient.isEmpty() ? NOT_PLACEABLE : new PlacementInfo(List.of(ingredient), List.of(0));
    }

    public static PlacementInfo createFromOptionals(List<Optional<ItemIngredient>> ingredients) {
        var present = new ArrayList<ItemIngredient>(ingredients.size());
        var slots = new ArrayList<Integer>(ingredients.size());
        int placementIndex = 0;
        for (var maybe : ingredients) {
            if (maybe.isPresent()) {
                var ingredient = maybe.get();
                if (ingredient.isEmpty()) {
                    return NOT_PLACEABLE;
                }
                present.add(ingredient);
                slots.add(placementIndex++);
            } else {
                slots.add(EMPTY_SLOT);
            }
        }
        return new PlacementInfo(present, slots);
    }

    public static PlacementInfo create(List<ItemIngredient> ingredients) {
        var slots = new ArrayList<Integer>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).isEmpty()) {
                return NOT_PLACEABLE;
            }
            slots.add(i);
        }
        return new PlacementInfo(ingredients, slots);
    }

    public boolean isImpossibleToPlace() {
        return slotsToIngredientIndex.isEmpty();
    }
}
