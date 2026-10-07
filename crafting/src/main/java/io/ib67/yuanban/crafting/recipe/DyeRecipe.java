package io.ib67.yuanban.crafting.recipe;

import net.minestom.server.color.Color;
import net.minestom.server.color.DyeColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import net.kyori.adventure.util.RGBLike;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@code crafting_dye}，原版 {@code DyeRecipe}：一个目标物品加若干带 {@code dye} 组件的染料。
 */
public record DyeRecipe(CraftingBookInfo book, ItemIngredient target, ItemIngredient dye, ItemStackTemplate result) implements CraftingRecipe {
    public DyeRecipe {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(dye, "dye");
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean matches(CraftingInput input, RecipeContext context) {
        if (input.ingredientCount() < 2) {
            return false;
        }
        boolean hasTarget = false;
        boolean hasDyes = false;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (target.test(stack)) {
                if (hasTarget) {
                    return false;
                }
                hasTarget = true;
            } else {
                if (!dye.test(stack) || !stack.has(DataComponents.DYE)) {
                    return false;
                }
                hasDyes = true;
            }
        }
        return hasDyes && hasTarget;
    }

    @Override
    public ItemStack assemble(CraftingInput input, RecipeContext context) {
        var dyes = new ArrayList<DyeColor>();
        var targetStack = ItemStack.AIR;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isAir()) {
                continue;
            }
            if (target.test(stack)) {
                if (!targetStack.isAir()) {
                    return ItemStack.AIR;
                }
                targetStack = stack;
            } else {
                if (!dye.test(stack)) {
                    return ItemStack.AIR;
                }
                dyes.add(stack.get(DataComponents.DYE, DyeColor.WHITE));
            }
        }
        if (targetStack.isAir() || dyes.isEmpty()) {
            return ItemStack.AIR;
        }
        var color = applyDyes(targetStack.get(DataComponents.DYED_COLOR), dyes);
        return result.applyOriginalComponents(targetStack, 0).with(DataComponents.DYED_COLOR, color);
    }

    /**
     * 原版 {@code DyedItemColor.applyDyes}：现有颜色与各染料颜色的分量取平均，再按各颜色最大分量的平均值调整亮度。
     */
    static RGBLike applyDyes(@Nullable RGBLike current, List<DyeColor> dyes) {
        int redTotal = 0;
        int greenTotal = 0;
        int blueTotal = 0;
        int intensityTotal = 0;
        int colorCount = 0;
        if (current != null) {
            intensityTotal += Math.max(current.red(), Math.max(current.green(), current.blue()));
            redTotal += current.red();
            greenTotal += current.green();
            blueTotal += current.blue();
            colorCount++;
        }
        for (var dye : dyes) {
            var color = dye.color();
            intensityTotal += Math.max(color.red(), Math.max(color.green(), color.blue()));
            redTotal += color.red();
            greenTotal += color.green();
            blueTotal += color.blue();
            colorCount++;
        }
        int red = redTotal / colorCount;
        int green = greenTotal / colorCount;
        int blue = blueTotal / colorCount;
        float averageIntensity = (float) intensityTotal / colorCount;
        float resultIntensity = Math.max(red, Math.max(green, blue));
        red = (int) (red * averageIntensity / resultIntensity);
        green = (int) (green * averageIntensity / resultIntensity);
        blue = (int) (blue * averageIntensity / resultIntensity);
        return new Color(red, green, blue);
    }

    @Override
    public String group() {
        return book.group();
    }

    @Override
    public CraftingCategory category() {
        return book.category();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(List.of(target, dye));
    }

    @Override
    public List<RecipeDisplay> display() {
        var dyes = new SlotDisplay.OnlyWithComponent(dye.display(), DataComponents.DYE);
        var targetDisplay = target.display();
        return List.of(new RecipeDisplay.CraftingShapeless(List.of(targetDisplay, dyes), new SlotDisplay.Dyed(dyes, targetDisplay), CRAFTING_TABLE));
    }
}
