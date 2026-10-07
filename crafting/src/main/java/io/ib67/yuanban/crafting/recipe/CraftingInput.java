package io.ib67.yuanban.crafting.recipe;

import io.ib67.yuanban.crafting.internal.item.StackedItemContents;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 {@code CraftingInput}：合成格去掉四周空行空列之后的内容，按行排列。
 */
public final class CraftingInput implements RecipeInput {
    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());

    private final int width;
    private final int height;
    private final List<ItemStack> items;
    private final StackedItemContents stackedContents = new StackedItemContents();
    private final int ingredientCount;

    private CraftingInput(int width, int height, List<ItemStack> items) {
        this.width = width;
        this.height = height;
        this.items = List.copyOf(items);
        int count = 0;
        for (var item : this.items) {
            if (!item.isAir()) {
                count++;
                stackedContents.accountStack(item, 1);
            }
        }
        this.ingredientCount = count;
    }

    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        return ofPositioned(width, height, items).input();
    }

    /**
     * 原版 {@code CraftingInput.ofPositioned}：同时给出去掉的左侧列数和顶部行数。
     */
    public static Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        if (width == 0 || height == 0) {
            return Positioned.EMPTY;
        }
        int left = width - 1;
        int right = 0;
        int top = height - 1;
        int bottom = 0;
        for (int y = 0; y < height; y++) {
            boolean rowEmpty = true;
            for (int x = 0; x < width; x++) {
                if (!items.get(x + y * width).isAir()) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    rowEmpty = false;
                }
            }
            if (!rowEmpty) {
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        int newWidth = right - left + 1;
        int newHeight = bottom - top + 1;
        if (newWidth <= 0 || newHeight <= 0) {
            return Positioned.EMPTY;
        }
        if (newWidth == width && newHeight == height) {
            return new Positioned(new CraftingInput(width, height, items), left, top);
        }
        var newItems = new ArrayList<ItemStack>(newWidth * newHeight);
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                newItems.add(items.get(x + left + (y + top) * width));
            }
        }
        return new Positioned(new CraftingInput(newWidth, newHeight, newItems), left, top);
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    public ItemStack getItem(int x, int y) {
        return items.get(x + y * width);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return ingredientCount == 0;
    }

    /**
     * 每个非空格子计一个物品。
     */
    @ApiStatus.Internal
    public StackedItemContents stackedContents() {
        return stackedContents;
    }

    public List<ItemStack> items() {
        return items;
    }

    public int ingredientCount() {
        return ingredientCount;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public record Positioned(CraftingInput input, int left, int top) {
        public static final Positioned EMPTY = new Positioned(CraftingInput.EMPTY, 0, 0);
    }
}
