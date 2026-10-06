package io.ib67.yuanban.recipe.definition;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;

import java.util.List;
import java.util.Objects;

/**
 * 配方的原料，对应原版 {@code Ingredient}。
 */
public sealed interface ItemIngredient {
    /**
     * 原料接受的物品。标签原料是这个标签在本次加载中展开得到的物品，可能为空。
     */
    List<Material> items();

    /**
     * 写作 {@code "#<tag>"} 的原料。
     */
    record OfTag(Key tag, List<Material> items) implements ItemIngredient {
        public OfTag {
            Objects.requireNonNull(tag, "tag");
            items = List.copyOf(items);
        }
    }

    /**
     * 写作单个物品 ID 或物品 ID 列表的原料，列表不为空，保持文件中的顺序。
     */
    record OfItems(List<Material> items) implements ItemIngredient {
        public OfItems {
            items = List.copyOf(items);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("ingredient item list must not be empty");
            }
        }
    }
}
