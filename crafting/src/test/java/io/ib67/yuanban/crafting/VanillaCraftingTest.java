package io.ib67.yuanban.crafting;

import io.ib67.yuanban.crafting.recipe.CraftingInput;
import io.ib67.yuanban.crafting.recipe.CraftingRecipe;
import io.ib67.yuanban.crafting.recipe.ShapedRecipe;
import io.ib67.yuanban.crafting.recipe.ShapelessRecipe;
import io.ib67.yuanban.crafting.recipe.TransmuteRecipe;
import io.ib67.yuanban.crafting.testing.InMemoryMaps;
import io.ib67.yuanban.itemtag.testing.ResetItemTags;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static io.ib67.yuanban.crafting.CraftingTableTest.open;
import static io.ib67.yuanban.crafting.CraftingTableTest.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原版 26.2 服务端 jar 中的全部合成配方：用每个原料的第一个物品摆出合成格检查匹配与产出；在背包中放入所需的物品，
 * 通过放置请求摆进工作台，检查合成格被这个配方匹配。
 */
@EnvTest
@ExtendWith(ResetItemTags.class)
class VanillaCraftingTest {
    @Test
    void everyRecipeMatchesItsOwnIngredients(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var context = crafting.recipes().context();
        int checked = 0;
        for (var entry : crafting.recipes().recipes().entrySet()) {
            var id = entry.getKey();
            var grid = new ArrayList<>(Collections.nCopies(9, ItemStack.AIR));
            switch (entry.getValue()) {
                case ShapedRecipe shaped -> {
                    for (int y = 0; y < shaped.height(); y++) {
                        for (int x = 0; x < shaped.width(); x++) {
                            int index = x + y * shaped.width();
                            int slot = x + y * 3;
                            shaped.ingredients().get(index).ifPresent(ingredient -> grid.set(slot, ItemStack.of(ingredient.items().getFirst())));
                        }
                    }
                    var input = CraftingInput.of(3, 3, grid);
                    assertTrue(shaped.matches(input, context), id::asString);
                    assertEquals(shaped.result().create(), shaped.assemble(input, context), id::asString);
                }
                case ShapelessRecipe shapeless -> {
                    for (int i = 0; i < shapeless.ingredients().size(); i++) {
                        grid.set(i, ItemStack.of(shapeless.ingredients().get(i).items().getFirst()));
                    }
                    var input = CraftingInput.of(3, 3, grid);
                    assertTrue(shapeless.matches(input, context), id::asString);
                    assertEquals(shapeless.result().create(), shapeless.assemble(input, context), id::asString);
                }
                case TransmuteRecipe transmute -> {
                    // 选择与结果不同种类的输入物品，结果不会与输入完全相同
                    var input = transmute.input().items().stream()
                            .filter(material -> material != transmute.result().material())
                            .findFirst()
                            .orElse(transmute.input().items().getFirst());
                    grid.set(0, ItemStack.of(input));
                    int materials = transmute.materialCount().min().orElse(1);
                    for (int i = 0; i < materials; i++) {
                        grid.set(1 + i, ItemStack.of(transmute.material().items().getFirst()));
                    }
                    var craftingInput = CraftingInput.of(3, 3, grid);
                    assertTrue(transmute.matches(craftingInput, context), id::asString);
                    assertFalse(transmute.assemble(craftingInput, context).isAir(), id::asString);
                }
                default -> {
                    continue;
                }
            }
            checked++;
        }
        assertTrue(checked > 1000, "checked " + checked);
    }

    @Test
    void everyPlaceableRecipeCanBePlaced(Env env) {
        var setup = setup(env);
        var table = open(setup);
        var inventory = setup.player().getInventory();
        var recipes = setup.crafting().recipes();
        int placed = 0;
        for (int displayId = 0; recipes.displayInfo(displayId).isPresent(); displayId++) {
            var info = recipes.displayInfo(displayId).orElseThrow();
            if (!(info.parent().value() instanceof CraftingRecipe recipe) || recipe.placementInfo().isImpossibleToPlace()) {
                continue;
            }
            inventory.clear();
            table.clear();
            var needed = new LinkedHashMap<Material, Integer>();
            for (var ingredient : recipe.placementInfo().ingredients()) {
                needed.merge(ingredient.items().getFirst(), 1, Integer::sum);
            }
            // 每格不超过最大堆叠数，例如牛奶桶每格一个
            int slot = 0;
            for (var item : needed.entrySet()) {
                int remaining = item.getValue();
                while (remaining > 0) {
                    int count = Math.min(remaining, item.getKey().maxStackSize());
                    inventory.setItemStack(slot++, ItemStack.of(item.getKey(), count));
                    remaining -= count;
                }
            }
            setup.client().placeRecipe(table.getWindowId(), displayId, false);

            var grid = new ArrayList<ItemStack>(9);
            for (int i = 1; i <= 9; i++) {
                grid.add(table.getItemStack(i));
            }
            var input = CraftingInput.of(3, 3, grid);
            var id = info.parent().id().asString();
            assertTrue(recipe.matches(input, recipes.context()), id);
            // 放置结束后带上配方提示计算结果，结果格是这个配方的产出
            assertEquals(recipe.assemble(input, recipes.context()), table.getItemStack(0), id);
            placed++;
        }
        assertTrue(placed > 1000, "placed " + placed);
    }

    /**
     * 原版 26.2 中不能放置的合成配方只有特殊配方。
     */
    @Test
    void onlySpecialRecipesCannotBePlaced(Env env) {
        var crafting = TestWorld.loadVanilla(env.process(), new InMemoryMaps());
        var unplaceable = new ArrayList<String>();
        crafting.recipes().recipes().forEach((id, recipe) -> {
            if (!recipe.isSpecial() && recipe.placementInfo().isImpossibleToPlace()) {
                unplaceable.add(id.asString());
            }
        });
        assertEquals(List.of(), unplaceable);
    }
}
