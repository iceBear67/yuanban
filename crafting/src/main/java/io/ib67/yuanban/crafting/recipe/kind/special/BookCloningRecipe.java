package io.ib67.yuanban.crafting.recipe.kind.special;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.ItemIngredient;
import io.ib67.yuanban.crafting.recipe.RecipeContext;
import io.ib67.yuanban.crafting.recipe.SpecialCraftingRecipe;
import io.ib67.yuanban.crafting.recipe.kind.misc.IntBounds;
import io.ib67.yuanban.crafting.recipe.kind.crafting.CraftingRemainders;
import io.ib67.yuanban.crafting.recipe.kind.misc.ItemStackTemplate;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_special_bookcloning}，原版 {@code BookCloningRecipe}：一本成书加若干书与笔，复制出代数加一的成书，原书留在合成格中。
 */
public record BookCloningRecipe(ItemIngredient source, ItemIngredient material, IntBounds allowedGenerations,
                                ItemStackTemplate result) implements SpecialCraftingRecipe {
    public BookCloningRecipe {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(allowedGenerations, "allowedGenerations");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() < 2) {
            return false;
        }
        boolean hasMaterial = false;
        boolean hasSource = false;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (source.test(stack)) {
                var content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (content == null || !allowedGenerations.matches(content.generation())) {
                    return false;
                }
                if (hasSource) {
                    return false;
                }
                hasSource = true;
            } else {
                if (!material.test(stack)) {
                    return false;
                }
                hasMaterial = true;
            }
        }
        return hasSource && hasMaterial;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        int count = 0;
        var sourceStack = ItemStack.AIR;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (source.test(stack) && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                if (!sourceStack.isAir()) {
                    return ItemStack.AIR;
                }
                sourceStack = stack;
            } else {
                if (!material.test(stack)) {
                    return ItemStack.AIR;
                }
                count++;
            }
        }
        var content = sourceStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return ItemStack.AIR;
        }
        var copied = new WrittenBookContent(content.title(), content.author(), content.generation() + 1, content.pages(), content.resolved());
        return result.applyOriginalComponents(sourceStack, count - 1).with(DataComponents.WRITTEN_BOOK_CONTENT, copied);
    }

    /**
     * 有合成剩余物的格子留下剩余物；第一本成书留在原处，之后的格子不再检查成书。
     */
    @Override
    public List<ItemStack> remainingItems(CraftingInput input) {
        var result = new ArrayList<>(Collections.nCopies(input.size(), ItemStack.AIR));
        for (int slot = 0; slot < result.size(); slot++) {
            var stack = input.getItem(slot);
            var remainder = CraftingRemainders.of(stack.material());
            if (remainder != null) {
                result.set(slot, ItemStack.of(remainder));
            } else if (stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                result.set(slot, stack.withAmount(1));
                break;
            }
        }
        return result;
    }
}
