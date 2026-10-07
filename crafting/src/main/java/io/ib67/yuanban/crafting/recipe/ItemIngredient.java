package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingRemainders;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.display.SlotDisplay;
import net.minestom.server.registry.TagKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 配方的原料，对应原版 {@code Ingredient}。
 */
public sealed interface ItemIngredient {
    /**
     * 原料接受的物品。标签原料是这个标签在本次加载中展开得到的物品，可能为空。
     */
    List<Material> items();

    /**
     * 原版 {@code Ingredient.test}：只比较物品种类。原料不含空气，所以空物品不会通过。
     */
    default boolean test(ItemStack stack) {
        return items().contains(stack.material());
    }

    /**
     * 原版 {@code Ingredient.acceptsItem}。
     */
    default boolean acceptsItem(Material material) {
        return items().contains(material);
    }

    default boolean isEmpty() {
        return items().isEmpty();
    }

    /**
     * 原版 {@code Ingredient.display()}：标签原料显示为标签；物品列表显示为 {@code Composite}，
     * 有合成剩余物的物品包装成 {@code WithRemainder}。
     */
    SlotDisplay display();

    /**
     * 网络编码使用的 Minestom 原料。标签原料引用 Minestom 注册表中的标签，{@code item-tag} 在配方之前提交。
     */
    Ingredient toNetwork();

    /**
     * 原版 {@code Ingredient.testOptionalIngredient}：没有原料的位置只接受空物品。
     */
    public static boolean testOptional(Optional<ItemIngredient> ingredient, ItemStack stack) {
        return ingredient.map(value -> value.test(stack)).orElseGet(stack::isAir);
    }

    /**
     * 原版 {@code Ingredient.optionalIngredientToDisplay}。
     */
    public static SlotDisplay displayOptional(Optional<ItemIngredient> ingredient) {
        return ingredient.map(ItemIngredient::display).orElse(SlotDisplay.Empty.INSTANCE);
    }

    /**
     * 写作 {@code "#<tag>"} 的原料。
     */
    record OfTag(Key tag, List<Material> items) implements ItemIngredient {
        public OfTag {
            Objects.requireNonNull(tag, "tag");
            items = List.copyOf(items);
        }

        @Override
        public SlotDisplay display() {
            return new SlotDisplay.Tag(TagKey.of(tag));
        }

        @Override
        public Ingredient toNetwork() {
            return new Ingredient(Objects.requireNonNull(Material.staticRegistry().getTag(TagKey.of(tag)),
                    () -> "item tag " + tag.asString() + " is not registered"));
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

        @Override
        public SlotDisplay display() {
            return new SlotDisplay.Composite(items.stream().map(OfItems::displayForSingleItem).toList());
        }

        @Override
        public Ingredient toNetwork() {
            return new Ingredient(items);
        }

        private static SlotDisplay displayForSingleItem(Material material) {
            var input = new SlotDisplay.Item(material);
            var remainder = CraftingRemainders.of(material);
            return remainder == null ? input : new SlotDisplay.WithRemainder(input, new SlotDisplay.ItemStack(ItemStack.of(remainder)));
        }
    }
}
